package com.fashionshop.backend.module.ai;

import java.util.List;

public interface AiClient {
    String name();

    String generateContent(String systemPrompt, List<AiMessage> history, String userMessage);
}
