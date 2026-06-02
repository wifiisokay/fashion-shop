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
}
