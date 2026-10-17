package com.example.historyq.controller;

import com.example.historyq.entity.Document;
import com.example.historyq.dto.PublicDocumentResponse;
import com.example.historyq.service.DocumentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import com.example.historyq.repository.DocumentPageRepository;
import com.example.historyq.storage.DocumentPageController;

import java.util.List;
import com.example.historyq.entity.Document;
import com.example.historyq.repository.DocumentRepository;
import com.example.historyq.storage.DocumentStorageService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;


import java.util.UUID;
import java.util.Map;
import com.example.historyq.service.RagFlowService;

@RestController
@RequestMapping("/api/public/documents")
public class PublicDocumentController {

    private final DocumentService documentService;
    private final DocumentRepository documentRepository;
    private final DocumentStorageService documentStorageService;
    private final DocumentPageRepository documentPageRepository;
    private final RagFlowService ragFlowService;


    public PublicDocumentController(
            DocumentService documentService,
            DocumentRepository documentRepository,
            DocumentStorageService documentStorageService,
            DocumentPageRepository documentPageRepository,
            RagFlowService ragFlowService
    ) {
        this.documentService = documentService;
        this.documentRepository = documentRepository;
        this.documentStorageService = documentStorageService;
        this.documentPageRepository = documentPageRepository;
        this.ragFlowService = ragFlowService;
    }
    @GetMapping
    public List<PublicDocumentResponse> getPublicDocuments(
            @RequestParam(required = false) String name
    ) {
        return documentService.searchPublicDocuments(name).stream()
                .map(PublicDocumentResponse::from)
                .toList();
    }

    @GetMapping("/{documentId}")
    public ResponseEntity<PublicDocumentResponse> getPublicDocument(@PathVariable UUID documentId) {
        return documentRepository.findById(documentId)
                .filter(document -> "PUBLIC".equals(document.getVisibility()))
                .map(document -> ResponseEntity.ok(PublicDocumentResponse.from(document)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{documentId}/chat")
    public ResponseEntity<?> chat(
            @PathVariable UUID documentId,
            @RequestBody Map<String, String> request
    ) {
        String question = request == null ? null : request.get("question");
        if (question == null || question.isBlank()) {
            return ResponseEntity.badRequest().body("La domanda non può essere vuota.");
        }

        Document document = documentRepository.findById(documentId).orElse(null);
        if (document == null || !"PUBLIC".equals(document.getVisibility())) {
            return ResponseEntity.notFound().build();
        }

        String chatId = document.getRagflowChatId();
        if (chatId == null || chatId.isBlank()) {
            return ResponseEntity.status(409).body("La chat non è ancora disponibile per questo documento.");
        }

        try {
            Map<String, Object> result = ragFlowService.askQuestion(
                    chatId,
                    question,
                    documentStorageService.listObjects(document.getStoragePath()).stream()
                            .map(objectName -> objectName.substring(objectName.lastIndexOf("/") + 1))
                            .toList()
            );
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Errore durante l'interrogazione RAG.");
        }
    }

    @GetMapping("/{documentId}/preview")
    public ResponseEntity<byte[]> getPublicPreview(
            @PathVariable UUID documentId
    ) {
        try {
            Document document = documentRepository.findById(documentId)
                    .orElseThrow(() ->
                            new IllegalArgumentException("Documento non trovato")
                    );

            // Non restituire anteprime di documenti privati.
            if (!"PUBLIC".equals(document.getVisibility())) {
                return ResponseEntity.notFound().build();
            }

            String prefix = "documents/" + documentId + "/pages/";
            var objects = documentStorageService.listObjects(prefix);

            if (objects.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            String firstPage = objects.get(0);
            byte[] image = documentStorageService.downloadBytes(firstPage);

            MediaType mediaType = firstPage.toLowerCase().endsWith(".png")
                    ? MediaType.IMAGE_PNG
                    : MediaType.IMAGE_JPEG;

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
    @GetMapping("/{documentId}/page-transcriptions/{filename:.+}")
    public ResponseEntity<DocumentPageController.PageTranscriptionResponse>
    getPublicTranscription(
            @PathVariable UUID documentId,
            @PathVariable String filename) {

        if (!filename.matches("[A-Za-z0-9._-]+") || filename.contains("..")) {
            return ResponseEntity.badRequest().build();
        }

        Document document = documentRepository.findById(documentId)
                .orElse(null);

        if (document == null || !"PUBLIC".equals(document.getVisibility())) {
            return ResponseEntity.notFound().build();
        }

        return documentPageRepository
                .findByDocument_IdAndFilename(documentId, filename)
                .map(page -> ResponseEntity.ok(
                        new DocumentPageController.PageTranscriptionResponse(
                                page.getId(),
                                page.getDocument().getId(),
                                page.getFilename(),
                                page.getOcrText()
                        )
                ))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
    @GetMapping("/{documentId}/pages")
    public ResponseEntity<List<String>> getPublicPages(
            @PathVariable UUID documentId) {
        try {
            Document document = documentRepository.findById(documentId)
                    .orElse(null);

            if (document == null || !"PUBLIC".equals(document.getVisibility())) {
                return ResponseEntity.notFound().build();
            }

            String prefix = "documents/" + documentId + "/pages/";

            List<String> pageNames = documentStorageService.listObjects(prefix)
                    .stream()
                    .map(objectName -> objectName.substring(prefix.length()))
                    .toList();

            return ResponseEntity.ok(pageNames);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{documentId}/pages/image/{filename:.+}")
    public ResponseEntity<byte[]> getPublicDocumentPage(
            @PathVariable UUID documentId,
            @PathVariable String filename) {
        try {
            Document document = documentRepository.findById(documentId)
                    .orElse(null);

            if (document == null || !"PUBLIC".equals(document.getVisibility())) {
                return ResponseEntity.notFound().build();
            }

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
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }


}