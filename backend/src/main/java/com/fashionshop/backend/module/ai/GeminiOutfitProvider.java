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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiOutfitProvider {

    private static final int MAX_COMBOS = 3;

    private final AiClientRouter aiClientRouter;
    private final ObjectMapper objectMapper;

    public List<OutfitComboResponse> generateCombos(
            ChatProductCard base,
            String anchorRole,
            List<ChatProductCard> topCandidates,
            List<ChatProductCard> bottomCandidates,
            List<ChatProductCard> outerCandidates,
            Long userId) {
        Map<String, ChatProductCard> pool = buildPool(base, topCandidates, bottomCandidates, outerCandidates);
        String prompt = buildPrompt(base, anchorRole, topCandidates, bottomCandidates, outerCandidates);
        try {
            String raw = aiClientRouter.generate(prompt, List.of(), "Chon toi da 3 combo va tra JSON.");
            return parseAndValidate(raw, base, anchorRole, pool);
        } catch (Exception e) {
            log.warn("[OUTFIT_AI] provider=GEMINI failed fallback=RULE reason={}", e.getMessage());
            return List.of();
        }
    }

    private String buildPrompt(
            ChatProductCard base,
            String anchorRole,
            List<ChatProductCard> tops,
            List<ChatProductCard> bottoms,
            List<ChatProductCard> outers) {
        return """
            Ban la stylist cho shop thoi trang Viet Nam.
            Chi chon productId/colorId trong candidate pool. Khong tu tao san pham.
            Moi combo phai dung slot rules: %s
            Outer la optional tru khi san pham goc la OUTER.
            Tra JSON thuan: {"combos":[{"label":"...","score":0.9,"reason":"...","colorStory":"...","occasion":["daily"],"top":{"productId":1,"colorId":2},"bottom":{"productId":3,"colorId":4},"outer":null}]}
            BASE role=%s: %s
            TOP: %s
            BOTTOM: %s
            OUTER: %s
            """.formatted(slotRules(anchorRole), anchorRole, serialize(base), serialize(tops), serialize(bottoms), serialize(outers));
    }

    private String slotRules(String anchorRole) {
        return switch (anchorRole == null ? "" : anchorRole) {
            case "top" -> "TOP base requires BOTTOM; OUTER optional";
            case "bottom" -> "BOTTOM base requires TOP; OUTER optional";
            case "outer" -> "OUTER base requires TOP and BOTTOM";
            case "dress" -> "DRESS base allows optional OUTER only";
            default -> "requires TOP and BOTTOM; OUTER optional";
        };
    }

    private String serialize(List<ChatProductCard> cards) {
        return cards == null ? "[]" : cards.stream().map(this::serialize).toList().toString();
    }

    private String serialize(ChatProductCard card) {
        if (card == null) return "null";
        return "{productId:%s,colorId:%s,name:\"%s\",role:%s,gender:%s}".formatted(
            card.getId(), card.getColorId(), safe(card.getName()), card.getRole(), card.getGender());
    }

    private List<OutfitComboResponse> parseAndValidate(
            String raw,
            ChatProductCard base,
            String anchorRole,
            Map<String, ChatProductCard> pool) {
        try {
            String json = stripToJson(raw);
            JsonNode combos = objectMapper.readTree(json).path("combos");
            if (!combos.isArray()) return List.of();
            List<OutfitComboResponse> valid = new ArrayList<>();
            for (JsonNode node : combos) {
                OutfitComboResponse combo = validateCombo(node, base, anchorRole, pool);
                if (combo != null) valid.add(combo);
                if (valid.size() >= MAX_COMBOS) break;
            }
            return valid;
        } catch (Exception e) {
            log.warn("[OUTFIT_AI] provider=GEMINI parse_failed fallback=RULE reason={}", e.getMessage());
            return List.of();
        }
    }

    private OutfitComboResponse validateCombo(
            JsonNode node,
            ChatProductCard base,
            String anchorRole,
            Map<String, ChatProductCard> pool) {
        ChatProductCard top = resolve(node.path("top"), "top", base, anchorRole, pool);
        ChatProductCard bottom = resolve(node.path("bottom"), "bottom", base, anchorRole, pool);
        ChatProductCard outer = resolve(node.path("outer"), "outer", base, anchorRole, pool);
        if (!validStructure(anchorRole, top, bottom, outer) || duplicateIds(top, bottom, outer)) {
            return null;
        }
        List<ChatProductCard> products = new ArrayList<>();
        add(products, base);
        add(products, top);
        add(products, bottom);
        add(products, outer);
        List<String> occasions = new ArrayList<>();
        node.path("occasion").forEach(value -> occasions.add(value.asText()));
        return OutfitComboResponse.builder()
            .label(node.path("label").asText("Goi y outfit"))
            .style(node.path("label").asText("outfit"))
            .outfitType(node.path("label").asText("outfit"))
            .score(clamp(node.path("score").asDouble(0.5)))
            .reason(node.path("reason").asText(""))
            .colorStory(node.path("colorStory").asText(""))
            .occasion(occasions.isEmpty() ? null : String.join(", ", occasions))
            .provider("GEMINI")
            .topSlot(slot(top, "top", false))
            .bottomSlot(slot(bottom, "bottom", false))
            .outerSlot(slot(outer, "outer", true))
            .products(products)
            .items(products)
            .build();
    }

    private ChatProductCard resolve(
            JsonNode node,
            String slotRole,
            ChatProductCard base,
            String anchorRole,
            Map<String, ChatProductCard> pool) {
        if (slotRole.equals(anchorRole)) return base;
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        long productId = node.has("productId") ? node.path("productId").asLong() : node.path("id").asLong();
        long colorId = node.path("colorId").asLong();
        ChatProductCard card = pool.get(productId + ":" + colorId);
        if (card == null || !slotRole.equals(card.getRole()) || !genderCompatible(base, card)) return null;
        return card;
    }

    private boolean validStructure(String anchorRole, ChatProductCard top, ChatProductCard bottom, ChatProductCard outer) {
        return switch (anchorRole == null ? "" : anchorRole) {
            case "top" -> top != null && bottom != null;
            case "bottom" -> top != null && bottom != null;
            case "outer" -> top != null && bottom != null && outer != null;
            case "dress" -> top == null && bottom == null;
            default -> top != null && bottom != null;
        };
    }

    private boolean duplicateIds(ChatProductCard... cards) {
        Set<Long> ids = new HashSet<>();
        for (ChatProductCard card : cards) {
            if (card != null && !ids.add(card.getId())) return true;
        }
        return false;
    }

    private boolean genderCompatible(ChatProductCard base, ChatProductCard candidate) {
        if (base == null || candidate == null || base.getGender() == null) return false;
        return base.getGender().equalsIgnoreCase(candidate.getGender()) || "UNISEX".equalsIgnoreCase(candidate.getGender());
    }

    private Map<String, ChatProductCard> buildPool(ChatProductCard base, List<ChatProductCard> tops,
                                                   List<ChatProductCard> bottoms, List<ChatProductCard> outers) {
        Map<String, ChatProductCard> pool = new HashMap<>();
        add(pool, base);
        tops.forEach(card -> add(pool, card));
        bottoms.forEach(card -> add(pool, card));
        outers.forEach(card -> add(pool, card));
        return pool;
    }

    private void add(Map<String, ChatProductCard> pool, ChatProductCard card) {
        if (card != null) pool.put(card.getId() + ":" + card.getColorId(), card);
    }

    private void add(List<ChatProductCard> products, ChatProductCard card) {
        if (card != null && products.stream().noneMatch(item -> item.getId().equals(card.getId()))) products.add(card);
    }

    private OutfitSlot slot(ChatProductCard card, String role, boolean optional) {
        if (card == null) return null;
        return OutfitSlot.builder()
            .productId(card.getId()).colorId(card.getColorId()).productName(card.getName())
            .colorName(card.getColorName()).colorCode(card.getColorCode()).colorFamily(card.getColorFamily())
            .slotRole(role).optional(optional).imageUrl(card.getImageUrl()).productUrl(card.getUrl()).build();
    }

    private String stripToJson(String raw) {
        String cleaned = raw == null ? "" : raw.replace("```json", "").replace("```", "").trim();
        int start = cleaned.indexOf('{');
        if (start < 0) throw new IllegalArgumentException("Gemini output has no JSON object");
        return cleaned.substring(start);
    }

    private double clamp(double score) {
        return score < 0 || score > 1 ? 0.5 : score;
    }

    private String safe(String value) {
        return value == null ? "" : value.replace("\"", "'");
    }
}
