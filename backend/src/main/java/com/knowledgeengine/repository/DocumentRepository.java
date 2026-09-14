package com.knowledgeengine.repository;

import com.knowledgeengine.model.Document;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    Optional<Document> findByContentHash(String contentHash);

    Optional<Document> findByFilename(String filename);
}
