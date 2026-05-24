package com.fashionshop.backend.module.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiClientRouter {

    private final AiProperties props;
    private final GeminiApiClient geminiApiClient;
    private final OllamaGemmaClient ollamaGemmaClient;

    public String generate(String systemPrompt, List<AiMessage> history, String userMessage) {
        AiClient primary = resolve(props.getPrimary());
        AiClient fallback = resolve(props.getFallback());

        try {
            return primary.generateContent(systemPrompt, history, userMessage);
        } catch (Exception primaryError) {
            log.warn("Primary AI client {} failed: {}", primary.name(), primaryError.getMessage());
            if (fallback == primary) {
                return fallbackMessage();
            }
            try {
                return fallback.generateContent(systemPrompt, history, userMessage);
            } catch (Exception fallbackError) {
                log.warn("Fallback AI client {} failed: {}", fallback.name(), fallbackError.getMessage());
                return fallbackMessage();
            }
        }
    }

    private AiClient resolve(String name) {
        if ("ollama-gemma".equalsIgnoreCase(name) || "ollama".equalsIgnoreCase(name) || "gemma".equalsIgnoreCase(name)) {
            return ollamaGemmaClient;
        }
        return geminiApiClient;
    }

    private String fallbackMessage() {
        return "Xin lỗi, hệ thống tư vấn đang bận. Mình vẫn có thể hỗ trợ bạn tìm sản phẩm, xem đơn hàng hoặc gợi ý phối đồ nếu bạn thử lại sau ít phút.";
    }
}
