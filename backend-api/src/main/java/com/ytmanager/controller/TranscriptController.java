package com.ytmanager.controller;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ytmanager.dto.TranscriptRequest;
import com.ytmanager.dto.TranscriptResponse;
import com.ytmanager.dto.TranscriptSegment;
import com.ytmanager.dto.TranscriptSnippet;
import com.ytmanager.service.PythonExecutionService;
import com.ytmanager.service.TranscriptProcessor;
import com.ytmanager.util.YoutubeUtils;

@RestController
@RequestMapping("/api/v1")
public class TranscriptController {

    private static final Logger logger = LoggerFactory.getLogger(TranscriptController.class);
    private final PythonExecutionService pythonExecutionService;
    private final TranscriptProcessor transcriptProcessor;

    public TranscriptController(PythonExecutionService pythonExecutionService, TranscriptProcessor transcriptProcessor) {
        this.pythonExecutionService = pythonExecutionService;
        this.transcriptProcessor = transcriptProcessor;
    }

    @PostMapping("/transcript")
    public ResponseEntity<TranscriptResponse> getTranscript(@RequestBody TranscriptRequest request) {
        String url = request.getUrl();
        logger.info("Received request for URL: {}", url);
        
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
            String jsonResponse = pythonExecutionService.executeScraper(url);
            logger.info("Got transcript JSON, length: {}", jsonResponse.length());

            List<Map<String, Object>> rawSnippets = transcriptProcessor.parseTranscriptJson(jsonResponse);
            String rawText = transcriptProcessor.getRawText(rawSnippets);
            List<TranscriptSnippet> snippets = transcriptProcessor.createSnippets(rawSnippets);
            logger.info("Created {} snippets", snippets.size());

            String segmentationType = request.getSegmentationType();
            List<TranscriptSegment> segments = null;

            if ("TIMESTAMP".equals(segmentationType)) {
                int duration = request.getSegmentDuration() != null ? request.getSegmentDuration() : 60;
                segments = transcriptProcessor.createSentenceAwareSegments(rawSnippets, duration);
                logger.info("Created {} sentence-aware segments", segments.size());
            }
            
            TranscriptResponse response = new TranscriptResponse(videoId, rawText, snippets, segments);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error executing scraper: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}