
package com.example.historyq.storage;

import com.example.historyq.entity.DocumentPage;
import com.example.historyq.repository.DocumentPageRepository;
import com.example.historyq.service.RagFlowService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
public class DocumentPageController {

    private final DocumentPageRepository documentPageRepository;
    private final RagFlowService ragFlowService;

    public DocumentPageController(
            DocumentPageRepository documentPageRepository,
            RagFlowService ragFlowService) {
        this.documentPageRepository = documentPageRepository;
        this.ragFlowService = ragFlowService;
    }

    @GetMapping("/{documentId}/page-transcriptions/{filename:.+}")
    public ResponseEntity<PageTranscriptionResponse> getTranscription(
            @PathVariable UUID documentId,
            @PathVariable String filename) {

        return documentPageRepository
                .findByDocument_IdAndFilename(documentId, filename)
                .map(page -> ResponseEntity.ok(toResponse(page)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{documentId}/page-transcriptions/{filename:.+}")
    public ResponseEntity<PageTranscriptionResponse> updateTranscription(
            @PathVariable UUID documentId,
            @PathVariable String filename,
            @RequestBody UpdateTranscriptionRequest request) {

        if (request == null || request.ocrText() == null) {
            return ResponseEntity.badRequest().build();
        }

        return documentPageRepository
                .findByDocument_IdAndFilename(documentId, filename)
                .map(page -> {
                    String datasetId =
                            page.getDocument().getRagflowDatasetId();

                    if (datasetId == null || datasetId.isBlank()) {
                        return ResponseEntity
                                .status(HttpStatus.CONFLICT)
                                .<PageTranscriptionResponse>build();
                    }

                    String oldRagflowDocumentId =
                            page.getRagflowDocumentId();

                    String newRagflowDocumentId = null;
                    boolean oldDocumentDeleted = false;

                    try {
                        // 1. Carica la nuova trascrizione.
                        newRagflowDocumentId =
                                ragFlowService.uploadOcrText(
                                        datasetId,
                                        filename,
                                        request.ocrText()
                                );

                        // 2. Avvia il parsing del nuovo documento.
                        ragFlowService.parseDocument(
                                datasetId,
                                newRagflowDocumentId
                        );

                        // 3. Attende il completamento del parsing.
                        ragFlowService.waitUntilParsed(
                                datasetId,
                                newRagflowDocumentId
                        );

                        // 4. Elimina la versione precedente, se presente.
                        if (oldRagflowDocumentId != null
                                && !oldRagflowDocumentId.isBlank()) {
                            ragFlowService.deleteDocument(
                                    datasetId,
                                    oldRagflowDocumentId
                            );
                            oldDocumentDeleted = true;
                        }

                        // 5. Aggiorna testo e ID nel database.
                        page.setOcrText(request.ocrText());
                        page.setRagflowDocumentId(
                                newRagflowDocumentId
                        );

                        DocumentPage saved =
                                documentPageRepository.save(page);

                        return ResponseEntity.ok(toResponse(saved));

                    } catch (Exception e) {
                        // Se la vecchia versione esiste ancora,
                        // prova a rimuovere il nuovo documento
                        // per evitare duplicati nel dataset.
                        if (newRagflowDocumentId != null
                                && !oldDocumentDeleted) {
                            try {
                                ragFlowService.deleteDocument(
                                        datasetId,
                                        newRagflowDocumentId
                                );
                            } catch (Exception cleanupException) {
                                // La pulizia è fallita:
                                // il nuovo documento potrebbe restare
                                // nel dataset RAGFlow.
                            }
                        }

                        return ResponseEntity
                                .status(HttpStatus.BAD_GATEWAY)
                                .<PageTranscriptionResponse>build();
                    }
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private PageTranscriptionResponse toResponse(DocumentPage page) {
        return new PageTranscriptionResponse(
                page.getId(),
                page.getDocument().getId(),
                page.getFilename(),
                page.getOcrText()
        );
    }

    public record UpdateTranscriptionRequest(String ocrText) {}

    public record PageTranscriptionResponse(
            UUID id,
            UUID documentId,
            String filename,
            String ocrText
    ) {}
}
