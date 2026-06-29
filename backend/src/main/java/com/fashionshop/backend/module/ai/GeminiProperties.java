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
    /** Model chính — dùng cho Outfit Rerank (chất lượng cao). */
    private String primaryModel = "gemini-2.5-flash";
    /** Model dự phòng — dùng khi primary fail, hoặc cho các tác vụ nhẹ (AI Insight, NLU). */
    private String fallbackModel = "gemini-3.1-flash-lite";
    private int maxOutputTokens = 800;
    private double temperature = 0.7;
    private int timeoutSeconds = 10;

    public String getPrimaryModel() {
        return (primaryModel != null && !primaryModel.isBlank()) ? primaryModel : "gemini-2.5-flash";
    }

    public String getFallbackModel() {
        return (fallbackModel != null && !fallbackModel.isBlank()) ? fallbackModel : "gemini-3.1-flash-lite";
    }

    /** Alias cho fallbackModel — dùng cho các tác vụ nhẹ không cần model xịn. */
    public String getLightModel() {
        return getFallbackModel();
    }
}
