package com.knowledgeengine.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.knowledgeengine.dto.QueryResponse;
import com.knowledgeengine.dto.SourceCitation;
import com.knowledgeengine.model.DocumentChunk;
import com.knowledgeengine.repository.DocumentChunkRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
public class RagService {

    private final DocumentChunkRepository documentChunkRepository;
    private final EmbeddingService embeddingService;
    private final RestTemplate restTemplate;

    @Value("${gemini.api.key:}")
    private String geminiApiKey;

    @Value("${gemini.api.url:https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent}")
    private String geminiApiUrl;

    public RagService(DocumentChunkRepository documentChunkRepository, EmbeddingService embeddingService, RestTemplate restTemplate) {
        this.documentChunkRepository = documentChunkRepository;
        this.embeddingService = embeddingService;
        this.restTemplate = restTemplate;
    }

    public QueryResponse answerQuestion(String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("Question cannot be blank.");
        }

        if (geminiApiKey == null || geminiApiKey.isBlank()) {
            throw new IllegalStateException("GEMINI_API_KEY is not configured. Set it in the .env file and map it in application.yml.");
        }

        String embeddedQuery = embeddingService.generateEmbedding(question);
        List<DocumentChunk> relevantChunks = documentChunkRepository.findNearestByEmbedding(embeddedQuery, 5);

        if (relevantChunks.isEmpty()) {
            return new QueryResponse("I could not find any relevant document chunks for that question.", List.of());
        }

        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("Use only the provided source context to answer the question. If the answer is not present in the context, say so clearly.\n\n");
        promptBuilder.append("Question: ").append(question).append("\n\n");
        promptBuilder.append("Context:\n");
        for (int i = 0; i < relevantChunks.size(); i++) {
            DocumentChunk chunk = relevantChunks.get(i);
            promptBuilder.append("[Chunk ").append(i + 1).append("]\n");
            promptBuilder.append(chunk.getContent()).append("\n\n");
        }

        String answer = callGemini(promptBuilder.toString());

        List<SourceCitation> citations = new ArrayList<>();
        for (DocumentChunk chunk : relevantChunks) {
            citations.add(new SourceCitation(
                    chunk.getDocument() != null ? chunk.getDocument().getFilename() : "unknown",
                    chunk.getChunkIndex(),
                    chunk.getSectionName() != null ? chunk.getSectionName() : "document",
                    chunk.getContent() != null ? chunk.getContent().trim() : ""
            ));
        }

        return new QueryResponse(answer, citations);
    }

    private String callGemini(String prompt) {
        String url = geminiApiUrl + "?key=" + geminiApiKey;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        List<Map<String, Object>> contents = new ArrayList<>();
        Map<String, Object> content = new HashMap<>();
        List<Map<String, String>> parts = new ArrayList<>();
        Map<String, String> part = new HashMap<>();
        part.put("text", prompt);
        parts.add(part);
        content.put("parts", parts);
        contents.add(content);
        body.put("contents", contents);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<GeminiResponse> response = restTemplate.postForEntity(url, request, GeminiResponse.class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null && response.getBody().candidates != null
                    && !response.getBody().candidates.isEmpty() && response.getBody().candidates.get(0).content != null
                    && response.getBody().candidates.get(0).content.parts != null
                    && !response.getBody().candidates.get(0).content.parts.isEmpty()) {
                return response.getBody().candidates.get(0).content.parts.get(0).text;
            }
            return "I could not generate a grounded answer from the provided context.";
        } catch (RestClientException e) {
            throw new IllegalStateException("Gemini API call failed: " + e.getMessage(), e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class GeminiResponse {
        public List<Candidate> candidates;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Candidate {
        public Content content;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Content {
        public List<Part> parts;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Part {
        public String text;
    }
}
