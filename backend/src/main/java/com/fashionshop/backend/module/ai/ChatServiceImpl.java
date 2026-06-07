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
import java.util.Set;
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
import com.fashionshop.backend.module.ai.dto.response.OutfitComboResponse;
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

        long startedAt = System.currentTimeMillis();
        // 4. Classify intent. Gemini NLU is used only for ambiguous requests.
        IntentClassifier.ClassificationResult externalClassification = intentClassifier.classifyDetailed(content, recentMessages);
        ChatIntent intent = externalClassification.intent();
        NluSearchParams contextNlu = null;
        boolean geminiNluUsed = false;
        ProductContextDto explicitContext = ProductContextDto.builder()
                .productId(productId)
                .colorId(colorId)
                .build();
        ChatContext chatContext = chatContextBuilder.build(content, explicitContext, recentMessages, null);
        ProductContextDto classificationContext = ProductContextDto.builder()
                .productId(chatContext.getProductId())
                .colorId(chatContext.getColorId())
                .build();
        InternalIntentClassifier.Classification classification = internalIntentClassifier.classify(
                content, classificationContext, recentMessages, chatContext.getOccasionTag(), chatContext.getStyleTag(), intent);
        if (shouldUseGeminiNlu(classification.internalIntent(), intent, content, recentMessages)) {
            contextNlu = extractNlu(session.getId(), content, intent);
            geminiNluUsed = contextNlu != null;
            if (contextNlu != null) {
                chatContext = chatContextBuilder.build(content, explicitContext, recentMessages, contextNlu);
                classificationContext = ProductContextDto.builder()
                        .productId(chatContext.getProductId())
                        .colorId(chatContext.getColorId())
                        .build();
                classification = internalIntentClassifier.classify(
                        content, classificationContext, recentMessages, chatContext.getOccasionTag(), chatContext.getStyleTag(), intent);
            }
        }
        intent = classification.responseIntent();
        chatContext.setInternalIntent(classification.internalIntent());
        chatContext.setResponseIntent(intent);
        chatContext.setQuestionType(classification.questionType());
        log.info("[AI_CHAT_FLOW] userId={} sessionId={} externalIntent={} externalReason={} normalizedMessage='{}' internalIntentBeforeFallback={} smalltalkBlockedByFashionSignal={} finalInternalIntent={} geminiNluUsed={} functionCalled={} message='{}' recentCount={}",
                userId, session.getId(), externalClassification.intent(), externalClassification.reason(),
                externalClassification.normalizedMessage(), classification.internalIntentBeforeFallback(),
                classification.smalltalkBlockedByFashionSignal(), chatContext.getInternalIntent(), geminiNluUsed,
                functionName(chatContext.getInternalIntent()), shorten(content), recentMessages.size());

        if (isFastPath(chatContext.getInternalIntent()) && intent != ChatIntent.ORDER_INQUIRY && intent != ChatIntent.RETURN_SUPPORT) {
            ChatMessageResponse response = fastPathResponse(chatContext, intent);
            saveAssistantMessage(session, response, intent);
            logChatDone(userId, session.getId(), chatContext, response, null, null, startedAt);
            return response;
        }

        if (chatContext.getInternalIntent() == InternalChatIntent.OUTFIT_BY_OCCASION) {
            ChatMessageResponse response = occasionOutfitResponse(content, contextNlu, chatContext);
            saveAssistantMessage(session, response, intent);
            logChatDone(userId, session.getId(), chatContext, response, null, "recommendByOccasion", startedAt);
            return response;
        }

        if (intent == ChatIntent.OUTFIT_SUGGEST) {
            boolean categoryAttributeOutfit = hasCategoryOrAttributeOutfitSignal(content);
            // Ưu tiên productId từ context (user đang xem trang SP)
            // Nếu không có → thử detect SP từ nội dung chat
            Optional<ProductContextDto> resolvedContext = productContextResolver.resolve(content, explicitContext, recentMessages);
            Long resolvedProductId = resolvedContext.map(ProductContextDto::getProductId).orElse(null);
            Long resolvedColorId = resolvedContext.map(ProductContextDto::getColorId).orElse(colorId);
            if (resolvedProductId == null && !categoryAttributeOutfit && !productContextResolver.hasPronounReference(content)) {
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
                    : (categoryAttributeOutfit
                        ? categoryAttributeOutfitResponse(content, contextNlu, chatContext)
                        : askForMainProductResponse(content));
            saveAssistantMessage(session, response, intent);
            String outfitFunction = resolvedProductId != null
                    ? "suggestOutfitByProduct"
                    : (categoryAttributeOutfit ? "suggestOutfitByCategoryOrAttributes" : "askForMainProduct");
            logChatDone(userId, session.getId(), chatContext, response, null,
                    outfitFunction, startedAt);
            return response;
        }

        if (chatContext.getInternalIntent() == InternalChatIntent.PRODUCT_DETAIL_QA && chatContext.getProductId() != null) {
            ChatMessageResponse response = productDetailStyleResponse(chatContext);
            saveAssistantMessage(session, response, intent);
            logChatDone(userId, session.getId(), chatContext, response, null, "getProductDetail", startedAt);
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
            boolean exactMatchFound = productResult.total() > 0 || !productResult.products().isEmpty();
            if (!exactMatchFound) {
            log.info("[AI_CHAT_NO_PRODUCTS] userId={} sessionId={} message='{}' intent={}",
                userId, session.getId(), shorten(content), intent);
            ProductRetrieverService.ProductSearchResult similarResult =
                productRetrieverService.searchSimilarByRole(content, nluParams, 6);
            String requestedKeyword = requestedKeyword(content);
            ChatMessageResponse response = noProductsFoundResponse(intent, requestedKeyword, similarResult);
            response.setInternalIntent(chatContext.getInternalIntent().name());
            logSearchStatus(userId, session.getId(), requestedKeyword, productResult, similarResult, response, "NO_EXACT_MATCH");
            saveAssistantMessage(session, response, intent);
            logChatDone(userId, session.getId(), chatContext, response, similarResult, "searchSimilarByRole",
                    requestedKeyword, null, startedAt);
            return response;
            }
            retrievedData = withSearchStatus("TYPE_MATCH", requestedKeyword(content), productResult, null, retrievedData);
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
            RerankResult rerankResult = applyGeminiProductRerank(aiResponse, productResult.products());
            response.setProducts(rerankResult.products());
            response.setSuggestedQuestions(response.getSuggestedQuestions() != null
                    ? response.getSuggestedQuestions()
                    : getDefaultSuggestions(intent));
            boolean contradiction = guardProductSearchText(response, productResult, "TYPE_MATCH");
            response.setSearchStatus("TYPE_MATCH");
            response.setInternalIntent(chatContext.getInternalIntent().name());
            logSearchStatus(userId, session.getId(), requestedKeyword(content), productResult, null, response,
                    contradiction ? "TYPE_MATCH_AI_CONTRADICTION"
                            : (rerankResult.geminiUsed() ? "TYPE_MATCH_GEMINI_RERANK" : "TYPE_MATCH"));
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
        logChatDone(userId, session.getId(), chatContext, response, productResult, null,
                productResult == null ? null : requestedKeyword(content), null, startedAt);

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

    private boolean shouldUseGeminiNlu(InternalChatIntent internalIntent, ChatIntent baseIntent, String content,
                                       List<ChatMessage> recentMessages) {
        if (Set.of(InternalChatIntent.SMALLTALK, InternalChatIntent.SHOP_POLICY, InternalChatIntent.OUT_OF_SCOPE)
                .contains(internalIntent)) {
            return false;
        }
        if (isClearProductSearch(content) || isClearProductOutfit(content)) {
            return false;
        }
        boolean followUp = content != null && content.length() <= 40 && recentMessages != null && !recentMessages.isEmpty();
        return followUp
                || baseIntent == ChatIntent.PRODUCT_SEARCH
                || baseIntent == ChatIntent.OUTFIT_SUGGEST
                || internalIntent == InternalChatIntent.STYLE_ADVICE;
    }

    private boolean isFastPath(InternalChatIntent intent) {
        return intent == InternalChatIntent.SMALLTALK
                || intent == InternalChatIntent.SHOP_POLICY
                || intent == InternalChatIntent.OUT_OF_SCOPE;
    }

    private boolean isClearProductSearch(String content) {
        String normalized = normalizeVi(content);
        return ProductSearchDictionary.productTerms(content).size() > 0
                && containsAny(normalized, " tim ", " co ", " mua ", " can ", " cho toi ")
                && !isClearProductOutfit(content);
    }

    private boolean isClearProductOutfit(String content) {
        String normalized = normalizeVi(content);
        return ProductSearchDictionary.productTerms(content).size() > 0
                && containsAny(normalized, " mac voi ", " phoi ", " mix ", " outfit ");
    }

    private ChatMessageResponse fastPathResponse(ChatContext context, ChatIntent intent) {
        InternalChatIntent internalIntent = context.getInternalIntent();
        if (internalIntent == InternalChatIntent.OUT_OF_SCOPE) {
            return ChatMessageResponse.builder()
                    .role("assistant")
                    .content(defaultTextForIntent(ChatIntent.OUT_OF_SCOPE))
                    .intent(ChatIntent.OUT_OF_SCOPE.name())
                    .suggestedQuestions(getDefaultSuggestions(ChatIntent.OUT_OF_SCOPE))
                    .isFromFallback(true)
                    .context(toContextDto(context))
                    .createdAt(LocalDateTime.now())
                    .build();
        }
        String content = switch (internalIntent) {
            case SMALLTALK -> "Chào bạn, mình có thể giúp tìm sản phẩm, tư vấn phối đồ hoặc giải đáp chính sách của Fashion Shop.";
            case SHOP_POLICY -> defaultTextForIntent(intent);
            default -> defaultTextForIntent(intent);
        };
        return ChatMessageResponse.builder()
                .role("assistant")
                .content(content)
                .intent(intent.name())
                .suggestedQuestions(getDefaultSuggestions(intent))
                .isFromFallback(internalIntent == InternalChatIntent.OUT_OF_SCOPE)
                .context(toContextDto(context))
                .createdAt(LocalDateTime.now())
                .build();
    }

    private boolean containsAny(String value, String... tokens) {
        for (String token : tokens) {
            if (value.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private void saveAssistantMessage(ChatSession session, ChatMessageResponse response, ChatIntent intent) {
        normalizeResponse(response);
        messageRepository.save(ChatMessage.builder()
                .session(session)
                .role(ChatRole.ASSISTANT)
                .content(serializeAssistantResponse(response))
                .metadata(buildAssistantMetadata(response, intent))
                .intent(intent.name())
                .hasProducts(!response.getProducts().isEmpty() || !response.getOutfitCombos().isEmpty())
                .build());
    }

    private void logChatDone(Long userId, Long sessionId, ChatContext context, ChatMessageResponse response,
                             ProductRetrieverService.ProductSearchResult productResult, String functionCalled,
                             long startedAt) {
        logChatDone(userId, sessionId, context, response, productResult, functionCalled, null, null, startedAt);
    }

    private void logChatDone(Long userId, Long sessionId, ChatContext context, ChatMessageResponse response,
                             ProductRetrieverService.ProductSearchResult productResult, String functionCalled,
                             String requestedKeyword, String fallbackReason, long startedAt) {
        String resolvedFunction = functionCalled != null ? functionCalled : functionName(context.getInternalIntent());
        int finalProducts = response.getProducts() == null ? 0 : response.getProducts().size();
        int outfitCombos = response.getOutfitCombos() == null ? 0 : response.getOutfitCombos().size();
        boolean roleSuggestionFlow = "searchSimilarByRole".equals(resolvedFunction);
        Integer exactReturned = productResult == null ? null : (roleSuggestionFlow ? 0 : safeProducts(productResult).size());
        Integer roleSuggestionReturned = productResult == null ? null : (roleSuggestionFlow ? safeProducts(productResult).size() : null);
        String resolvedFallbackReason = fallbackReason;
        if (resolvedFallbackReason == null && Boolean.TRUE.equals(response.getIsFromFallback())) {
            resolvedFallbackReason = "responseFallback";
        }
        log.info("[AI_CHAT_DONE] userId={} sessionId={} externalIntent={} internalIntent={} functionCalled={} requestedKeyword={} searchStatus={} exactReturned={} roleSuggestionReturned={} finalProducts={} outfitCombos={} fallbackReason={} latencyMs={}",
                userId, sessionId, response.getIntent(), context.getInternalIntent(),
                resolvedFunction,
                requestedKeyword,
                response.getSearchStatus(),
                exactReturned,
                roleSuggestionReturned,
                finalProducts,
                outfitCombos,
                resolvedFallbackReason,
                System.currentTimeMillis() - startedAt);
    }

    private void logSearchStatus(Long userId, Long sessionId, String requestedKeyword,
                                 ProductRetrieverService.ProductSearchResult exactResult,
                                 ProductRetrieverService.ProductSearchResult similarResult, ChatMessageResponse response,
                                 String statusDetail) {
        String searchStatus = searchStatus(exactResult, similarResult);
        log.info("[AI_PRODUCT_SEARCH_STATUS] userId={} sessionId={} requestedKeyword='{}' normalizedQuery='{}' exactTotal={} exactReturned={} inferredRole={} roleSuggestionTotal={} roleSuggestionReturned={} searchStatus={} statusDetail={} finalProducts={} geminiTextOverridden={} overrideReason={} fallbackLevel={}",
                userId,
                sessionId,
                requestedKeyword,
                normalizeVi(requestedKeyword).trim(),
                exactResult == null ? null : exactResult.total(),
                exactResult == null || exactResult.products() == null ? null : exactResult.products().size(),
                similarResult == null ? null : similarResult.inferredRole(),
                similarResult == null ? null : similarResult.total(),
                similarResult == null || similarResult.products() == null ? null : similarResult.products().size(),
                searchStatus,
                statusDetail,
                response == null || response.getProducts() == null ? 0 : response.getProducts().size(),
                statusDetail != null && statusDetail.contains("AI_CONTRADICTION"),
                statusDetail != null && statusDetail.contains("AI_CONTRADICTION") ? statusDetail : null,
                similarResult == null ? null : similarResult.fallbackLevel());
    }

    private String searchStatus(ProductRetrieverService.ProductSearchResult exactResult,
                                ProductRetrieverService.ProductSearchResult similarResult) {
        if (exactResult != null && (exactResult.total() > 0 || !safeProducts(exactResult).isEmpty())) {
            return "TYPE_MATCH";
        }
        if (similarResult != null && !safeProducts(similarResult).isEmpty()) {
            return "NEAR_ROLE_FALLBACK";
        }
        return "NO_MATCH";
    }

    private String withSearchStatus(String searchStatus, String requestedKeyword,
                                    ProductRetrieverService.ProductSearchResult exactResult,
                                    ProductRetrieverService.ProductSearchResult similarResult, String retrievedData) {
        return """
            Search status: %s
            Exact total: %s
            Exact returned: %s
            Role suggestion total: %s
            Role suggestion returned: %s
            Inferred role: %s
            Role label: %s
            Requested keyword: %s
            Fallback level: %s
            Provided products: backend-selected products only
            Guard rules:
            - If Search status is TYPE_MATCH, never say the shop does not have matching products.
            - If Search status is NEAR_ROLE_FALLBACK, say exact products were not found but same-role products are shown.
            - Gemini may only phrase the answer or return selectedProductIds chosen from the candidate list.
            - Gemini must not decide availability or invent products.
            - Only recommend products listed below.
            - If returning JSON, selectedProductIds must be a subset of the product IDs below. Unknown IDs will be ignored by backend validation.

            %s
            """.formatted(
                searchStatus,
                exactResult == null ? 0 : exactResult.total(),
                exactResult == null ? 0 : safeProducts(exactResult).size(),
                similarResult == null ? 0 : similarResult.total(),
                similarResult == null ? 0 : safeProducts(similarResult).size(),
                similarResult == null ? "" : similarResult.inferredRole(),
                roleLabel(similarResult == null ? null : similarResult.inferredRole()),
                requestedKeyword,
                similarResult == null ? "" : similarResult.fallbackLevel(),
                retrievedData == null ? "" : retrievedData
            );
    }

    private boolean guardProductSearchText(ChatMessageResponse response,
                                           ProductRetrieverService.ProductSearchResult productResult,
                                           String searchStatus) {
        if (response == null || productResult == null || searchStatus == null) {
            return false;
        }
        if (("TYPE_MATCH".equals(searchStatus) || "EXACT_MATCH_FOUND".equals(searchStatus))
                && !safeProducts(productResult).isEmpty()
                && containsNegativeAvailabilityText(response.getContent())) {
            response.setContent("Có bạn nhé. Shop hiện có một số sản phẩm phù hợp với yêu cầu của bạn, mình gửi bên dưới để bạn tham khảo.");
            response.setIsFromFallback(true);
            return true;
        }
        if (response.getProducts() != null
                && response.getProducts().isEmpty()
                && containsProductPromiseText(response.getContent())) {
            response.setContent(noMatchText());
            response.setIsFromFallback(true);
            return true;
        }
        return false;
    }

    private boolean containsNegativeAvailabilityText(String value) {
        String normalized = normalizeVi(value);
        return containsAny(normalized, " khong co ", " chua co ", " khong tim thay ", " khong the tim ", " rat tiec ");
    }

    private boolean containsProductPromiseText(String value) {
        String normalized = normalizeVi(value);
        return containsAny(normalized, " co ban nhe ", " gui ben duoi ", " san pham duoi day ",
                " cac mau duoi day ", " tham khao cac mau ", " minh gui ");
    }

    private String noMatchText() {
        return "Hiện shop chưa tìm thấy sản phẩm phù hợp với yêu cầu này. Bạn có thể thử tìm theo từ khóa rộng hơn hoặc chọn danh mục khác.";
    }

    private List<ChatProductCard> safeProducts(ProductRetrieverService.ProductSearchResult result) {
        return result == null || result.products() == null ? List.of() : result.products();
    }

    private RerankResult applyGeminiProductRerank(String aiResponse, List<ChatProductCard> backendCandidates) {
        List<ChatProductCard> candidates = backendCandidates == null ? List.of() : backendCandidates;
        if (candidates.isEmpty()) {
            return new RerankResult(List.of(), false);
        }
        List<Long> selectedIds = extractSelectedProductIds(aiResponse);
        if (selectedIds.isEmpty()) {
            log.info("[AI_GEMINI_RERANK] candidateIds={} selectedIds=[] invalidSelectedIds=[] fallbackUsed=true",
                    productIds(candidates));
            return new RerankResult(candidates, false);
        }

        Set<Long> candidateIds = candidates.stream()
                .map(ChatProductCard::getId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        List<Long> invalidIds = selectedIds.stream()
                .filter(id -> !candidateIds.contains(id))
                .toList();
        if (!invalidIds.isEmpty()) {
            log.info("[AI_GEMINI_RERANK] candidateIds={} selectedIds={} invalidSelectedIds={} fallbackUsed=true",
                    candidateIds, selectedIds, invalidIds);
            return new RerankResult(candidates, false);
        }

        Map<Long, ChatProductCard> byProductId = new LinkedHashMap<>();
        for (ChatProductCard candidate : candidates) {
            if (candidate.getId() != null) {
                byProductId.putIfAbsent(candidate.getId(), candidate);
            }
        }
        List<ChatProductCard> reranked = selectedIds.stream()
                .distinct()
                .map(byProductId::get)
                .filter(java.util.Objects::nonNull)
                .toList();
        if (reranked.isEmpty()) {
            log.info("[AI_GEMINI_RERANK] candidateIds={} selectedIds={} invalidSelectedIds=[] fallbackUsed=true",
                    candidateIds, selectedIds);
            return new RerankResult(candidates, false);
        }
        log.info("[AI_GEMINI_RERANK] candidateIds={} selectedIds={} invalidSelectedIds=[] fallbackUsed=false",
                candidateIds, selectedIds);
        return new RerankResult(reranked, true);
    }

    private List<Long> extractSelectedProductIds(String aiResponse) {
        if (aiResponse == null || aiResponse.isBlank() || !looksLikeJson(aiResponse)) {
            return List.of();
        }
        try {
            JsonNode json = objectMapper.readTree(extractJsonPayload(aiResponse));
            JsonNode selected = json.get("selectedProductIds");
            if (selected == null || !selected.isArray()) {
                return List.of();
            }
            List<Long> ids = new ArrayList<>();
            for (JsonNode node : selected) {
                if (node.canConvertToLong()) {
                    ids.add(node.asLong());
                }
            }
            return ids;
        } catch (Exception e) {
            log.info("[AI_GEMINI_RERANK] parse_failed reason={} fallbackUsed=true", e.getMessage());
            return List.of();
        }
    }

    private List<Long> productIds(List<ChatProductCard> products) {
        if (products == null) {
            return List.of();
        }
        return products.stream()
                .map(ChatProductCard::getId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
    }

    private String functionName(InternalChatIntent intent) {
        if (intent == null) {
            return "unknown";
        }
        return switch (intent) {
            case PRODUCT_SEARCH -> "searchProducts";
            case PRODUCT_DETAIL_QA -> "resolveProductByName/getProductDetail";
            case OUTFIT_BY_PRODUCT -> "suggestOutfitByProduct";
            case OUTFIT_BY_OCCASION -> "recommendByOccasion";
            case OUTFIT_BY_PRODUCT_AND_OCCASION -> "suggestOutfitByProductAndOccasion";
            case STYLE_ADVICE -> "buildStyleAdvice";
            case SHOP_POLICY -> "answerShopPolicy";
            case SMALLTALK -> "smalltalkFastResponse";
            case OUT_OF_SCOPE -> "politeRefusal";
        };
    }

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
                .suggestedQuestions(List.of("Gợi ý phối đồ với sản phẩm này", "Tìm sản phẩm tương tự"))
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
                .internalIntent(InternalChatIntent.PRODUCT_DETAIL_QA.name())
                .searchStatus("PRODUCT_CONTEXT_MATCH")
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
                .suggestedQuestions(List.of("Tìm áo thun nam", "Gợi ý phối đồ với sản phẩm này", "Thử lại"))
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

    private ChatMessageResponse categoryAttributeOutfitResponse(String content, NluSearchParams nluParams, ChatContext context) {
        ProductRetrieverService.ProductSearchResult baseResult =
                productRetrieverService.searchOutfitBaseCandidates(content, nluParams, 3);
        List<ChatProductCard> baseProducts = baseResult.products();
        if (baseProducts.isEmpty()) {
            return ChatMessageResponse.builder()
                    .role("assistant")
                    .content("Mình chưa tìm thấy sản phẩm nền phù hợp với yêu cầu này trong shop. Bạn có thể thử chọn một sản phẩm cụ thể hoặc mô tả rộng hơn để mình phối tiếp.")
                    .intent(ChatIntent.OUTFIT_SUGGEST.name())
                    .internalIntent(InternalChatIntent.OUTFIT_BY_PRODUCT.name())
                    .products(List.of())
                    .outfitCombos(List.of())
                    .styleTips(List.of(
                            "Với đồ màu tối, nên cân bằng bằng item sáng hoặc trung tính.",
                            "Chất liệu thoáng mát hợp cotton, linen hoặc form rộng vừa.",
                            "Nếu chọn áo tối màu, quần sáng hoặc xanh denim sẽ giúp tổng thể nhẹ hơn."
                    ))
                    .suggestedQuestions(List.of("Tìm áo thun màu tối", "Tìm áo polo nam", "Gợi ý phối đồ với sản phẩm này"))
                    .context(toContextDto(context))
                    .createdAt(LocalDateTime.now())
                    .build();
        }

        List<OutfitComboResponse> combos = new ArrayList<>();
        for (ChatProductCard base : baseProducts) {
            if (base.getId() == null) {
                continue;
            }
            try {
                OutfitSuggestionResponse suggestion =
                        outfitSuggestionService.getSuggestions(base.getId(), base.getColorId(), context);
                if (suggestion.getCombos() != null) {
                    combos.addAll(suggestion.getCombos());
                }
            } catch (Exception e) {
                log.warn("[AI_CHAT_OUTFIT_CATEGORY] baseProductId={} colorId={} failed={}",
                        base.getId(), base.getColorId(), e.getMessage());
            }
            if (combos.size() >= 3) {
                break;
            }
        }

        if (combos.isEmpty()) {
            return ChatMessageResponse.builder()
                    .role("assistant")
                    .content("Mình đã tìm được sản phẩm nền phù hợp, nhưng hiện shop chưa đủ item bổ sung để ghép thành outfit hoàn chỉnh. Bạn có thể tham khảo các sản phẩm nền bên dưới trước.")
                    .intent(ChatIntent.OUTFIT_SUGGEST.name())
                    .internalIntent(InternalChatIntent.OUTFIT_BY_PRODUCT.name())
                    .products(baseProducts)
                    .outfitCombos(List.of())
                    .styleTips(List.of("Ưu tiên phối màu tương phản nhẹ để outfit tối màu không bị nặng."))
                    .suggestedQuestions(List.of("Tìm sản phẩm tương tự", "Gợi ý phối đồ với sản phẩm này"))
                    .context(toContextDto(context))
                    .createdAt(LocalDateTime.now())
                    .build();
        }

        log.info("[AI_CHAT_OUTFIT_CATEGORY] categoryRole={} baseCandidates={} finalCombos={} provider={}",
                baseResult.inferredRole(), baseProducts.size(), Math.min(combos.size(), 3),
                combos.stream().map(OutfitComboResponse::getProvider).filter(p -> p != null).findFirst().orElse("RULE"));
        return ChatMessageResponse.builder()
                .role("assistant")
                .content("Mình chọn một vài sản phẩm nền phù hợp với yêu cầu của bạn và gợi ý outfit từ các item đang còn hàng trong shop.")
                .intent(ChatIntent.OUTFIT_SUGGEST.name())
                .internalIntent(InternalChatIntent.OUTFIT_BY_PRODUCT.name())
                .products(baseProducts)
                .outfitCombos(combos.stream().limit(3).toList())
                .styleTips(List.of(
                        "Tông tối nên đi cùng item trung tính hoặc denim để tổng thể dễ mặc.",
                        "Chất liệu thoáng mát hợp các outfit thường ngày, đi chơi hoặc cafe.",
                        "Giữ form áo vừa hoặc hơi rộng để outfit trông nhẹ hơn."
                ))
                .suggestedQuestions(List.of("Tìm sản phẩm tương tự", "Gợi ý phối đồ với sản phẩm này"))
                .context(toContextDto(context))
                .createdAt(LocalDateTime.now())
                .build();
    }

    private ChatMessageResponse occasionOutfitResponse(String content, NluSearchParams nluParams, ChatContext context) {
        ProductRetrieverService.ProductSearchResult result = nluParams != null
                ? productRetrieverService.search(nluParams, content, 6)
                : productRetrieverService.search(content, 6);
        String occasion = context.getOccasionLabel() != null ? context.getOccasionLabel() : "dịp này";
        return ChatMessageResponse.builder()
                .role("assistant")
                .content("Mình gợi ý một vài sản phẩm đang còn hàng trong shop để bạn phối đồ cho " + occasion + ".")
                .products(result.products())
                .totalCount((int) result.total())
                .intent(ChatIntent.OUTFIT_SUGGEST.name())
                .styleTips(List.of(
                        "Ưu tiên màu trung tính để dễ phối nhiều item.",
                        "Chọn form vừa vặn để outfit trông gọn và dễ mặc.",
                        "Có thể thêm áo khoác hoặc phụ kiện nếu cần điểm nhấn."
                ))
                .suggestedQuestions(List.of("Gợi ý phối đồ với sản phẩm này", "Tìm sản phẩm theo màu", "Tư vấn theo dáng người"))
                .context(toContextDto(context))
                .createdAt(LocalDateTime.now())
                .build();
    }

    private boolean hasCategoryOrAttributeOutfitSignal(String content) {
        String normalized = normalizeVi(content);
        return containsAny(normalized,
                " ao ", " quan ", " dam ", " vay ", " khoac ", " jacket ", " cardigan ", " blazer ",
                " mau ", " toi ", " den ", " navy ", " xam dam ", " thoang mat ", " mua he ");
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private String roleLabel(String role) {
        String normalized = role == null ? "" : role.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "top" -> "áo";
            case "bottom" -> "quần";
            case "outer" -> "áo khoác";
            case "dress" -> "váy/đầm";
            default -> "sản phẩm thời trang";
        };
    }

    private String requestedKeyword(String content) {
        List<String> terms = ProductSearchDictionary.productTerms(content);
        if (!terms.isEmpty()) {
            return terms.get(0);
        }
        String normalized = normalizeVi(content).trim();
        if (normalized.isBlank()) {
            return "sản phẩm";
        }
        String extracted = extractRolePhrase(normalized);
        if (!extracted.isBlank()) {
            return extracted;
        }
        return normalized.length() > 60 ? normalized.substring(0, 60) : normalized;
    }

    private String extractRolePhrase(String normalized) {
        String[] words = normalized.split("\\s+");
        Set<String> roleWords = Set.of("ao", "quan", "dam", "vay", "khoac", "jacket", "cardigan", "blazer");
        Set<String> stopWords = Set.of("khong", "ko", "k", "nao", "nhe", "a", "ban", "shop", "co",
                "tim", "mua", "cho", "toi", "minh", "san", "pham", "hang");
        for (int i = 0; i < words.length; i++) {
            if (!roleWords.contains(words[i])) {
                continue;
            }
            List<String> phrase = new ArrayList<>();
            phrase.add(words[i]);
            for (int j = i + 1; j < words.length && phrase.size() < 3; j++) {
                String word = words[j];
                if (stopWords.contains(word)) {
                    break;
                }
                phrase.add(word);
            }
            return String.join(" ", phrase);
        }
        return "";
    }

    private String roleLabelFromProductRole(String role) {
        return switch (role == null ? "" : role.trim().toLowerCase(Locale.ROOT)) {
            case "top" -> "áo";
            case "bottom" -> "quần";
            case "outer" -> "áo khoác";
            case "dress" -> "váy/đầm";
            default -> "sản phẩm thời trang";
        };
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

    private ChatMessageResponse noProductsFoundResponse(ChatIntent intent, String requestedKeyword,
                                                        ProductRetrieverService.ProductSearchResult similarResult) {
        if (similarResult != null && !similarResult.products().isEmpty()) {
            String roleLabel = roleLabelFromProductRole(
                    similarResult.inferredRole() != null ? similarResult.inferredRole() : similarResult.products().get(0).getRole());
            return ChatMessageResponse.builder()
                    .role("assistant")
                    .content("Hiện shop chưa có đúng " + requestedKeyword + " bạn tìm. Tuy nhiên, bạn có thể tham khảo một số sản phẩm thuộc nhóm "
                            + roleLabel + " dưới đây, có thể tương đồng với nhu cầu của bạn.")
                    .intent(intent.name())
                    .searchStatus("NEAR_ROLE_FALLBACK")
                    .totalCount((int) Math.min(similarResult.total(), Integer.MAX_VALUE))
                    .products(similarResult.products())
                    .suggestedQuestions(List.of("Gợi ý phối đồ với sản phẩm này", "Xem thêm sản phẩm cùng nhóm", "Tìm theo màu khác"))
                    .createdAt(LocalDateTime.now())
                    .build();
        }
        return ChatMessageResponse.builder()
                .role("assistant")
                .content(noMatchText())
                .intent(intent.name())
                .searchStatus("NO_MATCH")
                .totalCount(0)
                .products(List.of())
                .suggestedQuestions(List.of("Tìm sản phẩm cùng nhóm", "Xem sản phẩm đang sale", "Tư vấn phối đồ"))
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
            case PRODUCT_SEARCH -> List.of("Xem thêm sản phẩm khác", "Gợi ý phối đồ với sản phẩm này");
            case OUTFIT_SUGGEST -> List.of("Gợi ý phối đồ với sản phẩm này", "Tìm sản phẩm cụ thể");
            case ORDER_INQUIRY -> List.of("Xem đơn hàng khác", "Chính sách đổi trả");
            case RETURN_SUPPORT -> List.of("Hướng dẫn đổi/trả hoặc khiếu nại", "Liên hệ hỗ trợ");
            case GENERAL_SUPPORT -> List.of("Tìm sản phẩm", "Tư vấn size");
            case CHITCHAT -> List.of("Tìm áo thun nam", "Gợi ý phối đồ với sản phẩm này", "Xem đơn hàng");
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
            normalizeResponse(response);
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            log.warn("Failed to serialize assistant response: {}", e.getMessage());
            return response.getContent();
        }
    }

    private ChatMessageResponse normalizeResponse(ChatMessageResponse response) {
        if (response == null) {
            return null;
        }
        response.setProducts(dedupeProducts(response.getProducts()));
        if (response.getOutfitCombos() == null) {
            response.setOutfitCombos(List.of());
        }
        if (response.getStyleTips() == null) {
            response.setStyleTips(List.of());
        }
        if (response.getSuggestedQuestions() == null) {
            response.setSuggestedQuestions(List.of());
        }
        if (response.getCreatedAt() == null) {
            response.setCreatedAt(LocalDateTime.now());
        }
        if (response.getRole() == null) {
            response.setRole("assistant");
        }
        if (response.getInternalIntent() == null
                && response.getContext() != null
                && response.getContext().getInternalIntent() != null) {
            response.setInternalIntent(response.getContext().getInternalIntent().name());
        }
        return response;
    }

    private List<ChatProductCard> dedupeProducts(List<ChatProductCard> products) {
        if (products == null || products.isEmpty()) {
            return List.of();
        }
        Map<String, ChatProductCard> deduped = new LinkedHashMap<>();
        for (ChatProductCard product : products) {
            if (product == null || product.getId() == null) {
                continue;
            }
            String key = product.getId() + ":" + (product.getColorId() == null ? "none" : product.getColorId());
            deduped.putIfAbsent(key, product);
        }
        return new ArrayList<>(deduped.values());
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

    private record RerankResult(List<ChatProductCard> products, boolean geminiUsed) {
    }
}
