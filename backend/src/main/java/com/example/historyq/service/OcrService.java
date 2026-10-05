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
import java.io.InputStream;
import java.util.List;

@Service
public class OcrService {
    private final RestClient restClient;

    public OcrService(RestClient restClient) {
        this.restClient = restClient;
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
                .body(bodyBuilder.build())
                .retrieve()
                .body(OcrJobResponse.class);
    }
    public OcrJobResponse submitJobFromStorage(
            List<InputStream> inputStreams,
            List<String> filenames
    ) throws Exception {

        if (inputStreams == null || inputStreams.isEmpty()) {
            throw new IllegalArgumentException(
                    "Nessun file da inviare al servizio OCR."
            );
        }

        if (inputStreams.size() != filenames.size()) {
            throw new IllegalArgumentException(
                    "Numero di file e nomi file non corrispondente."
            );
        }

        MultipartBodyBuilder bodyBuilder =
                new MultipartBodyBuilder();

        for (int i = 0; i < inputStreams.size(); i++) {

            int index = i;

            byte[] content = inputStreams.get(index).readAllBytes();

            ByteArrayResource resource =
                    new ByteArrayResource(content) {
                        @Override
                        public String getFilename() {
                            return filenames.get(index);
                        }
                    };

            bodyBuilder.part("files", resource);
        }

        return restClient
                .post()
                .uri("/ocr/batch")
                .body(bodyBuilder.build())
                .retrieve()
                .body(OcrJobResponse.class);
    }
    public OcrJobResponse submitJobFromStorageBytes(
            List<byte[]> contents,
            List<String> filenames
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
                new MultipartBodyBuilder();

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
                .body(bodyBuilder.build())
                .retrieve()
                .body(OcrJobResponse.class);
    }



    public OcrJobStatusResponse getJobStatus(UUID jobId) {
        return restClient
                .get()
                .uri("/ocr/batch/{jobId}", jobId)
                .retrieve()
                .body(OcrJobStatusResponse.class);
    }
    public OcrJobResultResponse getJobResult(UUID jobId) {
        return restClient
                .get()
                .uri("/ocr/batch/{jobId}/result", jobId)
                .retrieve()
                .body(OcrJobResultResponse.class);
    }
}