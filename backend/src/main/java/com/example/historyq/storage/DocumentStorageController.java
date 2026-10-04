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

        import java.util.Map;
        import java.util.UUID;

        @RestController
        @RequestMapping("/api/documents")
        public class DocumentStorageController {

            private final DocumentService documentService;
            private final DocumentRepository documentRepository;
            private final RagFlowService ragFlowService;

            public DocumentStorageController(
                    DocumentService documentService,
                    DocumentRepository documentRepository,
                    RagFlowService ragFlowService
            ) {
                this.documentService = documentService;
                this.documentRepository = documentRepository;
                this.ragFlowService = ragFlowService;
            }

            /**
             * Endpoint per l'upload di documenti.
             */
            @PostMapping("/upload")
            public ResponseEntity<?> upload(
                    @RequestParam("files") MultipartFile[] files
            ) {
                try {

                    Document document =
                            documentService.upload(files);

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
                                    question
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
        }

