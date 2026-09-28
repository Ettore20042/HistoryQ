package com.example.historyq.service;

import com.example.historyq.entity.Document;
import com.example.historyq.repository.DocumentRepository;
import com.example.historyq.storage.DocumentStorageService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentStorageService storageService;

    public DocumentService(
            DocumentRepository documentRepository,
            DocumentStorageService storageService
    ) {
        this.documentRepository = documentRepository;
        this.storageService = storageService;
    }

    public Document upload(MultipartFile file) throws Exception {

        // 1. Generazione UUID
        UUID documentId = UUID.randomUUID();

        // 2. Costruzione del percorso nello storage
        String storagePath =
                "documents/" + documentId + "/original.pdf";

        // 3. Upload del PDF su Silo
        storageService.upload(file, storagePath);

        try {

            // 4. Creazione dell'entità Document
            Document document = new Document();

            document.setId(documentId);

            document.setOriginalName(file.getOriginalFilename());

            document.setStoredFilename("original.pdf");

            document.setFileType(file.getContentType());

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