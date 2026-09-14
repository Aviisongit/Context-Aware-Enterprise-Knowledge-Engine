package com.knowledgeengine.service;

import com.knowledgeengine.model.Document;
import com.knowledgeengine.repository.DocumentChunkRepository;
import com.knowledgeengine.repository.DocumentRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SyncService {

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;

    public SyncService(DocumentRepository documentRepository, DocumentChunkRepository documentChunkRepository) {
        this.documentRepository = documentRepository;
        this.documentChunkRepository = documentChunkRepository;
    }

    public String calculateContentHash(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder hex = new StringBuilder();
            for (byte value : hash) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not supported", e);
        }
    }

    public Optional<Document> findDocumentByContentHash(String contentHash) {
        return documentRepository.findByContentHash(contentHash);
    }

    @Transactional
    public void invalidatePreviousVersion(String filename, String newContentHash, Long currentDocumentId) {
        Optional<Document> previousByFilename = documentRepository.findByFilename(filename);
        if (previousByFilename.isEmpty()) {
            return;
        }

        Document previous = previousByFilename.get();
        if (previous.getId() != null && previous.getId().equals(currentDocumentId)) {
            return;
        }
        if (newContentHash.equals(previous.getContentHash())) {
            return;
        }

        documentChunkRepository.deleteByDocumentId(previous.getId());
        documentRepository.delete(previous);
    }

    public String normalizeTextForHash(String text) {
        return text == null ? "" : text.replace("\r\n", "\n").trim();
    }

    public String calculateContentHash(String text) {
        return calculateContentHash(normalizeTextForHash(text).getBytes(StandardCharsets.UTF_8));
    }
}
