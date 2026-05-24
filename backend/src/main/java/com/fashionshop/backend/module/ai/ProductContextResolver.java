package com.fashionshop.backend.module.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashionshop.backend.common.enums.ChatRole;
import com.fashionshop.backend.domain.ChatMessage;
import com.fashionshop.backend.module.ai.dto.ProductContextDto;
import com.fashionshop.backend.module.ai.dto.response.ChatProductCard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductContextResolver {

    private static final Pattern ORDINAL_PATTERN = Pattern.compile(
        "(?:mau|cai|san pham)\\s+thu\\s+(\\d+|nhat|hai|ba)",
        Pattern.CASE_INSENSITIVE
    );

    private final ProductRetrieverService productRetrieverService;
    private final ObjectMapper objectMapper;

    public Optional<ProductContextDto> resolve(String message, ProductContextDto explicitContext, List<ChatMessage> recentMessages) {
        Optional<ProductContextDto> explicit = validateExplicit(explicitContext);
        if (explicit.isPresent()) {
            return explicit;
        }

        Optional<ProductContextDto> ordinal = resolveOrdinal(message, recentMessages);
        if (ordinal.isPresent()) {
            return ordinal;
        }

        if (hasPronounReference(message)) {
            Optional<ProductContextDto> primary = latestPrimaryContext(recentMessages);
            if (primary.isPresent()) {
                return primary;
            }
        }

        return Optional.empty();
    }

    public boolean hasPronounReference(String message) {
        String normalized = normalizeVi(message);
        return containsAny(normalized,
            " no ", " mau do ", " mau nay ", " cai do ", " cai nay ",
            " san pham do ", " san pham nay ", " ao do ", " ao nay ",
            " quan do ", " quan nay ", " vay do ", " vay nay ");
    }

    private Optional<ProductContextDto> validateExplicit(ProductContextDto context) {
        if (context == null || context.getProductId() == null) {
            return Optional.empty();
        }
        try {
            return productRetrieverService.findProductCard(context.getProductId(), context.getColorId())
                .map(this::fromCard);
        } catch (Exception e) {
            log.warn("[AI_CONTEXT_RESOLVE] explicit productId={} colorId={} invalid={}",
                context.getProductId(), context.getColorId(), e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<ProductContextDto> resolveOrdinal(String message, List<ChatMessage> recentMessages) {
        String normalized = normalizeVi(message);
        Matcher matcher = ORDINAL_PATTERN.matcher(normalized);
        if (!matcher.find()) {
            return Optional.empty();
        }
        int index = switch (matcher.group(1)) {
            case "nhat" -> 0;
            case "hai" -> 1;
            case "ba" -> 2;
            default -> Math.max(0, Integer.parseInt(matcher.group(1)) - 1);
        };
        for (ChatMessage messageRow : safeRecent(recentMessages)) {
            if (messageRow.getRole() != ChatRole.ASSISTANT || messageRow.getMetadata() == null) {
                continue;
            }
            try {
                JsonNode products = objectMapper.readTree(messageRow.getMetadata()).path("products");
                if (products.isArray() && products.size() > index) {
                    return Optional.of(contextFromJson(products.get(index)));
                }
            } catch (Exception e) {
                log.debug("[AI_CONTEXT_RESOLVE] ordinal metadata skipped: {}", e.getMessage());
            }
        }
        return Optional.empty();
    }

    private Optional<ProductContextDto> latestPrimaryContext(List<ChatMessage> recentMessages) {
        for (ChatMessage messageRow : safeRecent(recentMessages)) {
            if (messageRow.getRole() != ChatRole.ASSISTANT || messageRow.getMetadata() == null) {
                continue;
            }
            try {
                JsonNode root = objectMapper.readTree(messageRow.getMetadata());
                JsonNode primary = root.path("primaryProductContext");
                if (primary.isMissingNode() || primary.isNull()) {
                    primary = root.path("baseProductContext");
                }
                if (!primary.isMissingNode() && !primary.isNull() && primary.path("productId").canConvertToLong()) {
                    return Optional.of(contextFromJson(primary));
                }
            } catch (Exception e) {
                log.debug("[AI_CONTEXT_RESOLVE] primary metadata skipped: {}", e.getMessage());
            }
        }
        return Optional.empty();
    }

    private ProductContextDto contextFromJson(JsonNode node) {
        return ProductContextDto.builder()
            .productId(node.path("productId").asLong())
            .colorId(node.path("colorId").isMissingNode() || node.path("colorId").isNull() ? null : node.path("colorId").asLong())
            .name(node.path("name").asText(null))
            .colorName(node.path("colorName").asText(null))
            .build();
    }

    private ProductContextDto fromCard(ChatProductCard card) {
        return ProductContextDto.builder()
            .productId(card.getId())
            .colorId(card.getColorId())
            .name(card.getName())
            .colorName(card.getColorName())
            .build();
    }

    private List<ChatMessage> safeRecent(List<ChatMessage> recentMessages) {
        return recentMessages == null ? List.of() : recentMessages;
    }

    private boolean containsAny(String value, String... tokens) {
        for (String token : tokens) {
            if (value.contains(token)) {
                return true;
            }
        }
        return false;
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
