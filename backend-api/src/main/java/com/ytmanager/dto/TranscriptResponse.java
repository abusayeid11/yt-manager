package com.ytmanager.dto;

public class TranscriptResponse {
    private String videoId;
    private String rawText;

    public TranscriptResponse() {}

    public TranscriptResponse(String videoId, String rawText) {
        this.videoId = videoId;
        this.rawText = rawText;
    }

    public String getVideoId() {
        return videoId;
    }

    public void setVideoId(String videoId) {
        this.videoId = videoId;
    }

    public String getRawText() {
        return rawText;
    }

    public void setRawText(String rawText) {
        this.rawText = rawText;
    }
}