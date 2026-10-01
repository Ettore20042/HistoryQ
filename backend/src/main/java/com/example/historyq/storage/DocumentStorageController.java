package com.example.historyq.storage;

import com.example.historyq.entity.Document;
import com.example.historyq.service.DocumentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.example.historyq.service.OcrService;
import java.util.UUID;
import com.example.historyq.service.RagFlowService;
import java.util.Map;

@RestController
@RequestMapping("/api/documents")
public class DocumentStorageController {

    private final DocumentService documentService;
    private final OcrService ocrService;
    private final RagFlowService ragFlowService;
    public DocumentStorageController(DocumentService documentService, OcrService ocrService, RagFlowService ragFlowService) {
        this.documentService = documentService;
        this.ocrService = ocrService;
        this.ragFlowService = ragFlowService;
    }

    @PostMapping("/upload")
    public ResponseEntity<?> upload(
            @RequestParam("file") MultipartFile file
    ) {
        try {
            Document document = documentService.upload(file);
            return ResponseEntity.ok(document);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body("Richiesta non valida: " + e.getMessage());

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("Errore durante l'upload: " + e.getMessage());
        }
    }
    @GetMapping("/ocr-test")
    public ResponseEntity<?> testOcrConnection() {
        String result = ocrService.testConnection();

        return ResponseEntity.ok(result);
    }
    @PostMapping("/ocr-test")
    public ResponseEntity<?> testOcr(
            @RequestParam("file") MultipartFile file) {
        try {
            var result = ocrService.submitJob(file);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("Errore durante il test OCR: " + e.getMessage());
        }
    }
    @GetMapping("/ocr-status/{jobId}")
    public ResponseEntity<?> getOcrStatus(@PathVariable UUID jobId) {
        try {
            var result = ocrService.getJobStatus(jobId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("Errore durante il recupero dello stato OCR: " + e.getMessage());
        }
    }
    @GetMapping("/ocr-result/{jobId}")
    public ResponseEntity<?> getOcrResult(@PathVariable UUID jobId) {
        try {
            var result = ocrService.getJobResult(jobId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("Errore durante il recupero del risultato OCR: " + e.getMessage());
        }
    }
    @GetMapping("/ragflow-ocr-test")
    public ResponseEntity<?> ragflowOcrTest() {
        try {
            String datasetId =
                    ragFlowService.createDataset("test-ocr");

            Map<String, Object> uploadResult =
                    ragFlowService.uploadOcrText(
                            datasetId,
                            "test-ocr",
                            "Questo è un testo di prova ottenuto tramite OCR."
                    );

            return ResponseEntity.ok(
                    Map.of(
                            "datasetId", datasetId,
                            "upload", uploadResult
                    )
            );

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(
                            "Errore RAGFlow OCR: "
                                    + e.getMessage()
                    );
        }
    }
    @GetMapping("/ragflow-dataset-test")
    public ResponseEntity<?> ragflowDatasetTest() {
        try {
            String datasetId =
                    ragFlowService.createDataset("test-ocr");

            return ResponseEntity.ok(
                    Map.of(
                            "status", "success",
                            "datasetId", datasetId
                    )
            );

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(
                            "Errore creazione dataset RAGFlow: "
                                    + e.getMessage()
                    );
        }
    }
    @GetMapping("/ragflow-parse-test")
    public ResponseEntity<?> ragflowParseTest() {
        try {
            String datasetId =
                    "338f1ec6bd7a11f19cd3dfaeeec52c8e";

            String documentId =
                    "33a68acabd7a11f19cd3dfaeeec52c8e";

            return ResponseEntity.ok(
                    ragFlowService.parseDocument(
                            datasetId,
                            documentId
                    )
            );

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(
                            "Errore parsing RAGFlow: "
                                    + e.getMessage()
                    );
        }
    }
    @GetMapping("/ragflow-status-test")
    public ResponseEntity<?> ragflowStatusTest() {
        try {
            return ResponseEntity.ok(
                    ragFlowService.getDocumentStatus(
                            "338f1ec6bd7a11f19cd3dfaeeec52c8e",
                            "33a68acabd7a11f19cd3dfaeeec52c8e"
                    )
            );

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(
                            "Errore stato RAGFlow: "
                                    + e.getMessage()
                    );
        }
    }
}