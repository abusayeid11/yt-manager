package com.ytmanager.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ytmanager.dto.SemanticResponse;
import com.ytmanager.dto.SemanticSegment;
import com.ytmanager.dto.TranscriptRequest;
import com.ytmanager.service.SemanticService;
import com.ytmanager.util.YoutubeUtils;

@RestController
@RequestMapping("/api/v1")
public class SemanticController {

    private static final Logger logger = LoggerFactory.getLogger(SemanticController.class);
    private final SemanticService semanticService;

    public SemanticController(SemanticService semanticService) {
        this.semanticService = semanticService;
    }

    @PostMapping("/transcript/semantic")
    public ResponseEntity<SemanticResponse> getSemanticTranscript(@RequestBody TranscriptRequest request) {
        String url = request.getUrl();
        logger.info("Received semantic request for URL: {}", url);
        
        if (url == null || url.isBlank()) {
            logger.warn("URL is null or blank");
            return ResponseEntity.badRequest().build();
        }

        String videoId = YoutubeUtils.extractVideoId(url);
        if (videoId == null) {
            logger.warn("Could not extract video ID from: {}", url);
            return ResponseEntity.badRequest().build();
        }
        
        logger.info("Extracted video ID: {}", videoId);

        try {
            List<SemanticSegment> segments = semanticService.getSemanticSegments(url);
            logger.info("Created {} semantic segments", segments.size());

            if (segments.isEmpty()) {
                logger.warn("No chapters returned. Check GEMINI_API_KEY is set.");
            }

            SemanticResponse response = new SemanticResponse(videoId, segments);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error executing semantic scraper: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}