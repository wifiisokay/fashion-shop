package com.fashionshop.backend.module.ai;

import com.fashionshop.backend.module.ai.dto.response.ChatProductCard;
import com.fashionshop.backend.module.ai.dto.response.OutfitComboResponse;
import com.fashionshop.backend.module.ai.dto.response.OutfitSlot;
import com.fashionshop.backend.module.ai.dto.response.OutfitSuggestionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

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
        log.info("[OUTFIT] request productId={}, colorId={}", productId, colorId);
        if (!refresh) {
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

        List<OutfitComboResponse> combos = getOrBuildCombos(productId, colorId, userId);
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
        return buildCombos(productId, colorId, null);
    }

    public List<OutfitComboResponse> buildCombos(Long productId, Long colorId, Long userId) {
        ChatProductCard base = productRetrieverService.findProductCard(productId, colorId)
            .orElseThrow(() -> new IllegalArgumentException("Product is not available"));
        String anchorRole = base.getRole();
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
        List<ChatProductCard> topCandidates = scoreAndShortlist(base, rawTopCandidates, productId, colorId, "top", 20);
        List<ChatProductCard> bottomCandidates = scoreAndShortlist(base, rawBottomCandidates, productId, colorId, "bottom", 20);
        List<ChatProductCard> outerCandidates = scoreAndShortlist(base, rawOuterCandidates, productId, colorId, "outer", 15);
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
            Long productId, Long colorId, String slotRole, int limit) {
        if (pool.isEmpty()) return List.of();
        List<ChatProductCard> copy = new ArrayList<>(pool);
        long seed = System.nanoTime()
            ^ (productId == null ? 0 : productId)
            ^ (colorId == null ? 0 : colorId << 8)
            ^ slotRole.hashCode();
        Collections.shuffle(copy, new Random(seed));
        return copy.stream()
            .sorted(java.util.Comparator.comparingDouble(
                (ChatProductCard c) -> -outfitScoringService.score(base, c)))
            .limit(limit)
            .toList();
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

            if (products.size() < 2) {
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

        if (combos.isEmpty()) {
            List<ChatProductCard> candidates = productRetrieverService.findAnyAvailableOutfitFallback(productId, base.getGender(), 12).stream()
                .filter(candidate -> !sameRole(base, candidate))
                .toList();
            log.info("[OUTFIT] candidates_fallback productId={}, count={}", productId, candidates.size());
            if (!candidates.isEmpty()) {
                List<ChatProductCard> products = new ArrayList<>();
                addIfAbsent(products, base);
                addIfAbsent(products, candidates.get(0));
                String description = "Set do toi gian, co san pham chinh va item bo sung khac vai tro de tao outfit hoan chinh.";
                combos.add(OutfitComboResponse.builder()
                    .outfitType("casual")
                    .style("casual")
                    .label("Set toi gian")
                    .description(description)
                    .reason(description)
                    .colorStory(buildColorStory(products))
                    .occasion(occasionForStyle("casual"))
                    .provider("RULE")
                    .topSlot(slotFrom(slotProduct(products, "top"), "top", !anchorIs(anchorRole, "top")))
                    .bottomSlot(slotFrom(slotProduct(products, "bottom"), "bottom", !anchorIs(anchorRole, "bottom")))
                    .outerSlot(slotFrom(slotProduct(products, "outer"), "outer", !anchorIs(anchorRole, "outer")))
                    .products(products)
                    .items(products)
                    .build());
                log.info("[OUTFIT] combo_minimal_fallback productId={}, items={}", productId, products.size());
            }
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
            if (products == null || products.size() < 2) {
                return false;
            }
            boolean hasMain = products.stream().anyMatch(product -> productId.equals(product.getId()));
            boolean hasComplementary = products.stream()
                .anyMatch(product -> !productId.equals(product.getId()) && (mainRole == null || !mainRole.equals(product.getRole())));
            boolean genderMismatch = isStrictGender(mainGender) && products.stream()
                .filter(product -> !productId.equals(product.getId()))
                .anyMatch(product -> product.getGender() == null || !mainGender.equalsIgnoreCase(product.getGender()));
            if (!hasMain || !hasComplementary || genderMismatch) {
                return false;
            }
        }
        return true;
    }

    private boolean sameRole(ChatProductCard base, ChatProductCard candidate) {
        if (base == null || candidate == null) {
            return false;
        }
        if (!isCoreRole(base.getRole()) || !isCoreRole(candidate.getRole())) {
            return false;
        }
        return base.getRole().equals(candidate.getRole());
    }

    private boolean isStrictGender(String gender) {
        return "MALE".equalsIgnoreCase(gender) || "FEMALE".equalsIgnoreCase(gender);
    }

    private boolean isCoreRole(String role) {
        return "top".equals(role) || "bottom".equals(role) || "dress".equals(role)
            || "skirt".equals(role) || "outerwear".equals(role);
    }

    private boolean needsSlot(String anchorRole, String slotRole) {
        if ("dress".equals(anchorRole)) {
            return "outer".equals(slotRole);
        }
        if ("skirt".equals(anchorRole)) {
            return "top".equals(slotRole) || "outer".equals(slotRole);
        }
        return !anchorIs(anchorRole, slotRole);
    }

    private boolean anchorIs(String anchorRole, String slotRole) {
        return switch (slotRole) {
            case "top" -> "top".equals(anchorRole);
            case "bottom" -> "bottom".equals(anchorRole) || "skirt".equals(anchorRole);
            case "outer" -> "outerwear".equals(anchorRole);
            default -> false;
        };
    }

    private ChatProductCard pick(List<ChatProductCard> candidates, int index) {
        return candidates.isEmpty() ? null : candidates.get(index % candidates.size());
    }


    private boolean shouldUseOuter(String anchorRole, int comboIndex, List<ChatProductCard> outerCandidates) {
        if (anchorIs(anchorRole, "outer")) {
            return true;
        }
        if (outerCandidates.isEmpty()) {
            return false;
        }
        return comboIndex % 2 == 1;
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
        return switch (slotRole) {
            case "top" -> "top".equals(role);
            case "bottom" -> "bottom".equals(role) || "skirt".equals(role);
            case "outer" -> "outerwear".equals(role);
            default -> false;
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

    private boolean looksLikeJson(String value) {
        String cleaned = value == null ? "" : value.trim();
        return cleaned.startsWith("{") || cleaned.startsWith("[") || cleaned.startsWith("```");
    }

}
