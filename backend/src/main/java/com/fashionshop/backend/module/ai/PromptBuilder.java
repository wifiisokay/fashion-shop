package com.fashionshop.backend.module.ai;

import com.fashionshop.backend.common.enums.ChatRole;
import com.fashionshop.backend.domain.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Xây dựng prompt gửi AI theo 4 phần (theo doc ai_chatbot_module_doc.md):
 *
 * <pre>
 *  1. System Instruction  — vai trò, phạm vi, quy tắc phối màu, chính sách shop
 *  2. User Preferences    — gender, size, màu ưa thích, style, budget (từ DB)
 *  3. Retrieved Data      — sản phẩm/đơn hàng/đổi trả thực tế từ MySQL
 *  4. Chat History        — tối đa 10 message gần nhất (5 cặp user/assistant)
 * </pre>
 *
 * Phần 1+2 → system message. Phần 3 nhúng vào system message cuối cùng.
 * Phần 4 → history messages gửi kèm.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PromptBuilder {

    private final SystemPromptProvider systemPromptProvider;

    // =====================================
    // Public API
    // =====================================

    /**
     * Build system prompt cho authenticated user (không dùng preferences nữa).
     */
    public String buildSystemPrompt(ChatIntent intent, String retrievedData, Long userId) {
        StringBuilder prompt = new StringBuilder(systemPromptProvider.forIntent(intent));

        // Phần 3: Retrieved Data
        appendRetrievedData(prompt, retrievedData);

        return prompt.toString();
    }

    /**
     * Build system prompt không có userId (guest hoặc không cần prefs).
     */
    public String buildSystemPrompt(ChatIntent intent, String retrievedData) {
        StringBuilder prompt = new StringBuilder(systemPromptProvider.forIntent(intent));
        appendRetrievedData(prompt, retrievedData);
        return prompt.toString();
    }

    /**
     * Convert ChatMessage DB records → AI message format (phần 4 — history).
     * Chỉ lấy 10 message gần nhất, reverse để thành ASC (cũ → mới).
     */
    public List<AiMessage> buildHistory(List<ChatMessage> recentMessages) {
        if (recentMessages == null || recentMessages.isEmpty()) {
            return Collections.emptyList();
        }

        List<ChatMessage> ordered = new ArrayList<>(recentMessages);
        Collections.reverse(ordered); // DESC → ASC

        int limit = Math.min(ordered.size(), 10);
        List<AiMessage> history = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            ChatMessage msg = ordered.get(i);
            String role = msg.getRole() == ChatRole.USER ? "user" : "model";
            // Rút gọn nội dung assistant message trong history để tiết kiệm token
            String content = msg.getRole() == ChatRole.ASSISTANT
                    ? truncateAssistantContent(msg.getContent())
                    : msg.getContent();
            history.add(new AiMessage(role, content));
        }
        return history;
    }

    // =====================================
    // Private helpers
    // =====================================

    private void appendRetrievedData(StringBuilder prompt, String retrievedData) {
        if (retrievedData != null && !retrievedData.isBlank()) {
            prompt.append("\n\n## [DỮ LIỆU TỪ CƠ SỞ DỮ LIỆU CỬA HÀNG]\n")
                  .append("(Chỉ gợi ý sản phẩm/thông tin có trong danh sách này)\n")
                  .append(retrievedData);
        }
    }

    /**
     * Rút gọn nội dung assistant message dài (JSON có products) trong history.
     * Giữ tối đa 300 ký tự để tiết kiệm context window cho Gemini.
     */
    private String truncateAssistantContent(String content) {
        if (content == null) return "";
        String trimmed = content.trim();
        // Nếu là JSON dài → chỉ lấy phần "text"
        if ((trimmed.startsWith("{") || trimmed.startsWith("```")) && trimmed.length() > 300) {
            return trimmed.substring(0, 300) + "...";
        }
        return trimmed;
    }
}
