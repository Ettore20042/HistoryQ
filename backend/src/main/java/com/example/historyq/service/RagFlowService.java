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
import java.nio.charset.StandardCharsets;

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
        log.info(
                "RAGFlow API key presente: {}",
                apiKey != null && !apiKey.isBlank()
        );
        log.info(
                "RAGFlow API key length: {}",
                apiKey != null ? apiKey.length() : 0
        );
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
    public Map<String, Object> uploadOcrText(String datasetId, String filename, String ocrText) {

        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("Il nome del file non può essere vuoto.");
        }

        if (ocrText == null || ocrText.isBlank()) {
            throw new IllegalArgumentException("Il testo OCR non può essere vuoto.");
        }

        String txtFilename = filename + ".txt";

        log.info(
                "Uploading OCR text '{}' to dataset '{}'",
                txtFilename,
                datasetId
        );

        log.info(
                "RAGFlow URL: {}/api/v1/datasets/{}/documents",
                baseUrl,
                datasetId
        );

        byte[] content = ocrText.getBytes(StandardCharsets.UTF_8);

        ByteArrayResource resource = new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return txtFilename;
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", resource);

        String responseBody = restClient.post()
                .uri("/api/v1/datasets/{datasetId}/documents", datasetId)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(String.class);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "success");
        result.put("fileName", txtFilename);
        result.put("message", "Testo OCR caricato con successo nel dataset.");
        result.put("rawResponse", responseBody != null ? responseBody : "");

        return result;
    }
    public String createDataset(String documentName) {

        if (documentName == null || documentName.isBlank()) {
            throw new IllegalArgumentException(
                    "Il nome del documento non può essere vuoto."
            );
        }

        Map<String, Object> payload = new LinkedHashMap<>();

        payload.put(
                "name",
                "document-" + documentName
        );

        payload.put(
                "chunk_method",
                "naive"
        );

        String responseBody = restClient.post()
                .uri("/api/v1/datasets")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(String.class);

        try {
            JsonNode root = objectMapper.readTree(responseBody);

            int code = root.path("code").asInt(-1);

            if (code != 0) {
                throw new RuntimeException(
                        "Errore creazione dataset RAGFlow: "
                                + root.path("message").asText()
                );
            }

            String datasetId = root.path("data").path("id").asText();

            if (datasetId.isBlank()) {
                throw new RuntimeException(
                        "RAGFlow non ha restituito il dataset ID."
                );
            }

            log.info(
                    "Dataset RAGFlow creato: {}",
                    datasetId
            );

            return datasetId;

        } catch (Exception e) {
            throw new RuntimeException(
                    "Errore nella risposta di RAGFlow durante la creazione del dataset.",
                    e
            );
        }
    }
    public Map<String, Object> parseDocument(
            String datasetId,
            String documentId
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();

        payload.put(
                "document_ids",
                List.of(documentId)
        );

        String responseBody = restClient.post()
                .uri(
                        "/api/v1/datasets/{datasetId}/documents/parse",
                        datasetId
                )
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(String.class);

        try {
            JsonNode root = objectMapper.readTree(responseBody); // Parse the response body as JSON

            if (root.path("code").asInt(-1) != 0) {
                throw new RuntimeException(
                        "Errore parsing RAGFlow: "
                                + root.path("message").asText()
                );
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "success");
            result.put("rawResponse", responseBody);// Store the raw response for debugging

            log.info(
                    "Parsing RAGFlow avviato: dataset={}, document={}",
                    datasetId,
                    documentId
            );

            return result;

        } catch (Exception e) {
            throw new RuntimeException(
                    "Errore durante l'avvio del parsing RAGFlow.",
                    e
            );
        }
    }
    public Map<String, Object> getDocumentStatus(
            String datasetId,
            String documentId
    ) {
        String responseBody = restClient.get()
                .uri(
                        "/api/v1/datasets/{datasetId}/documents",
                        datasetId
                )
                .retrieve()
                .body(String.class);

        try {
            JsonNode root = objectMapper.readTree(responseBody);

            if (root.path("code").asInt(-1) != 0) {
                throw new RuntimeException(
                        "Errore RAGFlow: "
                                + root.path("message").asText()
                );
            }

            JsonNode data = root.path("data");

            JsonNode documents;

            if (data.isArray()) {
                documents = data;
            } else {
                documents = data.path("docs");
            }

            for (JsonNode document : documents) {

                if (documentId.equals(document.path("id").asText())) {

                    Map<String, Object> result = new LinkedHashMap<>();

                    result.put("id", document.path("id").asText());
                    result.put("name", document.path("name").asText());
                    result.put("run", document.path("run").asText());
                    result.put(
                            "progress",
                            document.path("progress").asDouble()
                    );
                    result.put(
                            "chunkCount",
                            document.path("chunk_count").asInt()
                    );

                    return result;
                }
            }

            throw new RuntimeException(
                    "Documento non trovato nel dataset RAGFlow. "
                            + "Dataset=" + datasetId
                            + ", Document=" + documentId
            );

        } catch (Exception e) {
            throw new RuntimeException(
                    "Errore durante il controllo dello stato RAGFlow: "
                            + e.getMessage(),
                    e
            );
        }
    }
}
