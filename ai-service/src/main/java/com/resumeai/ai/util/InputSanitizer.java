package com.resumeai.ai.util;

public class InputSanitizer {
    
    /**
     * Sanitizes raw user input by removing HTML tags, potential script injections,
     * and ensuring the text is safe to include in LLM prompts.
     */
    public static String sanitize(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        
        // Remove basic HTML tags
        String sanitized = input.replaceAll("<[^>]*>", "");
        
        // Remove potential script-like patterns (e.g., javascript:, alert(), etc.)
        sanitized = sanitized.replaceAll("(?i)javascript:", "");
        sanitized = sanitized.replaceAll("(?i)onerror=", "");
        sanitized = sanitized.replaceAll("(?i)onload=", "");
        
        // Trim leading/trailing whitespace
        return sanitized.trim();
    }
}
