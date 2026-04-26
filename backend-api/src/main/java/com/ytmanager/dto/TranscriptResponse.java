package com.ytmanager.dto;

import java.util.List;

public class TranscriptResponse {
    private String videoId;
    private String rawText;
    private List<TranscriptSnippet> snippets;
    private List<TranscriptSegment> segments;

    public TranscriptResponse() {}

    public TranscriptResponse(String videoId, String rawText, List<TranscriptSnippet> snippets) {
        this.videoId = videoId;
        this.rawText = rawText;
        this.snippets = snippets;
    }

    public TranscriptResponse(String videoId, String rawText, List<TranscriptSnippet> snippets, List<TranscriptSegment> segments) {
        this.videoId = videoId;
        this.rawText = rawText;
        this.snippets = snippets;
        this.segments = segments;
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

    public List<TranscriptSnippet> getSnippets() {
        return snippets;
    }

    public void setSnippets(List<TranscriptSnippet> snippets) {
        this.snippets = snippets;
    }

    public List<TranscriptSegment> getSegments() {
        return segments;
    }

    public void setSegments(List<TranscriptSegment> segments) {
        this.segments = segments;
    }
}