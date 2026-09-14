package com.knowledgeengine.model;

public enum IngestionStatus {
    PENDING,
    PARSING,
    CHUNKING,
    EMBEDDING,
    INDEXING,
    COMPLETE,
    FAILED
}
