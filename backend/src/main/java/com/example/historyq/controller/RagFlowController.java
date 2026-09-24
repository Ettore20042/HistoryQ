package com.example.historyq.controller;

import com.example.historyq.dto.ChatRequest;
import com.example.historyq.service.RagFlowService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rag")
public class RagFlowController {

    private final RagFlowService ragFlowService;

    public RagFlowController(RagFlowService ragFlowService) {
        this.ragFlowService = ragFlowService;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(ragFlowService.checkStatus());
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> upload(
            @RequestParam(value = "files", required = false) MultipartFile[] files
    ) {
        if (files == null || files.length == 0 || Arrays.stream(files).allMatch(file -> file == null || file.isEmpty())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "Nessun file selezionato o file vuoto."
            ));
        }

        try {
            List<String> uploadedFiles = new ArrayList<>();
            for (MultipartFile file : files) {
                if (file != null && !file.isEmpty()) {
                    ragFlowService.uploadDocument(file);
                    uploadedFiles.add(file.getOriginalFilename() != null ? file.getOriginalFilename() : "document");
                }
            }

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", "success");
            response.put("uploadedCount", uploadedFiles.size());
            response.put("fileNames", uploadedFiles);
            response.put("message", uploadedFiles.size() + " file caricati con successo nel dataset.");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(502).body(Map.of(
                    "status", "error",
                    "message", "Impossibile raggiungere il servizio RAGFlow: " + e.getMessage()
            ));
        }
    }

    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@Valid @RequestBody ChatRequest request) {
        try {
            Map<String, Object> response = ragFlowService.askQuestion(request.question());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(502).body(Map.of(
                    "status", "error",
                    "message", "Impossibile contattare il servizio RAGFlow: " + e.getMessage()
            ));
        }
    }
}
