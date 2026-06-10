package com.fashionshop.backend.module.ai.nlu;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashionshop.backend.module.ai.AiClientRouter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class NluService {

    private static final Set<String> ALLOWED_INTENTS = Set.of(
        "PRODUCT_SEARCH",
        "OUTFIT_SUGGEST",
        "OUTFIT_BY_OCCASION",
        "OUTFIT_BY_PRODUCT",
        "OUTFIT_BY_PRODUCT_AND_OCCASION",
        "STYLE_ADVICE",
        "CHITCHAT"
    );

    private static final String SYSTEM = """
        You are Fashion Shop's Vietnamese shopping NLU parser.
        Return pure JSON only. Do not return markdown or explanations.
        Normalize vague user shopping requests into structured fields.
        Never invent product ids or product names.
        """;

    private final AiClientRouter aiClientRouter;
    private final ObjectMapper objectMapper;

    public NluSearchParams extract(String message, String previousIntent, NluSearchParams previousParams) {
        try {
            String raw = aiClientRouter.generate(SYSTEM, List.of(), buildPrompt(message, previousIntent, previousParams));
            NluSearchParams result = objectMapper.readValue(extractJson(raw), NluSearchParams.class);
            NluSearchParams sanitized = sanitize(result);
            if (sanitized == null) {
                return null;
            }
            NluSearchParams merged = sanitized.isFollowUp() ? merge(previousParams, sanitized) : sanitized;
            log.info("[NLU] message='{}' previousIntent={} resultIntent={} followUp={} category={} occasion={} style={}",
                shorten(message), previousIntent, merged.getIntent(), merged.isFollowUp(),
                merged.getCategoryKeywords(), merged.getOccasionKeywords(), merged.getStyleKeywords());
            return merged;
        } catch (Exception e) {
            log.warn("[NLU] extraction_failed fallback=rule message='{}' error={}", shorten(message), e.getMessage());
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
            Current message: "%s"
            Previous intent: %s
            Previous params: %s

            Return this JSON shape:
            {
              "intent": "PRODUCT_SEARCH|OUTFIT_SUGGEST|OUTFIT_BY_OCCASION|OUTFIT_BY_PRODUCT|OUTFIT_BY_PRODUCT_AND_OCCASION|STYLE_ADVICE|CHITCHAT",
              "productType": "ao|quan|vay|khoac|null",
              "categoryHint": "ao thun|quan jeans|null",
              "gender": "MALE|FEMALE|null",
              "occasion": "di choi|di lam|di hoc|du lich|null",
              "season": "spring|summer|autumn|winter|null",
              "budget": 300000,
              "style": "casual|minimal|streetwear|null",
              "isFollowUp": false,
              "referencedProductOrdinal": 2,
              "colorFamily": "neutral|cool|warm|earth|mixed|null",
              "colorKeyword": "specific color or null",
              "categoryKeywords": ["ao thun", "quan jeans"],
              "occasionKeywords": ["di lam", "di choi"],
              "styleKeywords": ["casual", "minimal"],
              "fitKeyword": "rong|om|vua|null",
              "priceMax": 300000,
              "isSale": true
            }
            """.formatted(message, previousIntent == null ? "none" : previousIntent, previousJson);
    }

    private NluSearchParams sanitize(NluSearchParams params) {
        if (params == null) {
            return null;
        }
        String intent = normalizeBlank(params.getIntent());
        if (intent != null) {
            intent = intent.toUpperCase(Locale.ROOT);
        }
        params.setIntent(intent != null && ALLOWED_INTENTS.contains(intent) ? intent : null);
        params.setGender(normalizeEnum(params.getGender(), Set.of("MALE", "FEMALE")));
        params.setColorFamily(normalizeEnum(params.getColorFamily(), Set.of("neutral", "cool", "warm", "earth", "mixed")));
        params.setCategoryKeywords(mergeKeywords(params.getCategoryKeywords(), params.getCategoryHint(), params.getProductType()));
        params.setOccasionKeywords(mergeKeywords(params.getOccasionKeywords(), params.getOccasion()));
        params.setStyleKeywords(mergeKeywords(params.getStyleKeywords(), params.getStyle()));
        if (params.getPriceMax() == null && params.getBudget() != null && params.getBudget() > 0) {
            params.setPriceMax(params.getBudget());
        }
        if (params.getPriceMax() != null && params.getPriceMax() <= 0) {
            params.setPriceMax(null);
        }
        return params;
    }

    private NluSearchParams merge(NluSearchParams previous, NluSearchParams current) {
        if (previous == null) {
            return current;
        }
        if (current.getIntent() == null) current.setIntent(previous.getIntent());
        if (current.getProductType() == null) current.setProductType(previous.getProductType());
        if (current.getCategoryHint() == null) current.setCategoryHint(previous.getCategoryHint());
        if (current.getGender() == null) current.setGender(previous.getGender());
        if (current.getColorFamily() == null) current.setColorFamily(previous.getColorFamily());
        if (current.getColorKeyword() == null) current.setColorKeyword(previous.getColorKeyword());
        if (isEmpty(current.getCategoryKeywords())) current.setCategoryKeywords(previous.getCategoryKeywords());
        if (current.getOccasion() == null) current.setOccasion(previous.getOccasion());
        if (isEmpty(current.getOccasionKeywords())) current.setOccasionKeywords(previous.getOccasionKeywords());
        if (current.getStyle() == null) current.setStyle(previous.getStyle());
        if (isEmpty(current.getStyleKeywords())) current.setStyleKeywords(previous.getStyleKeywords());
        if (current.getSeason() == null) current.setSeason(previous.getSeason());
        if (current.getFitKeyword() == null) current.setFitKeyword(previous.getFitKeyword());
        if (current.getBudget() == null) current.setBudget(previous.getBudget());
        if (current.getPriceMax() == null) current.setPriceMax(previous.getPriceMax());
        if (current.getReferencedProductOrdinal() == null) {
            current.setReferencedProductOrdinal(previous.getReferencedProductOrdinal());
        }
        if (current.getIsSale() == null) current.setIsSale(previous.getIsSale());
        return current;
    }

    private List<String> mergeKeywords(List<String> existing, String... extraValues) {
        List<String> values = new ArrayList<>();
        if (existing != null) {
            existing.stream()
                .map(this::normalizeBlank)
                .filter(v -> v != null && !values.contains(v))
                .forEach(values::add);
        }
        for (String extra : extraValues) {
            String normalized = normalizeBlank(extra);
            if (normalized != null && !values.contains(normalized)) {
                values.add(normalized);
            }
        }
        return values;
    }

    private boolean isEmpty(List<String> values) {
        return values == null || values.isEmpty();
    }

    private String normalizeEnum(String value, Set<String> allowedValues) {
        String normalized = normalizeBlank(value);
        if (normalized == null) {
            return null;
        }
        for (String allowed : allowedValues) {
            if (allowed.equalsIgnoreCase(normalized)) {
                return allowed;
            }
        }
        return null;
    }

    private String normalizeBlank(String value) {
        if (value == null || value.isBlank() || "null".equalsIgnoreCase(value)) {
            return null;
        }
        return value.trim();
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
