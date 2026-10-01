package com.example.historyq.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.client.MultipartBodyBuilder;
import com.example.historyq.dto.OcrJobResponse;
import com.example.historyq.dto.OcrJobStatusResponse;
import com.example.historyq.dto.OcrJobResultResponse;
import java.util.UUID;

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
    public OcrJobResponse submitJob(MultipartFile file) {
        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        bodyBuilder.part("file", file.getResource());
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