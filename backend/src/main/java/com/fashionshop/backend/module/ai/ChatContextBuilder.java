package com.fashionshop.backend.module.ai;

import com.fashionshop.backend.common.enums.Gender;
import com.fashionshop.backend.domain.ChatMessage;
import com.fashionshop.backend.module.ai.dto.ChatContext;
import com.fashionshop.backend.module.ai.dto.ProductContextDto;
import com.fashionshop.backend.module.ai.nlu.NluSearchParams;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ChatContextBuilder {

    private final ProductContextResolver productContextResolver;
    private final CategoryKeywordMapper categoryKeywordMapper;
    private final TagTranslationService tagTranslationService;
    private final OccasionNormalizer occasionNormalizer = new OccasionNormalizer();
    private final StyleNormalizer styleNormalizer = new StyleNormalizer();

    public ChatContext build(String message, ProductContextDto explicitContext, List<ChatMessage> recentMessages,
                             NluSearchParams nluParams) {
        String normalized = normalizeVi(message);
        Optional<ProductContextDto> resolved = productContextResolver.resolve(message, explicitContext, recentMessages);
        ProductContextDto productContext = resolved.orElse(null);

        String occasionTag = resolveOccasionTag(message, nluParams);
        String styleTag = resolveStyleTag(message, nluParams);
        String occasionLabel = occasionTag != null ? occasionNormalizer.labelFor(occasionTag) : null;
        String season = resolveSeason(message, nluParams);
        if (styleTag == null) {
            styleTag = styleNormalizer.inferForOccasion(occasionTag);
        }
        String gender = resolveGender(message, nluParams);
        String productType = resolveProductType(message, nluParams);
        Integer categoryId = resolveCategoryId(nluParams, message);

        return ChatContext.builder()
            .originalMessage(message)
            .normalizedMessage(normalized)
            .productId(productContext != null ? productContext.getProductId() : null)
            .colorId(productContext != null ? productContext.getColorId() : null)
            .colorName(productContext != null ? productContext.getColorName() : null)
            .colorFamily(null)
            .gender(gender)
            .categoryId(categoryId)
            .categorySlug(productContext != null ? productContext.getCategorySlug() : null)
            .categoryRole(productContext != null ? productContext.getCategoryRole() : null)
            .productType(productType)
            .occasionTag(occasionTag)
            .occasionLabel(occasionLabel)
            .styleTag(styleTag)
            .season(season)
            .budgetMax(nluParams != null && nluParams.getPriceMax() != null ? nluParams.getPriceMax().longValue() : null)
            .questionType(null)
            .action(null)
            .lastIntent(findLastIntent(recentMessages))
            .lastProductId(productContext != null ? productContext.getProductId() : null)
            .lastColorId(productContext != null ? productContext.getColorId() : null)
            .build();
    }

    private String resolveOccasionTag(String message, NluSearchParams nluParams) {
        if (nluParams != null && nluParams.getOccasionKeywords() != null && !nluParams.getOccasionKeywords().isEmpty()) {
            String joined = String.join(" ", nluParams.getOccasionKeywords());
            Optional<String> nluTag = tagTranslationService.detectOccasionTag(joined);
            if (nluTag.isPresent()) {
                return nluTag.get();
            }
        }
        Optional<String> direct = tagTranslationService.detectOccasionTag(message);
        if (direct.isPresent()) {
            return direct.get();
        }
        return occasionNormalizer.detectTag(message).orElse(null);
    }

    private String resolveStyleTag(String message, NluSearchParams nluParams) {
        if (nluParams != null && nluParams.getStyleKeywords() != null && !nluParams.getStyleKeywords().isEmpty()) {
            String joined = String.join(" ", nluParams.getStyleKeywords());
            Optional<String> nluTag = tagTranslationService.detectStyleTag(joined);
            if (nluTag.isPresent()) {
                return nluTag.get();
            }
        }
        Optional<String> direct = tagTranslationService.detectStyleTag(message);
        if (direct.isPresent()) {
            return direct.get();
        }
        return styleNormalizer.detectTag(message).orElse(null);
    }

    private String resolveGender(String message, NluSearchParams nluParams) {
        if (nluParams != null && nluParams.getGender() != null && !nluParams.getGender().isBlank()) {
            return nluParams.getGender();
        }
        String normalized = normalizeVi(message);
        if (normalized.contains(" nam ")) {
            return Gender.MALE.name();
        }
        if (normalized.contains(" nu ")) {
            return Gender.FEMALE.name();
        }
        return null;
    }

    private Integer resolveCategoryId(NluSearchParams nluParams, String message) {
        if (nluParams != null && nluParams.getCategoryKeywords() != null && !nluParams.getCategoryKeywords().isEmpty()) {
            String joined = String.join(" ", nluParams.getCategoryKeywords());
            List<Integer> ids = categoryKeywordMapper.detectCategoryIds(joined);
            if (!ids.isEmpty()) {
                return ids.get(0);
            }
        }
        List<Integer> ids = categoryKeywordMapper.detectCategoryIds(message == null ? "" : message);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private String resolveProductType(String message, NluSearchParams nluParams) {
        if (nluParams != null && nluParams.getCategoryKeywords() != null && !nluParams.getCategoryKeywords().isEmpty()) {
            return nluParams.getCategoryKeywords().get(0);
        }
        String normalized = normalizeVi(message);
        if (normalized.contains(" ao ")) return "ao";
        if (normalized.contains(" quan ")) return "quan";
        if (normalized.contains(" vay ") || normalized.contains(" dam ")) return "vay";
        if (normalized.contains(" khoac ") || normalized.contains(" jacket ")) return "khoac";
        return null;
    }

    private String resolveSeason(String message, NluSearchParams nluParams) {
        String normalized = normalizeVi(message);
        if (normalized.contains(" he ")) return "summer";
        if (normalized.contains(" dong ")) return "winter";
        if (normalized.contains(" thu ")) return "autumn";
        if (normalized.contains(" xuan ")) return "spring";
        return null;
    }

    private String findLastIntent(List<ChatMessage> recentMessages) {
        if (recentMessages == null) {
            return null;
        }
        for (ChatMessage message : recentMessages) {
            if (message.getIntent() != null && !message.getIntent().isBlank()) {
                return message.getIntent();
            }
        }
        return null;
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
}
