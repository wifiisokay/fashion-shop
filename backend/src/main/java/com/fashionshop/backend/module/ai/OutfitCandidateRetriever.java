package com.fashionshop.backend.module.ai;

import com.fashionshop.backend.module.ai.dto.response.ChatProductCard;
import com.fashionshop.backend.module.storage.CloudinaryUrlBuilder;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class OutfitCandidateRetriever {

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
        List<String> slugs = slugsForSlot(slotRole);
        if (anchor == null || slugs.isEmpty()) {
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
              MIN(COALESCE(p.sale_price, p.base_price) + COALESCE(pv.price_adjustment, 0)) AS display_price,
              p.base_price,
              p.sale_price,
              p.is_sale,
              pc.id AS color_id,
              pc.color_name,
              COALESCE(pi.image_url, pi_any.image_url) AS image_url,
              SUM(pv.stock_quantity) AS total_stock,
              c.slug AS category_slug,
              c.name AS category_name,
              p.gender,
              pc.color_code,
              pc.color_family
            FROM products p
            LEFT JOIN categories c ON c.id = p.category_id
            JOIN product_colors pc ON pc.product_id = p.id
            JOIN product_variants pv ON pv.product_id = p.id AND pv.color_id = pc.id
            LEFT JOIN product_images pi ON pi.product_id = p.id AND pi.color_id = pc.id AND pi.is_primary = 1
            LEFT JOIN product_images pi_any ON pi_any.product_id = p.id AND pi_any.is_primary = 1
            WHERE p.status = 'ACTIVE'
              AND pv.stock_quantity > 0
              AND p.id <> :anchorProductId
              AND c.slug IN (:slugs)
              AND pc.color_family IN (:families)
            """);
        if (isStrictGender(anchor.getGender())) {
            sql.append(" AND (p.gender = :gender OR p.gender = 'UNISEX')");
        }
        sql.append("""
             GROUP BY p.id, p.name, p.base_price, p.sale_price, p.is_sale, p.gender,
                      pc.id, pc.color_name, pc.color_code, pc.color_family, pi.image_url, pi_any.image_url, c.slug, c.name
             ORDER BY p.is_sale DESC, p.created_at DESC
             LIMIT 
            """).append(limit);

        Query query = entityManager.createNativeQuery(sql.toString());
        query.setParameter("anchorProductId", anchor.getId());
        query.setParameter("slugs", slugs);
        query.setParameter("families", compatibleFamilies);
        if (isStrictGender(anchor.getGender())) {
            query.setParameter("gender", anchor.getGender());
        }
        List<ChatProductCard> cards = mapRows(query.getResultList());
        log.info("[OUTFIT_CANDIDATES] anchor={} slot={} colorFamily={} compatible={} count={}",
            anchor.getId(), slotRole, anchor.getColorFamily(), compatibleFamilies, cards.size());
        return cards;
    }

    private List<String> slugsForSlot(String slotRole) {
        return switch (slotRole == null ? "" : slotRole) {
            case "top" -> List.of("ao-thun-nam", "ao-thun-nu", "ao-polo-nam", "ao-somi-nam", "ao-somi-nu");
            case "bottom" -> List.of("quan-dai-nam", "quan-dai-nu", "quan-ngan-nam", "vay-va-dam");
            case "outer" -> List.of("ao-khoac-nam", "ao-khoac-nu");
            default -> List.of();
        };
    }

    private List<ChatProductCard> mapRows(List<?> rows) {
        List<ChatProductCard> cards = new ArrayList<>();
        for (Object row : rows) {
            Object[] values = (Object[]) row;
            Boolean isSale = values[5] instanceof Boolean bool ? bool : ((Number) values[5]).intValue() == 1;
            String categorySlug = values[10] != null ? values[10].toString() : null;
            String categoryName = values[11] != null ? values[11].toString() : null;
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
                .gender(values[12] != null ? values[12].toString() : null)
                .colorCode(values[13] != null ? values[13].toString() : null)
                .colorFamily(values[14] != null ? values[14].toString() : null)
                .role(resolveRole(categorySlug, categoryName, productName))
                .build());
        }
        return cards;
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal decimal) return decimal;
        if (value instanceof Number number) return BigDecimal.valueOf(number.doubleValue());
        return new BigDecimal(value.toString());
    }

    private String resolveRole(String categorySlug, String categoryName, String productName) {
        String text = normalizeVi((categorySlug == null ? "" : categorySlug) + " "
            + (categoryName == null ? "" : categoryName) + " "
            + (productName == null ? "" : productName));
        if (text.contains(" dam ") || text.contains(" dress ")) return "dress";
        if (text.contains(" chan vay ") || text.contains(" vay ") || text.contains(" skirt ")) return "skirt";
        if (text.contains(" ao khoac ") || text.contains(" jacket ") || text.contains(" blazer ")) return "outerwear";
        if (text.contains(" quan ") || text.contains(" jean ") || text.contains(" short ")) return "bottom";
        if (text.contains(" ao ") || text.contains(" shirt ") || text.contains(" polo ") || text.contains(" hoodie ")) return "top";
        return "accessory";
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
