package com.example.historyq.service;

import com.example.historyq.entity.Document;
import com.example.historyq.entity.User;
import com.example.historyq.repository.DocumentRepository;
import com.example.historyq.repository.UserRepository;
import com.example.historyq.storage.DocumentStorageService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import java.io.InputStream;
import com.example.historyq.service.PdfToImageService;



@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentStorageService storageService;
    private final DocumentProcessingService documentProcessingService;
    private final UserRepository userRepository;
    private final PdfToImageService pdfToImageService;

    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of(".jpg", ".jpeg", ".png", ".tiff", ".webp",".pdf");

    public DocumentService(
            DocumentRepository documentRepository,
            DocumentStorageService storageService,
            DocumentProcessingService documentProcessingService,
            UserRepository userRepository,
            PdfToImageService pdfToImageService
    ) {
        this.documentRepository = documentRepository;
        this.storageService = storageService;
        this.documentProcessingService = documentProcessingService;
        this.userRepository = userRepository;
        this.pdfToImageService = pdfToImageService;
    }

    public Document upload(MultipartFile[] files,String displayName,String description,String historicalDate,String author,String archive_source) throws Exception {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Utente non trovato"));
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
        ).toLowerCase(); //Extraiamo l'estensione e la convertiamo in minuscolo per uniformità

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

        String firstExtension = originalFilename.substring(
                originalFilename.lastIndexOf(".")
        ).toLowerCase();

        document.setStoredFilename(
                "0001" + (firstExtension.equals(".pdf") ? ".png" : firstExtension)
        );

        document.setFileType(firstFile.getContentType());
        document.setFileSizeBytes(totalSize);
        document.setStoragePath(storagePath);
        document.setCreatedAt(OffsetDateTime.now());
        document.setUploadedBy(user);
        document.setDescription(description);
        document.setDisplayName(displayName);
        document.setHistoricalDate(historicalDate);
        document.setAuthor(author);
        document.setArchiveSource(archive_source);


        try {
            // 1. Upload di tutte le pagine su Silo
            int pageNumber = 0;

            for (int i = 0; i < files.length; i++) {
                MultipartFile file = files[i];
                String filename = file.getOriginalFilename();

                if (file.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Il file alla posizione " + (i + 1) + " è vuoto"
                    );
                }

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

                if (fileExtension.equals(".pdf")) {
                    try (InputStream inputStream = file.getInputStream()) {
                        List<byte[]> pages =
                                pdfToImageService.convertToImages(inputStream);

                        if (pages.isEmpty()) {
                            throw new IllegalArgumentException(
                                    "Il PDF non contiene pagine"
                            );
                        }

                        for (byte[] pageBytes : pages) {
                            pageNumber++;

                            String storedFilename = String.format(
                                    "%04d.png", pageNumber
                            );

                            storageService.uploadBytes(
                                    pageBytes,
                                    storagePath + storedFilename,
                                    "image/png"
                            );
                        }
                    }
                } else {
                    pageNumber++;

                    String storedFilename = String.format(
                            "%04d%s", pageNumber, fileExtension
                    );

                    storageService.upload(
                            file,
                            storagePath + storedFilename
                    );
                }
            }

            document.setPageCount(pageNumber);

        } catch (Exception e) {
            // Pulizia solo se fallisce il caricamento delle immagini.
            try {
                storageService.deleteByPrefix(storagePath);
            } catch (Exception cleanupException) {
                e.addSuppressed(cleanupException);
            }

            throw e;
        }

// 2. Upload completato: registriamo il documento.
        document.setProgress(10);
        document.setStatus("PROCESSING");
        documentRepository.save(document);

// 3. Avvio dell'elaborazione asincrona fuori dal try/catch
// che gestisce la pulizia delle immagini.
        documentProcessingService.processDocument(documentId);

        return document;

    }
        /**
         * Recupera i documenti caricati dall'utente corrente.
         *
         * @return Lista di documenti caricati dall'utente corrente.
         */
        public List<Document> getDocumentsForCurrentUser() {

            Authentication authentication =
                    SecurityContextHolder.getContext().getAuthentication();

            String username = authentication.getName();

            User user = userRepository.findByUsername(username)
                    .orElseThrow(() ->
                            new IllegalStateException("Utente autenticato non trovato"));

            return documentRepository.findByUploadedBy(user);
        }

    public Document updateVisibility(UUID documentId, String visibility) {

        if (!"PUBLIC".equals(visibility) && !"PRIVATE".equals(visibility)) {
            throw new IllegalArgumentException(
                    "La visibilità deve essere PUBLIC oppure PRIVATE"
            );
        }

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        String username = authentication.getName();

        User user = userRepository.findByUsername(username)
                .orElseThrow(() ->
                        new IllegalStateException("Utente autenticato non trovato"));

        Document document = documentRepository
                .findByIdAndUploadedBy(documentId, user)
                .orElseThrow(() ->
                        new IllegalArgumentException("Documento non trovato"));

        document.setVisibility(visibility);

        return documentRepository.save(document);
    }
    public Document getOwnedDocument(UUID documentId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalArgumentException("Documento non trovato");
        }
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("Documento non trovato"));
        return documentRepository.findByIdAndUploadedBy(documentId, user)
                .orElseThrow(() -> new IllegalArgumentException("Documento non trovato"));
    }

    public List<Document> getPublicDocuments() {
        return documentRepository.findByVisibilityOrderByDisplayNameAsc("PUBLIC");
    }

    public List<Document> searchPublicDocuments(String name) {
        if (name == null || name.isBlank()) {
            return getPublicDocuments();
        }

        return documentRepository
                .findByVisibilityAndDisplayNameContainingIgnoreCaseOrderByDisplayNameAsc(
                        "PUBLIC",
                        name.trim()
                );
    }
    }
