package com.example.historyq.service;

import com.example.historyq.entity.Document;
import com.example.historyq.repository.DocumentRepository;
import com.example.historyq.storage.DocumentStorageService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.example.historyq.dto.OcrJobResultResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.example.historyq.repository.DocumentPageRepository;
import com.example.historyq.entity.DocumentPage;
import org.springframework.beans.factory.annotation.Value;
import java.time.Duration;


// Orchestrator of the document processing workflow, including OCR and RAGFlow integration.
@Service
public class DocumentProcessingService {

    private final DocumentRepository documentRepository;
    private final DocumentStorageService storageService;
    private final OcrService ocrService;
    private final RagFlowService ragFlowService;
    private final NotificationService notificationService;
    private static final Logger logger = LoggerFactory.getLogger(DocumentProcessingService.class);
    private final DocumentPageRepository documentPageRepository;
    @Value("${ocr.job.timeout:30m}")
    private Duration ocrJobTimeout;

    @Value("${ocr.job.poll-interval:1s}")
    private Duration ocrJobPollInterval;

    public DocumentProcessingService(
            DocumentRepository documentRepository,
            DocumentStorageService storageService,
            OcrService ocrService,
            RagFlowService ragFlowService,
            NotificationService notificationService,
            DocumentPageRepository documentPageRepository
    ) {
        this.documentRepository = documentRepository;
        this.storageService = storageService;
        this.ocrService = ocrService;
        this.ragFlowService = ragFlowService;
        this.notificationService = notificationService;
        this.documentPageRepository = documentPageRepository;
    }

    @Async
    public void processDocument(UUID documentId, String engine) {

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Documento non trovato: " + documentId
                        )
                );

        String storagePath = document.getStoragePath();

        try {

            // 1. Recupero delle pagine da Silo
            List<byte[]> contents = new ArrayList<>(); //contiene i byte di tutte le pagine del documento
            List<String> filenames = new ArrayList<>();

            List<String> objectNames =
                    storageService.listObjects(storagePath); //Dammi tutti gli oggetti in questo percorso di storage

            if (objectNames.size() != document.getPageCount()) {
                throw new IllegalStateException(
                        "Numero di pagine nello storage non corrispondente "
                                + "al pageCount del documento."
                );
            }

            try {

                for (String objectName : objectNames) {

                    byte[] content =
                            storageService.downloadBytes(objectName);

                    contents.add(content);

                    String filename =
                            objectName.substring(
                                    objectName.lastIndexOf("/") + 1
                            ); //trasformiamo il percorso completo in un semplice nome di file ES( pagina1.jpg)

                    filenames.add(filename);
                }



            } catch (Exception e) {
                throw new RuntimeException(
                        "Errore durante il recupero delle pagine da Silo",
                        e
                );
            }
            // 2. Avvio OCR
            document.setStatus("OCR_PROCESSING");
            document.setProgress(20);
            documentRepository.save(document);

            var ocrJob =
                    ocrService.submitJobFromStorageBytes(
                            contents,
                            filenames,
                            engine
                    );

            UUID jobId =
                    UUID.fromString(ocrJob.getJobId()); // Otteniamo l'ID del job OCR come UUID


            var ocrStatus = ocrService.getJobStatus(jobId);

            long startTime = System.nanoTime();
            long timeoutNanos = ocrJobTimeout.toNanos();
            long pollIntervalMillis = ocrJobPollInterval.toMillis();

            if (timeoutNanos <= 0 || pollIntervalMillis <= 0) {
                throw new IllegalStateException(
                        "Il timeout OCR e l'intervallo di polling devono essere positivi."
                );
            }

            while ("PENDING".equals(ocrStatus.getStatus())
                    || "PROCESSING".equals(ocrStatus.getStatus())) {

                if (System.nanoTime() - startTime >= timeoutNanos) {
                    throw new RuntimeException(
                            "Timeout OCR dopo " + ocrJobTimeout
                    );
                }

                Thread.sleep(pollIntervalMillis);
                ocrStatus = ocrService.getJobStatus(jobId);
            }

            if ("FAILED".equals(ocrStatus.getStatus())) {
                var ocrResult = ocrService.getJobResult(jobId);

                throw new RuntimeException(
                        "OCR fallito: " + ocrResult.getError()
                );
            }

            if (!"COMPLETED".equals(ocrStatus.getStatus())) {
                throw new RuntimeException(
                        "Stato OCR non riconosciuto: "
                                + ocrStatus.getStatus()
                );
            }

            var ocrResult = ocrService.getJobResult(jobId);
            List<OcrJobResultResponse.PageResult> pageResults = ocrResult.getPages();

            // 3. OCR completato
            document.setStatus("OCR_COMPLETED");
            document.setProgress(50);
            documentRepository.save(document);


            //Recupera le pagine restituite dall'OCR e aggiorna il database con il testo OCR per ogni pagina


            if (pageResults != null && !pageResults.isEmpty()) {
                for (OcrJobResultResponse.PageResult pageResult : pageResults) {
                    if (pageResult.getFilename() == null
                            || pageResult.getFilename().isBlank()) {
                        continue;
                    }

                    DocumentPage page = documentPageRepository
                            .findByDocument_IdAndFilename(
                                    documentId,
                                    pageResult.getFilename()
                            )
                            .orElseGet(DocumentPage::new);

                    page.setDocument(document);
                    page.setFilename(pageResult.getFilename());
                    page.setOcrText(pageResult.getText());

                    documentPageRepository.save(page);
                }
            }

            // 4. Elaborazione RAGFlow
            document.setStatus("RAGFLOW_PROCESSING");
            document.setProgress(50);
            documentRepository.save(document);

            String storedFilename =
                    document.getStoredFilename();

            String ragflowDatasetId =
                    ragFlowService.createDataset(
                            storedFilename
                    );

            List<String> ragflowDocumentIds = new ArrayList<>();


            if (pageResults != null && !pageResults.isEmpty()) {
                for (OcrJobResultResponse.PageResult pageResult : pageResults) {
                    if (pageResult.getFilename() == null
                            || pageResult.getFilename().isBlank()
                            || pageResult.getText() == null
                            || pageResult.getText().isBlank()) {
                        continue;
                    }

                    String ragflowDocumentId = ragFlowService.uploadOcrText(
                            ragflowDatasetId,
                            pageResult.getFilename(),
                            pageResult.getText()
                    );

                    ragflowDocumentIds.add(ragflowDocumentId);

                    DocumentPage page = documentPageRepository
                            .findByDocument_IdAndFilename(
                                    documentId,
                                    pageResult.getFilename()
                            )
                            .orElseThrow(() -> new IllegalStateException(
                                    "Pagina non trovata: " + pageResult.getFilename()
                            ));

                    page.setRagflowDocumentId(ragflowDocumentId);
                    documentPageRepository.save(page);
                }
            } else {
                ragflowDocumentIds.add(
                        ragFlowService.uploadOcrText(
                                ragflowDatasetId,
                                storedFilename,
                                ocrResult.getResult()
                        )
                );
            }

            if (ragflowDocumentIds.isEmpty()) {
                throw new IllegalStateException(
                        "Nessuna pagina OCR valida da caricare in RAGFlow."
                );
            }

            document.setRagflowDatasetId(
                    ragflowDatasetId
            );

            document.setProgress(60);
            documentRepository.save(document);

            // 5. Parsing RAGFlow
            for (String ragflowDocumentId : ragflowDocumentIds) {
                ragFlowService.parseDocument(
                        ragflowDatasetId,
                        ragflowDocumentId
                );
            }

            for (String ragflowDocumentId : ragflowDocumentIds) {
                ragFlowService.waitUntilParsed(
                        ragflowDatasetId,
                        ragflowDocumentId
                );
            }

            document.setProgress(90);
            documentRepository.save(document);

            // 6. Creazione Chat
            String ragflowChatName =
                    storedFilename + "-" + documentId;

            String ragflowChatId =
                    ragFlowService.createChat(
                            ragflowChatName,
                            ragflowDatasetId
                    );
            document.setRagflowChatId(
                    ragflowChatId
            );

            // 7. Documento pronto
            document.setStatus("READY");
            document.setProgress(100);

            documentRepository.save(document);

            try {
                notificationService.notifyDocumentReady(document);
            } catch (Exception e) {
                logger.error(
                        "Impossibile creare la notifica per il documento {}",
                        document.getId(),
                        e
                );
            }

        } catch (Exception e) {

            document.setStatus("ERROR");
            document.setProgress(0);

            documentRepository.save(document);
            try {
                notificationService.notifyDocumentError(document);
            } catch (Exception e2) {
                logger.error(
                        "Impossibile creare la notifica di errore per il documento {}",
                        document.getId(),
                        e2
                );
            }

            throw new RuntimeException(
                    "Errore durante l'elaborazione del documento",
                    e
            );
        }
    }
}