package com.example.historyq.storage;

import com.example.historyq.entity.Document;
import com.example.historyq.service.DocumentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documents")
public class DocumentStorageController {

    private final DocumentService documentService;

    public DocumentStorageController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping("/upload")
    public ResponseEntity<?> upload(
            @RequestParam("file") MultipartFile file
    ) {
        try {
            Document document = documentService.upload(file);
            return ResponseEntity.ok(document);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("Errore durante l'upload: " + e.getMessage());
        }
    }
}