package com.ytmanager.dto;

public class TranscriptSegment {
    private String startTime;
    private Double start;
    private Double end;
    private String text;

    public TranscriptSegment() {}

    public TranscriptSegment(String startTime, Double start, Double end, String text) {
        this.startTime = startTime;
        this.start = start;
        this.end = end;
        this.text = text;
    }

    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }

    public Double getStart() { return start; }
    public void setStart(Double start) { this.start = start; }

    public Double getEnd() { return end; }
    public void setEnd(Double end) { this.end = end; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
}