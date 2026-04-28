package com.ytmanager.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class YoutubeUtils {

    private static final Pattern VIDEO_ID_PATTERN = Pattern.compile("[a-zA-Z0-9_-]{11}");

    private YoutubeUtils() {
    }

    public static String extractVideoId(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }

        if (VIDEO_ID_PATTERN.matcher(input).matches()) {
            return input;
        }

        String[] patterns = {
            "v=([a-zA-Z0-9_-]{11})",
            "youtu\\.be/([a-zA-Z0-9_-]{11})",
            "embed/([a-zA-Z0-9_-]{11})"
        };

        for (String pattern : patterns) {
            Matcher matcher = Pattern.compile(pattern).matcher(input);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return null;
    }
}