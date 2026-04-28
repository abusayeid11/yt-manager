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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PythonExecutionService {

    private static final Logger logger = LoggerFactory.getLogger(PythonExecutionService.class);
    private static final int PROCESS_TIMEOUT_SECONDS = 30;

    public String executeScraper(String url) throws Exception {
        return executeScraperInternal(url, null);
    }

    public String executeScraperWithArgs(String url, String... args) throws Exception {
        return executeScraperInternal(url, args);
    }

    private String executeScraperInternal(String url, String[] additionalArgs) throws Exception {
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

        boolean isSemantic = additionalArgs != null && additionalArgs.length > 0;
        logger.info("=== PYTHON EXECUTION SERVICE DEBUG{} ===", isSemantic ? " (semantic)" : "");
        logger.info("Python path exists: {} - {}", pythonPath, pythonPath.toFile().exists());
        logger.info("Script path exists: {} - {}", scriptPath, scriptPath.toFile().exists());
        logger.info("URL: {}", url);

        Map<String, String> env = new HashMap<>(System.getenv());
        env.put("PYTHONIOENCODING", "utf-8");

        List<String> command = new ArrayList<>();
        command.add(pythonPath.toString());
        command.add(scriptPath.toString());
        command.add(url);
        if (additionalArgs != null) {
            for (String arg : additionalArgs) {
                command.add(arg);
            }
        }

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.environment().putAll(env);
        processBuilder.redirectError(ProcessBuilder.Redirect.PIPE);

        logger.info("Starting process...");
        Process process = processBuilder.start();

        CompletableFuture<Process> futureProcess = process.onExit().toCompletableFuture();

        try {
            Process completedProcess = futureProcess.get(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            int exitCode = completedProcess.exitValue();

            logger.info("Process completed with exit code: {}", exitCode);

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            String stderrOutput = readErrorStream(process);

            if (!stderrOutput.isEmpty()) {
                logger.warn("Python stderr: {}", stderrOutput);
            }

            logger.info("Python output length: {}", output.toString().length());

            if (exitCode != 0) {
                String errorMessage = buildErrorMessage(exitCode, stderrOutput);
                logger.error("Error output: {}", errorMessage);
                throw new RuntimeException(errorMessage);
            }

            logger.info("=== END DEBUG ===");
            return output.toString().trim();

        } catch (java.util.concurrent.TimeoutException e) {
            logger.error("Process timed out after {} seconds", PROCESS_TIMEOUT_SECONDS);
            process.destroyForcibly();
            throw new RuntimeException("Scraper timed out after " + PROCESS_TIMEOUT_SECONDS + " seconds");
        }
    }

    private String readErrorStream(Process process) {
        StringBuilder errorOutput = new StringBuilder();
        try (BufferedReader errReader = new BufferedReader(
                new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
            String errLine;
            while ((errLine = errReader.readLine()) != null) {
                errorOutput.append(errLine).append("\n");
            }
        } catch (Exception e) {
            logger.warn("Failed to read stderr: {}", e.getMessage());
        }
        return errorOutput.toString().trim();
    }

    private String buildErrorMessage(int exitCode, String stderrOutput) {
        if (stderrOutput != null && !stderrOutput.isEmpty()) {
            if (stderrOutput.contains("Transcripts are disabled")) {
                return "Transcripts are disabled for this video";
            } else if (stderrOutput.contains("No transcript available")) {
                return "No transcript available for this video";
            } else if (stderrOutput.contains("google-generativeai not installed")) {
                return "google-generativeai not installed. Run: pip install google-generativeai";
            } else if (stderrOutput.contains("GEMINI_API_KEY not set")) {
                return "GEMINI_API_KEY not set. Set in scripts/.env";
            }
            return "Scraper error: " + stderrOutput;
        }
        return "Scraper failed with exit code: " + exitCode;
    }
}