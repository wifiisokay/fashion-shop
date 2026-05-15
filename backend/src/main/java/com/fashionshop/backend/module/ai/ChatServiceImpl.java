package com.fashionshop.backend.module.ai;

import com.fashionshop.backend.common.enums.ChatRole;
import com.fashionshop.backend.domain.ChatMessage;
import com.fashionshop.backend.domain.ChatSession;
import com.fashionshop.backend.domain.repository.ChatMessageRepository;
import com.fashionshop.backend.domain.repository.ChatSessionRepository;
import com.fashionshop.backend.domain.User;
import com.fashionshop.backend.domain.repository.UserRepository;
import com.fashionshop.backend.module.ai.dto.request.GuestChatRequest;
import com.fashionshop.backend.module.ai.dto.response.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Orchestrator RAG pipeline:
 * Nhận message → classify intent → retrieve data → build prompt → call Gemini → parse → save → return.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final IntentClassifier intentClassifier;
    private final DataRetrieverService dataRetriever;
    private final PromptBuilder promptBuilder;
    private final GeminiApiClient geminiClient;
    private final ObjectMapper objectMapper;

    // ========================
    // Customer
    // ========================

    @Override
    @Transactional
    public ChatMessageResponse processMessage(Long userId, String content) {
        // 1. Get/create today session
        ChatSession session = getOrCreateTodaySession(userId);

        // 2. Save user message
        ChatMessage userMsg = ChatMessage.builder()
                .session(session)
                .role(ChatRole.USER)
                .content(content)
                .build();
        messageRepository.save(userMsg);

        // 3. Load recent messages for context
        List<ChatMessage> recentMessages = messageRepository
                .findTop10BySessionIdOrderByCreatedAtDesc(session.getId());

        // 4. Classify intent
        ChatIntent intent = intentClassifier.classify(content, recentMessages);

        // 5. Retrieve data context
        String retrievedData = dataRetriever.retrieveContext(intent, content, userId);

        // 6. Build prompt + history
        String systemPrompt = promptBuilder.buildSystemPrompt(intent, retrievedData);
        List<GeminiApiClient.GeminiMessage> history = promptBuilder.buildHistory(recentMessages);

        // 7. Call Gemini
        String aiResponse;
        try {
            aiResponse = geminiClient.generateContent(systemPrompt, history, content);
        } catch (Exception e) {
            log.error("Gemini call failed for user {}: {}", userId, e.getMessage());
            aiResponse = "Xin lỗi, hệ thống AI đang bận. Vui lòng thử lại sau ít phút. 🙏";
        }

        // 8. Parse response
        ChatMessageResponse response = parseAiResponse(aiResponse, intent);

        // 9. Save assistant message
        ChatMessage assistantMsg = ChatMessage.builder()
                .session(session)
                .role(ChatRole.ASSISTANT)
                .content(aiResponse)
                .intent(intent.name())
                .hasProducts(response.getProducts() != null && !response.getProducts().isEmpty())
                .build();
        messageRepository.save(assistantMsg);

        return response;
    }

    @Override
    public ChatMessageResponse processGuestMessage(GuestChatRequest request) {
        String content = request.getContent();

        // Classify intent
        ChatIntent intent = intentClassifier.classify(content, Collections.emptyList());

        // Guest restrictions: ORDER_INQUIRY và RETURN_SUPPORT cần đăng nhập
        if (intent == ChatIntent.ORDER_INQUIRY || intent == ChatIntent.RETURN_SUPPORT) {
            return ChatMessageResponse.builder()
                    .role("assistant")
                    .content("Để xem thông tin đơn hàng hoặc yêu cầu đổi trả, bạn cần đăng nhập trước nhé! 🔐")
                    .intent(intent.name())
                    .suggestedQuestions(List.of("Xem sản phẩm mới", "Tư vấn phối đồ", "Chính sách đổi trả"))
                    .createdAt(LocalDateTime.now())
                    .build();
        }

        // Retrieve data (no userId → generic)
        String retrievedData = dataRetriever.retrieveContext(intent, content, null);

        // Build prompt
        String systemPrompt = promptBuilder.buildSystemPrompt(intent, retrievedData);

        // Build history from guest request
        List<GeminiApiClient.GeminiMessage> history = new ArrayList<>();
        if (request.getHistory() != null) {
            for (GuestChatRequest.GuestMessage msg : request.getHistory()) {
                history.add(new GeminiApiClient.GeminiMessage(msg.getRole(), msg.getText()));
            }
        }

        // Call Gemini
        String aiResponse;
        try {
            aiResponse = geminiClient.generateContent(systemPrompt, history, content);
        } catch (Exception e) {
            log.error("Gemini call failed for guest: {}", e.getMessage());
            aiResponse = "Xin lỗi, hệ thống AI đang bận. Vui lòng thử lại sau ít phút. 🙏";
        }

        return parseAiResponse(aiResponse, intent);
    }

    @Override
    @Transactional
    public ChatSessionResponse getTodaySession(Long userId) {
        ChatSession session = getOrCreateTodaySession(userId);
        return mapSessionToResponse(session);
    }

    @Override
    public List<ChatMessageResponse> getTodayMessages(Long userId) {
        return sessionRepository.findByUserIdAndSessionDate(userId, LocalDate.now())
                .map(session -> messageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId())
                        .stream().map(this::mapMessageToResponse).collect(Collectors.toList()))
                .orElse(Collections.emptyList());
    }

    @Override
    public Page<ChatSessionResponse> getUserSessions(Long userId, Pageable pageable) {
        return sessionRepository.findByUserIdOrderBySessionDateDesc(userId, pageable)
                .map(this::mapSessionToResponse);
    }

    @Override
    public List<ChatMessageResponse> getSessionMessages(Long sessionId, Long userId) {
        // Verify ownership
        ChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found"));
        if (!session.getUser().getId().equals(userId)) {
            throw new RuntimeException("Access denied");
        }
        return messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)
                .stream().map(this::mapMessageToResponse).collect(Collectors.toList());
    }

    // ========================
    // Admin
    // ========================

    @Override
    public Page<ChatSessionResponse> getAdminUserSessions(Long userId, Pageable pageable) {
        return sessionRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::mapSessionToResponse);
    }

    @Override
    public List<ChatMessageResponse> getAdminSessionMessages(Long sessionId) {
        return messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)
                .stream().map(this::mapMessageToResponse).collect(Collectors.toList());
    }

    @Override
    public ChatStatsResponse getChatStats() {
        LocalDateTime since = LocalDateTime.now().minusDays(30);

        // Intent distribution
        Map<String, Long> intentDist = new LinkedHashMap<>();
        messageRepository.countByIntentSince(since).forEach(row ->
            intentDist.put((String) row[0], (Long) row[1])
        );

        // Messages per day
        Map<String, Long> perDay = new LinkedHashMap<>();
        messageRepository.countPerDaySince(since).forEach(row ->
            perDay.put(row[0].toString(), (Long) row[1])
        );

        return ChatStatsResponse.builder()
                .intentDistribution(intentDist)
                .messagesPerDay(perDay)
                .totalMessages(messageRepository.count())
                .totalSessions(sessionRepository.count())
                .build();
    }

    @Override
    @Transactional
    public void deleteSession(Long sessionId) {
        sessionRepository.deleteById(sessionId);
    }

    // ========================
    // Private helpers
    // ========================

    private ChatSession getOrCreateTodaySession(Long userId) {
        LocalDate today = LocalDate.now();
        return sessionRepository.findByUserIdAndSessionDate(userId, today)
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new RuntimeException("User not found"));
                    ChatSession newSession = ChatSession.builder()
                            .user(user)
                            .sessionDate(today)
                            .build();
                    return sessionRepository.save(newSession);
                });
    }

    /**
     * Parse AI response: thử parse JSON (cho PRODUCT_SEARCH/OUTFIT_SUGGEST), fallback plain text.
     */
    private ChatMessageResponse parseAiResponse(String aiResponse, ChatIntent intent) {
        // Thử parse JSON
        if (intent == ChatIntent.PRODUCT_SEARCH || intent == ChatIntent.OUTFIT_SUGGEST) {
            try {
                // Loại bỏ markdown code fence nếu Gemini trả về ```json ... ```
                String cleaned = aiResponse.trim();
                if (cleaned.startsWith("```json")) {
                    cleaned = cleaned.substring(7);
                }
                if (cleaned.startsWith("```")) {
                    cleaned = cleaned.substring(3);
                }
                if (cleaned.endsWith("```")) {
                    cleaned = cleaned.substring(0, cleaned.length() - 3);
                }
                cleaned = cleaned.trim();

                JsonNode json = objectMapper.readTree(cleaned);

                String text = json.has("text") ? json.get("text").asText() : aiResponse;
                List<ChatProductCard> products = new ArrayList<>();
                List<String> suggestedQuestions = new ArrayList<>();

                if (json.has("products") && json.get("products").isArray()) {
                    for (JsonNode p : json.get("products")) {
                        products.add(ChatProductCard.builder()
                                .id(p.has("id") ? p.get("id").asLong() : null)
                                .name(p.has("name") ? p.get("name").asText() : null)
                                .price(p.has("price") ? new BigDecimal(p.get("price").asText()) : null)
                                .salePrice(p.has("salePrice") && !p.get("salePrice").isNull()
                                        ? new BigDecimal(p.get("salePrice").asText()) : null)
                                .imageUrl(p.has("imageUrl") ? p.get("imageUrl").asText() : null)
                                .matchReason(p.has("matchReason") ? p.get("matchReason").asText() : null)
                                .build());
                    }
                }

                if (json.has("suggestedQuestions") && json.get("suggestedQuestions").isArray()) {
                    for (JsonNode q : json.get("suggestedQuestions")) {
                        suggestedQuestions.add(q.asText());
                    }
                }

                return ChatMessageResponse.builder()
                        .role("assistant")
                        .content(text)
                        .products(products.isEmpty() ? null : products)
                        .suggestedQuestions(suggestedQuestions.isEmpty() ? null : suggestedQuestions)
                        .intent(intent.name())
                        .createdAt(LocalDateTime.now())
                        .build();
            } catch (Exception e) {
                log.debug("Failed to parse JSON from AI response, using plain text. Error: {}", e.getMessage());
            }
        }

        // Fallback: plain text response
        return ChatMessageResponse.builder()
                .role("assistant")
                .content(aiResponse)
                .intent(intent.name())
                .suggestedQuestions(getDefaultSuggestions(intent))
                .createdAt(LocalDateTime.now())
                .build();
    }

    private List<String> getDefaultSuggestions(ChatIntent intent) {
        return switch (intent) {
            case PRODUCT_SEARCH -> List.of("Xem thêm sản phẩm khác", "Tư vấn phối đồ");
            case OUTFIT_SUGGEST -> List.of("Gợi ý outfit khác", "Tìm sản phẩm cụ thể");
            case ORDER_INQUIRY -> List.of("Xem đơn hàng khác", "Chính sách đổi trả");
            case RETURN_SUPPORT -> List.of("Hướng dẫn trả hàng", "Liên hệ hỗ trợ");
            case GENERAL_SUPPORT -> List.of("Tìm sản phẩm", "Tư vấn size");
            case CHITCHAT -> List.of("Tìm áo thun nam", "Gợi ý outfit đi chơi", "Xem đơn hàng");
        };
    }

    private ChatSessionResponse mapSessionToResponse(ChatSession session) {
        return ChatSessionResponse.builder()
                .sessionId(session.getId())
                .sessionDate(session.getSessionDate())
                .messageCount(session.getMessages() != null ? session.getMessages().size() : 0)
                .createdAt(session.getCreatedAt())
                .build();
    }

    private ChatMessageResponse mapMessageToResponse(ChatMessage msg) {
        // Thử parse stored content nếu là assistant message có products
        if (msg.getRole() == ChatRole.ASSISTANT && Boolean.TRUE.equals(msg.getHasProducts())) {
            ChatIntent intent = msg.getIntent() != null
                    ? ChatIntent.valueOf(msg.getIntent()) : ChatIntent.CHITCHAT;
            return parseAiResponse(msg.getContent(), intent);
        }

        return ChatMessageResponse.builder()
                .role(msg.getRole() == ChatRole.USER ? "user" : "assistant")
                .content(msg.getContent())
                .intent(msg.getIntent())
                .createdAt(msg.getCreatedAt())
                .build();
    }
}
