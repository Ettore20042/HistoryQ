package com.example.historyq.controller;

import com.example.historyq.dto.CollectionResponse;
import com.example.historyq.entity.Collection;
import com.example.historyq.entity.User;
import com.example.historyq.repository.UserRepository;
import com.example.historyq.service.CollectionService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import com.example.historyq.entity.CollectionDocument;
import com.example.historyq.dto.CollectionDocumentResponse;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.security.core.Authentication;
import com.example.historyq.service.RagFlowService;
import com.example.historyq.service.TeamCollectionService;

import com.example.historyq.dto.CreateCollectionRequest;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

import java.util.List;

@RestController
@RequestMapping("/api/collections")
public class CollectionController {
    private final CollectionService collectionService;
    private final UserRepository userRepository;
    private final RagFlowService ragFlowService;
    private final TeamCollectionService teamCollectionService;

    public CollectionController(CollectionService collectionService, UserRepository userRepository,RagFlowService ragFlowService, TeamCollectionService teamCollectionService) {
        this.collectionService = collectionService;
        this.userRepository = userRepository;
        this.ragFlowService = ragFlowService;
        this.teamCollectionService = teamCollectionService;
    }


    private User getAuthenticatedUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Utente non trovato"));
    }

    @GetMapping("/my")
    public ResponseEntity<List<CollectionResponse>> getMyCollections(
            Authentication authentication) {

        User user = getAuthenticatedUser(authentication);

        List<CollectionResponse> collections = collectionService
                .getMyCollections(user)
                .stream()
                .map(collection -> new CollectionResponse(
                        collection.getId(),
                        collection.getName(),
                        collection.getDescription(),
                        collection.getVisibility(),
                        collection.getCreatedAt()))
                .toList();

        return ResponseEntity.ok(collections);
    }

    @PostMapping("/{collectionId}/documents/{documentId}")
    public ResponseEntity<Void> addDocumentToCollection(
            @PathVariable UUID collectionId,
            @PathVariable UUID documentId,
            Authentication authentication) {

        User user = getAuthenticatedUser(authentication);

        collectionService.addDocumentToCollection(collectionId, documentId, user);

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping
    public ResponseEntity<CollectionResponse> createCollection(
            @RequestBody CreateCollectionRequest request,
            Authentication authentication) {

        User user = getAuthenticatedUser(authentication);

        Collection collection = collectionService.createCollection(
                request.name(),
                request.description(),
                user
        );

        CollectionResponse response = new CollectionResponse(
                collection.getId(),
                collection.getName(),
                collection.getDescription(),
                collection.getVisibility(),
                collection.getCreatedAt()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{collectionId}")
    public ResponseEntity<CollectionResponse> getCollection(
            @PathVariable UUID collectionId,
            Authentication authentication,
            @RequestParam(required = false) UUID teamId) {

        User user = getAuthenticatedUser(authentication);

        Collection collection =
                collectionService.getCollectionById(collectionId, user, teamId);

        CollectionResponse response = new CollectionResponse(
                collection.getId(),
                collection.getName(),
                collection.getDescription(),
                collection.getVisibility(),
                collection.getCreatedAt()
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{collectionId}/documents")
    public ResponseEntity<List<CollectionDocumentResponse>> getCollectionDocuments(
            @PathVariable UUID collectionId,
            Authentication authentication,
            @RequestParam(required = false) UUID teamId) {

        User user = getAuthenticatedUser(authentication);

        List<CollectionDocumentResponse> documents =
                collectionService.getCollectionDocumentResponses(
                        collectionId,
                        user, teamId
                );

        return ResponseEntity.ok(documents);
    }

    @PostMapping("/{collectionId}/chat")
    public ResponseEntity<?> chat(
            @PathVariable UUID collectionId,
            @RequestBody Map<String, String> request,
            Authentication authentication,
            @RequestParam(required = false) UUID teamId) {

        try {
            String question = request.get("question");

            if (question == null || question.isBlank()) {
                return ResponseEntity.badRequest()
                        .body("La domanda non può essere vuota.");
            }

            User user = getAuthenticatedUser(authentication);

            String chatId = collectionService.getOrCreateCollectionChat(
                    collectionId,
                    user, teamId
            );

            Map<String, Object> result = ragFlowService.askQuestion(
                    chatId,
                    question,
                    List.of()
            );

            return ResponseEntity.ok(result);

        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode())
                    .body(e.getReason());

        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("Errore durante l'interrogazione della collezione: "
                            + e.getMessage());
        }
    }

}