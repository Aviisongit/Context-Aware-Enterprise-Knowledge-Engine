package com.knowledgeengine.repository;

import com.knowledgeengine.model.DocumentChunk;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {

    @Modifying
    @Transactional
    @Query(value = "INSERT INTO document_chunks (document_id, chunk_index, content, section_name, metadata, embedding) VALUES (:documentId, :chunkIndex, :content, :sectionName, :metadata, CAST(:embedding AS vector(768)))", nativeQuery = true)
    void insertChunk(
            @Param("documentId") Long documentId,
            @Param("chunkIndex") int chunkIndex,
            @Param("content") String content,
            @Param("sectionName") String sectionName,
            @Param("metadata") String metadata,
            @Param("embedding") String embedding);

    @Modifying
    @Transactional
    @Query("DELETE FROM DocumentChunk dc WHERE dc.document.id = :documentId")
    void deleteByDocumentId(@Param("documentId") Long documentId);

    @Query(value = "SELECT * FROM document_chunks ORDER BY embedding <=> cast(:queryVector as vector) LIMIT :topK", nativeQuery = true)
    List<DocumentChunk> findNearestByEmbedding(@Param("queryVector") String queryVector, @Param("topK") int topK);
}
