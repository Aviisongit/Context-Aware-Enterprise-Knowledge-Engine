package com.knowledgeengine.dto;

import java.util.List;

public class QueryResponse {

    private String answer;
    private List<SourceCitation> citations;

    public QueryResponse() {
    }

    public QueryResponse(String answer, List<SourceCitation> citations) {
        this.answer = answer;
        this.citations = citations;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public List<SourceCitation> getCitations() {
        return citations;
    }

    public void setCitations(List<SourceCitation> citations) {
        this.citations = citations;
    }
}
