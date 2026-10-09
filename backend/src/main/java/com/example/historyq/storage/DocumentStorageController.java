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
        import org.springframework.http.HttpStatus;
        import java.util.List;


        import java.util.Map;
        import java.util.UUID;

        @RestController
        @RequestMapping("/api/documents")
        public class DocumentStorageController {

            private final DocumentService documentService;
            private final DocumentRepository documentRepository;
            private final RagFlowService ragFlowService;
            private final DocumentStorageService documentStorageService;

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
                    @RequestParam("archive_source") String archive_source
            ) {
                try {

                    Document document =
                            documentService.upload(files, displayName, description, historicalDate, author, archive_source);

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

                    Document document = documentRepository.findById(documentId)
                            .orElseThrow(() ->
                                    new IllegalArgumentException(
                                            "Documento non trovato: " + documentId
                                    )
                            );

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
                    return ResponseEntity.badRequest()
                            .body("Richiesta non valida: " + e.getMessage());

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
                    Document document = documentRepository.findById(documentId)
                            .orElseThrow(() ->
                                    new IllegalArgumentException(
                                            "Documento non trovato: " + documentId
                                    )
                            );

                    return ResponseEntity.ok(Map.of(
                            "status", document.getStatus(),
                            "progress", document.getProgress()
                    ));

                } catch (IllegalArgumentException e) {
                    return ResponseEntity.badRequest()
                            .body("Richiesta non valida: " + e.getMessage());

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
                    Document document = documentRepository.findById(documentId)
                            .orElseThrow(() ->
                                    new IllegalArgumentException(
                                            "Documento non trovato: " + documentId
                                    )
                            );

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
                    return ResponseEntity.internalServerError().build();
                }
            }

            @GetMapping("/{documentId}/transcription")
            public ResponseEntity<?> getTranscription(
                    @PathVariable UUID documentId
            ) {
                var optionalDocument = documentRepository.findById(documentId);

                if (optionalDocument.isEmpty()) {
                    return ResponseEntity.notFound().build();
                }

                Document document = optionalDocument.get();

                if (!"READY".equals(document.getStatus())) {
                    return ResponseEntity.status(HttpStatus.CONFLICT)
                            .body("La trascrizione non è ancora disponibile. Stato: "
                                    + document.getStatus());
                }

                return ResponseEntity.ok(Map.of(
                        "transcription",
                        document.getOcrText() == null ? "" : document.getOcrText()
                ));
            }

            @GetMapping("/{documentId}/pages")
            public ResponseEntity<List<String>> getPages(
                    @PathVariable UUID documentId
            ) {
                try {
                    documentRepository.findById(documentId)
                            .orElseThrow(() ->
                                    new IllegalArgumentException(
                                            "Documento non trovato: " + documentId
                                    )
                            );

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
                    documentRepository.findById(documentId)
                            .orElseThrow(() ->
                                    new IllegalArgumentException(
                                            "Documento non trovato: " + documentId
                                    )
                            );

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
                    return ResponseEntity.internalServerError().build();
                }
            }





        }

