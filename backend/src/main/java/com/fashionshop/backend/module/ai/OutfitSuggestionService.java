package com.fashionshop.backend.module.ai;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.fashionshop.backend.module.ai.dto.ChatContext;
import com.fashionshop.backend.module.ai.dto.response.ChatProductCard;
import com.fashionshop.backend.module.ai.dto.response.OutfitComboResponse;
import com.fashionshop.backend.module.ai.dto.response.OutfitSlot;
import com.fashionshop.backend.module.ai.dto.response.OutfitSuggestionResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutfitSuggestionService {

    private static final List<StyleProfile> STYLE_PROFILES = List.of(
        new StyleProfile("daily", "Dao pho de mac", "hang ngay, dao pho hoac cuoi tuan"),
        new StyleProfile("work-ready", "Cong so linh hoat", "di lam, gap go hoac dao pho lich su"),
        new StyleProfile("weekend", "Chill cuoi tuan", "di choi, cafe hoac gap ban be"),
        new StyleProfile("clean", "Toi gian hien dai", "di hang ngay hoac hen gap nhe nhang")
    );

    private final ConcurrentHashMap<String, CompletableFuture<List<OutfitComboResponse>>> buildingMap =
        new ConcurrentHashMap<>();

    private final OutfitCacheManager outfitCacheManager;
    private final ProductRetrieverService productRetrieverService;
    private final OutfitCandidateRetriever outfitCandidateRetriever;
    private final TagTranslationService tagTranslationService;
    private final AiClientRouter aiClientRouter;
    private final GeminiOutfitProvider geminiOutfitProvider;
    private final OutfitScoringService outfitScoringService;

    public OutfitSuggestionResponse getSuggestions(Long productId, Long colorId) {
        return getSuggestions(productId, colorId, false);
    }

    public OutfitSuggestionResponse getSuggestions(Long productId, Long colorId, boolean refresh) {
        return getSuggestions(productId, colorId, refresh, null);
    }

    public OutfitSuggestionResponse getSuggestions(Long productId, Long colorId, boolean refresh, Long userId) {
        return getSuggestions(productId, colorId, refresh, userId, null);
    }

    public OutfitSuggestionResponse getSuggestions(Long productId, Long colorId, ChatContext context) {
        return getSuggestions(productId, colorId, false, null, context);
    }

    private OutfitSuggestionResponse getSuggestions(Long productId, Long colorId, boolean refresh, Long userId,
                                                    ChatContext context) {
        log.info("[OUTFIT] request productId={}, colorId={}", productId, colorId);
        boolean contextual = context != null && (context.getStyleTag() != null || context.getOccasionTag() != null);
        if (!refresh && !contextual) {
            var cached = outfitCacheManager.tryLoadValidCache(productId, colorId);
            if (cached.isPresent() && isValidCachedOutfit(productId, colorId, cached.get().combos())) {
                log.info("[OUTFIT] cache_hit productId={}, colorId={}, combos={}",
                    productId, colorId, cached.get().combos().size());
                return OutfitSuggestionResponse.builder()
                    .productId(productId)
                    .colorId(colorId)
                    .text("Minh goi y cac bo outfit phu hop voi san pham va mau ban dang chon:")
                    .cached(true)
                    .createdAt(cached.get().createdAt())
                    .combos(cached.get().combos())
                    .build();
            }
            cached.ifPresent(value -> log.info("[OUTFIT] cache_invalid productId={}, colorId={} -> rebuild",
                productId, colorId));
            if (cached.isEmpty()) {
                log.info("[OUTFIT] cache_miss_or_expired productId={}, colorId={}", productId, colorId);
            }
        } else {
            log.info("[OUTFIT] cache_refresh productId={}, colorId={}", productId, colorId);
        }

        List<OutfitComboResponse> combos = contextual
            ? buildCombos(productId, colorId, context.getStyleTag(), context.getOccasionTag(), userId)
            : getOrBuildCombos(productId, colorId, userId);
        log.info("[OUTFIT] build_result productId={}, colorId={}, combos={}", productId, colorId, combos.size());
        LocalDateTime createdAt = LocalDateTime.now();
        if (combos.isEmpty()) {
            log.warn("[OUTFIT] no_combo_found productId={}, colorId={}", productId, colorId);
        }

        String provider = combos.stream()
            .map(OutfitComboResponse::getProvider)
            .filter(p -> p != null && !p.isBlank())
            .findFirst()
            .orElse("RULE");

        return OutfitSuggestionResponse.builder()
            .productId(productId)
            .colorId(colorId)
            .text(combos.isEmpty()
                ? "Hien shop chua co du san pham khac vai tro de phoi thanh outfit hoan chinh cho item nay."
                : "Minh goi y cac bo outfit phu hop voi san pham va mau ban dang chon:")
            .cached(false)
            .provider(provider)
            .createdAt(createdAt)
            .combos(combos)
            .build();
    }

    private List<OutfitComboResponse> getOrBuildCombos(Long productId, Long colorId, Long userId) {
        String key = buildKey(productId, colorId);
        CompletableFuture<List<OutfitComboResponse>> existing = buildingMap.get(key);
        if (existing != null) {
            try {
                return existing.join();
            } catch (Exception e) {
                log.warn("[OUTFIT] wait_existing_build_failed key={}, error={}", key, e.getMessage());
            }
        }

        CompletableFuture<List<OutfitComboResponse>> future = new CompletableFuture<>();
        CompletableFuture<List<OutfitComboResponse>> previous = buildingMap.putIfAbsent(key, future);
        if (previous != null) {
            try {
                return previous.join();
            } catch (Exception e) {
                log.warn("[OUTFIT] wait_existing_build_failed key={}, error={}", key, e.getMessage());
            }
        }

        try {
            List<OutfitComboResponse> combos = buildCombos(productId, colorId, userId);
            if (!combos.isEmpty()) {
                try {
                    outfitCacheManager.safeUpsert(productId, colorId, combos);
                    String provider = combos.stream()
                        .map(OutfitComboResponse::getProvider)
                        .filter(p -> p != null)
                        .findFirst().orElse("RULE");
                    log.info("[OUTFIT] cache_upserted productId={}, colorId={}, combos={} provider={}",
                        productId, colorId, combos.size(), provider);
                } catch (Exception e) {
                    log.warn("[OUTFIT] cache_upsert_skipped key={}, error={}", key, e.getMessage());
                }
            }
            future.complete(combos);
            return combos;
        } catch (Exception e) {
            future.completeExceptionally(e);
            throw e;
        } finally {
            buildingMap.remove(key, future);
        }
    }

    private String buildKey(Long productId, Long colorId) {
        return productId + ":" + colorId;
    }

    public List<OutfitComboResponse> buildCombos(Long productId, Long colorId) {
        return buildCombos(productId, colorId, null, null, null);
    }

    public List<OutfitComboResponse> buildCombos(Long productId, Long colorId, Long userId) {
        return buildCombos(productId, colorId, null, null, userId);
    }

    public List<OutfitComboResponse> buildCombos(Long productId, Long colorId, String styleTag, String occasionTag, Long userId) {
        ChatProductCard base = productRetrieverService.findProductCard(productId, colorId)
            .orElseThrow(() -> new IllegalArgumentException("Product is not available"));
        String anchorRole = normalizeRole(base.getRole());
        log.info("[OUTFIT] base productId={}, colorId={}, role={}, gender={}, colorName={}, colorFamily={}",
            base.getId(), base.getColorId(), anchorRole, base.getGender(), base.getColorName(), base.getColorFamily());

        // Phase A: Mở rộng pool candidates (40 bottom, 25 outer)
        List<ChatProductCard> rawTopCandidates = needsSlot(anchorRole, "top")
            ? outfitCandidateRetriever.getCandidatesForSlot(base, "top", 30)
            : List.of();
        List<ChatProductCard> rawBottomCandidates = needsSlot(anchorRole, "bottom")
            ? outfitCandidateRetriever.getCandidatesForSlot(base, "bottom", 40)
            : List.of();
        List<ChatProductCard> rawOuterCandidates = needsSlot(anchorRole, "outer")
            ? outfitCandidateRetriever.getCandidatesForSlot(base, "outer", 25)
            : List.of();
        log.info("[OUTFIT] raw_candidates productId={} top={} bottom={} outer={}",
            productId, rawTopCandidates.size(), rawBottomCandidates.size(), rawOuterCandidates.size());

        // Phase B: shuffle → local score → shortlist top 20
        List<ChatProductCard> topCandidates = scoreAndShortlist(base, rawTopCandidates, productId, colorId, "top", 20, styleTag, occasionTag);
        List<ChatProductCard> bottomCandidates = scoreAndShortlist(base, rawBottomCandidates, productId, colorId, "bottom", 20, styleTag, occasionTag);
        List<ChatProductCard> outerCandidates = scoreAndShortlist(base, rawOuterCandidates, productId, colorId, "outer", 15, styleTag, occasionTag);
        log.info("[OUTFIT] shortlisted_candidates productId={} top={} bottom={} outer={}",
            productId, topCandidates.size(), bottomCandidates.size(), outerCandidates.size());

        base.setRole("main");
        base.setReason("San pham chinh ban dang xem");

        // Phase C: Gemini rerank
        List<OutfitComboResponse> combos = List.of();
        if (!topCandidates.isEmpty() || !bottomCandidates.isEmpty()) {
            log.info("[OUTFIT_AI] provider=GEMINI phase=rerank candidates=top:{}/bottom:{}/outer:{}",
                topCandidates.size(), bottomCandidates.size(), outerCandidates.size());
            try {
                combos = geminiOutfitProvider.generateCombos(
                    base, anchorRole, topCandidates, bottomCandidates, outerCandidates, userId);
            } catch (Exception e) {
                log.warn("[OUTFIT_AI] provider=GEMINI failed fallback=RULE reason={}", e.getMessage());
            }
        }

        // Phase E: Rule-based fallback nếu Gemini không trả combo
        if (combos.isEmpty()) {
            log.info("[OUTFIT_AI] provider=RULE fallback=true");
            combos = buildRuleBasedCombos(base, anchorRole, topCandidates, bottomCandidates, outerCandidates, productId, colorId);
            log.info("[OUTFIT_AI] provider=RULE success combos={}", combos.size());
        } else {
            log.info("[OUTFIT_AI] provider=GEMINI success combos={}", combos.size());
        }

        return combos;
    }

    /**
     * Phase B: Shuffle pool trước, sort theo score, lấy shortlist.
     * shuffle() TRƯỚC sort → đảm bảo khi score bằng nhau thì thứ tự khác nhau mỗi lần gọi.
     */
    private List<ChatProductCard> scoreAndShortlist(
            ChatProductCard base,
            List<ChatProductCard> pool,
            Long productId, Long colorId, String slotRole, int limit, String styleTag, String occasionTag) {
        if (pool.isEmpty()) return List.of();
        List<ChatProductCard> copy = new ArrayList<>(pool);
        long seed = System.nanoTime()
            ^ (productId == null ? 0 : productId)
            ^ (colorId == null ? 0 : colorId << 8)
            ^ slotRole.hashCode();
        Collections.shuffle(copy, new Random(seed));
        for (ChatProductCard candidate : copy) {
            OutfitScoringService.ScoreBreakdown breakdown = outfitScoringService.scoreWithBreakdown(base, candidate, styleTag, occasionTag);
            if (breakdown == null) {
                breakdown = new OutfitScoringService.ScoreBreakdown(0, 0, 0, 0, 0, 0);
            }
            log.info("[AI_STYLE_SCORE] baseId={} candidateId={} role={} score={} color={} fit={} stock={} style={} occasion={}",
                base.getId(), candidate.getId(), slotRole,
                breakdown.total(), breakdown.colorFamily(), breakdown.fitBalance(), breakdown.stock(),
                breakdown.styleTag(), breakdown.occasionTag());
        }
        return copy.stream()
            .sorted(java.util.Comparator.comparingDouble(
                (ChatProductCard c) -> -scoreTotal(base, c, styleTag, occasionTag)))
            .limit(limit)
            .toList();
    }

    private double scoreTotal(ChatProductCard base, ChatProductCard candidate, String styleTag, String occasionTag) {
        OutfitScoringService.ScoreBreakdown score = outfitScoringService.scoreWithBreakdown(base, candidate, styleTag, occasionTag);
        return score != null ? score.total() : 0;
    }

    /**
     * Rule-based fallback: logic cũ từ STYLE_PROFILES.
     */
    private List<OutfitComboResponse> buildRuleBasedCombos(
            ChatProductCard base, String anchorRole,
            List<ChatProductCard> topCandidates,
            List<ChatProductCard> bottomCandidates,
            List<ChatProductCard> outerCandidates,
            Long productId, Long colorId) {

        List<OutfitComboResponse> combos = new ArrayList<>();
        int comboCount = 0;

        for (int i = 0; i < STYLE_PROFILES.size(); i++) {
            List<ChatProductCard> products = new ArrayList<>();
            addIfAbsent(products, base);

            ChatProductCard top = anchorIs(anchorRole, "top") ? base : pick(topCandidates, i);
            ChatProductCard bottom = anchorIs(anchorRole, "bottom") ? base : pick(bottomCandidates, i);
            ChatProductCard outer = shouldUseOuter(anchorRole, i, outerCandidates)
                ? (anchorIs(anchorRole, "outer") ? base : pick(outerCandidates, i))
                : null;

            addIfAbsent(products, top);
            addIfAbsent(products, bottom);
            addIfAbsent(products, outer);

            if (!hasRequiredSlots(anchorRole, top, bottom, outer)) {
                log.debug("[OUTFIT] skip_style style={} reason=insufficient_items size={}", STYLE_PROFILES.get(i).style(), products.size());
                continue;
            }

            StyleProfile profile = STYLE_PROFILES.get(i);
            String description = generateReason(profile.label(), products);
            combos.add(OutfitComboResponse.builder()
                .outfitType(profile.style())
                .style(profile.style())
                .label(profile.label())
                .description(description)
                .reason(description)
                .colorStory(buildColorStory(products))
                .occasion(profile.occasion())
                .provider("RULE")
                .topSlot(slotFrom(top, "top", !anchorIs(anchorRole, "top")))
                .bottomSlot(slotFrom(bottom, "bottom", !anchorIs(anchorRole, "bottom")))
                .outerSlot(slotFrom(outer, "outer", !anchorIs(anchorRole, "outer")))
                .products(products)
                .items(products)
                .build());
            comboCount++;
            log.debug("[OUTFIT] combo_built style={}, items={}", profile.style(), products.size());
            if (comboCount >= 3) break;
        }

        return combos;
    }

    private boolean isValidCachedOutfit(Long productId, Long colorId, List<OutfitComboResponse> combos) {
        if (combos == null || combos.isEmpty()) {
            return false;
        }
        ChatProductCard mainProduct = productRetrieverService.findProductCard(productId, colorId).orElse(null);
        String mainRole = mainProduct != null ? mainProduct.getRole() : null;
        String mainGender = mainProduct != null ? mainProduct.getGender() : null;
        for (OutfitComboResponse combo : combos) {
            List<ChatProductCard> products = combo.getProducts() != null ? combo.getProducts() : combo.getItems();
            if (products == null || products.isEmpty()) {
                return false;
            }
            boolean hasMain = products.stream().anyMatch(product -> productId.equals(product.getId()));
            boolean hasComplementary = products.stream()
                .anyMatch(product -> !productId.equals(product.getId()) && (mainRole == null || !mainRole.equals(product.getRole())));
            boolean genderMismatch = isStrictGender(mainGender) && products.stream()
                .filter(product -> !productId.equals(product.getId()))
                .anyMatch(product -> product.getGender() == null || !mainGender.equalsIgnoreCase(product.getGender()));
            boolean duplicateIds = products.stream().map(ChatProductCard::getId).distinct().count() != products.size();
            boolean validStructure = hasRequiredSlots(mainRole, slotProduct(products, "top"),
                slotProduct(products, "bottom"), slotProduct(products, "outer"));
            if (!hasMain || (!"dress".equals(mainRole) && !hasComplementary) || genderMismatch || duplicateIds || !validStructure) {
                return false;
            }
        }
        return true;
    }

    private boolean sameRole(ChatProductCard base, ChatProductCard candidate) {
        if (base == null || candidate == null) {
            return false;
        }
        String baseRole = normalizeRole(base.getRole());
        String candidateRole = normalizeRole(candidate.getRole());
        if (!isCoreRole(baseRole) || !isCoreRole(candidateRole)) {
            return false;
        }
        return baseRole.equals(candidateRole);
    }

    private boolean isStrictGender(String gender) {
        return "MALE".equalsIgnoreCase(gender) || "FEMALE".equalsIgnoreCase(gender);
    }

    private boolean isCoreRole(String role) {
        return "top".equals(role) || "bottom".equals(role) || "dress".equals(role) || "outer".equals(role);
    }

    private boolean needsSlot(String anchorRole, String slotRole) {
        SlotPlan plan = slotPlan(normalizeRole(anchorRole));
        return plan.required().contains(slotRole) || plan.optional().contains(slotRole);
    }

    private boolean anchorIs(String anchorRole, String slotRole) {
        String normalized = normalizeRole(anchorRole);
        return switch (slotRole) {
            case "top" -> "top".equals(normalized);
            case "bottom" -> "bottom".equals(normalized);
            case "outer" -> "outer".equals(normalized);
            default -> false;
        };
    }

    private ChatProductCard pick(List<ChatProductCard> candidates, int index) {
        return candidates.isEmpty() ? null : candidates.get(index % candidates.size());
    }


    private boolean shouldUseOuter(String anchorRole, int comboIndex, List<ChatProductCard> outerCandidates) {
        SlotPlan plan = slotPlan(normalizeRole(anchorRole));
        if (anchorIs(anchorRole, "outer")) {
            return true;
        }
        if (outerCandidates.isEmpty()) {
            return false;
        }
        if (plan.required().contains("outer")) {
            return true;
        }
        return plan.optional().contains("outer") && comboIndex % 2 == 1;
    }

    private void addIfAbsent(List<ChatProductCard> products, ChatProductCard product) {
        if (product == null || products.stream().anyMatch(item -> item.getId().equals(product.getId())
                && item.getColorId().equals(product.getColorId()))) {
            return;
        }
        products.add(product);
    }

    private ChatProductCard slotProduct(List<ChatProductCard> products, String slotRole) {
        return products.stream()
            .filter(product -> roleMatchesSlot(product.getRole(), slotRole))
            .findFirst()
            .orElse(null);
    }

    private boolean roleMatchesSlot(String role, String slotRole) {
        String normalized = normalizeRole(role);
        return switch (slotRole) {
            case "top" -> "top".equals(normalized);
            case "bottom" -> "bottom".equals(normalized);
            case "outer" -> "outer".equals(normalized);
            default -> false;
        };
    }

    private boolean hasRequiredSlots(String anchorRole, ChatProductCard top, ChatProductCard bottom, ChatProductCard outer) {
        String normalized = normalizeRole(anchorRole);
        return switch (normalized == null ? "" : normalized) {
            case "top" -> top != null && bottom != null;
            case "bottom" -> top != null && bottom != null;
            case "outer" -> top != null && bottom != null && outer != null;
            case "dress" -> true;
            default -> top != null && bottom != null;
        };
    }

    private SlotPlan slotPlan(String anchorRole) {
        return switch (anchorRole == null ? "" : anchorRole) {
            case "top" -> new SlotPlan(Set.of("bottom"), Set.of("outer"));
            case "bottom" -> new SlotPlan(Set.of("top"), Set.of("outer"));
            case "outer" -> new SlotPlan(Set.of("top", "bottom"), Set.of());
            case "dress" -> new SlotPlan(Set.of(), Set.of("outer"));
            default -> new SlotPlan(Set.of("top", "bottom"), Set.of("outer"));
        };
    }

    private String normalizeRole(String role) {
        if (role == null) {
            return null;
        }
        return switch (role) {
            case "outerwear" -> "outer";
            case "skirt" -> "bottom";
            default -> role;
        };
    }

    private OutfitSlot slotFrom(ChatProductCard product, String slotRole, boolean optional) {
        if (product == null) {
            return null;
        }
        return OutfitSlot.builder()
            .productId(product.getId())
            .colorId(product.getColorId())
            .productName(product.getName())
            .colorName(product.getColorName())
            .colorCode(product.getColorCode())
            .colorFamily(product.getColorFamily())
            .slotRole(slotRole)
            .optional(optional)
            .imageUrl(product.getImageUrl())
            .productUrl(product.getUrl())
            .build();
    }

    private String buildColorStory(List<ChatProductCard> products) {
        List<String> families = products.stream()
            .map(ChatProductCard::getColorFamily)
            .filter(value -> value != null && !value.isBlank())
            .distinct()
            .toList();
        if (families.isEmpty()) {
            return "Phoi mau de mac dua tren cac item co san trong shop.";
        }
        return "Tong mau " + String.join(" + ", families) + " duoc loc theo compatibility tu tung mau san pham.";
    }

    private String occasionForStyle(String style) {
        return switch (style) {
            case "smart-casual" -> "di lam, gap go hoac dao pho lich su";
            case "streetwear" -> "di choi, cafe hoac hoat dong ngoai troi";
            default -> "hang ngay, dao pho hoac cuoi tuan";
        };
    }

    private String generateReason(String style, List<ChatProductCard> products) {
        StringBuilder data = new StringBuilder();
        for (ChatProductCard product : products) {
            data.append("- ").append(product.getName());
            if (product.getColorName() != null) data.append(" (mau ").append(product.getColorName()).append(")");
            if (product.getColorFamily() != null) data.append(" [tong ").append(product.getColorFamily()).append("]");
            if (product.getRole() != null) data.append(" - vai tro: ").append(product.getRole());
            data.append(" - gia: ").append(product.getDisplayPrice()).append("\n");
        }

        String styleLabel = tagTranslationService.labelForStyle(style);
        String systemInstruction = """
                Ban la stylist thoi trang nguoi Viet. Nhiem vu: viet dung 1 cau tieng Viet toi da 25 tu
                giai thich vi sao cac san pham nay phoi hop tot voi nhau.
                Chi nhan xet ve san pham duoc liet ke, khong bia them san pham khac.
                Tra ve 1 cau duy nhat, khong dau ngoac kep, khong giai thich.
                """;
        String userPrompt = "Phong cach: " + styleLabel + "\nSan pham trong outfit:\n" + data;

        try {
            String response = aiClientRouter.generate(systemInstruction, List.of(), userPrompt);
            if (response != null && !response.isBlank() && !looksLikeJson(response)) {
                String[] sentences = response.strip().split("[.!?]");
                if (sentences.length > 0 && !sentences[0].isBlank()) {
                    return sentences[0].strip() + ".";
                }
                return response.strip();
            }
        } catch (Exception e) {
            log.debug("Outfit reason generation failed: {}", e.getMessage());
        }
        return "Combo " + styleLabel + " can bang mau sac va form dang, de mac va noi bat.";
    }

    private record StyleProfile(String style, String label, String occasion) {
    }

    private record SlotPlan(Set<String> required, Set<String> optional) {
    }

    private boolean looksLikeJson(String value) {
        String cleaned = value == null ? "" : value.trim();
        return cleaned.startsWith("{") || cleaned.startsWith("[") || cleaned.startsWith("```");
    }

}
