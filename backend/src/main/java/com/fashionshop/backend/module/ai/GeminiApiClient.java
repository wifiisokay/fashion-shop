package com.fashionshop.backend.module.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;


import java.util.List;

/**
 * HTTP client gọi Gemini REST API (dùng RestClient built-in Spring Boot 3.4).
 */
@Slf4j
@Component
public class GeminiApiClient implements AiClient {

    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta";

    private final GeminiProperties props;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public GeminiApiClient(GeminiProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .build();
    }

    /**
     * Gọi Gemini generateContent API.
     *
     * @param systemPrompt system instruction
     * @param history      lịch sử tin nhắn [{role, text}]
     * @param userMessage  tin nhắn mới của user
     * @return text response từ Gemini
     */
    @Override
    public String name() {
        return "gemini";
    }

    @Override
    public String generateContent(String systemPrompt, List<AiMessage> history, String userMessage) {
        try {
            String requestBody = buildRequestBody(systemPrompt, history, userMessage);

            String url = String.format("/models/%s:generateContent?key=%s",
                    props.getModel(), props.getApiKey());

            String responseBody = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            return extractText(responseBody);
        } catch (Exception e) {
            log.error("Gemini API call failed: {}", e.getMessage(), e);
            throw new RuntimeException("AI_SERVICE_UNAVAILABLE", e);
        }
    }

    private String buildRequestBody(String systemPrompt, List<AiMessage> history, String userMessage) {
        try {
            ObjectNode root = objectMapper.createObjectNode();

            // System instruction
            ObjectNode systemInstruction = objectMapper.createObjectNode();
            ArrayNode sysParts = objectMapper.createArrayNode();
            sysParts.add(objectMapper.createObjectNode().put("text", systemPrompt));
            systemInstruction.set("parts", sysParts);
            root.set("systemInstruction", systemInstruction);

            // Contents: history + new user message
            ArrayNode contents = objectMapper.createArrayNode();

            // Add history messages
            if (history != null) {
                for (AiMessage msg : history) {
                    ObjectNode content = objectMapper.createObjectNode();
                    content.put("role", msg.role());
                    ArrayNode parts = objectMapper.createArrayNode();
                    parts.add(objectMapper.createObjectNode().put("text", msg.text()));
                    content.set("parts", parts);
                    contents.add(content);
                }
            }

            // Add new user message
            ObjectNode userContent = objectMapper.createObjectNode();
            userContent.put("role", "user");
            ArrayNode userParts = objectMapper.createArrayNode();
            userParts.add(objectMapper.createObjectNode().put("text", userMessage));
            userContent.set("parts", userParts);
            contents.add(userContent);

            root.set("contents", contents);

            // Generation config
            ObjectNode genConfig = objectMapper.createObjectNode();
            genConfig.put("temperature", props.getTemperature());
            genConfig.put("maxOutputTokens", props.getMaxOutputTokens());
            root.set("generationConfig", genConfig);

            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build Gemini request", e);
        }
    }

    private String extractText(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode candidates = root.path("candidates");
            if (candidates.isArray() && !candidates.isEmpty()) {
                JsonNode parts = candidates.get(0).path("content").path("parts");
                if (parts.isArray() && !parts.isEmpty()) {
                    return parts.get(0).path("text").asText("");
                }
            }
            log.warn("Gemini response has no candidates: {}", responseBody);
            return "Xin lỗi, hệ thống không thể xử lý yêu cầu này. Vui lòng thử lại.";
        } catch (Exception e) {
            log.error("Failed to parse Gemini response: {}", e.getMessage());
            return "Xin lỗi, hệ thống đang bận. Vui lòng thử lại sau.";
        }
    }

    /**
     * Message format cho Gemini history.
     * role: "user" hoặc "model"
     */
}
