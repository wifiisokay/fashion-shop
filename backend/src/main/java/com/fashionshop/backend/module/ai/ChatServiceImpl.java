package com.fashionshop.backend.module.ai;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fashionshop.backend.common.enums.ChatRole;
import com.fashionshop.backend.domain.ChatMessage;
import com.fashionshop.backend.domain.ChatSession;
import com.fashionshop.backend.domain.User;
import com.fashionshop.backend.domain.repository.ChatMessageRepository;
import com.fashionshop.backend.domain.repository.ChatSessionRepository;
import com.fashionshop.backend.domain.repository.UserRepository;
import com.fashionshop.backend.module.ai.dto.ProductContextDto;
import com.fashionshop.backend.module.ai.dto.ChatContext;
import com.fashionshop.backend.module.ai.dto.StyleAnswerResult;
import com.fashionshop.backend.module.ai.dto.response.ChatMessageResponse;
import com.fashionshop.backend.module.ai.dto.response.ChatContextDto;
import com.fashionshop.backend.module.ai.dto.response.ChatProductCard;
import com.fashionshop.backend.module.ai.dto.response.ChatSessionResponse;
import com.fashionshop.backend.module.ai.dto.response.ChatStatsResponse;
import com.fashionshop.backend.module.ai.dto.response.OutfitSuggestionResponse;
import com.fashionshop.backend.module.ai.nlu.NluSearchParams;
import com.fashionshop.backend.module.ai.nlu.NluService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
    private final InternalIntentClassifier internalIntentClassifier;
    private final ChatContextBuilder chatContextBuilder;
    private final DataRetrieverService dataRetriever;
    private final PromptBuilder promptBuilder;
    private final AiClientRouter aiClientRouter;
    private final ProductRetrieverService productRetrieverService;
    private final ProductTextResolverService productTextResolverService;
    private final OutfitSuggestionService outfitSuggestionService;
    private final StyleAnswerComposer styleAnswerComposer;
    private final NluService nluService;
    private final ProductContextResolver productContextResolver;
    private final ObjectMapper objectMapper;
    private final Cache<Long, NluSearchParams> lastNluParams = Caffeine.newBuilder()
        .maximumSize(10_000)
        .expireAfterAccess(java.time.Duration.ofHours(24))
        .build();

    // ========================
    // Customer
    // ========================

    @Override
    public ChatMessageResponse processMessage(Long userId, String content) {
        return processMessage(userId, content, null, null);
    }

    @Override
    public ChatMessageResponse processMessage(Long userId, String content, Long productId, Long colorId) {
        try {
            return processMessageInternal(userId, content, productId, colorId);
        } catch (Exception e) {
            log.error("[AI_CHAT_BOUNDARY_FALLBACK] userId={} reason={}", userId, e.getMessage(), e);
            return boundaryFallbackResponse();
        }
    }

    private ChatMessageResponse processMessageInternal(Long userId, String content, Long productId, Long colorId) {
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
                .findTop10BySessionIdOrderByCreatedAtDesc(session.getId())
                .stream()
                .limit(6)
                .toList();

        // 4. Classify intent
        ChatIntent intent = intentClassifier.classify(content, recentMessages);
        NluSearchParams contextNlu = intent == ChatIntent.PRODUCT_SEARCH || intent == ChatIntent.OUTFIT_SUGGEST
                ? extractNlu(session.getId(), content, intent)
                : null;
        ProductContextDto explicitContext = ProductContextDto.builder()
                .productId(productId)
                .colorId(colorId)
                .build();
        ChatContext chatContext = chatContextBuilder.build(content, explicitContext, recentMessages, contextNlu);
        ProductContextDto classificationContext = ProductContextDto.builder()
                .productId(chatContext.getProductId())
                .colorId(chatContext.getColorId())
                .build();
        InternalIntentClassifier.Classification classification = internalIntentClassifier.classify(
                content, classificationContext, recentMessages, chatContext.getOccasionTag(), chatContext.getStyleTag());
        intent = classification.responseIntent();
        chatContext.setInternalIntent(classification.internalIntent());
        chatContext.setResponseIntent(intent);
        chatContext.setQuestionType(classification.questionType());
        log.info("[AI_CHAT_FLOW] userId={} sessionId={} message='{}' productContext={}/{} intent={} recentCount={}",
                userId, session.getId(), shorten(content), productId, colorId, intent, recentMessages.size());

        if (intent == ChatIntent.OUTFIT_SUGGEST) {
            // Ưu tiên productId từ context (user đang xem trang SP)
            // Nếu không có → thử detect SP từ nội dung chat
            Optional<ProductContextDto> resolvedContext = productContextResolver.resolve(content, explicitContext, recentMessages);
            Long resolvedProductId = resolvedContext.map(ProductContextDto::getProductId).orElse(null);
            Long resolvedColorId = resolvedContext.map(ProductContextDto::getColorId).orElse(colorId);
            if (resolvedProductId == null && !productContextResolver.hasPronounReference(content)) {
                String genderHint = firstNonBlank(productTextResolverService.detectGenderHint(content), chatContext.getGender());
                resolvedProductId = productTextResolverService.resolveProductFromMessage(content, genderHint)
                        .map(ProductTextResolverService.ProductMatch::productId)
                        .orElse(null);
                if (resolvedProductId == null && ProductSearchDictionary.productTerms(content).isEmpty()) {
                    resolvedProductId = detectProductFromMessage(content, contextNlu);
                }
            }
            chatContext.setProductId(resolvedProductId);
            chatContext.setColorId(resolvedColorId);
            log.info("[AI_CHAT_OUTFIT] userId={} sessionId={} message='{}' resolvedProductId={} resolvedColorId={}",
                    userId, session.getId(), shorten(content), resolvedProductId, resolvedColorId);
            ChatMessageResponse response = resolvedProductId != null
                    ? outfitChatResponse(resolvedProductId, resolvedColorId, chatContext)
                    : askForMainProductResponse(content);
            messageRepository.save(ChatMessage.builder()
                    .session(session)
                    .role(ChatRole.ASSISTANT)
                    .content(serializeAssistantResponse(response))
                    .metadata(buildAssistantMetadata(response, intent))
                    .intent(intent.name())
                    .hasProducts(response.getOutfitCombos() != null && !response.getOutfitCombos().isEmpty())
                    .build());
            return response;
        }

        if (chatContext.getInternalIntent() == InternalChatIntent.PRODUCT_DETAIL_QA && chatContext.getProductId() != null) {
            ChatMessageResponse response = productDetailStyleResponse(chatContext);
            messageRepository.save(ChatMessage.builder()
                    .session(session)
                    .role(ChatRole.ASSISTANT)
                    .content(serializeAssistantResponse(response))
                    .metadata(buildAssistantMetadata(response, intent))
                    .intent(intent.name())
                    .hasProducts(false)
                    .build());
            return response;
        }

        if ((intent == ChatIntent.ORDER_INQUIRY || intent == ChatIntent.RETURN_SUPPORT) && userId == null) {
            return loginRequiredResponse(intent);
        }

        ProductRetrieverService.ProductSearchResult productResult = null;
        String retrievedData;
        if (intent == ChatIntent.PRODUCT_SEARCH) {
            NluSearchParams nluParams = contextNlu != null ? contextNlu : extractNlu(session.getId(), content, intent);
            try {
            productResult = nluParams != null
                ? productRetrieverService.search(nluParams, content, 6)
                : productRetrieverService.search(content, 6);
            } catch (Exception e) {
            log.error("[AI_CHAT_RETRIEVE_ERROR] userId={} sessionId={} intent={} reason={}",
                userId, session.getId(), intent, e.getMessage());
            ChatMessageResponse response = productRetrieveErrorResponse(intent);
            messageRepository.save(ChatMessage.builder()
                .session(session)
                .role(ChatRole.ASSISTANT)
                .content(serializeAssistantResponse(response))
                .metadata(buildAssistantMetadata(response, intent))
                .intent(intent.name())
                .hasProducts(false)
                .build());
            return response;
            }
            retrievedData = productResult.contextText();
            log.info("[AI_CHAT_RETRIEVE] userId={} sessionId={} intent={} total={} returned={} contextLength={}",
                userId, session.getId(), intent, productResult.total(), productResult.products().size(), retrievedData.length());
            if (isCountOnlyQuestion(content)) {
            ChatMessageResponse response = countOnlyResponse(productResult.total(), intent);
            messageRepository.save(ChatMessage.builder()
                .session(session)
                .role(ChatRole.ASSISTANT)
                .content(serializeAssistantResponse(response))
                .metadata(buildAssistantMetadata(response, intent))
                .intent(intent.name())
                .hasProducts(false)
                .build());
            return response;
            }
            if (productResult.products().isEmpty()) {
            log.info("[AI_CHAT_NO_PRODUCTS] userId={} sessionId={} message='{}' intent={}",
                userId, session.getId(), shorten(content), intent);
            ChatMessageResponse response = noProductsFoundResponse(intent);
            messageRepository.save(ChatMessage.builder()
                .session(session)
                .role(ChatRole.ASSISTANT)
                .content(serializeAssistantResponse(response))
                .metadata(buildAssistantMetadata(response, intent))
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

        // 7. Call Gemini. Failures fall back to deterministic backend responses.
        String aiResponse;
        try {
            aiResponse = aiClientRouter.generate(systemPrompt, history, content);
            log.info("[AI_CHAT_AI_RESPONSE] userId={} sessionId={} intent={} responseLength={} responsePreview='{}'",
                    userId, session.getId(), intent, aiResponse == null ? 0 : aiResponse.length(), shorten(aiResponse));
            log.debug("[AI_RAW_RESPONSE] intent={} length={} preview={}",
                    intent, aiResponse == null ? 0 : aiResponse.length(),
                    aiResponse == null ? "" : aiResponse.substring(0, Math.min(300, aiResponse.length())));
        } catch (Exception e) {
            log.error("AI call failed for user {}: {}", userId, e.getMessage());
            aiResponse = fallbackByIntent(intent);
        }

        // 8. Parse response
        ChatMessageResponse response = parseAiResponse(aiResponse, intent);
        if (productResult != null) {
            response.setProducts(productResult.products());
            response.setSuggestedQuestions(response.getSuggestedQuestions() != null
                    ? response.getSuggestedQuestions()
                    : getDefaultSuggestions(intent));
        }
        logParsedResponse(userId, session.getId(), intent, response, productResult);

        // 9. Save assistant message
        ChatMessage assistantMsg = ChatMessage.builder()
                .session(session)
                .role(ChatRole.ASSISTANT)
                .content(serializeAssistantResponse(response))
                .metadata(buildAssistantMetadata(response, intent))
                .intent(intent.name())
                .hasProducts((response.getProducts() != null && !response.getProducts().isEmpty())
                        || (response.getOutfitCombos() != null && !response.getOutfitCombos().isEmpty()))
                .build();
        messageRepository.save(assistantMsg);

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

    private boolean isCountOnlyQuestion(String value) {
        String normalized = normalizeVi(value);
        return normalized.contains(" bao nhieu ")
                || normalized.contains(" may san pham ")
                || normalized.contains(" so luong ")
                || normalized.contains(" co bao nhieu ");
    }

    private String normalizeVi(String input) {
        if (input == null) {
            return "";
        }
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase(Locale.ROOT)
                .replace('-', ' ')
                .trim();
        return " " + normalized + " ";
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

    private ChatMessageResponse outfitChatResponse(Long productId, Long colorId, ChatContext chatContext) {
        OutfitSuggestionResponse outfit = outfitSuggestionService.getSuggestions(productId, colorId, chatContext);
        ChatProductCard anchor = productRetrieverService.findProductCard(productId, colorId).orElse(null);
        StyleAnswerResult styleAnswer = styleAnswerComposer.compose(chatContext, anchor, outfit.getCombos());
        return ChatMessageResponse.builder()
                .role("assistant")
                .content(outfit.getText() != null ? outfit.getText() : "Mình gợi ý các outfit phù hợp với sản phẩm bạn đang xem.")
                .intent(ChatIntent.OUTFIT_SUGGEST.name())
                .outfitCombos(outfit.getCombos())
                .products(List.of())
                .productContext(ProductContextDto.builder().productId(productId).colorId(colorId).build())
                .content(styleAnswer.getContent())
                .styleTips(styleAnswer.getStyleTips())
                .context(toContextDto(chatContext))
                .suggestedQuestions(styleAnswer.getSuggestedQuestions())
                .suggestedQuestions(List.of("Gợi ý outfit khác", "Tìm sản phẩm tương tự"))
                .createdAt(LocalDateTime.now())
                .build();
    }

    private ChatMessageResponse productDetailStyleResponse(ChatContext chatContext) {
        ChatProductCard anchor = productRetrieverService.findProductCard(chatContext.getProductId(), chatContext.getColorId())
                .orElse(null);
        StyleAnswerResult answer = styleAnswerComposer.compose(chatContext, anchor, List.of());
        return ChatMessageResponse.builder()
                .role("assistant")
                .content(answer.getContent())
                .intent(ChatIntent.GENERAL_SUPPORT.name())
                .products(anchor != null ? List.of(anchor) : List.of())
                .styleTips(answer.getStyleTips())
                .context(toContextDto(chatContext))
                .suggestedQuestions(answer.getSuggestedQuestions())
                .createdAt(LocalDateTime.now())
                .build();
    }

    private ChatMessageResponse boundaryFallbackResponse() {
        return ChatMessageResponse.builder()
                .role("assistant")
                .content("Xin lỗi, mình chưa xử lý được yêu cầu này ngay lúc này. Bạn thử lại hoặc mô tả ngắn hơn nhé.")
                .intent(ChatIntent.GENERAL_SUPPORT.name())
                .products(List.of())
                .outfitCombos(List.of())
                .isFromFallback(true)
                .suggestedQuestions(List.of("Tìm áo thun nam", "Gợi ý outfit đi làm", "Thử lại"))
                .createdAt(LocalDateTime.now())
                .build();
    }

    private ChatContextDto toContextDto(ChatContext context) {
        return ChatContextDto.builder()
                .internalIntent(context.getInternalIntent())
                .occasionTag(context.getOccasionTag())
                .occasionLabel(context.getOccasionLabel())
                .styleTag(context.getStyleTag())
                .season(context.getSeason())
                .gender(context.getGender())
                .productId(context.getProductId())
                .colorId(context.getColorId())
                .productType(context.getProductType())
                .build();
    }

    private ChatMessageResponse askForMainProductResponse(String content) {
        List<ChatProductCard> suggestions = List.of();
        try {
            ProductRetrieverService.ProductSearchResult result = productRetrieverService.search(content, 5);
            suggestions = result.products();
        } catch (Exception e) {
            log.warn("[AI_CHAT_OUTFIT_CLARIFY] suggestion lookup failed: {}", e.getMessage());
        }
        return ChatMessageResponse.builder()
                .role("assistant")
                .content("Mình chưa xác định chính xác sản phẩm bạn muốn phối. Bạn có thể chọn một sản phẩm trong danh sách dưới đây hoặc gửi lại tên sản phẩm ngắn hơn.")
                .intent(ChatIntent.OUTFIT_SUGGEST.name())
                .products(suggestions)
                .suggestedQuestions(List.of("Phối sản phẩm đầu tiên", "Tìm sản phẩm khác cùng loại"))
                .createdAt(LocalDateTime.now())
                .build();
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private ChatMessageResponse productRetrieveErrorResponse(ChatIntent intent) {
        return ChatMessageResponse.builder()
                .role("assistant")
                .content("Xin lỗi, mình đang gặp lỗi khi tìm sản phẩm. Bạn thử lại sau ít phút nhé.")
                .intent(intent.name())
                .products(List.of())
                .outfitCombos(List.of())
                .suggestedQuestions(getDefaultSuggestions(intent))
                .createdAt(LocalDateTime.now())
                .build();
    }

    private ChatMessageResponse noProductsFoundResponse(ChatIntent intent) {
        return ChatMessageResponse.builder()
                .role("assistant")
                .content("Hiện shop chưa có sản phẩm phù hợp với tiêu chí này. Bạn thử tăng ngân sách, đổi màu hoặc chọn danh mục gần hơn nhé.")
                .intent(intent.name())
                .totalCount(0)
                .products(List.of())
                .suggestedQuestions(List.of("Tìm áo thun nam màu đen", "Tìm váy nữ đi làm", "Xem sản phẩm đang sale"))
                .createdAt(LocalDateTime.now())
                .build();
    }

    private ChatMessageResponse countOnlyResponse(long total, ChatIntent intent) {
        return ChatMessageResponse.builder()
                .role("assistant")
                .content("Shop hiá»‡n cÃ³ " + total + " sáº£n pháº©m phÃ¹ há»£p vÃ  cÃ²n hÃ ng trong dá»¯ liá»‡u hiá»‡n táº¡i.")
                .intent(intent.name())
                .totalCount((int) Math.min(total, Integer.MAX_VALUE))
                .countType("PRODUCT_COLOR")
                .products(List.of())
                .outfitCombos(List.of())
                .suggestedQuestions(List.of("Xem má»™t vÃ i máº«u phÃ¹ há»£p", "TÃ¬m theo mÃ u khÃ¡c", "TÆ° váº¥n phá»‘i Ä‘á»“"))
                .createdAt(LocalDateTime.now())
                .build();
    }

    /**
     * Phân tích tin nhắn chat để tìm SP phù hợp nhất làm nền phối đồ.
     * VD: "áo thun đen mặc với gì?" → tìm SP áo thun màu đen đầu tiên trong DB.
     * Trả null nếu không tìm thấy SP nào.
     */
    private NluSearchParams extractNlu(Long sessionId, String content, ChatIntent intent) {
        NluSearchParams previous = lastNluParams.getIfPresent(sessionId);
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
                    ? productRetrieverService.search(nluParams, content, 1)
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
            case OUT_OF_SCOPE    -> "Fashi chỉ tư vấn về sản phẩm và phong cách trong Fashion Shop thôi bạn ơi. Bạn muốn mình giúp tìm outfit hay sản phẩm nào không?";
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
     * Fix products=0: extract products[] từ AI JSON và hydrate từ DB.
     */
    private ChatMessageResponse parseAiResponse(String aiResponse, ChatIntent intent) {
        // Thử parse JSON
        if (intent == ChatIntent.PRODUCT_SEARCH || intent == ChatIntent.OUTFIT_SUGGEST) {
            try {
                String cleaned = extractJsonPayload(aiResponse);

                JsonNode json = objectMapper.readTree(cleaned);

                String text = json.has("text") ? json.get("text").asText() : aiResponse;
                List<String> suggestedQuestions = new ArrayList<>();

                if (json.has("suggestedQuestions") && json.get("suggestedQuestions").isArray()) {
                    for (JsonNode q : json.get("suggestedQuestions")) {
                        suggestedQuestions.add(q.asText());
                    }
                }

                // Extract products[] từ AI JSON response — fix products=0 bug
                List<ChatProductCard> aiProducts = new ArrayList<>();
                if (json.has("products") && json.get("products").isArray()) {
                    for (JsonNode p : json.get("products")) {
                        Long pid = null;
                        Long cid = null;
                        if (p.has("id")) pid = p.get("id").asLong();
                        else if (p.has("productId")) pid = p.get("productId").asLong();
                        if (p.has("colorId")) cid = p.get("colorId").asLong();
                        if (pid != null) {
                            try {
                                productRetrieverService.findProductCard(pid, cid)
                                    .ifPresent(aiProducts::add);
                            } catch (Exception ignored) {
                                log.debug("[AI_CHAT_PARSE] skip productId={} reason=not_found", pid);
                            }
                        }
                    }
                    log.info("[AI_CHAT_PARSED] intent={} ai_products_parsed={} hydrated={}",
                        intent, json.get("products").size(), aiProducts.size());
                }

                ChatMessageResponse resp = ChatMessageResponse.builder()
                        .role("assistant")
                        .content(text)
                        .suggestedQuestions(suggestedQuestions.isEmpty() ? null : suggestedQuestions)
                        .intent(intent.name())
                        .createdAt(LocalDateTime.now())
                        .build();

                // Nếu AI trả products[] → dùng products từ AI (đã hydrate)
                if (!aiProducts.isEmpty()) {
                    resp.setProducts(aiProducts);
                }

                return resp;
            } catch (Exception e) {
                log.info("[AI_CHAT_PARSE_FALLBACK] intent={} error={} responsePreview='{}'",
                        intent, e.getMessage(), shorten(aiResponse));
                log.debug("[AI_CHAT_RAW_RESPONSE] intent={} raw={}", intent, aiResponse);
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
            case OUT_OF_SCOPE -> "Fashi chỉ tư vấn về sản phẩm và phong cách trong Fashion Shop. Bạn muốn mình giúp gì không?";
        };
    }

    private List<String> getDefaultSuggestions(ChatIntent intent) {
        return switch (intent) {
            case PRODUCT_SEARCH -> List.of("Xem thêm sản phẩm khác", "Tư vấn phối đồ");
            case OUTFIT_SUGGEST -> List.of("Gợi ý outfit khác", "Tìm sản phẩm cụ thể");
            case ORDER_INQUIRY -> List.of("Xem đơn hàng khác", "Chính sách đổi trả");
            case RETURN_SUPPORT -> List.of("Hướng dẫn đổi/trả hoặc khiếu nại", "Liên hệ hỗ trợ");
            case GENERAL_SUPPORT -> List.of("Tìm sản phẩm", "Tư vấn size");
            case CHITCHAT -> List.of("Tìm áo thun nam", "Gợi ý outfit đi chơi", "Xem đơn hàng");
            case OUT_OF_SCOPE -> List.of("Tìm sản phẩm mới", "Tư vấn phối đồ", "Xem chính sách shop");
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

    private String buildAssistantMetadata(ChatMessageResponse response, ChatIntent intent) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("intent", intent.name());

            List<ChatProductCard> products = response.getProducts() != null ? response.getProducts() : List.of();
            if (!products.isEmpty()) {
                root.set("primaryProductContext", productContextNode(products.get(0)));
                ArrayNode items = objectMapper.createArrayNode();
                for (ChatProductCard product : products) {
                    items.add(productContextNode(product));
                }
                root.set("products", items);
            }

            if (response.getProductContext() != null) {
                root.set("baseProductContext", productContextNode(response.getProductContext()));
            } else if (response.getOutfitCombos() != null && !response.getOutfitCombos().isEmpty()) {
                List<ChatProductCard> comboProducts = response.getOutfitCombos().get(0).getProducts() != null
                        ? response.getOutfitCombos().get(0).getProducts()
                        : response.getOutfitCombos().get(0).getItems();
                if (comboProducts != null && !comboProducts.isEmpty()) {
                    root.set("baseProductContext", productContextNode(comboProducts.get(0)));
                }
            }

            return root.size() > 1 ? objectMapper.writeValueAsString(root) : null;
        } catch (Exception e) {
            log.warn("[AI_CHAT_METADATA] build failed: {}", e.getMessage());
            return null;
        }
    }

    private ObjectNode productContextNode(ChatProductCard product) {
        ObjectNode node = objectMapper.createObjectNode();
        if (product.getId() != null) node.put("productId", product.getId());
        if (product.getColorId() != null) node.put("colorId", product.getColorId());
        if (product.getName() != null) node.put("name", product.getName());
        if (product.getColorName() != null) node.put("colorName", product.getColorName());
        if (product.getCategoryName() != null) node.put("categoryName", product.getCategoryName());
        if (product.getCategorySlug() != null) node.put("categorySlug", product.getCategorySlug());
        if (product.getCategoryRole() != null) node.put("categoryRole", product.getCategoryRole());
        if (product.getParentCategoryName() != null) node.put("parentCategoryName", product.getParentCategoryName());
        return node;
    }

    private ObjectNode productContextNode(ProductContextDto context) {
        ObjectNode node = objectMapper.createObjectNode();
        if (context.getProductId() != null) node.put("productId", context.getProductId());
        if (context.getColorId() != null) node.put("colorId", context.getColorId());
        if (context.getName() != null) node.put("name", context.getName());
        if (context.getColorName() != null) node.put("colorName", context.getColorName());
        if (context.getCategoryName() != null) node.put("categoryName", context.getCategoryName());
        if (context.getCategorySlug() != null) node.put("categorySlug", context.getCategorySlug());
        if (context.getCategoryRole() != null) node.put("categoryRole", context.getCategoryRole());
        if (context.getParentCategoryName() != null) node.put("parentCategoryName", context.getParentCategoryName());
        return node;
    }

    private void logParsedResponse(Long userId, Long sessionId, ChatIntent intent, ChatMessageResponse response,
                                   ProductRetrieverService.ProductSearchResult productResult) {
        int finalProducts = response.getProducts() == null ? 0 : response.getProducts().size();
        int outfits = response.getOutfitCombos() == null ? 0 : response.getOutfitCombos().size();
        int suggestions = response.getSuggestedQuestions() == null ? 0 : response.getSuggestedQuestions().size();
        if (sessionId == null) {
            log.info("[AI_CHAT_PARSED] guest intent={} finalProducts={} dbProducts={} outfits={} suggestions={}",
                    intent, finalProducts, productResult == null ? null : productResult.products().size(), outfits, suggestions);
            return;
        }
        log.info("[AI_CHAT_PARSED] userId={} sessionId={} intent={} finalProducts={} dbProducts={} outfits={} suggestions={}",
                userId, sessionId, intent, finalProducts, productResult == null ? null : productResult.products().size(), outfits, suggestions);
    }

    private String extractJsonPayload(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("AI response is blank");
        }
        String cleaned = value.trim();
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
        return extractFirstBalancedJson(cleaned);
    }

    private String extractFirstBalancedJson(String value) {
        int start = -1;
        char open = 0;
        char close = 0;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '{' || ch == '[') {
                start = i;
                open = ch;
                close = ch == '{' ? '}' : ']';
                break;
            }
        }
        if (start < 0) {
            throw new IllegalArgumentException("No JSON object found in AI response");
        }
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (ch == '\\') {
                escaped = true;
                continue;
            }
            if (ch == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (ch == open) {
                depth++;
            } else if (ch == close) {
                depth--;
                if (depth == 0) {
                    return value.substring(start, i + 1);
                }
            }
        }
        throw new IllegalArgumentException("Unbalanced JSON payload in AI response");
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
