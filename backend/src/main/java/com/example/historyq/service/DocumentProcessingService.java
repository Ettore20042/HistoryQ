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
// Orchestrator of the document processing workflow, including OCR and RAGFlow integration.
@Service
public class DocumentProcessingService {

    private final DocumentRepository documentRepository;
    private final DocumentStorageService storageService;
    private final OcrService ocrService;
    private final RagFlowService ragFlowService;

    public DocumentProcessingService(
            DocumentRepository documentRepository,
            DocumentStorageService storageService,
            OcrService ocrService,
            RagFlowService ragFlowService
    ) {
        this.documentRepository = documentRepository;
        this.storageService = storageService;
        this.ocrService = ocrService;
        this.ragFlowService = ragFlowService;
    }

    @Async
    public void processDocument(UUID documentId) {

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

                // Avvio OCR
                document.setStatus("OCR_PROCESSING");
                document.setProgress(20);
                documentRepository.save(document);// Aggiorna lo stato del documento a "OCR_PROCESSING" e salva il progresso al 20% nel database.

                var ocrJob =
                        ocrService.submitJobFromStorageBytes(
                                contents,
                                filenames
                        );

                // Il resto dell'elaborazione continua qui...

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
                            filenames
                    );

            UUID jobId =
                    UUID.fromString(ocrJob.getJobId()); // Otteniamo l'ID del job OCR come UUID

            var ocrStatus =
                    ocrService.getJobStatus(jobId);

            long startTime =
                    System.currentTimeMillis();

            long timeout =
                    2 * 60 * 1000L;

            while (
                    (
                            ocrStatus.getStatus().equals("PENDING")
                                    || ocrStatus.getStatus().equals("PROCESSING")
                    )
                            && (System.currentTimeMillis() - startTime) < timeout
            ) {

                Thread.sleep(1000);

                ocrStatus =
                        ocrService.getJobStatus(jobId);
            }

            if (
                    ocrStatus.getStatus().equals("PENDING")
                            || ocrStatus.getStatus().equals("PROCESSING")
            ) {
                throw new RuntimeException("Timeout OCR");
            }

            if (ocrStatus.getStatus().equals("FAILED")) {

                var ocrResult =
                        ocrService.getJobResult(jobId);

                throw new RuntimeException(
                        "OCR fallito: " + ocrResult.getError()
                );
            }

            var ocrResult =
                    ocrService.getJobResult(jobId);

            // 3. OCR completato
            document.setStatus("OCR_COMPLETED");
            document.setOcrText(ocrResult.getResult());
            document.setProgress(50);
            documentRepository.save(document);

            // 4. Elaborazione RAGFlow
            document.setStatus("RAGFLOW_PROCESSING");
            document.setProgress(50);
            documentRepository.save(document);

            String originalFilename =
                    document.getOriginalName();

            String ragflowDatasetId =
                    ragFlowService.createDataset(
                            originalFilename
                    );

            String ragflowDocumentId =
                    ragFlowService.uploadOcrText(
                            ragflowDatasetId,
                            originalFilename,
                            ocrResult.getResult()
                    );

            document.setRagflowDatasetId(
                    ragflowDatasetId
            );

            document.setProgress(60);
            documentRepository.save(document);

            // 5. Parsing RAGFlow
            ragFlowService.parseDocument(
                    ragflowDatasetId,
                    ragflowDocumentId
            );

            ragFlowService.waitUntilParsed(
                    ragflowDatasetId,
                    ragflowDocumentId
            );

            document.setProgress(90);
            documentRepository.save(document);

            // 6. Creazione Chat
            String ragflowChatName =
                    originalFilename + "-" + documentId;

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

        } catch (Exception e) {

            document.setStatus("ERROR");
            document.setProgress(0);

            documentRepository.save(document);

            throw new RuntimeException(
                    "Errore durante l'elaborazione del documento",
                    e
            );
        }
    }
}