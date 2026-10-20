package com.example.historyq.service;

import com.example.historyq.entity.Collection;
import com.example.historyq.entity.CollectionDocument;
import com.example.historyq.repository.CollectionDocumentRepository;
import com.example.historyq.repository.CollectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CollectionRagStateService {

    private final CollectionRepository collectionRepository;
    private final CollectionDocumentRepository collectionDocumentRepository;
    private final RagFlowService ragFlowService;

    public CollectionRagStateService(
            CollectionRepository collectionRepository,
            CollectionDocumentRepository collectionDocumentRepository,
            RagFlowService ragFlowService) {
        this.collectionRepository = collectionRepository;
        this.collectionDocumentRepository = collectionDocumentRepository;
        this.ragFlowService = ragFlowService;
    }

    @Transactional
    public String ensureDataset(UUID collectionId) {
        Collection collection = collectionRepository.findByIdForUpdate(collectionId)
                .orElseThrow(() -> new IllegalStateException("Collezione non trovata."));

        if (collection.getRagflowDatasetId() == null
                || collection.getRagflowDatasetId().isBlank()) {
            collection.setRagflowDatasetId(
                    ragFlowService.createDataset("collection-" + collection.getName()));
            collectionRepository.save(collection);
        }

        return collection.getRagflowDatasetId();
    }

    @Transactional
    public void markAssociationProcessing(UUID associationId, String ragflowDocumentId) {
        CollectionDocument association = findAssociation(associationId);
        association.setRagflowDocId(ragflowDocumentId);
        association.setStatus("PROCESSING");
        collectionDocumentRepository.save(association);
    }

    @Transactional
    public boolean claimPendingAssociation(UUID associationId) {
        return collectionDocumentRepository.claimPending(associationId) == 1;
    }

    @Transactional
    public void markAssociationStatus(UUID associationId, String status) {
        CollectionDocument association = findAssociation(associationId);
        association.setStatus(status);
        collectionDocumentRepository.save(association);
    }

    private CollectionDocument findAssociation(UUID associationId) {
        return collectionDocumentRepository.findById(associationId)
                .orElseThrow(() -> new IllegalStateException(
                        "Relazione collezione-documento non trovata."));
    }
}
