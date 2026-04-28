package com.ytmanager.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.ytmanager.dto.ChapterBoundary;
import com.ytmanager.dto.SemanticSegment;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class SemanticService {

private static final Logger logger = LoggerFactory.getLogger(SemanticService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PythonExecutionService pythonExecutionService;

    public SemanticService(PythonExecutionService pythonExecutionService) {
        this.pythonExecutionService = pythonExecutionService;
    }

    public List<SemanticSegment> getSemanticSegments(String url) throws Exception {
        String jsonResponse = pythonExecutionService.executeScraperWithArgs(url, "--semantic");
        return parseSemanticResponse(jsonResponse);
    }

    public List<SemanticSegment> parseSemanticResponse(String jsonResponse) throws Exception {
        List<SemanticSegment> segments = new ArrayList<SemanticSegment>();

        JsonNode root = objectMapper.readTree(jsonResponse);
        JsonNode snippetsNode = root.get("snippets");
        JsonNode chaptersNode = root.get("chapters");

        if (snippetsNode == null || !snippetsNode.isArray()) {
            throw new IllegalArgumentException("No snippets found in response");
        }

        if (chaptersNode == null || !chaptersNode.isArray() || chaptersNode.isEmpty()) {
            logger.warn("No chapters found in response. GEMINI_API_KEY may not be set.");
            return segments;
        }

        List<Map<String, Object>> rawSnippets = new ArrayList<Map<String, Object>>();
        for (JsonNode node : snippetsNode) {
            Map<String, Object> snippet = new java.util.HashMap<>();
            snippet.put("text", node.get("text").asText());
            snippet.put("start", node.get("start").asDouble());
            snippet.put("duration", node.has("duration") ? node.get("duration").asDouble() : 0.0);
            rawSnippets.add(snippet);
        }

        List<ChapterBoundary> boundaries = new ArrayList<ChapterBoundary>();
        for (JsonNode chapter : chaptersNode) {
            ChapterBoundary boundary = new ChapterBoundary(
                chapter.get("start_index").asInt(),
                chapter.get("title").asText()
            );
            boundaries.add(boundary);
        }

        boundaries.sort((a, b) -> Integer.compare(a.getStartIndex(), b.getStartIndex()));

        for (int i = 0; i < boundaries.size(); i++) {
            ChapterBoundary current = boundaries.get(i);
            int startIdx = current.getStartIndex();
            int endIdx = (i + 1 < boundaries.size())
                ? boundaries.get(i + 1).getStartIndex()
                : rawSnippets.size();

            if (startIdx >= rawSnippets.size()) {
                continue;
            }

            double segStart = ((Number) rawSnippets.get(startIdx).get("start")).doubleValue();
            double segEnd = (endIdx < rawSnippets.size())
                ? ((Number) rawSnippets.get(endIdx - 1).get("start")).doubleValue() + ((Number) rawSnippets.get(endIdx - 1).get("duration")).doubleValue()
                : segStart + 60.0;

            StringBuilder textBuilder = new StringBuilder();
            for (int j = startIdx; j < endIdx && j < rawSnippets.size(); j++) {
                if (textBuilder.length() > 0) {
                    textBuilder.append(" ");
                }
                textBuilder.append(rawSnippets.get(j).get("text"));
            }

            SemanticSegment segment = new SemanticSegment(
                formatTime(segStart),
                segStart,
                segEnd,
                textBuilder.toString().trim(),
                current.getTitle()
            );
            segments.add(segment);
        }

        return segments;
    }

    private String formatTime(double seconds) {
        int mins = (int) (seconds / 60);
        int secs = (int) (seconds % 60);
        return String.format("%d:%02d", mins, secs);
    }
}