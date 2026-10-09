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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.nio.charset.StandardCharsets;

@Service
public class RagFlowService {

    private static final Logger log = LoggerFactory.getLogger(RagFlowService.class);

    private final RestClient restClient;
    private final String baseUrl;
    private final ObjectMapper objectMapper;

    public RagFlowService(
            @Value("${ragflow.base.url:http://127.0.0.1:9380}") String baseUrl,
            @Value("${ragflow.api.key:}") String apiKey,
            ObjectMapper objectMapper
    ) {
        this.baseUrl = baseUrl;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
    }



    /**
     * Asks a question to RAGFlow chat endpoint.
     */

    public Map<String, Object> askQuestion(
            String chatId,
            String question
    ) {
        return askQuestion(chatId, question, Collections.emptyList());
    }

    public Map<String, Object> askQuestion(
            String chatId,
            String question,
            List<String> pageFilenames
    ) {
        if (chatId == null || chatId.isBlank()) {
            throw new IllegalArgumentException(
                    "Il chat ID RAGFlow non può essere vuoto."
            );
        }

        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException(
                    "La domanda non può essere vuota."
            );
        }

        log.info(
                "Domanda inviata a RAGFlow: chat={}",
                chatId
        );

        Map<String, Object> payload = new LinkedHashMap<>();

        payload.put(
                "question",
                question.trim()
        );

        payload.put(
                "stream",
                false
        );

        String responseBody = restClient.post()
                .uri(
                        "/api/v1/chats/{chatId}/completions",
                        chatId
                )
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(String.class);

        String answer = extractAnswer(responseBody);

        List<?> citations = addPageReferences(
                extractCitations(responseBody),
                pageFilenames
        );

        Map<String, Object> result = new LinkedHashMap<>();

        result.put(
                "status",
                "success"
        );

        result.put(
                "answer",
                answer
        );

        result.put(
                "citations",
                citations
        );

        return result;
    }

    private List<?> addPageReferences(
            List<?> citations,
            List<String> pageFilenames
    ) {
        if (citations.isEmpty() || pageFilenames == null || pageFilenames.isEmpty()) {
            return citations;
        }

        Map<String, String> documentNames = new HashMap<>();
        for (String pageFilename : pageFilenames) {
            if (pageFilename != null && !pageFilename.isBlank()) {
                documentNames.put(pageFilename + ".txt", pageFilename);
            }
        }

        List<Object> enriched = new java.util.ArrayList<>();
        for (Object citation : citations) {
            if (!(citation instanceof Map<?, ?> citationMap)) {
                enriched.add(citation);
                continue;
            }

            Object documentName = citationMap.get("document_name");
            String pageFilename = documentName instanceof String
                    ? documentNames.get(documentName)
                    : null;

            if (pageFilename == null) {
                enriched.add(citation);
                continue;
            }

            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : citationMap.entrySet()) {
                if (entry.getKey() instanceof String key) {
                    copy.put(key, entry.getValue());
                }
            }
            copy.put("pageFilename", pageFilename);
            enriched.add(copy);
        }

        return enriched;
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

            JsonNode data = root.path("data");

            JsonNode dataCitations = data.path("citations");
            if (dataCitations.isArray()) {
                return objectMapper.convertValue(dataCitations, List.class);
            }

            JsonNode reference = data.path("reference");

            if (reference.isArray()) {
                return objectMapper.convertValue(reference, List.class);
            }

            JsonNode chunks = reference.path("chunks");
            if (chunks.isArray()) {
                return objectMapper.convertValue(chunks, List.class);
            }

            return Collections.emptyList();

        } catch (Exception e) {
            log.warn(
                    "Errore parsing citazioni RAGFlow: {}",
                    e.getMessage()
            );
            return Collections.emptyList();
        }
    }
    public String uploadOcrText(
            String datasetId,
            String filename,
            String ocrText
    ) {
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException(
                    "Il nome del file non può essere vuoto."
            );
        }

        if (ocrText == null || ocrText.isBlank()) {
            throw new IllegalArgumentException(
                    "Il testo OCR non può essere vuoto."
            );
        }

        String txtFilename = filename + ".txt";

        log.info(
                "Uploading OCR text '{}' to dataset '{}'",
                txtFilename,
                datasetId
        );

        byte[] content = ocrText.getBytes(StandardCharsets.UTF_8);

        ByteArrayResource resource = new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return txtFilename;
            }
        };

        MultiValueMap<String, Object> body =
                new LinkedMultiValueMap<>();

        body.add("file", resource);

        String responseBody = restClient.post()
                .uri(
                        "/api/v1/datasets/{datasetId}/documents",
                        datasetId
                )
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(String.class);

        try {
            JsonNode root =
                    objectMapper.readTree(responseBody);

            if (root.path("code").asInt(-1) != 0) {
                throw new RuntimeException(
                        "Errore upload documento RAGFlow: "
                                + root.path("message").asText()
                );
            }

            JsonNode data = root.path("data");

            if (!data.isArray() || data.isEmpty()) {
                throw new RuntimeException(
                        "RAGFlow non ha restituito il documento creato."
                );
            }

            String documentId =
                    data.get(0).path("id").asText();

            if (documentId.isBlank()) {
                throw new RuntimeException(
                        "RAGFlow non ha restituito il document ID."
                );
            }

            log.info(
                    "Documento OCR caricato in RAGFlow: {}",
                    documentId
            );

            return documentId;

        } catch (Exception e) {
            throw new RuntimeException(
                    "Errore nella risposta di RAGFlow durante l'upload del documento: "
                            + e.getMessage(),
                    e
            );
        }
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
            // Parse the response body as JSON with Jackson library
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
    public String createChat(String documentName,String datasetId){
        if (datasetId == null || datasetId.isBlank()) {
            throw new IllegalArgumentException(
                    "Il dataset ID non può essere vuoto."
            );
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(
                "name",
                "chat-" + documentName
        );
        payload.put(
                "dataset_ids",
                List.of(datasetId)
        );
        String responseBody = restClient.post()
                .uri("/api/v1/chats")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(String.class);
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            if (root.path("code").asInt(-1) != 0) {
                throw new RuntimeException(
                        "Errore creazione chat RAGFlow: "
                                + root.path("message").asText()
                );
            }
            String chatId = root.path("data").path("id").asText();
            if (chatId.isBlank()) {
                throw new RuntimeException(
                        "RAGFlow non ha restituito il chat ID."
                );
            }
            log.info(
                    "Chat RAGFlow creata: {} per dataset {}",
                    chatId,
                    datasetId
            );
            return chatId;
        } catch (Exception e) {
            throw new RuntimeException(
                    "Errore nella risposta di RAGFlow durante la creazione della chat."
                   + e.getMessage(),
                    e
            );


        }

    }
    public void waitUntilParsed(
            String datasetId,
            String documentId
    ) throws InterruptedException {

        long startTime = System.currentTimeMillis();
        long timeout = 2 * 60 * 1000L;

        while (System.currentTimeMillis() - startTime < timeout) {

            Map<String, Object> status =
                    getDocumentStatus(datasetId, documentId);

            String run = (String) status.get("run");

            log.info(
                    "RAGFlow parsing: dataset={}, document={}, run={}, progress={}",
                    datasetId,
                    documentId,
                    run,
                    status.get("progress")
            );

            if ("DONE".equals(run)) {
                return;
            }

            if ("FAIL".equals(run) || "CANCEL".equals(run)) {
                throw new RuntimeException(
                        "Parsing RAGFlow fallito: " + run
                );
            }

            Thread.sleep(1000);
        }

        throw new RuntimeException(
                "Timeout durante il parsing RAGFlow."
        );
    }
}
