package com.example.historyq.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.client.MultipartBodyBuilder;
import com.example.historyq.dto.OcrJobResponse;
import com.example.historyq.dto.OcrJobStatusResponse;
import com.example.historyq.dto.OcrJobResultResponse;
import java.util.UUID;
import org.springframework.core.io.ByteArrayResource;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;

@Service
public class OcrService {
    private final RestClient restClient;
    private final String ocrServiceToken;

    public OcrService(RestClient restClient,@Value("${ocr.service.token}") String ocrServiceToken) {
        if (ocrServiceToken == null || ocrServiceToken.isBlank()) {
            throw new IllegalStateException(
                    "La variabile OCR_API_TOKEN non è configurata."
            );
        }
        this.restClient = restClient;
        this.ocrServiceToken = ocrServiceToken;
    }
    public String testConnection() {
        return restClient
                .get()
                .uri("/")
                .retrieve()
                .body(String.class);
    }

    public OcrJobResponse submitJob(
            MultipartFile[] files
    ) {
        if (files == null || files.length == 0) {
            throw new IllegalArgumentException(
                    "Nessun file da inviare al servizio OCR."
            );
        }

        MultipartBodyBuilder bodyBuilder =
                new MultipartBodyBuilder();

        for (MultipartFile file : files) {
            bodyBuilder.part(
                    "files",
                    file.getResource()
            );
        }

        return restClient
                .post()
                .uri("/ocr/batch")
                .header("Authorization", "Bearer " + ocrServiceToken)
                .body(bodyBuilder.build())
                .retrieve()
                .body(OcrJobResponse.class);
    }

    public OcrJobResponse submitJobFromStorageBytes(
            List<byte[]> contents,
            List<String> filenames,
            String engine
    ) {

        if (contents == null || contents.isEmpty()) {
            throw new IllegalArgumentException(
                    "Nessun file da inviare al servizio OCR."
            );
        }

        if (contents.size() != filenames.size()) {
            throw new IllegalArgumentException(
                    "Numero di file e nomi file non corrispondente."
            );
        }

        MultipartBodyBuilder bodyBuilder =
                new MultipartBodyBuilder(); // Creiamo un body multipart per la richiesta
        bodyBuilder.part("engine", engine); // Aggiungiamo il parametro engine al body

        for (int i = 0; i < contents.size(); i++) {

            int index = i;

            ByteArrayResource resource =
                    new ByteArrayResource(contents.get(index)) {
                        @Override
                        public String getFilename() {
                            return filenames.get(index);
                        }
                    };

            bodyBuilder.part(
                    "files",
                    resource
            );
        }

        return restClient
                .post()
                .uri("/ocr/batch")
                .header("Authorization", "Bearer " + ocrServiceToken) // Aggiungiamo l'header di autorizzazione con il token
                .body(bodyBuilder.build())
                .retrieve()
                .body(OcrJobResponse.class);
    }



    public OcrJobStatusResponse getJobStatus(UUID jobId) {
        return restClient
                .get()
                .uri("/ocr/batch/{jobId}", jobId)
                .header("Authorization", "Bearer " + ocrServiceToken)
                .retrieve()
                .body(OcrJobStatusResponse.class);
    }
    public OcrJobResultResponse getJobResult(UUID jobId) {
        return restClient
                .get()
                .uri("/ocr/batch/{jobId}/result", jobId)
                .header("Authorization", "Bearer " + ocrServiceToken)
                .retrieve()
                .body(OcrJobResultResponse.class);
    }
}