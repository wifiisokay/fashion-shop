package com.fashionshop.backend.module.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashionshop.backend.module.ai.dto.ChatContext;
import com.fashionshop.backend.module.ai.dto.response.ChatProductCard;
import com.fashionshop.backend.module.ai.dto.response.OutfitComboResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiStyleAnswerService {

    private final AiClientRouter aiClientRouter;
    private final ObjectMapper objectMapper;

    private static final String SYSTEM = """
        Bạn là stylist AI của Fashion Shop. Chỉ trả JSON thuần, không markdown, không giải thích.
        Không bịa sản phẩm mới. Chỉ sử dụng dữ liệu trong input.
        Ngôn ngữ: tiếng Việt tự nhiên, súc tích.
        Schema:
        {
          "content": "string",
          "comboExplanations": [{"comboId":"string","reason":"string"}],
          "styleTips": ["string"],
          "suggestedQuestions": ["string", "string"]
        }
        Quy tắc:
        - content 2-4 câu.
        - styleTips 2-4 bullet ngắn.
        - suggestedQuestions 2-3 câu hỏi liên quan.
        - Không nhắc giá, không nhắc hàng tồn.
        """;

    public StyleAnswerPayload generate(ChatContext context, ChatProductCard anchor, List<OutfitComboResponse> combos) {
        try {
            String prompt = buildPrompt(context, anchor, combos);
            String raw = aiClientRouter.generate(SYSTEM, List.of(), prompt);
            String cleaned = JsonUtils.extractJson(raw);
            StyleAnswerPayload payload = objectMapper.readValue(cleaned, StyleAnswerPayload.class);
            if (payload == null || payload.content == null || payload.content.isBlank()) {
                throw new IllegalArgumentException("Empty content");
            }
            return payload;
        } catch (Exception e) {
            log.warn("[AI_STYLE_ANSWER] failed: {}", e.getMessage());
            return null;
        }
    }

    private String buildPrompt(ChatContext context, ChatProductCard anchor, List<OutfitComboResponse> combos) throws Exception {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("userMessage", context.getOriginalMessage());
        input.put("internalIntent", context.getInternalIntent() != null ? context.getInternalIntent().name() : null);
        input.put("gender", context.getGender());
        Map<String, Object> occasion = new LinkedHashMap<>();
        occasion.put("tag", context.getOccasionTag());
        occasion.put("label", context.getOccasionLabel());
        input.put("occasion", occasion);
        input.put("styleTag", context.getStyleTag());
        if (anchor != null) {
            Map<String, Object> anchorProduct = new LinkedHashMap<>();
            anchorProduct.put("id", anchor.getId());
            anchorProduct.put("name", anchor.getName());
            anchorProduct.put("color", anchor.getColorName());
            anchorProduct.put("role", anchor.getRole());
            anchorProduct.put("material", anchor.getMaterial());
            anchorProduct.put("season", anchor.getSeason());
            anchorProduct.put("styleTags", anchor.getStyleTags());
            anchorProduct.put("occasionTags", anchor.getOccasionTags());
            input.put("anchorProduct", anchorProduct);
        }

        List<Map<String, Object>> comboPayload = new ArrayList<>();
        if (combos != null) {
            int idx = 1;
            for (OutfitComboResponse combo : combos) {
                Map<String, Object> comboData = new LinkedHashMap<>();
                comboData.put("comboId", "combo-" + idx++);
                comboData.put("label", combo.getLabel());
                comboData.put("colorStory", combo.getColorStory());
                comboData.put("occasion", combo.getOccasion());
                comboData.put("items", summarizeComboItems(combo));
                comboPayload.add(comboData);
            }
        }
        input.put("combos", comboPayload);
        return objectMapper.writeValueAsString(input);
    }

    private List<Map<String, Object>> summarizeComboItems(OutfitComboResponse combo) {
        List<Map<String, Object>> items = new ArrayList<>();
        if (combo.getTopSlot() != null) {
            items.add(slotItem("top", combo.getTopSlot().getProductId(), combo.getTopSlot().getProductName(), combo.getTopSlot().getColorName()));
        }
        if (combo.getBottomSlot() != null) {
            items.add(slotItem("bottom", combo.getBottomSlot().getProductId(), combo.getBottomSlot().getProductName(), combo.getBottomSlot().getColorName()));
        }
        if (combo.getOuterSlot() != null) {
            items.add(slotItem("outer", combo.getOuterSlot().getProductId(), combo.getOuterSlot().getProductName(), combo.getOuterSlot().getColorName()));
        }
        if (combo.getItems() != null && !combo.getItems().isEmpty()) {
            for (ChatProductCard item : combo.getItems()) {
                items.add(slotItem(item.getRole(), item.getId(), item.getName(), item.getColorName()));
            }
        }
        if (combo.getProducts() != null && !combo.getProducts().isEmpty()) {
            for (ChatProductCard item : combo.getProducts()) {
                items.add(slotItem(item.getRole(), item.getId(), item.getName(), item.getColorName()));
            }
        }
        return items;
    }

    private Map<String, Object> slotItem(String role, Long id, String name, String color) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("productId", id);
        item.put("role", role);
        item.put("name", name);
        item.put("color", color);
        return item;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StyleAnswerPayload {
        public String content;
        public List<ComboExplanation> comboExplanations;
        public List<String> styleTips;
        public List<String> suggestedQuestions;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ComboExplanation {
        public String comboId;
        public String reason;
    }
}
