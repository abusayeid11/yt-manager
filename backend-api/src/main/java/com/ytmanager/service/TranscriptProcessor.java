package com.ytmanager.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.ytmanager.dto.TranscriptSegment;
import com.ytmanager.dto.TranscriptSnippet;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class TranscriptProcessor {

    private static final Logger logger = LoggerFactory.getLogger(TranscriptProcessor.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final Pattern SENTENCE_END = Pattern.compile("[.!?]+\\s*$");

    public List<Map<String, Object>> parseTranscriptJson(String jsonResponse) throws Exception {
        List<Map<String, Object>> snippets = new ArrayList<Map<String, Object>>();

        JsonNode root = objectMapper.readTree(jsonResponse);
        JsonNode snippetsNode = root.get("snippets");

        if (snippetsNode != null && snippetsNode.isArray()) {
            for (JsonNode node : snippetsNode) {
                JsonNode textNode = node.get("text");
                JsonNode startNode = node.get("start");
                JsonNode durationNode = node.get("duration");

                if (textNode != null && startNode != null) {
                    Map<String, Object> snippet = new HashMap<String, Object>();
                    snippet.put("text", textNode.asText());
                    snippet.put("start", startNode.asDouble());
                    snippet.put("duration", durationNode != null ? durationNode.asDouble() : 0.0);
                    snippets.add(snippet);
                }
            }
        }

        return snippets;
    }

    public List<TranscriptSnippet> createSnippets(List<Map<String, Object>> rawSnippets) {
        List<TranscriptSnippet> snippets = new ArrayList<TranscriptSnippet>();

        for (Map<String, Object> raw : rawSnippets) {
            Double start = (Double) raw.get("start");
            String text = (String) raw.get("text");
            snippets.add(new TranscriptSnippet(formatTime(start), start, text));
        }

        return snippets;
    }

    public String getRawText(List<Map<String, Object>> snippets) {
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> snippet : snippets) {
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append(snippet.get("text"));
        }
        return sb.toString();
    }

    public List<TranscriptSegment> createSentenceAwareSegments(List<Map<String, Object>> snippets, int segmentDurationSeconds) {
        if (snippets == null || snippets.isEmpty()) {
            return new ArrayList<TranscriptSegment>();
        }

        List<TranscriptSegment> segments = new ArrayList<TranscriptSegment>();
        double currentWindowStart = 0.0;
        StringBuilder currentText = new StringBuilder();
        double windowEnd = segmentDurationSeconds;
        double currentTextEnd = segmentDurationSeconds;
        boolean lastEndedWithSentence = false;

        for (Map<String, Object> snippet : snippets) {
            double start = (Double) snippet.get("start");
            double duration = (Double) snippet.get("duration");
            String text = (String) snippet.get("text");

            if (start >= windowEnd && currentText.length() > 0) {
                double extensionLimit = windowEnd + segmentDurationSeconds * 0.5;
                if (lastEndedWithSentence && start < extensionLimit) {
                } else {
                    TranscriptSegment segment = new TranscriptSegment(
                        formatTime(currentWindowStart),
                        currentWindowStart,
                        currentTextEnd,
                        currentText.toString().trim()
                    );
                    segments.add(segment);

                    currentWindowStart = windowEnd;
                    windowEnd = currentWindowStart + segmentDurationSeconds;
                    currentTextEnd = windowEnd;
                    currentText = new StringBuilder();
                    lastEndedWithSentence = false;
                }
            }

            if (currentText.length() > 0) {
                currentText.append(" ");
            }
            currentText.append(text);
            currentTextEnd = start + duration;
            lastEndedWithSentence = isSentenceEnd(text);

            double forcedSplitThreshold = currentWindowStart + segmentDurationSeconds * 0.8;
            if (start >= forcedSplitThreshold && currentText.length() > 0) {
                TranscriptSegment segment = new TranscriptSegment(
                    formatTime(currentWindowStart),
                    currentWindowStart,
                    currentTextEnd,
                    currentText.toString().trim()
                );
                segments.add(segment);

                currentWindowStart = windowEnd;
                windowEnd = currentWindowStart + segmentDurationSeconds;
                currentTextEnd = windowEnd;
                currentText = new StringBuilder();
                lastEndedWithSentence = false;
            }

            while (start >= windowEnd && currentText.length() > 0) {
                TranscriptSegment segment = new TranscriptSegment(
                    formatTime(currentWindowStart),
                    currentWindowStart,
                    currentTextEnd,
                    currentText.toString().trim()
                );
                segments.add(segment);

                currentWindowStart = windowEnd;
                windowEnd = currentWindowStart + segmentDurationSeconds;
                currentTextEnd = windowEnd;
                currentText = new StringBuilder();
                lastEndedWithSentence = false;
            }
        }

        if (currentText.length() > 0) {
            TranscriptSegment segment = new TranscriptSegment(
                formatTime(currentWindowStart),
                currentWindowStart,
                currentTextEnd,
                currentText.toString().trim()
            );
            segments.add(segment);
        }

        return segments;
    }

    private boolean isSentenceEnd(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        String trimmed = text.trim();
        return SENTENCE_END.matcher(trimmed).find() ||
               trimmed.endsWith(".") || trimmed.endsWith("!") || trimmed.endsWith("?");
    }

    private String formatTime(double seconds) {
        int mins = (int) (seconds / 60);
        int secs = (int) (seconds % 60);
        return String.format("%d:%02d", mins, secs);
    }
}