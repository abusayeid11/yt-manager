package com.ytmanager.controller;

import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ytmanager.dto.TranscriptRequest;
import com.ytmanager.dto.TranscriptResponse;
import com.ytmanager.service.TranscriptService;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "http://localhost:3000")
public class TranscriptController {

    private static final Logger logger = LoggerFactory.getLogger(TranscriptController.class);
    private final TranscriptService transcriptService;

    private static final Pattern VIDEO_ID_PATTERN = Pattern.compile("[a-zA-Z0-9_-]{11}");

    public TranscriptController(TranscriptService transcriptService) {
        this.transcriptService = transcriptService;
    }

    @PostMapping("/transcript")
    public ResponseEntity<TranscriptResponse> getTranscript(@RequestBody TranscriptRequest request) {
        String url = request.getUrl();
        logger.info("Received request for URL: {}", url);
        
        if (url == null || url.isBlank()) {
            logger.warn("URL is null or blank");
            return ResponseEntity.badRequest().build();
        }

        String videoId = extractVideoId(url);
        if (videoId == null) {
            logger.warn("Could not extract video ID from: {}", url);
            return ResponseEntity.badRequest().build();
        }
        
        logger.info("Extracted video ID: {}", videoId);

        try {
            String rawText = transcriptService.executeScraper(url);
            logger.info("Got transcript, length: {}", rawText.length());
            TranscriptResponse response = new TranscriptResponse(videoId, rawText);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error executing scraper: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private String extractVideoId(String input) {
        if (VIDEO_ID_PATTERN.matcher(input).matches()) {
            return input;
        }

        String[] patterns = {
            "v=([a-zA-Z0-9_-]{11})",
            "youtu\\.be/([a-zA-Z0-9_-]{11})",
            "embed/([a-zA-Z0-9_-]{11})"
        };

        for (String pattern : patterns) {
            java.util.regex.Matcher matcher = Pattern.compile(pattern).matcher(input);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return null;
    }
}