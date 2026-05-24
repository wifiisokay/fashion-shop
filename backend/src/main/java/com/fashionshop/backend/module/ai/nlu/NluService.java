package com.fashionshop.backend.module.ai.nlu;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashionshop.backend.module.ai.AiClientRouter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NluService {

    private final AiClientRouter aiClientRouter;
    private final ObjectMapper objectMapper;

    private static final String SYSTEM = """
        Bạn là AI phân tích yêu cầu mua sắm thời trang. Chỉ trả JSON thuần, không markdown, không giải thích.
        Intent hợp lệ: PRODUCT_SEARCH, OUTFIT_SUGGEST, CHITCHAT.
        Gender hợp lệ: MALE, FEMALE, null. Color family hợp lệ: neutral, cool, warm, earth, mixed, null.
        Quy tắc màu: tím/lavender/purple/burgundy -> warm; xanh/blue/navy/mint/green/teal -> cool;
        be/kem/nude/trắng/đen/xám/ghi -> neutral; nâu/camel/olive/rêu/gạch/đất -> earth.
        Nếu message là follow-up như "còn màu nào", "có size L không", "còn mẫu nào không" thì isFollowUp=true.
        """;

    public NluSearchParams extract(String message, String previousIntent, NluSearchParams previousParams) {
        try {
            String raw = aiClientRouter.generate(SYSTEM, List.of(), buildPrompt(message, previousIntent, previousParams));
            NluSearchParams result = objectMapper.readValue(extractJson(raw), NluSearchParams.class);
            NluSearchParams merged = result.isFollowUp() ? merge(previousParams, result) : result;
            log.info("[NLU] message='{}' previousIntent={} resultIntent={} followUp={} colorFamily={} categories={}",
                shorten(message), previousIntent, merged.getIntent(), merged.isFollowUp(), merged.getColorFamily(), merged.getCategoryKeywords());
            return merged;
        } catch (Exception e) {
            log.warn("[NLU] extraction failed, fallback to rule-based. message='{}' error={}", shorten(message), e.getMessage());
            return null;
        }
    }

    private String buildPrompt(String message, String previousIntent, NluSearchParams previousParams) {
        String previousJson = "{}";
        try {
            if (previousParams != null) {
                previousJson = objectMapper.writeValueAsString(previousParams);
            }
        } catch (Exception ignored) {
        }
        return """
            Message hiện tại: "%s"
            Intent trước: %s
            Params trước: %s

            Trả JSON đúng shape:
            {
              "intent": "PRODUCT_SEARCH|OUTFIT_SUGGEST|CHITCHAT",
              "gender": "MALE|FEMALE|null",
              "colorFamily": "neutral|cool|warm|earth|mixed|null",
              "colorKeyword": "tên màu cụ thể user nhắc hoặc null",
              "categoryKeywords": ["áo thun", "quần jeans"],
              "occasionKeywords": ["đi làm", "đi chơi"],
              "styleKeywords": ["casual", "minimal"],
              "fitKeyword": "rộng|ôm|vừa|null",
              "priceMax": 300000,
              "isSale": true,
              "isFollowUp": false
            }
            """.formatted(message, previousIntent == null ? "none" : previousIntent, previousJson);
    }

    private NluSearchParams merge(NluSearchParams previous, NluSearchParams current) {
        if (previous == null) {
            return current;
        }
        if (current.getIntent() == null) current.setIntent(previous.getIntent());
        if (current.getGender() == null) current.setGender(previous.getGender());
        if (current.getColorFamily() == null) current.setColorFamily(previous.getColorFamily());
        if (current.getColorKeyword() == null) current.setColorKeyword(previous.getColorKeyword());
        if (isEmpty(current.getCategoryKeywords())) current.setCategoryKeywords(previous.getCategoryKeywords());
        if (isEmpty(current.getOccasionKeywords())) current.setOccasionKeywords(previous.getOccasionKeywords());
        if (isEmpty(current.getStyleKeywords())) current.setStyleKeywords(previous.getStyleKeywords());
        if (current.getFitKeyword() == null) current.setFitKeyword(previous.getFitKeyword());
        if (current.getPriceMax() == null) current.setPriceMax(previous.getPriceMax());
        if (current.getIsSale() == null) current.setIsSale(previous.getIsSale());
        return current;
    }

    private boolean isEmpty(List<String> values) {
        return values == null || values.isEmpty();
    }

    private String extractJson(String raw) throws Exception {
        String cleaned = raw == null ? "" : raw.trim();
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
        JsonNode node = objectMapper.readTree(cleaned);
        return objectMapper.writeValueAsString(node);
    }

    private String shorten(String value) {
        if (value == null) return "";
        String trimmed = value.replaceAll("\\s+", " ").trim();
        return trimmed.length() > 120 ? trimmed.substring(0, 120) + "..." : trimmed;
    }
}
