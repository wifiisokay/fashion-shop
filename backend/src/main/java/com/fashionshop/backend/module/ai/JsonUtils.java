package com.fashionshop.backend.module.ai;

public final class JsonUtils {

    private JsonUtils() {
    }

    public static String extractJson(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("AI response is blank");
        }
        String cleaned = value.trim();
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        }
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        cleaned = cleaned.trim();
        return extractFirstBalancedJson(cleaned);
    }

    private static String extractFirstBalancedJson(String value) {
        int start = -1;
        char open = 0;
        char close = 0;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '{' || ch == '[') {
                start = i;
                open = ch;
                close = ch == '{' ? '}' : ']';
                break;
            }
        }
        if (start < 0) {
            throw new IllegalArgumentException("No JSON object found in AI response");
        }
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (ch == '\\') {
                escaped = true;
                continue;
            }
            if (ch == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (ch == open) {
                depth++;
            } else if (ch == close) {
                depth--;
                if (depth == 0) {
                    return value.substring(start, i + 1);
                }
            }
        }
        throw new IllegalArgumentException("Unbalanced JSON payload in AI response");
    }
}
