package com.example.historyq.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RagFlowService {

    private static final Logger log = LoggerFactory.getLogger(RagFlowService.class);

    private final RestClient restClient;
    private final String baseUrl;
    private final String datasetId;
    private final String chatId;
    private final ObjectMapper objectMapper;

    public RagFlowService(
            @Value("${ragflow.base.url:http://127.0.0.1:9380}") String baseUrl,
            @Value("${ragflow.api.key:}") String apiKey,
            @Value("${ragflow.dataset.id:}") String datasetId,
            @Value("${ragflow.chat.id:}") String chatId,
            ObjectMapper objectMapper
    ) {
        this.baseUrl = baseUrl;
        this.datasetId = datasetId;
        this.chatId = chatId;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
    }

    /**
     * Checks connection to RAGFlow and returns live health status with document count.
     */
    public Map<String, Object> checkStatus() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ragflowBaseUrl", baseUrl);
        result.put("datasetId", datasetId);
        result.put("chatId", chatId);

        try {
            String response = restClient.get()
                    .uri("/api/v1/datasets/{datasetId}/documents", datasetId)
                    .retrieve()
                    .body(String.class);

            if (response != null) {
                JsonNode root = objectMapper.readTree(response);
                int code = root.path("code").asInt(-1);
                if (code == 0) {
                    int total = root.path("data").path("total").asInt(0);
                    result.put("connected", true);
                    result.put("status", "ONLINE");
                    result.put("documentCount", total);
                    result.put("message", "Connesso con successo a RAGFlow. Dataset verificato.");
                    return result;
                }
            }
            result.put("connected", false);
            result.put("status", "ERROR");
            result.put("message", "RAGFlow ha risposto con codice di errore.");
        } catch (Exception e) {
            log.warn("Errore durante il controllo dello stato di RAGFlow: {}", e.getMessage());
            result.put("connected", false);
            result.put("status", "OFFLINE");
            result.put("message", "Impossibile raggiungere RAGFlow su " + baseUrl + " (" + e.getMessage() + ")");
        }

        return result;
    }

    /**
     * Uploads a document to RAGFlow dataset ingestion endpoint.
     */
    public Map<String, Object> uploadDocument(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File mancante o vuoto.");
        }

        String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "document";
        log.info("Uploading document '{}' to dataset '{}'", fileName, datasetId);

        String responseBody = restClient.post()
                .uri("/api/v1/datasets/{datasetId}/documents", datasetId)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(buildUploadBody(file))
                .retrieve()
                .body(String.class);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "success");
        result.put("fileName", fileName);
        result.put("message", "Documento caricato con successo nel dataset.");
        result.put("rawResponse", responseBody != null ? responseBody : "");
        return result;
    }

    /**
     * Asks a question to RAGFlow chat endpoint.
     */
    public Map<String, Object> askQuestion(String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("La domanda non può essere vuota.");
        }

        log.info("Asking question to RAGFlow (dataset='{}', chat='{}')", datasetId, chatId);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("question", question.trim());
        payload.put("stream", false);

        if (chatId != null && !chatId.isBlank() && !chatId.startsWith("your_")) {
            payload.put("chat_id", chatId);
        }
        if (datasetId != null && !datasetId.isBlank() && !datasetId.startsWith("your_")) {
            payload.put("dataset_ids", List.of(datasetId));
        }

        String responseBody = restClient.post()
                .uri("/api/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(String.class);

        String answer = extractAnswer(responseBody);
        List<?> citations = extractCitations(responseBody);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "success");
        result.put("answer", answer);
        result.put("citations", citations);
        result.put("rawResponse", responseBody != null ? responseBody : "");
        return result;
    }

    private MultiValueMap<String, Object> buildUploadBody(MultipartFile file) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "document";
        try {
            ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return filename;
                }
            };
            body.add("file", resource);
        } catch (Exception e) {
            body.add("file", file.getResource());
        }
        return body;
    }

    private String extractAnswer(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return "Nessuna risposta ricevuta da RAGFlow.";
        }

        try {
            JsonNode root = objectMapper.readTree(rawBody);

            // 1. data.answer
            JsonNode dataAnswer = root.path("data").path("answer");
            if (dataAnswer.isTextual()) {
                return dataAnswer.asText();
            }

            // 2. answer
            JsonNode answerNode = root.path("answer");
            if (answerNode.isTextual()) {
                return answerNode.asText();
            }

            // 3. data.content
            JsonNode dataContent = root.path("data").path("content");
            if (dataContent.isTextual()) {
                return dataContent.asText();
            }

            // 4. choices[0].message.content (OpenAI format)
            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                JsonNode choiceContent = choices.get(0).path("message").path("content");
                if (choiceContent.isTextual()) {
                    return choiceContent.asText();
                }
            }

            // 5. error message if RAGFlow returned error code
            JsonNode messageNode = root.path("message");
            if (messageNode.isTextual() && !messageNode.asText().isBlank() && !root.path("code").asText().equals("0")) {
                return "RAGFlow error: " + messageNode.asText();
            }

            return rawBody;
        } catch (Exception e) {
            log.warn("Errore parsing risposta RAGFlow: {}", e.getMessage());
            return rawBody;
        }
    }

    private List<?> extractCitations(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return Collections.emptyList();
        }

        try {
            JsonNode root = objectMapper.readTree(rawBody);

            JsonNode citations = root.path("citations");
            if (citations.isArray()) {
                return objectMapper.convertValue(citations, List.class);
            }

            JsonNode dataCitations = root.path("data").path("citations");
            if (dataCitations.isArray()) {
                return objectMapper.convertValue(dataCitations, List.class);
            }

            JsonNode reference = root.path("data").path("reference");
            if (reference.isArray()) {
                return objectMapper.convertValue(reference, List.class);
            }

            return Collections.emptyList();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
