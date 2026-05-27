package com.fashionshop.backend.module.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashionshop.backend.module.ai.dto.response.ChatProductCard;
import com.fashionshop.backend.module.ai.dto.response.OutfitComboResponse;
import com.fashionshop.backend.module.ai.dto.response.OutfitSlot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Gemini rerank provider cho outfit suggestion.
 *
 * Phase C trong outfit pipeline:
 *   Nhận shortlist đã score → gửi cho Gemini → Gemini chọn 3 combo + viết label/reason/colorStory/occasion
 *   Backend validate kết quả AI (Phase D) → trả combos
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiOutfitProvider {

    private static final int MAX_COMBOS = 3;

    private final AiClientRouter aiClientRouter;
    private final ObjectMapper objectMapper;
    private final UserPreferenceService userPreferenceService;

    /**
     * Gọi Gemini để rerank và chọn 3 combo outfit tốt nhất.
     *
     * @param base           sản phẩm gốc
     * @param anchorRole     vai trò slot của sản phẩm gốc
     * @param topCandidates  shortlist top slot candidates
     * @param bottomCandidates shortlist bottom slot candidates
     * @param outerCandidates  shortlist outer slot candidates
     * @param userId         userId để inject preference (nullable)
     * @return danh sách combo đã validate, empty nếu Gemini fail
     */
    public List<OutfitComboResponse> generateCombos(
            ChatProductCard base,
            String anchorRole,
            List<ChatProductCard> topCandidates,
            List<ChatProductCard> bottomCandidates,
            List<ChatProductCard> outerCandidates,
            Long userId) {

        // Build allowed set để validate kết quả AI
        Set<String> allowedKeys = buildAllowedKeys(base, topCandidates, bottomCandidates, outerCandidates);

        // Build prompt
        String userPrefHint = buildPrefHint(userId);
        String prompt = buildRerankPrompt(base, anchorRole, topCandidates, bottomCandidates, outerCandidates, userPrefHint);

        long t0 = System.currentTimeMillis();
        String rawResponse;
        try {
            rawResponse = aiClientRouter.generate(prompt, List.of(), "Hãy chọn 3 combo outfit tốt nhất và trả JSON.");
            log.info("[OUTFIT_AI] provider=GEMINI phase=rerank candidates=top:{}/bottom:{}/outer:{} latencyMs={}",
                topCandidates.size(), bottomCandidates.size(), outerCandidates.size(),
                System.currentTimeMillis() - t0);
        } catch (Exception e) {
            log.warn("[OUTFIT_AI] provider=GEMINI phase=rerank failed reason={}", e.getMessage());
            return List.of();
        }

        // Parse và validate
        List<OutfitComboResponse> combos = parseAndValidate(rawResponse, base, anchorRole, allowedKeys);
        log.info("[OUTFIT_AI] provider=GEMINI success combos={} latencyMs={}",
            combos.size(), System.currentTimeMillis() - t0);
        return combos;
    }

    // ==========================================
    // Prompt building
    // ==========================================

    private String buildRerankPrompt(
            ChatProductCard base,
            String anchorRole,
            List<ChatProductCard> tops,
            List<ChatProductCard> bottoms,
            List<ChatProductCard> outers,
            String prefHint) {

        return """
            Bạn là AI stylist cho shop thời trang Việt Nam. Nhiệm vụ: từ sản phẩm gốc và danh sách candidates đã được hệ thống lọc còn hàng, hãy chọn %d outfit combo đẹp nhất.
            
            LUẬT BẮT BUỘC:
            - Chỉ dùng productId/colorId có trong danh sách candidates bên dưới
            - KHÔNG tự tạo sản phẩm mới ngoài danh sách
            - outer slot là OPTIONAL — chỉ thêm khi thực sự phù hợp, không bắt buộc
            - label phải ngắn gọn, tự nhiên tiếng Việt (vd: 'Dạo phố mùa hè', 'Công sở lịch lãm', 'Chill cuối tuần')
            - KHÔNG dùng label cố định Casual/Smart-casual/Streetwear — phải sáng tạo
            - score là số từ 0.0 đến 1.0 phản ánh độ phù hợp của combo
            - Trả JSON hợp lệ, không markdown, không preamble, không giải thích ngoài JSON
            
            %s
            
            SẢN PHẨM GỐC (role=%s):
            %s
            
            TOP CANDIDATES:
            %s
            
            BOTTOM CANDIDATES:
            %s
            
            OUTER CANDIDATES (OPTIONAL):
            %s
            
            JSON SCHEMA TRẢ VỀ (trả đúng schema này, không thêm field khác):
            {
              "combos": [
                {
                  "label": "...",
                  "score": 0.0,
                  "reason": "giải thích 1 câu tại sao combo này phù hợp",
                  "colorStory": "tổng màu ... + ... — ghi chú ngắn",
                  "occasion": ["daily", "school"],
                  "top": {"productId": 1, "colorId": 2},
                  "bottom": {"productId": 3, "colorId": 4},
                  "outer": null
                }
              ]
            }
            """.formatted(
                MAX_COMBOS,
                prefHint,
                anchorRole,
                serializeCard(base),
                serializeCards(tops),
                serializeCards(bottoms),
                serializeCards(outers)
            );
    }

    private String buildPrefHint(Long userId) {
        if (userId == null) return "";
        String prefs = userPreferenceService.formatForPrompt(userId);
        if (prefs.isBlank()) return "";
        return "USER PREFERENCE (học từ lịch sử chat — ưu tiên combo phù hợp):\n" + prefs;
    }

    private String serializeCard(ChatProductCard card) {
        if (card == null) return "null";
        return String.format("{productId:%d, colorId:%d, name:\"%s\", colorFamily:\"%s\", fitType:\"%s\", role:\"%s\"}",
            card.getId(), card.getColorId(), safe(card.getName()),
            safe(card.getColorFamily()), safe(card.getFitType()), safe(card.getRole()));
    }

    private String serializeCards(List<ChatProductCard> cards) {
        if (cards == null || cards.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (ChatProductCard c : cards) {
            sb.append(serializeCard(c)).append(",");
        }
        if (sb.length() > 1) sb.setLength(sb.length() - 1);
        sb.append("]");
        return sb.toString();
    }

    private String safe(String s) {
        return s != null ? s : "";
    }

    // ==========================================
    // Parse + Phase D Validate
    // ==========================================

    private List<OutfitComboResponse> parseAndValidate(
            String rawResponse,
            ChatProductCard base,
            String anchorRole,
            Set<String> allowedKeys) {
        try {
            // Strip markdown wrapper if present
            String cleaned = rawResponse.trim();
            if (cleaned.startsWith("```json")) cleaned = cleaned.substring(7);
            if (cleaned.startsWith("```"))     cleaned = cleaned.substring(3);
            if (cleaned.endsWith("```"))       cleaned = cleaned.substring(0, cleaned.length() - 3);
            cleaned = cleaned.trim();

            // Find first JSON object
            int jsonStart = cleaned.indexOf('{');
            if (jsonStart < 0) {
                log.warn("[OUTFIT_AI] provider=GEMINI parse_error: no JSON object in response");
                return List.of();
            }
            cleaned = cleaned.substring(jsonStart);

            JsonNode root = objectMapper.readTree(cleaned);
            JsonNode combosNode = root.path("combos");
            if (!combosNode.isArray()) {
                log.warn("[OUTFIT_AI] provider=GEMINI parse_error: 'combos' field missing or not array");
                return List.of();
            }

            List<OutfitComboResponse> result = new ArrayList<>();
            for (JsonNode comboNode : combosNode) {
                OutfitComboResponse combo = validateAndBuildCombo(comboNode, base, anchorRole, allowedKeys);
                if (combo != null) {
                    result.add(combo);
                }
                if (result.size() >= MAX_COMBOS) break;
            }
            return result;
        } catch (Exception e) {
            log.warn("[OUTFIT_AI] provider=GEMINI parse_failed reason={}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Phase D: Validate một combo từ AI.
     * - top/bottom bắt buộc (trừ khi anchor là top/bottom)
     * - productId:colorId phải nằm trong allowedKeys
     * - outer: nếu sai → bỏ outer, giữ combo
     * - score: clamp về [0,1]
     */
    private OutfitComboResponse validateAndBuildCombo(
            JsonNode node,
            ChatProductCard base,
            String anchorRole,
            Set<String> allowedKeys) {
        try {
            String label = node.path("label").asText("Gợi ý outfit");
            double score = node.path("score").asDouble(0.5);
            if (score < 0 || score > 1) score = 0.5;
            String reason = node.path("reason").asText("");
            String colorStory = node.path("colorStory").asText("");

            List<String> occasion = new ArrayList<>();
            for (JsonNode o : node.path("occasion")) {
                occasion.add(o.asText());
            }

            // Resolve slots — anchor counts as its slot
            JsonNode topNode    = node.path("top");
            JsonNode bottomNode = node.path("bottom");
            JsonNode outerNode  = node.path("outer");

            OutfitSlot topSlot    = resolveSlot(topNode,    "top",    base, anchorRole, allowedKeys);
            OutfitSlot bottomSlot = resolveSlot(bottomNode, "bottom", base, anchorRole, allowedKeys);
            OutfitSlot outerSlot  = resolveSlot(outerNode,  "outer",  base, anchorRole, allowedKeys);

            // Validate: top hoặc bottom phải có (không thể cả hai đều null)
            boolean hasTop    = topSlot != null;
            boolean hasBottom = bottomSlot != null;
            boolean anchorIsTop    = "top".equals(anchorRole);
            boolean anchorIsBottom = "bottom".equals(anchorRole) || "skirt".equals(anchorRole);

            if (!hasTop && !anchorIsTop) {
                log.debug("[OUTFIT_VALIDATE] skip combo label='{}' reason=missing_top", label);
                return null;
            }
            if (!hasBottom && !anchorIsBottom && !"dress".equals(anchorRole)) {
                log.debug("[OUTFIT_VALIDATE] skip combo label='{}' reason=missing_bottom", label);
                return null;
            }

            // Build products list
            List<ChatProductCard> products = new ArrayList<>();
            addBaseToProducts(products, base);

            return OutfitComboResponse.builder()
                .label(label)
                .style(label)
                .outfitType(label)
                .score(score)
                .reason(reason)
                .colorStory(colorStory)
                .occasion(occasion.isEmpty() ? null : String.join(", ", occasion))
                .provider("GEMINI")
                .topSlot(topSlot)
                .bottomSlot(bottomSlot)
                .outerSlot(outerSlot)
                .products(products)
                .items(products)
                .build();
        } catch (Exception e) {
            log.warn("[OUTFIT_VALIDATE] skip combo reason={}", e.getMessage());
            return null;
        }
    }

    private OutfitSlot resolveSlot(
            JsonNode slotNode,
            String slotRole,
            ChatProductCard base,
            String anchorRole,
            Set<String> allowedKeys) {

        // Nếu anchor là slot này → dùng base
        boolean anchorIsThisSlot = switch (slotRole) {
            case "top"    -> "top".equals(anchorRole);
            case "bottom" -> "bottom".equals(anchorRole) || "skirt".equals(anchorRole);
            case "outer"  -> "outerwear".equals(anchorRole);
            default -> false;
        };
        if (anchorIsThisSlot) {
            return slotFrom(base, slotRole, false);
        }

        // Parse AI slot
        if (slotNode == null || slotNode.isMissingNode() || slotNode.isNull()) {
            return null;
        }
        if (!slotNode.has("productId") && !slotNode.has("id")) {
            return null;
        }
        long pid = slotNode.has("productId") ? slotNode.get("productId").asLong()
                                              : slotNode.get("id").asLong();
        long cid = slotNode.path("colorId").asLong(0);

        String key = pid + ":" + cid;
        if (!allowedKeys.contains(key)) {
            // Thử với colorId=0 (bất kỳ màu nào)
            boolean pidAllowed = allowedKeys.stream().anyMatch(k -> k.startsWith(pid + ":"));
            if (!pidAllowed) {
                log.debug("[OUTFIT_VALIDATE] slot={} productId={} colorId={} not in allowedSet", slotRole, pid, cid);
                return null;
            }
        }

        return OutfitSlot.builder()
            .productId(pid)
            .colorId(cid > 0 ? cid : null)
            .slotRole(slotRole)
            .optional("outer".equals(slotRole))
            .build();
    }

    private OutfitSlot slotFrom(ChatProductCard product, String slotRole, boolean optional) {
        if (product == null) return null;
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

    private void addBaseToProducts(List<ChatProductCard> products, ChatProductCard base) {
        if (base != null && products.stream().noneMatch(p -> p.getId().equals(base.getId()))) {
            products.add(base);
        }
    }

    private Set<String> buildAllowedKeys(
            ChatProductCard base,
            List<ChatProductCard> tops,
            List<ChatProductCard> bottoms,
            List<ChatProductCard> outers) {

        Set<String> keys = new java.util.HashSet<>();
        if (base != null) keys.add(base.getId() + ":" + base.getColorId());

        for (ChatProductCard c : tops)    keys.add(c.getId() + ":" + c.getColorId());
        for (ChatProductCard c : bottoms) keys.add(c.getId() + ":" + c.getColorId());
        for (ChatProductCard c : outers)  keys.add(c.getId() + ":" + c.getColorId());
        return keys;
    }
}
