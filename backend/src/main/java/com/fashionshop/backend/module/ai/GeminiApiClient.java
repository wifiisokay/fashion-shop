package com.fashionshop.backend.module.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

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
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(props.getTimeoutSeconds()));
        requestFactory.setReadTimeout(Duration.ofSeconds(props.getTimeoutSeconds()));
        this.restClient = RestClient.builder()
            .baseUrl(BASE_URL)
            .requestFactory(requestFactory)
            .build();
    }

    @Override
    public String name() {
        return "gemini";
    }

    @Override
    public String generateContent(String systemPrompt, List<AiMessage> history, String userMessage) {
        try {
            String responseBody = restClient.post()
                .uri("/models/{model}:generateContent?key={key}", props.getModel(), props.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(buildRequestBody(systemPrompt, history, userMessage))
                .retrieve()
                .body(String.class);
            return extractText(responseBody);
        } catch (Exception e) {
            log.error("Gemini API call failed: {}", e.getMessage());
            throw new IllegalStateException("AI_SERVICE_UNAVAILABLE", e);
        }
    }

    private String buildRequestBody(String systemPrompt, List<AiMessage> history, String userMessage) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            ObjectNode systemInstruction = objectMapper.createObjectNode();
            systemInstruction.set("parts", textParts(systemPrompt));
            root.set("systemInstruction", systemInstruction);

            ArrayNode contents = objectMapper.createArrayNode();
            if (history != null) {
                for (AiMessage message : history) {
                    contents.add(content(message.role(), message.text()));
                }
            }
            contents.add(content("user", userMessage));
            root.set("contents", contents);

            ObjectNode config = objectMapper.createObjectNode();
            config.put("temperature", props.getTemperature());
            config.put("maxOutputTokens", props.getMaxOutputTokens());
            root.set("generationConfig", config);
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build Gemini request", e);
        }
    }

    private ObjectNode content(String role, String text) {
        ObjectNode content = objectMapper.createObjectNode();
        content.put("role", role);
        content.set("parts", textParts(text));
        return content;
    }

    private ArrayNode textParts(String text) {
        ArrayNode parts = objectMapper.createArrayNode();
        parts.add(objectMapper.createObjectNode().put("text", text == null ? "" : text));
        return parts;
    }

    private String extractText(String responseBody) {
        try {
            JsonNode parts = objectMapper.readTree(responseBody)
                .path("candidates").path(0).path("content").path("parts");
            String text = parts.isArray() && !parts.isEmpty() ? parts.get(0).path("text").asText("") : "";
            if (text.isBlank()) {
                throw new IllegalStateException("Gemini returned empty content");
            }
            return text;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse Gemini response", e);
        }
    }
}
