package com.knowledgeengine.controller;

import com.knowledgeengine.dto.QueryRequest;
import com.knowledgeengine.dto.QueryResponse;
import com.knowledgeengine.service.RagService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class QueryController {

    private final RagService ragService;

    public QueryController(RagService ragService) {
        this.ragService = ragService;
    }

    @PostMapping("/query")
    public ResponseEntity<QueryResponse> query(@RequestBody QueryRequest request) {
        try {
            QueryResponse response = ragService.answerQuestion(request.getQuestion());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(503).body(new QueryResponse("Gemini configuration is missing or invalid: " + e.getMessage(), java.util.List.of()));
        }
    }
}
