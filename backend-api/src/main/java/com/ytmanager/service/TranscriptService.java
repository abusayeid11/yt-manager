package com.ytmanager.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TranscriptService {

    private static final Logger logger = LoggerFactory.getLogger(TranscriptService.class);

    public String executeScraper(String url) throws Exception {
        Path scriptPath = Paths.get("C:\\Users\\abusayeid\\Documents\\yt-manager\\scripts\\scraper.py");
        Path pythonPath = Paths.get("C:\\Users\\abusayeid\\Documents\\yt-manager\\scripts\\venv\\Scripts\\python");

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
        logger.info("Python output: {}", output.toString());
        
        if (exitCode != 0) {
            String errorOutput = output.toString().trim();
            logger.error("Error output: {}", errorOutput);
            if (errorOutput.contains("Transcripts are disabled")) {
                throw new RuntimeException("Transcripts are disabled for this video");
            }
            throw new RuntimeException(errorOutput.isEmpty() ? "Failed to execute scraper" : errorOutput);
        }

        logger.info("=== END DEBUG ===");
        return output.toString().trim();
    }
}