package com.example.historyq.service;

import com.example.historyq.entity.Document;
import com.example.historyq.repository.DocumentRepository;
import com.example.historyq.storage.DocumentStorageService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.UUID;
import com.example.historyq.service.OcrService;
import java.util.Set;
@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentStorageService storageService;
    private final OcrService ocrService;
    private static final Set<String>ALLOWED_EXTENSIONS=Set.of(".jpg",".jpeg",".png",".tiff",".webp");

    public DocumentService(
            DocumentRepository documentRepository,
            DocumentStorageService storageService,
            OcrService ocrService
    ) {
        this.documentRepository = documentRepository;
        this.storageService = storageService;
        this.ocrService = ocrService;
    }

    public Document upload(MultipartFile file) throws Exception {

        // 1. Generazione UUID
        String originalFilename = file.getOriginalFilename();

        if (originalFilename == null || !originalFilename.contains(".")) {
            throw new IllegalArgumentException("Il file deve avere un'estensione");
        }

        String extension = originalFilename.substring(
                originalFilename.lastIndexOf(".")
        ).toLowerCase();

        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException(
                    "Formato non supportato: " + extension
            );
        }

        UUID documentId = UUID.randomUUID();

        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(
                    originalFilename.lastIndexOf(".")
            );
        }


        // 2. Costruzione del percorso nello storage
        String storedFilename = "original" + extension;

        String storagePath =
                "documents/" + documentId + "/" + storedFilename;

        // 3. Upload del file su Silo
        storageService.upload(file, storagePath);
        try {
        var ocrJob=ocrService.submitJob(file);
        var ocrStatus=ocrService.getJobStatus(UUID.fromString(ocrJob.getJobId()));
        System.out.println("OCR JOB: " + ocrJob.getJobId());
        System.out.println("OCR STATUS: " + ocrStatus.getStatus());
        long startTime = System.currentTimeMillis();
        long timeout = 2 * 60 * 1000L;
        while (
                (
                        ocrStatus.getStatus().equals("PENDING")
                                || ocrStatus.getStatus().equals("PROCESSING")
                )
                        && (System.currentTimeMillis() - startTime) < timeout
        ) {
            Thread.sleep(1000);
            ocrStatus=ocrService.getJobStatus(UUID.fromString(ocrJob.getJobId()));
            System.out.println("OCR STATUS: " + ocrStatus.getStatus());
        }
        if (
                ocrStatus.getStatus().equals("PENDING")
                        || ocrStatus.getStatus().equals("PROCESSING")
        ) {
            throw new RuntimeException("Timeout OCR");
        }

            if (ocrStatus.getStatus().equals("FAILED")) {
                var ocrResult = ocrService.getJobResult(
                        UUID.fromString(ocrJob.getJobId())
                );

                throw new RuntimeException(
                        "OCR fallito: " + ocrResult.getError()
                );
            }
        var ocrResult=ocrService.getJobResult(UUID.fromString(ocrJob.getJobId()));



            // 4. Creazione dell'entità Document
            Document document = new Document();

            document.setId(documentId);
            if (file == null || file.isEmpty()) {
                throw new IllegalArgumentException("Il file è obbligatorio e non può essere vuoto");
            }

            document.setOriginalName(originalFilename != null ? originalFilename : "document");

            document.setStoredFilename(storedFilename);

            document.setFileType(file.getContentType());

            document.setOcrText(ocrResult.getResult());

            document.setFileSizeBytes(file.getSize());

            document.setStoragePath(storagePath);

            document.setCreatedAt(OffsetDateTime.now());

            // TODO:
            // document.setUploadedBy(...);

            // 5. Salvataggio dei metadati su PostgreSQL
            return documentRepository.save(document);

        } catch (Exception e) {

            // PostgreSQL non è riuscito:
            // rimuoviamo il file già caricato su Silo
            try {
                storageService.delete(storagePath);
            } catch (Exception cleanupException) {
                e.addSuppressed(cleanupException);
            }

            throw e;
        }
    }
}