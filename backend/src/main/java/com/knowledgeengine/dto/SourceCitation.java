package com.knowledgeengine.dto;

public class SourceCitation {

    private String documentFilename;
    private Integer chunkIndex;
    private String sectionName;
    private String snippet;

    public SourceCitation() {
    }

    public SourceCitation(String documentFilename, Integer chunkIndex, String sectionName, String snippet) {
        this.documentFilename = documentFilename;
        this.chunkIndex = chunkIndex;
        this.sectionName = sectionName;
        this.snippet = snippet;
    }

    public String getDocumentFilename() {
        return documentFilename;
    }

    public void setDocumentFilename(String documentFilename) {
        this.documentFilename = documentFilename;
    }

    public Integer getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(Integer chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public String getSectionName() {
        return sectionName;
    }

    public void setSectionName(String sectionName) {
        this.sectionName = sectionName;
    }

    public String getSnippet() {
        return snippet;
    }

    public void setSnippet(String snippet) {
        this.snippet = snippet;
    }
}
