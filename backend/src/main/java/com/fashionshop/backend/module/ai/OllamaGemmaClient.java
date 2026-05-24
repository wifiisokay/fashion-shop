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

@Slf4j
@Component
public class OllamaGemmaClient implements AiClient {

    private final OllamaProperties props;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public OllamaGemmaClient(OllamaProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
            .baseUrl(props.getBaseUrl())
            .build();
    }

    @Override
    public String name() {
        return "ollama-gemma";
    }

    @Override
    public String generateContent(String systemPrompt, List<AiMessage> history, String userMessage) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("model", props.getModel());
            root.put("stream", false);

            ArrayNode messages = objectMapper.createArrayNode();
            messages.add(message("system", systemPrompt));
            if (history != null) {
                for (AiMessage item : history) {
                    messages.add(message("model".equals(item.role()) ? "assistant" : item.role(), item.text()));
                }
            }
            messages.add(message("user", userMessage));
            root.set("messages", messages);

            String responseBody = restClient.post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .body(objectMapper.writeValueAsString(root))
                .retrieve()
                .body(String.class);

            JsonNode response = objectMapper.readTree(responseBody);
            return response.path("message").path("content").asText("");
        } catch (Exception e) {
            log.error("Ollama/Gemma call failed: {}", e.getMessage(), e);
            throw new RuntimeException("AI_FALLBACK_UNAVAILABLE", e);
        }
    }

    private ObjectNode message(String role, String content) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("role", role);
        node.put("content", content == null ? "" : content);
        return node;
    }
}
