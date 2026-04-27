package com.ytmanager.dto;

public class SemanticSegment extends TranscriptSegment {
    private String title;

    public SemanticSegment() {}

    public SemanticSegment(String startTime, Double start, Double end, String text, String title) {
        super(startTime, start, end, text);
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}