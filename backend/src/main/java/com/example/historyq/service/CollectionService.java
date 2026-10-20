package com.example.historyq.service;
import com.example.historyq.entity.Collection;
import com.example.historyq.entity.User;
import com.example.historyq.repository.CollectionDocumentRepository;
import com.example.historyq.repository.CollectionRepository;
import com.example.historyq.repository.UserRepository;
import org.springframework.stereotype.Service;
import com.example.historyq.entity.CollectionDocument;
import com.example.historyq.entity.Document;
import java.time.OffsetDateTime;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import com.example.historyq.dto.CollectionDocumentResponse;
import com.example.historyq.repository.DocumentPageRepository;
import com.example.historyq.entity.DocumentPage;
import java.util.Comparator;
import java.util.stream.Collectors;


import java.util.List;
import com.example.historyq.repository.DocumentRepository;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;



@Service
public class CollectionService {

    private final CollectionRepository collectionRepository;
    private final CollectionDocumentRepository collectionDocumentRepository;
    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final DocumentPageRepository documentPageRepository;
    private final RagFlowService ragFlowService;
    private final ApplicationEventPublisher eventPublisher;

    public CollectionService(CollectionRepository collectionRepository, CollectionDocumentRepository collectionDocumentRepository, UserRepository userRepository, DocumentRepository documentRepository, DocumentPageRepository documentPageRepository, RagFlowService ragFlowService, ApplicationEventPublisher eventPublisher) {
        this.collectionRepository = collectionRepository;
        this.collectionDocumentRepository = collectionDocumentRepository;
        this.userRepository = userRepository;
        this.documentRepository = documentRepository;
        this.documentPageRepository = documentPageRepository;
        this.ragFlowService = ragFlowService;
        this.eventPublisher = eventPublisher;
    }
    public List<Collection> getMyCollections(User user) {
        return collectionRepository.findByOwnerId(user.getId());
    }
    @Transactional
    public CollectionDocument addDocumentToCollection(
            UUID collectionId,
            UUID documentId,
            User user) {

        Collection collection = collectionRepository.findById(collectionId)
                .filter(c -> c.getOwner().getId().equals(user.getId()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Collezione non trovata"
                ));

        Document document = documentRepository.findById(documentId)
                .filter(d ->
                        d.getUploadedBy().getId().equals(user.getId())
                                || "PUBLIC".equalsIgnoreCase(d.getVisibility())
                )
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Documento non trovato o non accessibile"
                ));

        boolean alreadyExists =
                collectionDocumentRepository
                        .existsByCollectionIdAndDocumentId(collectionId, documentId);

        if (alreadyExists) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Documento già presente nella collezione"
            );
        }

        CollectionDocument association = new CollectionDocument();
        association.setCollection(collection);
        association.setDocument(document);
        association.setStatus("PENDING");
        association.setAddedAt(OffsetDateTime.now());

        CollectionDocument saved = collectionDocumentRepository.save(association);
        eventPublisher.publishEvent(new CollectionDocumentAddedEvent(saved.getId()));
        return saved;
    }
    @Transactional
    public Collection createCollection(
            String name,
            String description,
            User user) {

        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Il nome della collezione è obbligatorio"
            );
        }

        Collection collection = new Collection();
        collection.setName(name.trim());
        collection.setDescription(description);
        collection.setVisibility("PRIVATE");
        collection.setOwner(user);
        collection.setCreatedAt(OffsetDateTime.now());

        return collectionRepository.save(collection);
    }
    public Collection getCollectionById(UUID collectionId, User user) {
        return collectionRepository.findById(collectionId)
                .filter(c -> c.getOwner().getId().equals(user.getId()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Collezione non trovata"
                ));
    }

    public List<CollectionDocument> getCollectionDocuments(
            UUID collectionId,
            User user) {

        getCollectionById(collectionId, user);

        return collectionDocumentRepository
                .findAllByCollection_Id(collectionId);

    }
    @Transactional(readOnly = true)
    public List<CollectionDocumentResponse> getCollectionDocumentResponses(
            UUID collectionId,
            User user) {

        getCollectionById(collectionId, user);

        return collectionDocumentRepository
                .findAllByCollection_Id(collectionId)
                .stream()
                .map(association -> {
                    Document document = association.getDocument();

                    return new CollectionDocumentResponse(
                            document.getId(),
                            document.getDisplayName(),
                            document.getDescription(),
                            document.getOriginalName(),
                            document.getHistoricalDate(),
                            document.getAuthor(),
                            document.getArchiveSource(),
                            document.getPageCount(),
                            document.getStatus(),
                            association.getStatus(),
                            association.getAddedAt()
                    );
                })
                .toList();
    }

    public String getOrCreateCollectionChat(
            UUID collectionId,
            User user) {

        Collection collection = getCollectionById(collectionId, user);

        List<CollectionDocument> associations =
                collectionDocumentRepository.findAllByCollection_Id(collectionId);

        boolean hasParsed = associations.stream()
                .anyMatch(association -> "PARSED".equals(association.getStatus()));
        boolean hasUnparsed = associations.stream()
                .anyMatch(association -> !"PARSED".equals(association.getStatus()));
        boolean hasError = associations.stream()
                .anyMatch(association -> "ERROR".equals(association.getStatus()));

        if (!hasParsed || hasUnparsed) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    associations.isEmpty()
                            ? "La collezione non contiene documenti interrogabili."
                            : hasError
                                ? "Uno o più documenti della collezione non sono stati elaborati."
                            : "Uno o più documenti della collezione sono ancora in elaborazione."
            );
        }

        String chatId = collection.getRagflowChatId();
        if (chatId != null && !chatId.isBlank()) {
            return chatId;
        }

        String datasetId = collection.getRagflowDatasetId();

        if (datasetId == null || datasetId.isBlank()) {
            throw new IllegalStateException(
                    "Il dataset RAGFlow della collezione non è disponibile."
            );
        }

        chatId = ragFlowService.createChat(
                "collection-" + collection.getName(),
                datasetId
        );

        collection.setRagflowChatId(chatId);
        collectionRepository.save(collection);

        return chatId;
    }



}