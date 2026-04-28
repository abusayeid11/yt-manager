package com.ytmanager.dto;

public class TranscriptSnippet {
    private String startTime;
    private Double start;
    private Double duration;
    private String text;

    public TranscriptSnippet() {}

    public TranscriptSnippet(String startTime, Double start, Double duration, String text) {
        this.startTime = startTime;
        this.start = start;
        this.duration = duration;
        this.text = text;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public Double getStart() {
        return start;
    }

    public void setStart(Double start) {
        this.start = start;
    }

    public Double getDuration() {
        return duration;
    }

    public void setDuration(Double duration) {
        this.duration = duration;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}