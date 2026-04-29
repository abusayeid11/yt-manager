package com.ytmanager.dto;

public class VideoMetadata {
    private String videoId;
    private String title;
    private String channelName;
    private String channelUrl;
    private String thumbnailUrl;
    private Integer width;
    private Integer height;

    public VideoMetadata() {}

    public VideoMetadata(String videoId, String title, String channelName, String channelUrl, String thumbnailUrl) {
        this.videoId = videoId;
        this.title = title;
        this.channelName = channelName;
        this.channelUrl = channelUrl;
        this.thumbnailUrl = thumbnailUrl;
    }

    public String getVideoId() {
        return videoId;
    }

    public void setVideoId(String videoId) {
        this.videoId = videoId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getChannelName() {
        return channelName;
    }

    public void setChannelName(String channelName) {
        this.channelName = channelName;
    }

    public String getChannelUrl() {
        return channelUrl;
    }

    public void setChannelUrl(String channelUrl) {
        this.channelUrl = channelUrl;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public void setThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }

    public Integer getWidth() {
        return width;
    }

    public void setWidth(Integer width) {
        this.width = width;
    }

    public Integer getHeight() {
        return height;
    }

    public void setHeight(Integer height) {
        this.height = height;
    }
}