package com.knowledgeengine.repository;

import com.knowledgeengine.model.IngestionJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IngestionJobRepository extends JpaRepository<IngestionJob, Long> {
}
