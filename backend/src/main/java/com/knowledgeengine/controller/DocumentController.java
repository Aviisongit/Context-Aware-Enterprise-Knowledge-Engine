package com.knowledgeengine.controller;

import com.knowledgeengine.model.IngestionJob;
import com.knowledgeengine.repository.IngestionJobRepository;
import com.knowledgeengine.service.IngestionService;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class DocumentController {

    private final IngestionService ingestionService;
    private final IngestionJobRepository ingestionJobRepository;

    public DocumentController(IngestionService ingestionService, IngestionJobRepository ingestionJobRepository) {
        this.ingestionService = ingestionService;
        this.ingestionJobRepository = ingestionJobRepository;
    }

    @PostMapping("/documents")
    public ResponseEntity<Map<String, Object>> uploadDocument(@RequestParam("file") MultipartFile file) {
        try {
            Map<String, Object> response = ingestionService.enqueueDocument(file);
            return ResponseEntity.accepted().body(response);
        } catch (IllegalArgumentException | IOException e) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error", "Document ingestion failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<Map<String, Object>> getJobStatus(@PathVariable Long jobId) {
        Optional<IngestionJob> jobOptional = ingestionJobRepository.findById(jobId);
        if (jobOptional.isEmpty()) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error", "Job not found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }

        IngestionJob job = jobOptional.get();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jobId", job.getId());
        response.put("documentId", job.getDocument() != null ? job.getDocument().getId() : null);
        response.put("status", job.getStatus());
        response.put("errorMessage", job.getErrorMessage());
        response.put("startedAt", job.getStartedAt());
        response.put("finishedAt", job.getFinishedAt());
        response.put("filename", job.getDocument() != null ? job.getDocument().getFilename() : null);
        return ResponseEntity.ok(response);
    }
}
