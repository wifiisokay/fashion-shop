package com.fashionshop.backend.module.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AiClientRouter {

    private final GeminiApiClient geminiApiClient;

    public String generate(String systemPrompt, List<AiMessage> history, String userMessage) {
        return geminiApiClient.generateContent(systemPrompt, history, userMessage);
    }

    /** Dùng lightModel (3.1 Flash Lite) trực tiếp, không qua primary/failover.
     *  Thích hợp cho AI Insight, NLU và các tác vụ nhẹ để bảo tồn quota Gemini 2.5 Flash. */
    public String generateLight(String systemPrompt, List<AiMessage> history, String userMessage) {
        return geminiApiClient.generateContentLight(systemPrompt, history, userMessage);
    }
}
