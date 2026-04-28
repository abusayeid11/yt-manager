package com.ytmanager.dto;

import java.util.List;

public class SemanticResponse {
    private String videoId;
    private List<SemanticSegment> segments;

    public SemanticResponse() {}

    public SemanticResponse(String videoId, List<SemanticSegment> segments) {
        this.videoId = videoId;
        this.segments = segments;
    }

    public String getVideoId() {
        return videoId;
    }

    public void setVideoId(String videoId) {
        this.videoId = videoId;
    }

    public List<SemanticSegment> getSegments() {
        return segments;
    }

    public void setSegments(List<SemanticSegment> segments) {
        this.segments = segments;
    }
}