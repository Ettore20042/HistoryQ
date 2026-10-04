package com.example.historyq.service;

import com.example.historyq.entity.Document;
import com.example.historyq.repository.DocumentRepository;
import com.example.historyq.storage.DocumentStorageService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.example.historyq.service.RagFlowService;
import java.time.OffsetDateTime;
import java.util.UUID;
import com.example.historyq.service.OcrService;
import java.util.Set;
@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentStorageService storageService;
    private final OcrService ocrService;
    private final RagFlowService ragFlowService;
    private static final Set<String>ALLOWED_EXTENSIONS=Set.of(".jpg",".jpeg",".png",".tiff",".webp");

    public DocumentService(
            DocumentRepository documentRepository,
            DocumentStorageService storageService,
            OcrService ocrService,
            RagFlowService ragFlowService
    ) {
        this.documentRepository = documentRepository;
        this.storageService = storageService;
        this.ocrService = ocrService;
        this.ragFlowService = ragFlowService;
    }

    public Document upload(MultipartFile[] files) throws Exception {

        // 1. Validazione dei file
        if (files == null || files.length == 0) {
            throw new IllegalArgumentException(
                    "Nessun file selezionato"
            );
        }

        String originalFilename = files[0].getOriginalFilename();

        if (originalFilename == null || !originalFilename.contains(".")) {
            throw new IllegalArgumentException(
                    "Il file deve avere un'estensione"
            );
        }

        String extension = originalFilename.substring(
                originalFilename.lastIndexOf(".")
        ).toLowerCase();

        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException(
                    "Formato non supportato: " + extension
            );
        }

// 2. Generazione UUID
        UUID documentId = UUID.randomUUID();


        // 2. Costruzione del percorso nello storage
        String storagePath =
                "documents/" + documentId + "/pages/";

// 3. Upload di tutte le pagine su Silo
        for (int i = 0; i < files.length; i++) {

            MultipartFile file = files[i];

            String filename = file.getOriginalFilename();

            if (filename == null || !filename.contains(".")) {
                throw new IllegalArgumentException(
                        "Il file alla posizione " + (i + 1)
                                + " deve avere un'estensione"
                );
            }

            String fileExtension = filename.substring(
                    filename.lastIndexOf(".")
            ).toLowerCase();

            if (!ALLOWED_EXTENSIONS.contains(fileExtension)) {
                throw new IllegalArgumentException(
                        "Formato non supportato: " + fileExtension
                );
            }

            String storedFilename = String.format(
                    "%04d%s",
                    i + 1,
                    fileExtension
            );

            storageService.upload(
                    file,
                    storagePath + storedFilename
            );
        }
        try {
        var ocrJob=ocrService.submitJob(files);
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
            // 4. Creazione dell'entità Document
            Document document = new Document();

            document.setId(documentId);

            MultipartFile firstFile = files[0];

            if (firstFile.isEmpty()) {
                throw new IllegalArgumentException(
                        "Il primo file è obbligatorio e non può essere vuoto"
                );
            }

            long totalSize = 0;

            for (MultipartFile currentFile : files) {
                totalSize += currentFile.getSize();
            }

            document.setOriginalName(
                    originalFilename != null ? originalFilename : "document"
            );

            document.setStoredFilename(
                    String.format("0001%s",
                            firstFile.getOriginalFilename()
                                    .substring(firstFile.getOriginalFilename().lastIndexOf("."))
                                    .toLowerCase()
                    )
            );

            document.setFileType(firstFile.getContentType());

            document.setOcrText(ocrResult.getResult());

            document.setFileSizeBytes(totalSize);

            document.setStoragePath(storagePath);

            document.setCreatedAt(OffsetDateTime.now());

            String ragflowDatasetId =
                    ragFlowService.createDataset(originalFilename);

            String ragflowDocumentId =
                    ragFlowService.uploadOcrText(
                            ragflowDatasetId,
                            originalFilename,
                            ocrResult.getResult()
                    );

            ragFlowService.parseDocument(
                    ragflowDatasetId,
                    ragflowDocumentId
            );

            ragFlowService.waitUntilParsed(
                    ragflowDatasetId,
                    ragflowDocumentId
            );

            String ragflowChatId =
                    ragFlowService.createChat(
                            originalFilename,
                            ragflowDatasetId
                    );

            document.setRagflowDatasetId(ragflowDatasetId);
            document.setRagflowChatId(ragflowChatId);

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