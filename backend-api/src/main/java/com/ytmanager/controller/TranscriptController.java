package com.ytmanager.controller;

import java.util.List;
import java.util.Map;
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
import com.ytmanager.dto.TranscriptSnippet;
import com.ytmanager.dto.TranscriptSegment;
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
        if (url == null || url.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        String videoId = extractVideoId(url);
        if (videoId == null) {
            logger.warn("Could not extract video ID from: {}", url);
            return ResponseEntity.badRequest().build();
        }

        try {
            String jsonResponse = transcriptService.executeScraper(url);
            List<Map<String, Object>> rawSnippets = transcriptService.parseTranscriptJson(jsonResponse);
            String rawText = transcriptService.getRawText(rawSnippets);
            List<TranscriptSnippet> snippets = transcriptService.createSnippets(rawSnippets);

            String segmentationType = request.getSegmentationType();
            List<TranscriptSegment> segments = null;
            if ("TIMESTAMP".equals(segmentationType)) {
                int duration = request.getSegmentDuration() != null ? request.getSegmentDuration() : 60;
                segments = transcriptService.createSentenceAwareSegments(rawSnippets, duration);
            }

            TranscriptResponse response = new TranscriptResponse(videoId, rawText, snippets, segments);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private String extractVideoId(String input) {
        if (VIDEO_ID_PATTERN.matcher(input).matches()) return input;
        String[] patterns = {"v=([a-zA-Z0-9_-]{11})", "youtu\\.be/([a-zA-Z0-9_-]{11})", "embed/([a-zA-Z0-9_-]{11})"};
        for (String pattern : patterns) {
            java.util.regex.Matcher m = Pattern.compile(pattern).matcher(input);
            if (m.find()) return m.group(1);
        }
        return null;
    }
}