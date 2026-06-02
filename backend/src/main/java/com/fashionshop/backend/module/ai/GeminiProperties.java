package com.fashionshop.backend.module.ai;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Gemini API configuration — đọc từ application.properties.
 */
@Component
@ConfigurationProperties(prefix = "gemini")
@Getter
@Setter
public class GeminiProperties {

    private String apiKey;
    private String model = "gemini-3.1-flash-lite";
    private int maxOutputTokens = 800;
    private double temperature = 0.7;
    private int timeoutSeconds = 10;
}
