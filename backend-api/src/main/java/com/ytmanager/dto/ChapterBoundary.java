package com.ytmanager.dto;

public class ChapterBoundary {
    private int startIndex;
    private String title;

    public ChapterBoundary() {}

    public ChapterBoundary(int startIndex, String title) {
        this.startIndex = startIndex;
        this.title = title;
    }

    public int getStartIndex() {
        return startIndex;
    }

    public void setStartIndex(int startIndex) {
        this.startIndex = startIndex;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}