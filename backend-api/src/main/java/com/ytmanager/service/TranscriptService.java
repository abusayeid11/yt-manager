package com.ytmanager.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
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
public class TranscriptService {

    private static final Logger logger = LoggerFactory.getLogger(TranscriptService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final Pattern SENTENCE_END = Pattern.compile("[.!?]+\\s*$");

    public String executeScraper(String url) throws Exception {
        String projectRoot = System.getenv("PROJECT_ROOT");
        if (projectRoot == null || projectRoot.isBlank()) {
            projectRoot = System.getProperty("user.dir");
        }

        String pythonExec = System.getenv("PYTHON_EXEC");
        if (pythonExec == null || pythonExec.isBlank()) {
            pythonExec = System.getProperty("os.name").toLowerCase().contains("windows")
                ? "python"
                : "python3";
        }

        Path scriptPath = Paths.get(projectRoot, "scripts", "scraper.py");
        Path venvPython = Paths.get(projectRoot, "scripts", "venv", "bin", "python");
        Path venvPythonWin = Paths.get(projectRoot, "scripts", "venv", "Scripts", "python.exe");

        Path pythonPath;
        if (venvPython.toFile().exists()) {
            pythonPath = venvPython;
        } else if (venvPythonWin.toFile().exists()) {
            pythonPath = venvPythonWin;
        } else {
            pythonPath = Paths.get(pythonExec);
        }

        logger.info("=== TRANSCRIPT SERVICE DEBUG ===");
        logger.info("Python path exists: {} - {}", pythonPath, pythonPath.toFile().exists());
        logger.info("Script path exists: {} - {}", scriptPath, scriptPath.toFile().exists());
        logger.info("URL: {}", url);

        Map<String, String> env = new HashMap<>(System.getenv());
        env.put("PYTHONIOENCODING", "utf-8");

        ProcessBuilder processBuilder = new ProcessBuilder(
            pythonPath.toString(),
            scriptPath.toString(),
            url
        );
        processBuilder.environment().putAll(env);
        processBuilder.redirectErrorStream(true);
        
        logger.info("Starting process...");
        Process process = processBuilder.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        int exitCode = process.waitFor();
        logger.info("Exit code: {}", exitCode);
        logger.info("Python output length: {}", output.toString().length());
        
        if (exitCode != 0) {
            String errorOutput = output.toString().trim();
            logger.error("Error output: {}", errorOutput);
            if (errorOutput.contains("Transcripts are disabled")) {
                throw new RuntimeException("Transcripts are disabled for this video");
            } else if (errorOutput.contains("No transcript available")) {
                throw new RuntimeException("No transcript available for this video");
            }
            throw new RuntimeException(errorOutput.isEmpty() ? "Failed to execute scraper" : errorOutput);
        }

        logger.info("=== END DEBUG ===");
        return output.toString().trim();
    }

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
        boolean lastEndedWithSentence = false;

        for (Map<String, Object> snippet : snippets) {
            double start = (Double) snippet.get("start");
            String text = (String) snippet.get("text");

            if (currentText.length() > 0) {
                currentText.append(" ");
            }
            currentText.append(text);
            lastEndedWithSentence = isSentenceEnd(text);

            if (start >= windowEnd && (lastEndedWithSentence || start >= windowEnd + segmentDurationSeconds * 0.5)) {
                TranscriptSegment segment = new TranscriptSegment(
                    formatTime(currentWindowStart),
                    currentWindowStart,
                    windowEnd,
                    currentText.toString().trim()
                );
                segments.add(segment);
                
                currentWindowStart = windowEnd;
                windowEnd = currentWindowStart + segmentDurationSeconds;
                currentText = new StringBuilder();
                lastEndedWithSentence = false;
            }

            if (start >= windowEnd + segmentDurationSeconds * 0.8 && currentText.length() > 0) {
                TranscriptSegment segment = new TranscriptSegment(
                    formatTime(currentWindowStart),
                    currentWindowStart,
                    windowEnd,
                    currentText.toString().trim()
                );
                segments.add(segment);
                
                currentWindowStart = windowEnd;
                windowEnd = currentWindowStart + segmentDurationSeconds;
                currentText = new StringBuilder();
                lastEndedWithSentence = false;
            }
        }

        if (currentText.length() > 0) {
            TranscriptSegment segment = new TranscriptSegment(
                formatTime(currentWindowStart),
                currentWindowStart,
                windowEnd,
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