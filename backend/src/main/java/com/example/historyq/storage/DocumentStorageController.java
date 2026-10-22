        /**
         * Controller per la gestione degli upload di documenti.
         */
        package com.example.historyq.storage;

        import com.example.historyq.entity.Document;
        import com.example.historyq.repository.DocumentRepository;
        import com.example.historyq.service.DocumentService;
        import com.example.historyq.service.RagFlowService;
        import org.springframework.http.ResponseEntity;
        import org.springframework.web.bind.annotation.*;
        import org.springframework.web.multipart.MultipartFile;
        import org.springframework.web.bind.annotation.GetMapping;
        import java.util.Map;
        import org.springframework.http.HttpHeaders;
        import org.springframework.http.MediaType;
        import org.springframework.http.ResponseEntity;
        import com.example.historyq.storage.DocumentStorageService;
        import java.io.InputStream;
        import java.util.List;
        import org.slf4j.Logger;
        import org.slf4j.LoggerFactory;
        import java.io.ByteArrayInputStream;
        import java.io.File;
        import java.io.IOException;
        import java.io.InputStream;
        import java.util.ArrayList;
        import java.util.Locale;
        import java.util.Set;


        import java.util.Map;
        import java.util.UUID;

        @RestController
        @RequestMapping("/api/documents")
        public class DocumentStorageController {

            private final DocumentService documentService;
            private final DocumentRepository documentRepository;
            private final RagFlowService ragFlowService;
            private final DocumentStorageService documentStorageService;
            private final Logger logger = LoggerFactory.getLogger(DocumentStorageController.class);

            public DocumentStorageController(
                    DocumentService documentService,
                    DocumentRepository documentRepository,
                    RagFlowService ragFlowService,
                    DocumentStorageService documentStorageService
            ) {
                this.documentService = documentService;
                this.documentRepository = documentRepository;
                this.ragFlowService = ragFlowService;
                this.documentStorageService = documentStorageService;
            }

            /**
             * Endpoint per l'upload di documenti.
             */
            @PostMapping("/upload")
            public ResponseEntity<?> upload(
                    @RequestParam("files") MultipartFile[] files,
                    @RequestParam("description") String description,
                    @RequestParam("displayName") String displayName,
                    @RequestParam("historicalDate") String historicalDate,
                    @RequestParam("author") String author,
                    @RequestParam("archive_source") String archive_source,
                    @RequestParam(value="engine",defaultValue="tesseract") String engine
            ) {
                try {

                    Document document =
                            documentService.upload(files, displayName, description, historicalDate, author, archive_source, engine);

                    return ResponseEntity.ok(document);

                } catch (IllegalArgumentException e) {

                    return ResponseEntity.badRequest()
                            .body(
                                    "Richiesta non valida: "
                                            + e.getMessage()
                            );

                } catch (Exception e) {

                    return ResponseEntity.internalServerError()
                            .body(
                                    "Errore durante l'upload: "
                                            + e.getMessage()
                            );
                }
            }


            @PostMapping("/{documentId}/chat")
            public ResponseEntity<?> chat(
                    @PathVariable UUID documentId,
                    @RequestBody Map<String, String> request
            ) {
                try {
                    String question = request.get("question");

                    if (question == null || question.isBlank()) {
                        return ResponseEntity.badRequest()
                                .body("La domanda non può essere vuota.");
                    }

                    Document document = documentService.getOwnedDocument(documentId);

                    String chatId = document.getRagflowChatId();

                    if (chatId == null || chatId.isBlank()) {
                        throw new IllegalStateException(
                                "Il documento non ha una Chat RAGFlow associata."
                        );
                    }

                    Map<String, Object> result =
                            ragFlowService.askQuestion(
                                    chatId,
                                    question,
                                    documentStorageService.listObjects(document.getStoragePath())
                                            .stream()
                                            .map(objectName -> objectName.substring(objectName.lastIndexOf("/") + 1))
                                            .toList()
                            );

                    return ResponseEntity.ok(result);

                } catch (IllegalArgumentException e) {
                    return ResponseEntity.notFound().build();

                } catch (Exception e) {
                    return ResponseEntity.internalServerError()
                            .body(
                                    "Errore durante l'interrogazione RAG: "
                                            + e.getMessage()
                            );
                }
            }
            @GetMapping("/{documentId}/status")
            public ResponseEntity<?> getStatus(
                    @PathVariable UUID documentId
            ) {
                try {
                    Document document = documentService.getOwnedDocument(documentId);

                    return ResponseEntity.ok(Map.of(
                            "status", document.getStatus(),
                            "progress", document.getProgress()
                    ));

                } catch (IllegalArgumentException e) {
                    return ResponseEntity.notFound().build();

                } catch (Exception e) {
                    return ResponseEntity.internalServerError()
                            .body(
                                    "Errore durante il recupero dello stato del documento: "
                                            + e.getMessage()
                            );
                }
            }
            @GetMapping
            public ResponseEntity<?> getDocuments() {
                try {
                    return ResponseEntity.ok(
                            documentService.getDocumentsForCurrentUser()
                    );

                } catch (Exception e) {
                    return ResponseEntity.internalServerError()
                            .body(
                                    "Errore durante il recupero dei documenti: "
                                            + e.getMessage()
                            );
                }
            }

            @GetMapping("/{documentId}/preview")
            public ResponseEntity<byte[]> getPreview(
                    @PathVariable UUID documentId
            ) {
                try {
                    Document document = documentService.getOwnedDocument(documentId);

                    String prefix = "documents/" + documentId + "/pages/";

                    var objects = documentStorageService.listObjects(prefix);

                    if (objects.isEmpty()) {
                        return ResponseEntity.notFound().build();
                    }

                    String firstPage = objects.get(0);

                    byte[] image = documentStorageService.downloadBytes(firstPage);

                    MediaType mediaType = MediaType.IMAGE_JPEG;

                    if (firstPage.toLowerCase().endsWith(".png")) {
                        mediaType = MediaType.IMAGE_PNG;
                    }

                    return ResponseEntity.ok()
                            .header(
                                    HttpHeaders.CONTENT_DISPOSITION,
                                    "inline"
                            )
                            .contentType(mediaType)
                            .body(image);

                } catch (IllegalArgumentException e) {
                    return ResponseEntity.notFound().build();

                } catch (Exception e) {
                    logger.error(
                            "Errore durante il recupero dell'anteprima del documento {}",
                            documentId,
                            e
                    );
                    return ResponseEntity.internalServerError().build();
                }
            }

            @GetMapping("/{documentId}/pages")
            public ResponseEntity<List<String>> getPages(
                    @PathVariable UUID documentId
            ) {
                try {
                    documentService.getOwnedDocument(documentId);

                    String prefix = "documents/" + documentId + "/pages/";

                    List<String> objects = documentStorageService.listObjects(prefix);

                    List<String> pageNames = objects.stream()
                            .map(objectName -> objectName.substring(prefix.length()))
                            .toList();

                    return ResponseEntity.ok(pageNames);

                } catch (IllegalArgumentException e) {
                    return ResponseEntity.notFound().build();

                } catch (Exception e) {
                    return ResponseEntity.internalServerError().build();
                }
            }

            @GetMapping("/{documentId}/pages/image/{filename:.+}")
            public ResponseEntity<byte[]> getDocumentPage(
                    @PathVariable UUID documentId,
                    @PathVariable String filename
            ) {
                try {
                    documentService.getOwnedDocument(documentId);

                    // Accettiamo soltanto nomi di file semplici e immagini supportate.
                    if (!filename.matches("[A-Za-z0-9._-]+")
                            || filename.contains("..")) {
                        return ResponseEntity.badRequest().build();
                    }

                    String lowerFilename = filename.toLowerCase();

                    MediaType mediaType;
                    if (lowerFilename.endsWith(".jpg")
                            || lowerFilename.endsWith(".jpeg")) {
                        mediaType = MediaType.IMAGE_JPEG;
                    } else if (lowerFilename.endsWith(".png")) {
                        mediaType = MediaType.IMAGE_PNG;
                    } else {
                        return ResponseEntity.badRequest().build();
                    }

                    String objectName =
                            "documents/" + documentId + "/pages/" + filename;

                    byte[] image = documentStorageService.downloadBytes(objectName);

                    return ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                            .contentType(mediaType)
                            .body(image);

                } catch (IllegalArgumentException e) {
                    return ResponseEntity.notFound().build();
                } catch (Exception e) {
                    logger.error(
                            "Errore durante il recupero della pagina {} del documento {}",
                            filename,
                            documentId,
                            e
                    );
                    return ResponseEntity.internalServerError().build();
                }
            }
            @PatchMapping("/{documentId}/visibility")
            public ResponseEntity<?> updateVisibility(
                    @PathVariable UUID documentId,
                    @RequestBody Map<String, String> request) {

                try {
                    Document document = documentService.updateVisibility(
                            documentId,
                            request.get("visibility")
                    );

                    return ResponseEntity.ok(
                            Map.of(
                                    "id", document.getId(),
                                    "visibility", document.getVisibility()
                            )
                    );

                } catch (IllegalArgumentException e) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("error", e.getMessage()));
                }
            }

            @PostMapping("/{documentId}/reprocess")
            public ResponseEntity<?> reprocessDocument(
                    @PathVariable UUID documentId,
                    @RequestBody Map<String, String> request
            ) {
                try {
                    String engine = request.get("engine");

                    if (engine == null || !Set.of(
                            "tesseract",
                            "churro",
                            "lighton-1b",
                            "lighton-4b"
                    ).contains(engine)) {
                        return ResponseEntity.badRequest()
                                .body(Map.of("error", "Motore OCR non valido."));
                    }

                    // Recupera il documento originale e verifica la proprietà.
                    Document original = documentService.getOwnedDocument(documentId);

                    // Recupera le immagini già archiviate in MinIO.
                    List<String> objects =
                            documentStorageService.listObjects(original.getStoragePath());

                    if (objects.isEmpty()) {
                        return ResponseEntity.badRequest()
                                .body(Map.of(
                                        "error",
                                        "Il documento non contiene pagine da rielaborare."
                                ));
                    }

                    List<MultipartFile> files = new ArrayList<>();

                    for (String objectName : objects) {
                        String filename = objectName.substring(
                                objectName.lastIndexOf('/') + 1
                        );

                        byte[] content =
                                documentStorageService.downloadBytes(objectName);

                        String lower = filename.toLowerCase(Locale.ROOT);
                        String contentType;

                        if (lower.endsWith(".png")) {
                            contentType = "image/png";
                        } else if (lower.endsWith(".jpg")
                                || lower.endsWith(".jpeg")) {
                            contentType = "image/jpeg";
                        } else if (lower.endsWith(".tiff")
                                || lower.endsWith(".tif")) {
                            contentType = "image/tiff";
                        } else if (lower.endsWith(".webp")) {
                            contentType = "image/webp";
                        } else {
                            return ResponseEntity.badRequest()
                                    .body(Map.of(
                                            "error",
                                            "Formato di pagina non supportato: " + filename
                                    ));
                        }

                        files.add(new MultipartFile() {
                            @Override
                            public String getName() {
                                return "files";
                            }

                            @Override
                            public String getOriginalFilename() {
                                return filename;
                            }

                            @Override
                            public String getContentType() {
                                return contentType;
                            }

                            @Override
                            public boolean isEmpty() {
                                return content.length == 0;
                            }

                            @Override
                            public long getSize() {
                                return content.length;
                            }

                            @Override
                            public byte[] getBytes() {
                                return content.clone();
                            }

                            @Override
                            public InputStream getInputStream() {
                                return new ByteArrayInputStream(content);
                            }

                            @Override
                            public void transferTo(File dest) throws IOException {
                                java.nio.file.Files.write(dest.toPath(), content);
                            }
                        });
                    }

                    // Riutilizza il normale upload: crea un nuovo documento
                    // e avvia la pipeline OCR/RAGFlow con il motore selezionato.
                    String duplicateName = original.getDisplayName() + " (copia OCR)";

                    if (duplicateName.length() > 512) {
                        duplicateName = duplicateName.substring(0, 512);
                    }

                    Document duplicate = documentService.upload(
                            files.toArray(new MultipartFile[0]),
                            duplicateName,
                            original.getDescription(),
                            original.getHistoricalDate(),
                            original.getAuthor(),
                            original.getArchiveSource(),
                            engine
                    );

                    return ResponseEntity.accepted().body(Map.of(
                            "documentId", duplicate.getId(),
                            "displayName", duplicate.getDisplayName(),
                            "status", duplicate.getStatus(),
                            "engine", engine
                    ));

                } catch (IllegalArgumentException e) {
                    return ResponseEntity.notFound().build();

                } catch (Exception e) {
                    logger.error(
                            "Errore durante la rielaborazione del documento {}",
                            documentId,
                            e
                    );

                    return ResponseEntity.internalServerError()
                            .body(Map.of(
                                    "error",
                                    "Errore durante la rielaborazione del documento."
                            ));
                }
            }



        }
