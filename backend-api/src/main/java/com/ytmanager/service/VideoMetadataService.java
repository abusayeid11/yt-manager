package com.ytmanager.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ytmanager.dto.VideoMetadata;

@Service
public class VideoMetadataService {

    private static final Logger logger = LoggerFactory.getLogger(VideoMetadataService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(10))
            .build();

    @Value("${video.metadata.enabled:true}")
    private boolean metadataEnabled;

    private static final String NOEMBED_URL = "https://noembed.com/embed?url=%s&format=json";

    public VideoMetadata fetchMetadata(String videoId) {
        if (!metadataEnabled) {
            logger.debug("Video metadata fetching is disabled");
            return null;
        }

        if (videoId == null || videoId.isBlank()) {
            logger.warn("Invalid video ID: {}", videoId);
            return null;
        }

        try {
            String videoUrl = "https://www.youtube.com/watch?v=" + videoId;
            String requestUrl = String.format(NOEMBED_URL, videoUrl);

            logger.info("Fetching metadata from noembed: {}", requestUrl);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(requestUrl))
                    .timeout(java.time.Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode json = objectMapper.readTree(response.body());

                VideoMetadata metadata = new VideoMetadata();
                metadata.setVideoId(videoId);
                metadata.setTitle(json.has("title") ? json.get("title").asText() : null);
                metadata.setChannelName(json.has("author_name") ? json.get("author_name").asText() : null);
                metadata.setChannelUrl(json.has("author_url") ? json.get("author_url").asText() : null);
                metadata.setThumbnailUrl(json.has("thumbnail_url") ? json.get("thumbnail_url").asText() : null);
                metadata.setWidth(json.has("width") ? json.get("width").asInt() : null);
                metadata.setHeight(json.has("height") ? json.get("height").asInt() : null);

                logger.info("Successfully fetched metadata for video: {}", videoId);
                return metadata;
            } else {
                logger.warn("noembed returned status code: {}", response.statusCode());
                return null;
            }

        } catch (Exception e) {
            logger.error("Failed to fetch video metadata: {}", e.getMessage());
            return null;
        }
    }

    public boolean isMetadataEnabled() {
        return metadataEnabled;
    }
}