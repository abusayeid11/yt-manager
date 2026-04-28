package com.ytmanager.dto;

public class TranscriptRequest {
    private String url;
    private String segmentationType;
    private Integer segmentDuration;

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

    public String getSegmentationType() {
        return segmentationType;
    }

    public void setSegmentationType(String segmentationType) {
        this.segmentationType = segmentationType;
    }

    public Integer getSegmentDuration() {
        return segmentDuration;
    }

    public void setSegmentDuration(Integer segmentDuration) {
        this.segmentDuration = segmentDuration;
    }
}