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
    private String primaryModel = "gemini-2.5-flash";
    private String fallbackModel = "gemini-3.1-flash-lite";
    /** Model nhẹ dành cho các tác vụ không cần AI phức tạp (AI Insight, NLU). */
    private String lightModel = "gemini-3.1-flash-lite";
    private int maxOutputTokens = 800;
    private double temperature = 0.7;
    private int timeoutSeconds = 10;

    public String getPrimaryModel() {
        return (primaryModel != null && !primaryModel.isBlank()) ? primaryModel : model;
    }

    public String getFallbackModel() {
        return (fallbackModel != null && !fallbackModel.isBlank()) ? fallbackModel : model;
    }

    public String getLightModel() {
        return (lightModel != null && !lightModel.isBlank()) ? lightModel : fallbackModel;
    }
}
