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

    private int getProbeTimeout() {
        String env = System.getenv("SCRAPER_PROBE_TIMEOUT_SECONDS");
        return env != null ? Integer.parseInt(env) : 30;
    }

    private int getFetchTimeout() {
        String env = System.getenv("SCRAPER_FETCH_TIMEOUT_SECONDS");
        return env != null ? Integer.parseInt(env) : 300;
    }

    public String executeScraper(String url) throws Exception {
        int probeTimeout = getProbeTimeout();
        int fetchTimeout = getFetchTimeout();
        return executeScraperInternal(url, null, probeTimeout, fetchTimeout);
    }

    public String executeScraperWithArgs(String url, String... args) throws Exception {
        return executeScraperInternal(url, args, getProbeTimeout(), getFetchTimeout());
    }

    private String executeScraperInternal(String url, String[] additionalArgs, int probeTimeout, int fetchTimeout) throws Exception {
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

        Path scriptsDir = Paths.get(projectRoot, "scripts").toAbsolutePath();

        boolean isSemantic = additionalArgs != null && additionalArgs.length > 0;
        int processTimeout = fetchTimeout + 60;

        logger.info("=== PYTHON EXECUTION SERVICE DEBUG{} ===", isSemantic ? " (semantic)" : "");
        logger.info("Project root: {}", projectRoot);
        logger.info("Scripts directory: {}", scriptsDir);
        logger.info("Python path: {} (exists: {})", pythonPath, pythonPath.toFile().exists());
        logger.info("Script path: {} (exists: {})", scriptPath, scriptPath.toFile().exists());
        logger.info("URL: {}", url);
        logger.info("Total process timeout: {}s", processTimeout);

        Map<String, String> env = new HashMap<>(System.getenv());
        env.put("PYTHONIOENCODING", "utf-8");

        logger.debug("PATH env: {}", env.get("PATH"));
        logger.debug("PYTHONPATH env: {}", env.get("PYTHONPATH"));
        logger.debug("VIRTUAL_ENV: {}", env.get("VIRTUAL_ENV"));

        List<String> command = new ArrayList<>();
        command.add(pythonPath.toString());
        command.add("-u");
        command.add(scriptPath.toString());
        command.add(url);
        if (additionalArgs != null) {
            for (String arg : additionalArgs) {
                command.add(arg);
            }
        }

        logger.info("Full command: {}", String.join(" ", command));

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(scriptsDir.toFile());
        processBuilder.environment().putAll(env);
        processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);

        logger.info("Starting process from directory: {}", scriptsDir);
        long startTime = System.currentTimeMillis();
        Process process = processBuilder.start();

        CompletableFuture<Process> futureProcess = process.onExit().toCompletableFuture();

        try {
            logger.info("Starting concurrent stdout reader...");
            CompletableFuture<String> stdoutFuture = CompletableFuture.supplyAsync(() -> {
                StringBuilder output = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }
                } catch (Exception e) {
                    logger.error("Error reading stdout: {}", e.getMessage());
                }
                return output.toString();
            });

            long beforeWait = System.currentTimeMillis();
            logger.info("Waiting for process to complete...");
            Process completedProcess = futureProcess.get(processTimeout, TimeUnit.SECONDS);
            long waitTime = System.currentTimeMillis() - beforeWait;
            int exitCode = completedProcess.exitValue();

            logger.info("Process completed after {}ms with exit code: {}", waitTime, exitCode);

            long beforeStdout = System.currentTimeMillis();
            String output = stdoutFuture.get(30, TimeUnit.SECONDS);
            long stdoutTime = System.currentTimeMillis() - beforeStdout;
            logger.info("Stdout read completed in {}ms", stdoutTime);

            String stderrOutput = readErrorStream(process);

            if (!stderrOutput.isEmpty()) {
                logger.warn("Python stderr: {}", stderrOutput);
            }

            long totalTime = System.currentTimeMillis() - startTime;
            logger.info("Total execution time: {}ms", totalTime);
            logger.info("Python output length: {}", output.length());

            if (exitCode != 0) {
                String errorMessage = buildErrorMessage(exitCode, stderrOutput);
                logger.error("Error output: {}", errorMessage);
                throw new RuntimeException(errorMessage);
            }

            logger.info("=== END DEBUG ===");
            return output.trim();

        } catch (java.util.concurrent.TimeoutException e) {
            long elapsed = System.currentTimeMillis() - startTime;
            logger.error("Process timed out after {} seconds (elapsed: {}ms)", processTimeout, elapsed);
            process.destroyForcibly();
            throw new RuntimeException("Scraper timed out after " + processTimeout + " seconds");
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
            if (exitCode == 10) {
                return "Video is unavailable or private";
            } else if (exitCode == 11) {
                return "Probe timed out - video may be inaccessible";
            } else if (exitCode == 12) {
                return "Transcript fetch timed out - video may be too long";
            } else if (stderrOutput.contains("Transcripts are disabled")) {
                return "Transcripts are disabled for this video";
            } else if (stderrOutput.contains("No transcript available") || stderrOutput.contains("No transcripts available")) {
                return "No transcript available for this video";
            } else if (stderrOutput.contains("Video is unavailable") || stderrOutput.contains("private")) {
                return "Video is unavailable or private";
            } else if (stderrOutput.contains("not available in your region") || stderrOutput.contains("region")) {
                return "Transcript not available in your region";
            } else if (stderrOutput.contains("google-generativeai not installed")) {
                return "google-generativeai not installed. Run: pip install google-generativeai";
            } else if (stderrOutput.contains("GEMINI_API_KEY not set")) {
                return "GEMINI_API_KEY not set. Set in scripts/.env";
            } else if (stderrOutput.contains("timed out")) {
                return "Request timed out: " + stderrOutput;
            }
            return "Scraper error: " + stderrOutput;
        }
        return "Scraper failed with exit code: " + exitCode;
    }
}