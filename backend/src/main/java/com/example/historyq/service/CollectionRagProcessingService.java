package com.example.historyq.service;

import com.example.historyq.entity.CollectionDocument;
import com.example.historyq.entity.DocumentPage;
import com.example.historyq.repository.CollectionDocumentRepository;
import com.example.historyq.repository.DocumentPageRepository;
import com.example.historyq.repository.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CollectionRagProcessingService {

    private static final Logger log =
            LoggerFactory.getLogger(CollectionRagProcessingService.class);

    private final CollectionDocumentRepository collectionDocumentRepository;
    private final DocumentPageRepository documentPageRepository;
    private final DocumentRepository documentRepository;
    private final CollectionRagStateService stateService;
    private final RagFlowService ragFlowService;

    public CollectionRagProcessingService(
            CollectionDocumentRepository collectionDocumentRepository,
            DocumentPageRepository documentPageRepository,
            DocumentRepository documentRepository,
            CollectionRagStateService stateService,
            RagFlowService ragFlowService) {
        this.collectionDocumentRepository = collectionDocumentRepository;
        this.documentPageRepository = documentPageRepository;
        this.documentRepository = documentRepository;
        this.stateService = stateService;
        this.ragFlowService = ragFlowService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDocumentAdded(CollectionDocumentAddedEvent event) {
        processAssociation(event.associationId());
    }

    private void processAssociation(UUID associationId) {
        try {
            CollectionDocument association = collectionDocumentRepository
                    .findById(associationId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Relazione collezione-documento non trovata."));

            if (!stateService.claimPendingAssociation(associationId)) {
                return;
            }

            String datasetId = stateService.ensureDataset(
                    association.getCollection().getId());

            waitForOcr(association.getDocument().getId());

            List<DocumentPage> pages = documentPageRepository
                    .findAllByDocument_Id(association.getDocument().getId())
                    .stream()
                    .sorted(Comparator.comparing(DocumentPage::getFilename))
                    .toList();

            String ocrText = pages.stream()
                    .filter(page -> page.getOcrText() != null
                            && !page.getOcrText().isBlank())
                    .map(page -> "Pagina: " + page.getFilename()
                            + "\n" + page.getOcrText())
                    .collect(Collectors.joining("\n\n"));

            if (ocrText.isBlank()) {
                throw new IllegalStateException(
                        "Il documento non contiene testo OCR utilizzabile.");
            }

            String ragflowDocumentId = association.getRagflowDocId();
            if (ragflowDocumentId == null || ragflowDocumentId.isBlank()) {
                ragflowDocumentId = ragFlowService.uploadOcrText(
                        datasetId,
                        association.getDocument().getId()
                                + "_" + association.getDocument().getOriginalName(),
                        ocrText);
                stateService.markAssociationProcessing(
                        associationId, ragflowDocumentId);
            }

            ragFlowService.parseDocument(datasetId, ragflowDocumentId);
            ragFlowService.waitUntilParsed(datasetId, ragflowDocumentId);
            stateService.markAssociationStatus(associationId, "PARSED");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            markError(associationId, e);
        } catch (Exception e) {
            markError(associationId, e);
        }
    }

    private void waitForOcr(UUID documentId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 2 * 60 * 1000L;
        while (System.currentTimeMillis() < deadline) {
            String status = documentRepository.findById(documentId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Documento non trovato."))
                    .getStatus();

            if ("OCR_COMPLETED".equals(status)
                    || "RAGFLOW_PROCESSING".equals(status)
                    || "READY".equals(status)) {
                return;
            }
            if ("ERROR".equals(status)) {
                throw new IllegalStateException(
                        "L'elaborazione OCR del documento è fallita.");
            }
            Thread.sleep(1000);
        }
        throw new IllegalStateException("Timeout durante l'attesa dell'OCR.");
    }

    private void markError(UUID associationId, Exception exception) {
        log.error("Elaborazione RAGFlow fallita per la relazione {}",
                associationId, exception);
        try {
            stateService.markAssociationStatus(associationId, "ERROR");
        } catch (Exception statusException) {
            log.error("Impossibile salvare lo stato ERROR per la relazione {}",
                    associationId, statusException);
        }
    }
}
