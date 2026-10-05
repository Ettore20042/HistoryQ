package com.example.historyq.service;

import com.example.historyq.entity.Document;
import com.example.historyq.repository.DocumentRepository;
import com.example.historyq.storage.DocumentStorageService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentStorageService storageService;
    private final DocumentProcessingService documentProcessingService;

    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of(".jpg", ".jpeg", ".png", ".tiff", ".webp");

    public DocumentService(
            DocumentRepository documentRepository,
            DocumentStorageService storageService,
            DocumentProcessingService documentProcessingService
    ) {
        this.documentRepository = documentRepository;
        this.storageService = storageService;
        this.documentProcessingService = documentProcessingService;
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


        // 3. Costruzione del percorso nello storage
        String storagePath =
                "documents/" + documentId + "/pages/";

        Document document = new Document();

        document.setId(documentId);
        document.setStatus("PROCESSING");
        document.setProgress(0);
        document.setPageCount(files.length);

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
                originalFilename
        );

        document.setStoredFilename(
                String.format(
                        "0001%s",
                        firstFile.getOriginalFilename()
                                .substring(
                                        firstFile.getOriginalFilename().lastIndexOf(".")
                                )
                                .toLowerCase()
                )
        );

        document.setFileType(firstFile.getContentType());
        document.setFileSizeBytes(totalSize);
        document.setStoragePath(storagePath);
        document.setCreatedAt(OffsetDateTime.now());

        try {

            // 4. Upload di tutte le pagine su Silo
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

            // Upload completato
            document.setProgress(10);

            // 5. Avvio elaborazione asincrona
            documentRepository.save(document);

            documentProcessingService.processDocument(documentId);

            return document;
        } catch (Exception e) {
            document.setProgress(0);
            document.setStatus("ERROR");
            documentRepository.save(document);

            // Rimuoviamo i file già caricati su Silo
            try {
                storageService.delete(storagePath);
            } catch (Exception cleanupException) {
                e.addSuppressed(cleanupException);
            }

            throw e;
        }
    }
}