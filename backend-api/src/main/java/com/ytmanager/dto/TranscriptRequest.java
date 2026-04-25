package com.ytmanager.dto;

public class TranscriptRequest {
    private String url;

    public TranscriptRequest() {}

    public TranscriptRequest(String url) {
        this.url = url;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}