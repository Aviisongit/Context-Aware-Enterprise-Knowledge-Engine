package com.knowledgeengine.service;

import com.knowledgeengine.model.Document;
import com.knowledgeengine.model.DocumentSource;
import com.knowledgeengine.model.IngestionJob;
import com.knowledgeengine.model.IngestionStatus;
import com.knowledgeengine.model.SyncStatus;
import com.knowledgeengine.repository.DocumentChunkRepository;
import com.knowledgeengine.repository.DocumentRepository;
import com.knowledgeengine.repository.IngestionJobRepository;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class IngestionService {

    private final DocumentProcessingService documentProcessingService;
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final IngestionJobRepository ingestionJobRepository;
    private final EmbeddingService embeddingService;
    private final SyncService syncService;
    private final SimpMessagingTemplate messagingTemplate;

    public IngestionService(
            DocumentProcessingService documentProcessingService,
            DocumentRepository documentRepository,
            DocumentChunkRepository documentChunkRepository,
            IngestionJobRepository ingestionJobRepository,
            EmbeddingService embeddingService,
            SyncService syncService,
            SimpMessagingTemplate messagingTemplate) {
        this.documentProcessingService = documentProcessingService;
        this.documentRepository = documentRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.ingestionJobRepository = ingestionJobRepository;
        this.embeddingService = embeddingService;
        this.syncService = syncService;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional
    public Map<String, Object> enqueueDocument(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Upload file is required.");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("Uploaded file must include a valid filename.");
        }

        byte[] fileBytes = file.getBytes();
        Document document = new Document();
        document.setTitle(filename);
        document.setFilename(filename);
        document.setContent("");
        document.setSource(determineSource(filename));
        document.setContentHash("pending-" + UUID.randomUUID());
        document.setSyncStatus(SyncStatus.PENDING);
        Document savedDocument = documentRepository.save(document);

        IngestionJob job = new IngestionJob();
        job.setDocument(savedDocument);
        job.setStatus(IngestionStatus.PENDING);
        job.setStartedAt(Instant.now());
        IngestionJob savedJob = ingestionJobRepository.save(job);
        broadcastTelemetry(savedJob, savedDocument, "PENDING", "Queued for ingestion");

        processIngestionAsync(savedJob.getId(), savedDocument.getId(), fileBytes, filename);

        return Map.of(
                "jobId", savedJob.getId(),
                "documentId", savedDocument.getId(),
                "status", savedJob.getStatus().name());
    }

    @Async("ingestionExecutor")
    public void processIngestionAsync(Long jobId, Long documentId, byte[] fileBytes, String filename) {
        processIngestion(jobId, documentId, fileBytes, filename);
    }

    @Transactional
    public void processIngestion(Long jobId, Long documentId, byte[] fileBytes, String filename) {
        IngestionJob job = ingestionJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Ingestion job not found: " + jobId));
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));

        try {
            job.setStatus(IngestionStatus.PARSING);
            ingestionJobRepository.save(job);
            broadcastTelemetry(job, document, "PARSING", "Parsing document content");

            String contentHash = syncService.calculateContentHash(fileBytes);
            Optional<Document> existingByHash = syncService.findDocumentByContentHash(contentHash);
            if (existingByHash.isPresent()) {
                Document existing = existingByHash.get();
                existing.setSyncStatus(SyncStatus.SYNCED);
                documentRepository.save(existing);

                document.setContent(existing.getContent());
                document.setContentHash(existing.getContentHash());
                document.setSource(existing.getSource());
                document.setSyncStatus(SyncStatus.SYNCED);
                documentRepository.save(document);

                job.setStatus(IngestionStatus.COMPLETE);
                job.setFinishedAt(Instant.now());
                job.setErrorMessage(null);
                ingestionJobRepository.save(job);
                broadcastTelemetry(job, document, "COMPLETE", "Document already synced; skipped re-indexing");
                return;
            }

            syncService.invalidatePreviousVersion(filename, contentHash, document.getId());

            DocumentProcessingService.ParsedDocument parsedDocument = documentProcessingService.parseDocument(fileBytes, filename);
            document.setTitle(filename);
            document.setContent(parsedDocument.content());
            document.setFilename(filename);
            document.setSource(parsedDocument.source());
            document.setContentHash(contentHash);
            document.setSyncStatus(SyncStatus.PENDING);
            documentRepository.save(document);

            job.setStatus(IngestionStatus.CHUNKING);
            ingestionJobRepository.save(job);
            broadcastTelemetry(job, document, "CHUNKING", "Chunking content into retrieval units");

            List<DocumentProcessingService.Chunk> chunks = parsedDocument.chunks();
            if (chunks.isEmpty()) {
                chunks = List.of(new DocumentProcessingService.Chunk(parsedDocument.content(), "document"));
            }

            for (int i = 0; i < chunks.size(); i++) {
                DocumentProcessingService.Chunk chunk = chunks.get(i);
                String embeddingVector = embeddingService.generateEmbedding(chunk.text());
                documentChunkRepository.insertChunk(
                        document.getId(),
                        i,
                        chunk.text(),
                        chunk.sectionName(),
                        "section=" + chunk.sectionName(),
                        embeddingVector);
            }

            job.setStatus(IngestionStatus.EMBEDDING);
            ingestionJobRepository.save(job);
            broadcastTelemetry(job, document, "EMBEDDING", "Generating vector embeddings");

            job.setStatus(IngestionStatus.INDEXING);
            ingestionJobRepository.save(job);
            broadcastTelemetry(job, document, "INDEXING", "Persisting chunks and vectors");

            document.setSyncStatus(SyncStatus.SYNCED);
            documentRepository.save(document);

            job.setStatus(IngestionStatus.COMPLETE);
            job.setFinishedAt(Instant.now());
            job.setErrorMessage(null);
            ingestionJobRepository.save(job);
            broadcastTelemetry(job, document, "COMPLETE", "Ingestion completed successfully");
        } catch (Exception e) {
            job.setStatus(IngestionStatus.FAILED);
            job.setErrorMessage(e.getMessage());
            job.setFinishedAt(Instant.now());
            document.setSyncStatus(SyncStatus.FAILED);
            documentRepository.save(document);
            ingestionJobRepository.save(job);
            broadcastTelemetry(job, document, "FAILED", e.getMessage());
        }
    }

    @Transactional
    public Document ingestDocument(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Upload file is required.");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("Uploaded file must include a valid filename.");
        }

        byte[] fileBytes = file.getBytes();
        String contentHash = syncService.calculateContentHash(fileBytes);
        Optional<Document> existingByHash = syncService.findDocumentByContentHash(contentHash);
        if (existingByHash.isPresent()) {
            Document existing = existingByHash.get();
            existing.setSyncStatus(SyncStatus.SYNCED);
            return documentRepository.save(existing);
        }

        syncService.invalidatePreviousVersion(filename, contentHash, null);

        DocumentProcessingService.ParsedDocument parsedDocument = documentProcessingService.parseDocument(file);

        Document document = new Document();
        document.setTitle(filename);
        document.setContent(parsedDocument.content());
        document.setFilename(filename);
        document.setSource(parsedDocument.source());
        document.setContentHash(contentHash);
        document.setSyncStatus(SyncStatus.PENDING);

        Document savedDocument = documentRepository.save(document);

        IngestionJob job = new IngestionJob();
        job.setDocument(savedDocument);
        job.setStatus(IngestionStatus.PENDING);
        job.setStartedAt(Instant.now());
        ingestionJobRepository.save(job);

        job.setStatus(IngestionStatus.PARSING);
        ingestionJobRepository.save(job);

        List<DocumentProcessingService.Chunk> chunks = parsedDocument.chunks();
        if (chunks.isEmpty()) {
            chunks = List.of(new DocumentProcessingService.Chunk(parsedDocument.content(), "document"));
        }

        job.setStatus(IngestionStatus.CHUNKING);
        ingestionJobRepository.save(job);

        for (int i = 0; i < chunks.size(); i++) {
            DocumentProcessingService.Chunk chunk = chunks.get(i);
            String embeddingVector = embeddingService.generateEmbedding(chunk.text());
            documentChunkRepository.insertChunk(
                    savedDocument.getId(),
                    i,
                    chunk.text(),
                    chunk.sectionName(),
                    "section=" + chunk.sectionName(),
                    embeddingVector);
        }

        job.setStatus(IngestionStatus.EMBEDDING);
        ingestionJobRepository.save(job);

        job.setStatus(IngestionStatus.INDEXING);
        ingestionJobRepository.save(job);

        savedDocument.setSyncStatus(SyncStatus.SYNCED);
        savedDocument = documentRepository.save(savedDocument);

        job.setStatus(IngestionStatus.COMPLETE);
        job.setFinishedAt(Instant.now());
        ingestionJobRepository.save(job);
        return savedDocument;
    }

    private void broadcastTelemetry(IngestionJob job, Document document, String status, String stageDescription) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("jobId", job.getId());
        payload.put("documentId", document != null ? document.getId() : null);
        payload.put("status", status);
        payload.put("stage", stageDescription);
        payload.put("timestamp", Instant.now().toString());

        messagingTemplate.convertAndSend("/topic/jobs/" + job.getId(), payload);
        messagingTemplate.convertAndSend("/topic/ingestion", payload);
    }

    private DocumentSource determineSource(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".pdf")) {
            return DocumentSource.PDF;
        }
        if (lower.endsWith(".md")) {
            return DocumentSource.MARKDOWN;
        }
        return DocumentSource.TEXT;
    }
}
