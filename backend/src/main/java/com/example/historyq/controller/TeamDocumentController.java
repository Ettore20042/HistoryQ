package com.example.historyq.controller;

import com.example.historyq.dto.TeamDocumentResponse;
import com.example.historyq.dto.PublicDocumentResponse;
import com.example.historyq.entity.Document;
import com.example.historyq.service.TeamDocumentService;
import com.example.historyq.storage.DocumentStorageService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import com.example.historyq.entity.DocumentPage;
import com.example.historyq.service.RagFlowService;
import java.util.Map;

@RestController
@RequestMapping("/api/teams/{teamId}/documents")
public class TeamDocumentController {

    private final TeamDocumentService teamDocumentService;
    private final DocumentStorageService storageService;
    private final RagFlowService ragFlowService;

    public TeamDocumentController(
            TeamDocumentService teamDocumentService,
            DocumentStorageService storageService,
            RagFlowService ragFlowService
    ) {
        this.teamDocumentService = teamDocumentService;
        this.storageService = storageService;
        this.ragFlowService = ragFlowService;
    }

    @GetMapping
    public List<TeamDocumentResponse> getSharedDocuments(
            @PathVariable UUID teamId
    ) {
        return teamDocumentService.getSharedDocuments(teamId);
    }

    @GetMapping("/candidates")
    public List<TeamDocumentResponse> getCandidates(
            @PathVariable UUID teamId,
            @RequestParam(defaultValue = "ALL") String source,
            @RequestParam(required = false) String query
    ) {
        return teamDocumentService.getCandidates(teamId, source, query);
    }

    @PostMapping
    public List<TeamDocumentResponse> addDocuments(
            @PathVariable UUID teamId,
            @RequestBody AddDocumentsRequest request
    ) {
        return teamDocumentService.addDocuments(teamId, request.documentIds());
    }

    @DeleteMapping("/{documentId}")
    public ResponseEntity<Void> removeDocument(
            @PathVariable UUID teamId,
            @PathVariable UUID documentId
    ) {
        teamDocumentService.removeDocument(teamId, documentId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{documentId}")
    public PublicDocumentResponse getDocument(
            @PathVariable UUID teamId,
            @PathVariable UUID documentId
    ) {
        Document document = teamDocumentService.getSharedDocument(teamId, documentId);
        return PublicDocumentResponse.from(document);
    }

    @GetMapping("/{documentId}/preview")
    public ResponseEntity<byte[]> preview(
            @PathVariable UUID teamId,
            @PathVariable UUID documentId
    ) throws Exception {
        Document document = teamDocumentService.getSharedDocument(teamId, documentId);
        String prefix = "documents/" + documentId + "/pages/";
        List<String> objects = storageService.listObjects(prefix);
        if (objects.isEmpty()) return ResponseEntity.notFound().build();
        String firstPage = objects.get(0);
        MediaType mediaType = mediaTypeFor(firstPage);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .contentType(mediaType)
                .body(storageService.downloadBytes(firstPage));
    }

    @GetMapping("/{documentId}/download")
    public ResponseEntity<byte[]> download(
            @PathVariable UUID teamId,
            @PathVariable UUID documentId
    ) throws Exception {
        Document document = teamDocumentService.getSharedDocument(teamId, documentId);
        String prefix = "documents/" + documentId + "/pages/";
        List<String> objects = storageService.listObjects(prefix);
        if (objects.isEmpty()) return ResponseEntity.notFound().build();

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (String object : objects) {
                String name = object.substring(prefix.length());
                zip.putNextEntry(new ZipEntry(name));
                zip.write(storageService.downloadBytes(object));
                zip.closeEntry();
            }

        }
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + safeFilename(document) + ".zip\""
                )
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(output.toByteArray());
    }

    @GetMapping("/{documentId}/pages")
    public List<String> pages(@PathVariable UUID teamId, @PathVariable UUID documentId)
            throws Exception {
        teamDocumentService.getSharedDocument(teamId, documentId);
        String prefix = "documents/" + documentId + "/pages/";
        return storageService.listObjects(prefix).stream()
                .map(object -> object.substring(prefix.length()))
                .toList();
    }

    @GetMapping("/{documentId}/pages/image/{filename:.+}")
    public ResponseEntity<byte[]> page(
            @PathVariable UUID teamId, @PathVariable UUID documentId,
            @PathVariable String filename) throws Exception {
        teamDocumentService.getSharedPage(teamId, documentId, filename);
        String object = "documents/" + documentId + "/pages/" + filename;
        return ResponseEntity.ok().contentType(mediaTypeFor(object))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(storageService.downloadBytes(object));
    }

    @GetMapping("/{documentId}/page-transcriptions/{filename:.+}")
    public ResponseEntity<PageTranscriptionResponse> transcription(
            @PathVariable UUID teamId, @PathVariable UUID documentId,
            @PathVariable String filename) {
        DocumentPage page = teamDocumentService.getSharedPage(teamId, documentId, filename);
        return ResponseEntity.ok(new PageTranscriptionResponse(
                page.getId(), documentId, page.getFilename(), page.getOcrText()));
    }

    @PostMapping("/{documentId}/chat")
    public ResponseEntity<?> chat(
            @PathVariable UUID teamId, @PathVariable UUID documentId,
            @RequestBody Map<String, String> request) throws Exception {
        Document document = teamDocumentService.getSharedDocument(teamId, documentId);
        String question = request == null ? null : request.get("question");
        if (question == null || question.isBlank()) {
            return ResponseEntity.badRequest().body("La domanda non può essere vuota.");
        }
        if (document.getRagflowChatId() == null || document.getRagflowChatId().isBlank()) {
            return ResponseEntity.status(409).body("La chat non è ancora disponibile.");
        }
        return ResponseEntity.ok(ragFlowService.askQuestion(
                document.getRagflowChatId(), question,
                storageService.listObjects(document.getStoragePath()).stream()
                        .map(object -> object.substring(object.lastIndexOf("/") + 1))
                        .toList()));
    }

    public record PageTranscriptionResponse(
            UUID id, UUID documentId, String filename, String ocrText) {
    }

    private MediaType mediaTypeFor(String objectName) {
        String lower = objectName.toLowerCase();
        if (lower.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (lower.endsWith(".webp")) return MediaType.parseMediaType("image/webp");
        return MediaType.IMAGE_JPEG;
    }

    private String safeFilename(Document document) {
        return document.getDisplayName()
                .replaceAll("[^A-Za-z0-9._-]", "_");
    }

    public record AddDocumentsRequest(List<UUID> documentIds) {
    }
}
