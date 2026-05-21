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
import com.fashionshop.backend.module.ai.nlu.NluSearchParams;
import com.fashionshop.backend.module.ai.nlu.NluService;
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
import java.util.concurrent.ConcurrentHashMap;
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
    private final AiClientRouter aiClientRouter;
    private final ProductRetrieverService productRetrieverService;
    private final OutfitSuggestionService outfitSuggestionService;
    private final UserPreferenceService userPreferenceService;
    private final NluService nluService;
    private final ObjectMapper objectMapper;
    private final Map<Long, NluSearchParams> lastNluParams = new ConcurrentHashMap<>();

    // ========================
    // Customer
    // ========================

    @Override
    @Transactional
    public ChatMessageResponse processMessage(Long userId, String content) {
        return processMessage(userId, content, null, null);
    }

    @Override
    @Transactional
    public ChatMessageResponse processMessage(Long userId, String content, Long productId, Long colorId) {
        content = validateContent(content);
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
        log.info("[AI_CHAT_FLOW] userId={} sessionId={} message='{}' productContext={}/{} intent={} recentCount={}",
                userId, session.getId(), shorten(content), productId, colorId, intent, recentMessages.size());

        if (intent == ChatIntent.OUTFIT_SUGGEST) {
            // Ưu tiên productId từ context (user đang xem trang SP)
            // Nếu không có → thử detect SP từ nội dung chat
            Long resolvedProductId = productId;
            Long resolvedColorId = colorId;
            if (resolvedProductId == null) {
                NluSearchParams nluParams = extractNlu(session.getId(), content, intent);
                resolvedProductId = detectProductFromMessage(content, nluParams);
            }
            log.info("[AI_CHAT_OUTFIT] userId={} sessionId={} message='{}' resolvedProductId={} resolvedColorId={}",
                    userId, session.getId(), shorten(content), resolvedProductId, resolvedColorId);
            ChatMessageResponse response = resolvedProductId != null
                    ? outfitChatResponse(resolvedProductId, resolvedColorId)
                    : askForMainProductResponse();
            messageRepository.save(ChatMessage.builder()
                    .session(session)
                    .role(ChatRole.ASSISTANT)
                    .content(serializeAssistantResponse(response))
                    .intent(intent.name())
                    .hasProducts(response.getOutfitCombos() != null && !response.getOutfitCombos().isEmpty())
                    .build());
            return response;
        }

        if ((intent == ChatIntent.ORDER_INQUIRY || intent == ChatIntent.RETURN_SUPPORT) && userId == null) {
            return loginRequiredResponse(intent);
        }

        ProductRetrieverService.ProductSearchResult productResult = null;
        String retrievedData;
        if (intent == ChatIntent.PRODUCT_SEARCH) {
            NluSearchParams nluParams = extractNlu(session.getId(), content, intent);
            productResult = nluParams != null
                    ? productRetrieverService.search(nluParams, 6)
                    : productRetrieverService.search(content, 6);
            retrievedData = productResult.contextText();
            log.info("[AI_CHAT_RETRIEVE] userId={} sessionId={} intent={} total={} returned={} contextLength={}",
                    userId, session.getId(), intent, productResult.total(), productResult.products().size(), retrievedData.length());
            if (productResult.products().isEmpty()) {
                log.info("[AI_CHAT_NO_PRODUCTS] userId={} sessionId={} message='{}' intent={}",
                        userId, session.getId(), shorten(content), intent);
                ChatMessageResponse response = noProductsFoundResponse(intent);
                messageRepository.save(ChatMessage.builder()
                        .session(session)
                        .role(ChatRole.ASSISTANT)
                        .content(serializeAssistantResponse(response))
                        .intent(intent.name())
                        .hasProducts(false)
                        .build());
                return response;
            }
        } else {
            retrievedData = dataRetriever.retrieveContext(intent, content, userId);
            log.info("[AI_CHAT_RETRIEVE] userId={} sessionId={} intent={} contextLength={}",
                    userId, session.getId(), intent, retrievedData == null ? 0 : retrievedData.length());
        }

        // 6. Build prompt + history (có userId → nhúng user preferences)
        String systemPrompt = promptBuilder.buildSystemPrompt(intent, retrievedData, userId);
        List<AiMessage> history = promptBuilder.buildHistory(recentMessages);

        // 7. Call AI (Gemini primary, Ollama fallback)
        String aiResponse;
        try {
            aiResponse = aiClientRouter.generate(systemPrompt, history, content);
            log.info("[AI_CHAT_AI_RESPONSE] userId={} sessionId={} intent={} responseLength={} responsePreview='{}'",
                    userId, session.getId(), intent, aiResponse == null ? 0 : aiResponse.length(), shorten(aiResponse));
        } catch (Exception e) {
            log.error("AI call failed for user {}: {}", userId, e.getMessage());
            aiResponse = fallbackByIntent(intent);
        }

        // 8. Parse response
        ChatMessageResponse response = parseAiResponse(aiResponse, intent);
        log.info("[AI_CHAT_PARSED] userId={} sessionId={} intent={} products={} outfits={} suggestions={}",
                userId, session.getId(), intent,
                response.getProducts() == null ? 0 : response.getProducts().size(),
                response.getOutfitCombos() == null ? 0 : response.getOutfitCombos().size(),
                response.getSuggestedQuestions() == null ? 0 : response.getSuggestedQuestions().size());
        if (productResult != null) {
            response.setProducts(productResult.products());
            response.setSuggestedQuestions(response.getSuggestedQuestions() != null
                    ? response.getSuggestedQuestions()
                    : getDefaultSuggestions(intent));
        }

        // 9. Save assistant message
        ChatMessage assistantMsg = ChatMessage.builder()
                .session(session)
                .role(ChatRole.ASSISTANT)
                .content(serializeAssistantResponse(response))
                .intent(intent.name())
                .hasProducts((response.getProducts() != null && !response.getProducts().isEmpty())
                        || (response.getOutfitCombos() != null && !response.getOutfitCombos().isEmpty()))
                .build();
        messageRepository.save(assistantMsg);

        userPreferenceService.updateAsync(userId, content, response.getContent());

        return response;
    }

    @Override
    public ChatMessageResponse processGuestMessage(GuestChatRequest request) {
        String content = validateContent(request.getContent());

        // Classify intent
        ChatIntent intent = intentClassifier.classify(content, Collections.emptyList());
        log.info("[AI_CHAT_FLOW] guest message='{}' productContext={}/{} intent={}",
                shorten(content), request.getProductId(), request.getColorId(), intent);

        if (intent == ChatIntent.OUTFIT_SUGGEST) {
            // Ưu tiên productId từ request context, sau đó thử detect từ message
            Long resolvedProductId = request.getProductId();
            if (resolvedProductId == null) {
                NluSearchParams nluParams = nluService.extract(content, intent.name(), null);
                resolvedProductId = detectProductFromMessage(content, nluParams);
            }
            log.info("[AI_CHAT_OUTFIT] guest message='{}' resolvedProductId={} resolvedColorId={}",
                    shorten(content), resolvedProductId, request.getColorId());
            return resolvedProductId != null
                    ? outfitChatResponse(resolvedProductId, request.getColorId())
                    : askForMainProductResponse();
        }

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
        ProductRetrieverService.ProductSearchResult productResult = null;
        String retrievedData;
        if (intent == ChatIntent.PRODUCT_SEARCH) {
            NluSearchParams nluParams = nluService.extract(content, intent.name(), null);
            productResult = nluParams != null
                    ? productRetrieverService.search(nluParams, 6)
                    : productRetrieverService.search(content, 6);
            retrievedData = productResult.contextText();
            log.info("[AI_CHAT_RETRIEVE] guest intent={} total={} returned={} contextLength={}",
                    intent, productResult.total(), productResult.products().size(), retrievedData.length());
            if (productResult.products().isEmpty()) {
                log.info("[AI_CHAT_NO_PRODUCTS] guest message='{}' intent={}", shorten(content), intent);
                return noProductsFoundResponse(intent);
            }
        } else {
            retrievedData = dataRetriever.retrieveContext(intent, content, null);
            log.info("[AI_CHAT_RETRIEVE] guest intent={} contextLength={}",
                    intent, retrievedData == null ? 0 : retrievedData.length());
        }

        // Build prompt
        String systemPrompt = promptBuilder.buildSystemPrompt(intent, retrievedData);

        // Build history from guest request
        List<AiMessage> history = new ArrayList<>();
        if (request.getHistory() != null) {
            for (GuestChatRequest.GuestMessage msg : request.getHistory()) {
                history.add(new AiMessage(msg.getRole(), msg.getText()));
            }
        }

        // Call AI
        String aiResponse;
        try {
            aiResponse = aiClientRouter.generate(systemPrompt, history, content);
            log.info("[AI_CHAT_AI_RESPONSE] guest intent={} responseLength={} responsePreview='{}'",
                    intent, aiResponse == null ? 0 : aiResponse.length(), shorten(aiResponse));
        } catch (Exception e) {
            log.error("AI call failed for guest: {}", e.getMessage());
            aiResponse = fallbackByIntent(intent);
        }

        ChatMessageResponse response = parseAiResponse(aiResponse, intent);
        log.info("[AI_CHAT_PARSED] guest intent={} products={} outfits={} suggestions={}",
                intent,
                response.getProducts() == null ? 0 : response.getProducts().size(),
                response.getOutfitCombos() == null ? 0 : response.getOutfitCombos().size(),
                response.getSuggestedQuestions() == null ? 0 : response.getSuggestedQuestions().size());
        if (productResult != null) {
            response.setProducts(productResult.products());
            response.setSuggestedQuestions(response.getSuggestedQuestions() != null
                    ? response.getSuggestedQuestions()
                    : getDefaultSuggestions(intent));
        }
        return response;
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

    private String validateContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("Message content is required");
        }
        String trimmed = content.trim();
        if (trimmed.length() > 500) {
            throw new IllegalArgumentException("Message content must be at most 500 characters");
        }
        return trimmed;
    }

    private String shorten(String value) {
        if (value == null) return "";
        String trimmed = value.replaceAll("\\s+", " ").trim();
        return trimmed.length() > 160 ? trimmed.substring(0, 160) + "..." : trimmed;
    }

    private ChatMessageResponse loginRequiredResponse(ChatIntent intent) {
        return ChatMessageResponse.builder()
                .role("assistant")
                .content("Để xem thông tin đơn hàng hoặc yêu cầu đổi trả, bạn cần đăng nhập trước nhé.")
                .intent(intent.name())
                .suggestedQuestions(List.of("Tìm sản phẩm mới", "Tư vấn phối đồ", "Chính sách đổi trả"))
                .createdAt(LocalDateTime.now())
                .build();
    }

    private ChatMessageResponse outfitChatResponse(Long productId, Long colorId) {
        OutfitSuggestionResponse outfit = outfitSuggestionService.getSuggestions(productId, colorId);
        return ChatMessageResponse.builder()
                .role("assistant")
                .content(outfit.getText() != null ? outfit.getText() : "Mình gợi ý các outfit phù hợp với sản phẩm bạn đang xem.")
                .intent(ChatIntent.OUTFIT_SUGGEST.name())
                .outfitCombos(outfit.getCombos())
                .suggestedQuestions(List.of("Gợi ý outfit khác", "Tìm sản phẩm tương tự"))
                .createdAt(LocalDateTime.now())
                .build();
    }

    private ChatMessageResponse askForMainProductResponse() {
        return ChatMessageResponse.builder()
                .role("assistant")
                .content("Bạn muốn mình phối outfit dựa trên sản phẩm nào? Hãy mở trang chi tiết sản phẩm hoặc gửi tên item chính như áo thun, quần jean, đầm kèm màu bạn muốn phối.")
                .intent(ChatIntent.OUTFIT_SUGGEST.name())
                .suggestedQuestions(List.of("Tìm áo thun để phối", "Tìm quần jean để phối", "Gợi ý đồ đi làm"))
                .createdAt(LocalDateTime.now())
                .build();
    }

    private ChatMessageResponse noProductsFoundResponse(ChatIntent intent) {
        return ChatMessageResponse.builder()
                .role("assistant")
                .content("Mình chưa tìm thấy sản phẩm phù hợp trong dữ liệu hiện tại của shop. Bạn thử đổi giới tính, màu hoặc khoảng giá để mình lọc lại chính xác hơn.")
                .intent(intent.name())
                .products(List.of())
                .suggestedQuestions(List.of("Tìm áo thun nam màu đen", "Tìm váy nữ đi làm", "Xem sản phẩm đang sale"))
                .createdAt(LocalDateTime.now())
                .build();
    }

    /**
     * Phân tích tin nhắn chat để tìm SP phù hợp nhất làm nền phối đồ.
     * VD: "áo thun đen mặc với gì?" → tìm SP áo thun màu đen đầu tiên trong DB.
     * Trả null nếu không tìm thấy SP nào.
     */
    private NluSearchParams extractNlu(Long sessionId, String content, ChatIntent intent) {
        NluSearchParams previous = lastNluParams.get(sessionId);
        String previousIntent = previous != null && previous.getIntent() != null ? previous.getIntent() : intent.name();
        NluSearchParams current = nluService.extract(content, previousIntent, previous);
        if (current == null) {
            return null;
        }
        if (current.getIntent() != null && current.getIntent().equals(ChatIntent.CHITCHAT.name())) {
            return null;
        }
        if (current.getIntent() == null || current.getIntent().equals(ChatIntent.PRODUCT_SEARCH.name())
                || current.getIntent().equals(ChatIntent.OUTFIT_SUGGEST.name())) {
            lastNluParams.put(sessionId, current);
        }
        return current;
    }

    private Long detectProductFromMessage(String content, NluSearchParams nluParams) {
        try {
            ProductRetrieverService.ProductSearchResult result = nluParams != null
                    ? productRetrieverService.search(nluParams, 1)
                    : productRetrieverService.search(content, 1);
            if (!result.products().isEmpty()) {
                Long productId = result.products().get(0).getId();
                log.info("[AI_CHAT_DETECT_PRODUCT] message='{}' total={} detectedProductId={} colorId={}",
                        shorten(content), result.total(), productId, result.products().get(0).getColorId());
                return productId;
            }
            log.info("[AI_CHAT_DETECT_PRODUCT] message='{}' total={} detectedProductId=null",
                    shorten(content), result.total());
        } catch (Exception e) {
            log.warn("[AI_CHAT_DETECT_PRODUCT] message='{}' failed={}", shorten(content), e.getMessage());
        }
        return null;
    }

    /**
     * Fallback message riêng cho từng intent khi AI timeout/lỗi.
     * Không throw exception — luôn trả message thân thiện.
     */
    private String fallbackByIntent(ChatIntent intent) {
        return switch (intent) {
            case PRODUCT_SEARCH  -> "Xin lỗi, mình không thể tìm kiếm lúc này. Bạn xem sản phẩm tại trang shop nhé!";
            case OUTFIT_SUGGEST  -> "Xin lỗi, mình chưa gợi ý được outfit lúc này. Bạn thử lại sau ít giây nhé!";
            case ORDER_INQUIRY   -> "Xin lỗi, mình không tra được. Bạn vào trang Đơn hàng kiểm tra nhé.";
            case RETURN_SUPPORT  -> "Xin lỗi, mình đang bận. Bạn vào trang Đơn hàng → Yêu cầu đổi trả để thực hiện nhé.";
            default              -> "Xin lỗi, mình đang bận. Bạn thử lại sau ít giây nhé!";
        };
    }

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

                products.clear(); // Product cards must come from database retrieval only.

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
                if (looksLikeJson(aiResponse)) {
                    return ChatMessageResponse.builder()
                            .role("assistant")
                            .content(defaultTextForIntent(intent))
                            .intent(intent.name())
                            .suggestedQuestions(getDefaultSuggestions(intent))
                            .createdAt(LocalDateTime.now())
                            .build();
                }
            }
        }

        // Fallback: plain text response
        return ChatMessageResponse.builder()
                .role("assistant")
                .content(sanitizePlainText(aiResponse, intent))
                .intent(intent.name())
                .suggestedQuestions(getDefaultSuggestions(intent))
                .createdAt(LocalDateTime.now())
                .build();
    }

    private boolean looksLikeJson(String value) {
        if (value == null) return false;
        String cleaned = value.trim();
        if (cleaned.startsWith("```json") || cleaned.startsWith("```")) return true;
        return cleaned.startsWith("{") || cleaned.startsWith("[");
    }

    private String sanitizePlainText(String value, ChatIntent intent) {
        if (value == null || value.isBlank() || looksLikeJson(value)) {
            return defaultTextForIntent(intent);
        }
        return value;
    }

    private String defaultTextForIntent(ChatIntent intent) {
        return switch (intent) {
            case PRODUCT_SEARCH -> "Mình đã tìm các sản phẩm phù hợp từ dữ liệu của shop cho bạn.";
            case OUTFIT_SUGGEST -> "Mình sẽ gợi ý outfit dựa trên sản phẩm chính và các item còn hàng trong shop.";
            case ORDER_INQUIRY -> "Mình đã kiểm tra thông tin đơn hàng của bạn từ hệ thống.";
            case RETURN_SUPPORT -> "Mình đã kiểm tra thông tin đổi trả và chính sách hỗ trợ phù hợp.";
            case GENERAL_SUPPORT -> "Mình có thể hỗ trợ bạn về chính sách, size, thanh toán và vận chuyển.";
            case CHITCHAT -> "Mình có thể hỗ trợ bạn tìm sản phẩm, phối đồ hoặc kiểm tra đơn hàng.";
        };
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
        if (msg.getRole() == ChatRole.ASSISTANT) {
            ChatMessageResponse stored = parseStoredAssistantResponse(msg.getContent());
            if (stored != null) {
                if (stored.getCreatedAt() == null) {
                    stored.setCreatedAt(msg.getCreatedAt());
                }
                if (stored.getRole() == null) {
                    stored.setRole("assistant");
                }
                return stored;
            }
        }

        return ChatMessageResponse.builder()
                .role(msg.getRole() == ChatRole.USER ? "user" : "assistant")
                .content(msg.getContent())
                .intent(msg.getIntent())
                .createdAt(msg.getCreatedAt())
                .build();
    }

    private String serializeAssistantResponse(ChatMessageResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            log.warn("Failed to serialize assistant response: {}", e.getMessage());
            return response.getContent();
        }
    }

    private ChatMessageResponse parseStoredAssistantResponse(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        String trimmed = content.trim();
        if (!(trimmed.startsWith("{") || trimmed.startsWith("["))) {
            return null;
        }
        try {
            return objectMapper.readValue(trimmed, ChatMessageResponse.class);
        } catch (Exception e) {
            log.debug("Stored assistant response is not JSON payload: {}", e.getMessage());
            return null;
        }
    }
}
