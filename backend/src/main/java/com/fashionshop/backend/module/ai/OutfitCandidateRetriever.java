package com.fashionshop.backend.module.ai;

import com.fashionshop.backend.module.ai.dto.response.ChatProductCard;
import com.fashionshop.backend.module.product.ProductTagLibrary;
import com.fashionshop.backend.module.storage.CloudinaryUrlBuilder;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Objects;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Slf4j
@Service
public class OutfitCandidateRetriever {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String EFFECTIVE_PRICE_SQL = """
        CASE
          WHEN p.is_sale = 1
           AND p.sale_price IS NOT NULL
           AND p.sale_price > 0
           AND p.sale_price < p.base_price
           AND (p.sale_start_at IS NULL OR p.sale_start_at <= NOW())
           AND (p.sale_end_at IS NULL OR p.sale_end_at >= NOW())
          THEN p.sale_price
          ELSE p.base_price
        END
        """;
    private static final String CURRENT_SALE_SQL = """
        CASE
          WHEN p.is_sale = 1
           AND p.sale_price IS NOT NULL
           AND p.sale_price > 0
           AND p.sale_price < p.base_price
           AND (p.sale_start_at IS NULL OR p.sale_start_at <= NOW())
           AND (p.sale_end_at IS NULL OR p.sale_end_at >= NOW())
          THEN 1
          ELSE 0
        END
        """;

    private static final Map<String, Set<String>> COMPATIBLE = Map.of(
        "neutral", Set.of("neutral", "cool", "warm", "earth", "mixed"),
        "cool", Set.of("neutral", "earth", "cool"),
        "warm", Set.of("neutral", "earth", "warm"),
        "earth", Set.of("neutral", "earth", "cool", "warm"),
        "mixed", Set.of("neutral", "cool", "warm", "earth", "mixed")
    );

    @PersistenceContext
    private EntityManager entityManager;

    public List<ChatProductCard> getCandidatesForSlot(ChatProductCard anchor, String slotRole, int limit) {
        List<String> roles = roleNamesForSlot(slotRole);
        if (anchor == null || roles.isEmpty()) {
            return List.of();
        }

        Set<String> compatibleFamilies = COMPATIBLE.getOrDefault(
            anchor.getColorFamily() == null ? "mixed" : anchor.getColorFamily(),
            COMPATIBLE.get("mixed")
        );
        StringBuilder sql = new StringBuilder("""
            SELECT
              p.id,
              p.name,
              MIN((%s) + COALESCE(pv.price_adjustment, 0)) AS display_price,
              p.base_price,
              p.sale_price,
              (%s) AS is_currently_on_sale,
              pc.id AS color_id,
              pc.color_name,
              COALESCE(pi.image_url, pi_any.image_url) AS image_url,
              SUM(pv.stock_quantity) AS total_stock,
              c.slug AS category_slug,
              c.name AS category_name,
                            c.role AS category_role,
                            parent_c.name AS parent_category_name,
                            p.gender,
                            pc.color_code,
                            pc.color_family,
                            p.fit_type,
                            p.style_tags,
                            p.occasion_tags,
                            p.material,
                            p.season
            FROM products p
            LEFT JOIN categories c ON c.id = p.category_id
                        LEFT JOIN categories parent_c ON parent_c.id = c.parent_id
            JOIN product_colors pc ON pc.product_id = p.id
            JOIN product_variants pv ON pv.product_id = p.id AND pv.color_id = pc.id
            LEFT JOIN product_images pi ON pi.product_id = p.id AND pi.color_id = pc.id AND pi.is_primary = 1
            LEFT JOIN product_images pi_any ON pi_any.product_id = p.id AND pi_any.is_primary = 1
            WHERE p.status = 'ACTIVE'
              AND pv.stock_quantity > 0
              AND p.id <> :anchorProductId
                            AND c.role IN (:roles)
              AND pc.color_family IN (:families)
            """.formatted(EFFECTIVE_PRICE_SQL, CURRENT_SALE_SQL));
        if (isStrictGender(anchor.getGender())) {
            sql.append(" AND (p.gender = :gender OR p.gender = 'UNISEX')");
        }
        sql.append("""
             GROUP BY p.id, p.name, p.base_price, p.sale_price, p.sale_start_at, p.sale_end_at, p.is_sale, p.gender,
                      pc.id, pc.color_name, pc.color_code, pc.color_family, p.fit_type, p.style_tags, p.occasion_tags, p.material, p.season,
                      pi.image_url, pi_any.image_url, c.slug, c.name, c.role, parent_c.name
             ORDER BY p.created_at DESC
             LIMIT 
            """).append(limit);

        Query query = entityManager.createNativeQuery(sql.toString());
        query.setParameter("anchorProductId", anchor.getId());
        query.setParameter("roles", roles);
        query.setParameter("families", compatibleFamilies);
        if (isStrictGender(anchor.getGender())) {
            query.setParameter("gender", anchor.getGender());
        }
        List<ChatProductCard> cards = mapRows(query.getResultList());
        // Fit Balance Scoring: sort candidates so complementary fits rank higher
        String anchorFit = anchor.getFitType();
        if (anchorFit != null && !anchorFit.isBlank()) {
            cards.sort(Comparator.comparingInt((ChatProductCard c) -> fitBalanceScore(anchorFit, c.getFitType())).reversed());
        }
        log.info("[OUTFIT_CANDIDATES] anchor={} slot={} colorFamily={} compatible={} anchorFit={} count={}",
            anchor.getId(), slotRole, anchor.getColorFamily(), compatibleFamilies, anchorFit, cards.size());
        return cards;
    }

    private List<String> roleNamesForSlot(String slotRole) {
        return switch (slotRole == null ? "" : slotRole) {
            case "top" -> List.of("TOP");
            case "bottom" -> List.of("BOTTOM");
            case "outer" -> List.of("OUTER");
            default -> List.of();
        };
    }

    /**
     * Fit Balance Scoring: anchor loose/oversized → prefer fitted candidates, và ngược lại.
     * Neutral fits get moderate score with everything.
     * Returns 0-3: 3 = ideal complement, 2 = good, 1 = neutral, 0 = same group (unbalanced).
     */
    private int fitBalanceScore(String anchorFit, String candidateFit) {
        if (candidateFit == null || candidateFit.isBlank()) {
            return 1; // unknown fit → neutral score
        }
        boolean anchorFitted = ProductTagLibrary.FIT_FITTED_GROUP.contains(anchorFit);
        boolean anchorLoose = ProductTagLibrary.FIT_LOOSE_GROUP.contains(anchorFit);
        boolean anchorNeutral = ProductTagLibrary.FIT_NEUTRAL_GROUP.contains(anchorFit);
        boolean candidateFitted = ProductTagLibrary.FIT_FITTED_GROUP.contains(candidateFit);
        boolean candidateLoose = ProductTagLibrary.FIT_LOOSE_GROUP.contains(candidateFit);
        boolean candidateNeutral = ProductTagLibrary.FIT_NEUTRAL_GROUP.contains(candidateFit);

        // Ideal: loose anchor + fitted candidate, or fitted anchor + loose candidate
        if ((anchorLoose && candidateFitted) || (anchorFitted && candidateLoose)) {
            return 3;
        }
        // Good: neutral pairs well with everything
        if (anchorNeutral || candidateNeutral) {
            return 2;
        }
        // Same group: unbalanced outfit (both loose or both fitted)
        if ((anchorLoose && candidateLoose) || (anchorFitted && candidateFitted)) {
            return 0;
        }
        return 1;
    }

    private List<ChatProductCard> mapRows(List<?> rows) {
        List<ChatProductCard> cards = new ArrayList<>();
        for (Object row : rows) {
            Object[] values = (Object[]) row;
            Boolean isSale = values[5] instanceof Boolean bool ? bool : ((Number) values[5]).intValue() == 1;
            String categorySlug = values[10] != null ? values[10].toString() : null;
            String categoryName = values[11] != null ? values[11].toString() : null;
            String categoryRole = values[12] != null ? values[12].toString() : null;
            String parentCategoryName = values[13] != null ? values[13].toString() : null;
            String productName = values[1].toString();
            Long productId = ((Number) values[0]).longValue();
            cards.add(ChatProductCard.builder()
                .id(productId)
                .name(productName)
                .displayPrice(toBigDecimal(values[2]))
                .price(toBigDecimal(values[3]))
                .salePrice(Boolean.TRUE.equals(isSale) ? toBigDecimal(values[4]) : null)
                .colorId(((Number) values[6]).longValue())
                .colorName(values[7] != null ? values[7].toString() : null)
                .imageUrl(CloudinaryUrlBuilder.listing(values[8] != null ? values[8].toString() : null))
                .url("/products/" + productId)
                .totalStock(values[9] instanceof Number number ? number.longValue() : null)
                .categorySlug(categorySlug)
                .categoryName(categoryName)
                .categoryRole(categoryRole)
                .parentCategoryName(parentCategoryName)
                .gender(values[14] != null ? values[14].toString() : null)
                .colorCode(values[15] != null ? values[15].toString() : null)
                .colorFamily(values[16] != null ? values[16].toString() : null)
                .fitType(values[17] != null ? values[17].toString() : null)
                .styleTags(parseJsonList(values[18]))
                .occasionTags(parseJsonList(values[19]))
                .material(values[20] != null ? values[20].toString() : null)
                .season(values[21] != null ? values[21].toString() : null)
                .role(resolveRole(categoryRole, categorySlug, categoryName, productName))
                .build());
        }
        return cards;
    }

    private List<String> parseJsonList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return list.stream().filter(Objects::nonNull).map(Object::toString).toList();
        }
        String raw = value.toString();
        if (raw.isBlank()) {
            return List.of();
        }
        try {
            return OBJECT_MAPPER.readValue(raw, new TypeReference<List<String>>() {});
        } catch (Exception ignored) {
            return List.of(raw);
        }
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal decimal) return decimal;
        if (value instanceof Number number) return BigDecimal.valueOf(number.doubleValue());
        return new BigDecimal(value.toString());
    }

    private String resolveRole(String categoryRole, String categorySlug, String categoryName, String productName) {
        String normalizedRole = normalizeCategoryRole(categoryRole);
        if (normalizedRole != null) {
            return normalizedRole;
        }
        String text = normalizeVi((categorySlug == null ? "" : categorySlug) + " "
            + (categoryName == null ? "" : categoryName) + " "
            + (productName == null ? "" : productName));
        if (text.contains(" dam ") || text.contains(" dress ") || text.contains(" vay ")) return "dress";
        if (text.contains(" ao khoac ") || text.contains(" jacket ") || text.contains(" blazer ")) return "outer";
        if (text.contains(" quan ") || text.contains(" jean ") || text.contains(" short ")) return "bottom";
        if (text.contains(" ao ") || text.contains(" shirt ") || text.contains(" polo ") || text.contains(" hoodie ")) return "top";
        return "accessory";
    }

    private String normalizeCategoryRole(String categoryRole) {
        if (categoryRole == null || categoryRole.isBlank()) {
            return null;
        }
        return switch (categoryRole.trim().toUpperCase(Locale.ROOT)) {
            case "TOP" -> "top";
            case "BOTTOM" -> "bottom";
            case "OUTER" -> "outer";
            case "DRESS" -> "dress";
            case "ROOT" -> "root";
            default -> null;
        };
    }

    private String normalizeVi(String input) {
        if (input == null) return "";
        return " " + Normalizer.normalize(input, Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "")
            .replace('đ', 'd')
            .replace('Đ', 'D')
            .toLowerCase()
            .replace('-', ' ')
            .trim() + " ";
    }

    private boolean isStrictGender(String gender) {
        return "MALE".equalsIgnoreCase(gender) || "FEMALE".equalsIgnoreCase(gender);
    }
}
