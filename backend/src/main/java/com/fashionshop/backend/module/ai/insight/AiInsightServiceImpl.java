package com.fashionshop.backend.module.ai.insight;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashionshop.backend.domain.Product;
import com.fashionshop.backend.domain.Review;
import com.fashionshop.backend.domain.repository.ProductRepository;
import com.fashionshop.backend.domain.repository.ReviewRepository;
import com.fashionshop.backend.exception.BusinessException;
import com.fashionshop.backend.exception.ErrorCode;
import com.fashionshop.backend.module.ai.AiClientRouter;
import com.fashionshop.backend.module.ai.insight.dto.AiInsightResponse;
import com.fashionshop.backend.module.ai.insight.dto.AiInsightResponse.ReviewSummary;
import com.fashionshop.backend.module.ai.insight.dto.AiInsightResponse.StyleGuide;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Orchestrator cho AI Insight pipeline:
 *   1. Kiểm tra Redis cache → trả về ngay nếu có
 *   2. Lấy Product + Reviews từ DB
 *   3. Build prompt → gọi Gemini qua AiClientRouter (dùng lại, không sửa)
 *   4. Parse JSON response → AiInsightResponse
 *   5. Lưu cache → trả về
 *
 * Fallback: nếu Gemini lỗi hoặc parse lỗi → trả về styleGuide tĩnh từ product tags.
 * Không throw exception ra ngoài — endpoint luôn trả 200.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiInsightServiceImpl implements AiInsightService {

    private static final int MIN_REVIEWS_FOR_SUMMARY = 3;
    private static final int MAX_REVIEWS_TO_FETCH    = 50;

    private final ProductRepository     productRepository;
    private final ReviewRepository      reviewRepository;
    private final AiClientRouter        aiClientRouter;
    private final AiInsightCacheManager cacheManager;
    private final AiInsightPromptBuilder promptBuilder;
    private final ObjectMapper          objectMapper;

    // ──────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────

    @Override
    public AiInsightResponse getInsight(Long productId) {
        // 1. Cache hit → trả về ngay
        Optional<AiInsightResponse> cached = cacheManager.tryLoad(productId);
        if (cached.isPresent()) {
            return cached.get();
        }

        // 2. Lấy product (404 nếu không tồn tại)
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.PRODUCT_NOT_FOUND));

        // 3. Lấy reviews mới nhất — dùng method đã có trong ReviewRepository
        List<Review> reviews = reviewRepository.findByProductId(
                productId,
                PageRequest.of(0, MAX_REVIEWS_TO_FETCH,
                        Sort.by(Sort.Direction.DESC, "createdAt"))
        ).getContent();

        double avgRating = reviews.stream()
                .mapToInt(Review::getRating)
                .average()
                .orElse(0.0);

        boolean hasEnoughReviews = reviews.stream()
                .filter(r -> r.getComment() != null && !r.getComment().isBlank())
                .count() >= MIN_REVIEWS_FOR_SUMMARY;

        // 4. Build prompt + gọi Gemini
        AiInsightResponse response;
        try {
            String prompt = promptBuilder.build(product, reviews, avgRating);
            // Dùng lightModel (3.1 Flash Lite) để bảo tồn quota 20 RPD của Gemini 2.5 Flash
            // chỉ cho Outfit Rerank — tác vụ AI quan trọng nhất.
            String rawJson = aiClientRouter.generateLight(prompt, List.of(), "");
            response = parseResponse(rawJson, hasEnoughReviews);
            log.info("[AI_INSIGHT] gemini_success productId={} hasReviewSummary={}", productId, hasEnoughReviews);
        } catch (Exception e) {
            log.warn("[AI_INSIGHT] gemini_failed productId={} reason={} → using fallback",
                    productId, e.getMessage());
            response = buildFallback(product);
        }

        // 5. Lưu cache
        cacheManager.save(productId, response);

        return response;
    }

    // ──────────────────────────────────────────────
    // Parse JSON từ Gemini
    // ──────────────────────────────────────────────

    private AiInsightResponse parseResponse(String rawJson, boolean hasEnoughReviews) {
        try {
            // Gemini đôi khi wrap trong ```json ... ``` — strip nếu có
            String cleaned = rawJson.trim();
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.replaceAll("^```[a-z]*\\s*", "").replaceAll("```\\s*$", "").trim();
            }

            JsonNode root = objectMapper.readTree(cleaned);

            // Parse reviewSummary (chỉ khi có đủ reviews)
            ReviewSummary reviewSummary = null;
            if (hasEnoughReviews && root.has("reviewSummary")) {
                JsonNode rs = root.get("reviewSummary");
                reviewSummary = new ReviewSummary(
                        textOrNull(rs, "overall"),
                        stringList(rs, "pros"),
                        stringList(rs, "cons"),
                        textOrNull(rs, "fitNote")
                );
            }

            // Parse styleGuide
            StyleGuide styleGuide = null;
            if (root.has("styleGuide")) {
                JsonNode sg = root.get("styleGuide");
                styleGuide = new StyleGuide(
                        stringList(sg, "occasions"),
                        stringList(sg, "outfitTips"),
                        textOrNull(sg, "whoShouldBuy"),
                        textOrNull(sg, "seasonTip")
                );
            }

            // Nếu parse được nhưng thiếu styleGuide → fallback styleGuide = null vẫn ok
            return new AiInsightResponse(
                    reviewSummary != null,
                    reviewSummary,
                    styleGuide,
                    LocalDateTime.now()
            );

        } catch (Exception e) {
            log.warn("[AI_INSIGHT] parse_failed reason={}", e.getMessage());
            throw new RuntimeException("AI_INSIGHT_PARSE_FAILED", e);
        }
    }

    // ──────────────────────────────────────────────
    // Fallback khi Gemini lỗi hoàn toàn
    // ──────────────────────────────────────────────

    private AiInsightResponse buildFallback(Product product) {
        // Tạo styleGuide tĩnh từ occasionTags và styleTags của product
        List<String> occasions = product.getOccasionTags() != null
                ? product.getOccasionTags()
                : List.of();

        List<String> tips = new ArrayList<>();
        if (product.getFitType() != null) {
            tips.add("Phom " + product.getFitType() + " dễ phối với nhiều trang phục");
        }
        if (product.getMaterial() != null) {
            tips.add("Chất liệu " + product.getMaterial() + " thoải mái khi mặc");
        }

        StyleGuide styleGuide = new StyleGuide(
                occasions,
                tips.isEmpty() ? List.of("Phối cùng quần/váy đơn sắc để tôn vẻ đẹp sản phẩm") : tips,
                null,
                product.getSeason()
        );

        return new AiInsightResponse(false, null, styleGuide, LocalDateTime.now());
    }

    // ──────────────────────────────────────────────
    // JSON helpers
    // ──────────────────────────────────────────────

    private String textOrNull(JsonNode node, String field) {
        if (node == null || !node.has(field)) return null;
        String text = node.get(field).asText("").trim();
        return text.isBlank() ? null : text;
    }

    private List<String> stringList(JsonNode node, String field) {
        if (node == null || !node.has(field) || !node.get(field).isArray()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        node.get(field).forEach(item -> {
            String text = item.asText("").trim();
            if (!text.isBlank()) result.add(text);
        });
        return result;
    }
}
