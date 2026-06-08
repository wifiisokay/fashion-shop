package com.fashionshop.backend.module.ai;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import com.fashionshop.backend.common.enums.CategoryRole;
import com.fashionshop.backend.common.enums.Gender;
import com.fashionshop.backend.module.ai.dto.response.ChatProductCard;
import com.fashionshop.backend.module.ai.dto.response.ChatProductVariantOption;
import com.fashionshop.backend.module.ai.nlu.NluSearchParams;
import com.fashionshop.backend.module.storage.CloudinaryUrlBuilder;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductRetrieverService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final CategoryKeywordMapper categoryKeywordMapper;
    private final TagTranslationService tagTranslationService;

    @PersistenceContext
    private EntityManager entityManager;

    public ProductSearchResult search(String message, int limit) {
        return search(message, limit, List.of());
    }

    public ProductSearchResult search(String message, int limit, List<Long> excludedProductIds) {
        String lower = message == null ? "" : message.toLowerCase(Locale.ROOT);
        SearchParams params = extractParams(lower, normalizeVi(lower), excludedProductIds);
        return search(params, limit, message);
    }

    public ProductSearchResult search(NluSearchParams nlu, int limit) {
        return search(nlu, "", limit, List.of());
    }

    public ProductSearchResult search(NluSearchParams nlu, String originalMessage, int limit) {
        return search(nlu, originalMessage, limit, List.of());
    }

    public ProductSearchResult search(NluSearchParams nlu, String originalMessage, int limit, List<Long> excludedProductIds) {
        if (nlu == null) {
            return search(originalMessage, limit, excludedProductIds);
        }
        SearchParams params = fromNlu(nlu, originalMessage, excludedProductIds);
        return search(params, limit, originalMessage);
    }

    public Integer getProductCategoryId(Long productId) {
        if (productId == null) {
            return null;
        }
        try {
            Object result = entityManager.createNativeQuery("SELECT category_id FROM products WHERE id = :productId")
                    .setParameter("productId", productId)
                    .getSingleResult();
            return result instanceof Number number ? number.intValue() : null;
        } catch (Exception e) {
            log.warn("[AI_GET_CATEGORY_ID_FAILED] productId={} reason={}", productId, e.getMessage());
            return null;
        }
    }

    public ProductSearchResult searchSimilarByRole(String message, NluSearchParams nlu, int limit) {
        SearchParams params = nlu != null ? fromNlu(nlu, message)
                : extractParams(message == null ? "" : message.toLowerCase(Locale.ROOT),
                        normalizeVi(message == null ? "" : message.toLowerCase(Locale.ROOT)));
        Optional<CategoryRole> role = categoryKeywordMapper.detectRole(message);
        if (role.isEmpty() && !params.categoryIds.isEmpty()) {
            role = categoryKeywordMapper.findRoleByCategoryIds(params.categoryIds);
        }
        if (role.isEmpty()) {
            return new ProductSearchResult(0, List.of(), "Total matched: 0\nNo inferred role for role suggestion.",
                    "NO_INFERRED_ROLE", null);
        }
        String inferredRole = role.get().name();
        SearchParams roleParams = params.roleFallback(inferredRole);
        ProductSearchResult roleResult = search(roleParams, limit, message);
        if (!roleResult.products().isEmpty()) {
            log.info("[AI_SEARCH_ROLE_FALLBACK] message='{}' role={} level=LEVEL_2_ROLE total={} returned={}",
                    shorten(message), inferredRole, roleResult.total(), roleResult.products().size());
            return new ProductSearchResult(roleResult.total(), roleResult.products(), roleResult.contextText(),
                    "LEVEL_2_ROLE", inferredRole);
        }

        log.info("[AI_SEARCH_ROLE_FALLBACK] message='{}' role={} level=NO_ROLE_MATCH total=0 returned=0",
                shorten(message), inferredRole);
        return new ProductSearchResult(0, List.of(), "Total matched: 0\nNo same-role products found.",
                "NO_ROLE_MATCH", inferredRole);
    }

    public ProductSearchResult searchOutfitBaseCandidates(String message, NluSearchParams nlu, int limit) {
        SearchParams params = nlu != null ? fromNlu(nlu, message)
                : extractParams(message == null ? "" : message.toLowerCase(Locale.ROOT),
                        normalizeVi(message == null ? "" : message.toLowerCase(Locale.ROOT)));
        Optional<CategoryRole> role = categoryKeywordMapper.detectRole(message);
        if (role.isEmpty() && !params.categoryIds.isEmpty()) {
            role = categoryKeywordMapper.findRoleByCategoryIds(params.categoryIds);
        }
        if (role.isEmpty()) {
            log.info(
                    "[AI_OUTFIT_BASE_SEARCH] message='{}' categoryRole=null darkColor={} colorFamily={} styleHint={} result=NO_ROLE",
                    shorten(message), params.darkColor(), params.colorFamily(), styleHint(message));
            return new ProductSearchResult(0, List.of(), "Total matched: 0\nNo inferred outfit base role.",
                    "NO_INFERRED_ROLE", null);
        }

        String categoryRole = role.get().name();
        SearchParams baseParams = params.outfitBase(categoryRole);
        ProductSearchResult result = search(baseParams, limit, message);
        if (result.products().isEmpty() && baseParams.hasColorConstraint()) {
            SearchParams relaxed = baseParams.withoutColor();
            result = search(relaxed, limit, message);
            log.info(
                    "[AI_OUTFIT_BASE_SEARCH] message='{}' categoryRole={} darkColor={} colorFamily={} styleHint={} relaxedColor=true total={} returned={}",
                    shorten(message), categoryRole, baseParams.darkColor(), baseParams.colorFamily(),
                    styleHint(message),
                    result.total(), result.products().size());
            return new ProductSearchResult(result.total(), result.products(), result.contextText(),
                    "OUTFIT_BASE_ROLE_RELAXED_COLOR", categoryRole);
        }

        log.info(
                "[AI_OUTFIT_BASE_SEARCH] message='{}' categoryRole={} darkColor={} colorFamily={} styleHint={} relaxedColor=false total={} returned={}",
                shorten(message), categoryRole, baseParams.darkColor(), baseParams.colorFamily(), styleHint(message),
                result.total(), result.products().size());
        return new ProductSearchResult(result.total(), result.products(), result.contextText(),
                "OUTFIT_BASE_ROLE", categoryRole);
    }

    private ProductSearchResult search(SearchParams params, int limit, String logMessage) {
        log.info("[AI_SEARCH_PARAMS] message='{}' limit={} params={}", shorten(logMessage), limit, params);
        if (!params.hasExactSearchCriteria()) {
            log.info(
                    "[AI_SEARCH_RESULT] message='{}' limit={} total=0 returned=0 reason=no_exact_search_criteria finalParams={}",
                    shorten(logMessage), limit, params);
            return new ProductSearchResult(0, List.of(), "Total matched: 0\nNo exact search criteria.");
        }
        List<ChatProductCard> cards = queryProducts(params, limit);
        long total = countProducts(params);
        
        if (cards.isEmpty() && params.hasColorConstraint()) {
            SearchParams fallbackParams = params.withoutColor();
            if (fallbackParams.hasExactSearchCriteria()) {
                log.info("[AI_SEARCH_COLOR_FALLBACK] message='{}' limit={} params={} fallbackParams={}",
                        shorten(logMessage), limit, params, fallbackParams);
                List<ChatProductCard> fallbackCards = queryProducts(fallbackParams, limit);
                long fallbackTotal = countProducts(fallbackParams);
                if (!fallbackCards.isEmpty()) {
                    return new ProductSearchResult(fallbackTotal, fallbackCards, formatContext(fallbackTotal, fallbackCards),
                            "NEAR_COLOR_FALLBACK", null);
                }
            }
        }
        
        if (cards.isEmpty()) {
            logEmptyResultDiagnostics(params);
        }
        log.info("[AI_SEARCH_RESULT] message='{}' limit={} total={} returned={} finalParams={}",
                shorten(logMessage), limit, total, cards.size(), params);
        return new ProductSearchResult(total, cards, formatContext(total, cards));
    }

    public List<ChatProductCard> findOutfitCandidates(Long baseProductId, Long colorId, int limit) {
        ChatProductCard current = findProductCard(baseProductId, colorId)
                .orElseThrow(() -> new IllegalArgumentException("Product is not available"));
        return findComplementaryOutfitCandidates(current, limit);
    }

    public List<ChatProductCard> findComplementaryOutfitCandidates(ChatProductCard current, int limit) {
        String anchorRole = normalizeRole(current.getRole());
        List<String> targetRoles = complementaryRoleNames(anchorRole);
        log.info("[OUTFIT_RETRIEVER] baseProductId={}, role={}, gender={}, targetRoles={}",
                current.getId(), anchorRole, current.getGender(), targetRoles);
        StringBuilder sql = baseSelect();
        sql.append(" AND p.id <> :baseProductId");
        if (!targetRoles.isEmpty()) {
            sql.append(" AND c.role IN (:targetRoles)");
        }
        appendStrictGenderFilter(sql, current.getGender());
        sql.append(groupByClause());
        sql.append(" ORDER BY p.is_sale DESC, p.created_at DESC LIMIT ").append(limit);
        Query query = entityManager.createNativeQuery(sql.toString());
        query.setParameter("baseProductId", current.getId());
        if (!targetRoles.isEmpty()) {
            query.setParameter("targetRoles", targetRoles);
        }
        bindStrictGenderFilter(query, current.getGender());
        List<ChatProductCard> candidates = mapRows(query.getResultList()).stream()
                .filter(card -> !sameCoreRole(current.getRole(), card.getRole()))
                .toList();
        if (!candidates.isEmpty()) {
            log.info("[OUTFIT_RETRIEVER] primary_candidates baseProductId={}, count={}", current.getId(),
                    candidates.size());
            return candidates;
        }
        return List.of();
    }

    private String shorten(String value) {
        if (value == null)
            return "";
        String trimmed = value.replaceAll("\\s+", " ").trim();
        return trimmed.length() > 120 ? trimmed.substring(0, 120) + "..." : trimmed;
    }

    public Optional<ChatProductCard> findProductCard(Long productId, Long colorId) {
        StringBuilder sql = baseSelect();
        sql.append(" AND p.id = :productId");
        if (colorId != null) {
            sql.append(" AND pc.id = :colorId");
        }
        sql.append(groupByClause());
        sql.append(" ORDER BY pc.display_order ASC, pc.id ASC LIMIT 1");
        Query query = entityManager.createNativeQuery(sql.toString());
        query.setParameter("productId", productId);
        if (colorId != null) {
            query.setParameter("colorId", colorId);
        }
        List<ChatProductCard> cards = mapRows(query.getResultList());
        return cards.stream().findFirst();
    }

    private List<ChatProductCard> queryProducts(SearchParams params, int limit) {
        StringBuilder sql = baseSelect();
        appendFilters(sql, params);
        sql.append(groupByClause());
        sql.append(" ORDER BY p.is_sale DESC, p.created_at DESC LIMIT ").append(limit);
        log.debug("[AI_SEARCH_SQL] type=query params={} sql={}", params, sql);
        Query query = entityManager.createNativeQuery(sql.toString());
        bindFilters(query, params);
        List<ChatProductCard> cards = mapRows(query.getResultList());
        return ColorNormalizer.boostByColorRelevance(cards, params.colorKeyword, params.colorFamily, params.darkColor);
    }

    String buildSearchSqlForAudit(String message) {
        String lower = message == null ? "" : message.toLowerCase(Locale.ROOT);
        SearchParams params = extractParams(lower, normalizeVi(lower));
        StringBuilder sql = baseSelect();
        appendFilters(sql, params);
        return sql.toString();
    }

    String buildCountSqlForAudit(String message) {
        String lower = message == null ? "" : message.toLowerCase(Locale.ROOT);
        SearchParams params = extractParams(lower, normalizeVi(lower));
        StringBuilder sql = countSelect();
        appendFilters(sql, params);
        return sql.toString();
    }

    private long countProducts(SearchParams params) {
        StringBuilder sql = countSelect();
        appendFilters(sql, params);
        log.debug("[AI_SEARCH_SQL] type=count params={} sql={}", params, sql);
        Query query = entityManager.createNativeQuery(sql.toString());
        bindFilters(query, params);
        Object value = query.getSingleResult();
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private StringBuilder countSelect() {
        return new StringBuilder("""
                SELECT COUNT(DISTINCT CONCAT(p.id, ':', pc.id))
                FROM products p
                LEFT JOIN categories c ON c.id = p.category_id
                JOIN product_colors pc ON pc.product_id = p.id
                JOIN product_variants pv ON pv.product_id = p.id AND pv.color_id = pc.id
                WHERE p.status = 'ACTIVE'
                  AND pv.stock_quantity > 0
                """);
    }

    private void logEmptyResultDiagnostics(SearchParams params) {
        try {
            SearchParams noTextTerms = params.withoutTextTerms();
            SearchParams noCategory = params.withoutCategory();
            SearchParams noGender = params.withoutGender();
            SearchParams noColor = params.withoutColor();
            SearchParams broad = params.withoutTextTerms().withoutCategory().withoutGender().withoutColor();
            log.info(
                    "[AI_SEARCH_EMPTY_DIAG] params={} countNoTextTerms={} countNoCategory={} countNoGender={} countNoColor={} countBroad={}",
                    params,
                    countProducts(noTextTerms),
                    countProducts(noCategory),
                    countProducts(noGender),
                    countProducts(noColor),
                    countProducts(broad));
        } catch (Exception e) {
            log.warn("[AI_SEARCH_EMPTY_DIAG] params={} failed={}", params, e.getMessage());
        }
    }

    private StringBuilder baseSelect() {
        return new StringBuilder("""
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
                                c.role AS category_role,
                                parent_c.name AS parent_category_name,
                                p.gender,
                                pc.color_code,
                                pc.color_family,
                                p.fit_type,
                                p.style_tags,
                                p.occasion_tags,
                                p.material,
                                p.season,
                                p.description
                FROM products p
                LEFT JOIN categories c ON c.id = p.category_id
                            LEFT JOIN categories parent_c ON parent_c.id = c.parent_id
                JOIN product_colors pc ON pc.product_id = p.id
                JOIN product_variants pv ON pv.product_id = p.id AND pv.color_id = pc.id
                LEFT JOIN product_images pi ON pi.product_id = p.id AND pi.color_id = pc.id AND pi.is_primary = 1
                LEFT JOIN product_images pi_any ON pi_any.product_id = p.id AND pi_any.is_primary = 1
                WHERE p.status = 'ACTIVE'
                  AND pv.stock_quantity > 0
                """);
    }

    private String groupByClause() {
        return " GROUP BY p.id, p.name, p.base_price, p.sale_price, p.is_sale, p.gender, " +
                "pc.id, pc.color_name, pc.color_code, pc.color_family, p.fit_type, p.style_tags, p.occasion_tags, p.material, p.season, p.description, "
                +
                "pi.image_url, pi_any.image_url, c.slug, c.name, c.role, parent_c.name";
    }

    private void appendFilters(StringBuilder sql, SearchParams params) {
        List<String> colorKeywords = expandColorKeywords(params.colorKeyword);
        boolean useColorKeyword = !colorKeywords.isEmpty();
        boolean useColorFamily = !useColorKeyword && hasText(params.colorFamily);

        if (params.gender != null) {
            sql.append(" AND (p.gender = :gender OR p.gender = 'UNISEX')");
        }
        if (!params.categoryIds.isEmpty()) {
            sql.append(" AND p.category_id IN (:categoryIds)");
        }
        if (params.maxPrice != null) {
            sql.append(" AND COALESCE(p.sale_price, p.base_price) + COALESCE(pv.price_adjustment, 0) <= :maxPrice");
        }
        if (params.saleOnly) {
            sql.append(" AND p.is_sale = 1");
        }
        if (hasText(params.categoryRole)) {
            sql.append(" AND c.role = :categoryRole");
        }
        // Keep an explicit color strict; color family is only a fallback when no exact
        // color was requested.
        if (useColorKeyword) {
            sql.append(" AND (");
            for (int i = 0; i < colorKeywords.size(); i++) {
                if (i > 0) {
                    sql.append(" OR ");
                }
                sql.append(" LOWER(pc.color_name) LIKE :colorKeyword").append(i);
            }
            sql.append(")");
        } else if (useColorFamily) {
            sql.append(" AND pc.color_family = :colorFamily");
        }
        if (params.darkColor) {
            sql.append(
                    " AND (LOWER(pc.color_name) LIKE '%đen%' OR LOWER(pc.color_name) LIKE '%black%' OR LOWER(pc.color_name) LIKE '%navy%' OR LOWER(pc.color_family) IN ('cool','neutral'))");
        }
        appendTextTerms(sql, params.textTerms);
        if (hasText(params.styleTag)) {
            sql.append(" AND JSON_CONTAINS(p.style_tags, JSON_QUOTE(:styleTag))");
        }
        if (hasText(params.occasionTag)) {
            sql.append(" AND JSON_CONTAINS(p.occasion_tags, JSON_QUOTE(:occasionTag))");
        }
        if (params.excludedProductIds != null && !params.excludedProductIds.isEmpty()) {
            sql.append(" AND p.id NOT IN (:excludedProductIds)");
        }
    }

    private void bindFilters(Query query, SearchParams params) {
        List<String> colorKeywords = expandColorKeywords(params.colorKeyword);
        boolean useColorKeyword = !colorKeywords.isEmpty();
        boolean useColorFamily = !useColorKeyword && hasText(params.colorFamily);

        if (params.gender != null) {
            query.setParameter("gender", params.gender.name());
        }
        if (!params.categoryIds.isEmpty()) {
            query.setParameter("categoryIds", params.categoryIds);
        }
        if (params.maxPrice != null) {
            query.setParameter("maxPrice", params.maxPrice);
        }
        if (useColorKeyword) {
            for (int i = 0; i < colorKeywords.size(); i++) {
                query.setParameter("colorKeyword" + i, normalizeLike(colorKeywords.get(i)));
            }
        } else if (useColorFamily) {
            query.setParameter("colorFamily", params.colorFamily);
        }
        if (hasText(params.categoryRole)) {
            query.setParameter("categoryRole", params.categoryRole);
        }
        for (int i = 0; i < params.textTerms.size(); i++) {
            query.setParameter("textTerm" + i, normalizeLike(params.textTerms.get(i)));
        }
        if (hasText(params.styleTag)) {
            query.setParameter("styleTag", params.styleTag);
        }
        if (hasText(params.occasionTag)) {
            query.setParameter("occasionTag", params.occasionTag);
        }
        if (params.excludedProductIds != null && !params.excludedProductIds.isEmpty()) {
            query.setParameter("excludedProductIds", params.excludedProductIds);
        }
    }

    private void appendTextTerms(StringBuilder sql, List<String> textTerms) {
        if (textTerms.isEmpty()) {
            return;
        }
        sql.append(" AND (");
        for (int i = 0; i < textTerms.size(); i++) {
            if (i > 0) {
                sql.append(" OR ");
            }
            String parameter = ":textTerm" + i;
            sql.append("(LOWER(COALESCE(p.name, '')) LIKE ").append(parameter)
                    .append(" OR LOWER(COALESCE(p.description, '')) LIKE ").append(parameter)
                    .append(" OR LOWER(COALESCE(c.name, '')) LIKE ").append(parameter)
                    .append(" OR LOWER(COALESCE(c.slug, '')) LIKE ").append(parameter)
                    .append(" OR LOWER(COALESCE(CAST(p.style_tags AS CHAR), '')) LIKE ").append(parameter)
                    .append(" OR LOWER(COALESCE(CAST(p.occasion_tags AS CHAR), '')) LIKE ").append(parameter)
                    .append(" OR LOWER(COALESCE(p.fit_type, '')) LIKE ").append(parameter)
                    .append(" OR LOWER(COALESCE(p.season, '')) LIKE ").append(parameter)
                    .append(" OR LOWER(COALESCE(p.material, '')) LIKE ").append(parameter)
                    .append(" OR LOWER(COALESCE(p.gender, '')) LIKE ").append(parameter)
                    .append(" OR LOWER(COALESCE(pc.color_name, '')) LIKE ").append(parameter)
                    .append(" OR LOWER(COALESCE(pc.color_family, '')) LIKE ").append(parameter)
                    .append(")");
        }
        sql.append(")");
    }

    private void appendStrictGenderFilter(StringBuilder sql, String gender) {
        if (isStrictGender(gender)) {
            sql.append(" AND p.gender = :gender");
        } else {
            sql.append(" AND (p.gender IS NULL OR p.gender <> 'UNISEX')");
        }
    }

    private void bindStrictGenderFilter(Query query, String gender) {
        if (isStrictGender(gender)) {
            query.setParameter("gender", gender);
        }
    }

    private boolean isStrictGender(String gender) {
        return "MALE".equalsIgnoreCase(gender) || "FEMALE".equalsIgnoreCase(gender);
    }

    private SearchParams fromNlu(NluSearchParams nlu, String originalMessage) {
        return fromNlu(nlu, originalMessage, List.of());
    }

    private SearchParams fromNlu(NluSearchParams nlu, String originalMessage, List<Long> excludedProductIds) {
        Gender gender = null;
        if (nlu.getGender() != null && !nlu.getGender().isBlank()) {
            try {
                gender = Gender.valueOf(nlu.getGender());
            } catch (IllegalArgumentException ignored) {
            }
        }
        String categoryText = String.join(" ",
                mergeList(safeList(nlu.getCategoryKeywords()), nlu.getCategoryHint(), nlu.getProductType()));
        String searchableText = originalMessage + " " + categoryText + " "
                + String.join(" ", mergeList(safeList(nlu.getStyleKeywords()), nlu.getStyle())) + " "
                + String.join(" ", mergeList(safeList(nlu.getOccasionKeywords()), nlu.getOccasion()));
        List<Integer> categoryIds = categoryKeywordMapper.detectCategoryIds(categoryText);
        String styleTag = tagTranslationService
                .detectStyleTag(String.join(" ", mergeList(safeList(nlu.getStyleKeywords()), nlu.getStyle())))
                .orElse(null);
        String occasionTag = tagTranslationService
                .detectOccasionTag(String.join(" ", mergeList(safeList(nlu.getOccasionKeywords()), nlu.getOccasion())))
                .orElse(null);
        Long budget = nlu.getPriceMax() != null ? nlu.getPriceMax() : nlu.getBudget();
        BigDecimal maxPrice = budget != null ? BigDecimal.valueOf(budget) : null;
        String colorKeyword = normalizeBlank(nlu.getColorKeyword());
        boolean darkColor = isDarkColorHint(colorKeyword, originalMessage);
        if (darkColor && isGenericDarkColorKeyword(colorKeyword)) {
            colorKeyword = null;
        }
        String colorFamily = normalizeBlank(nlu.getColorFamily());
        if (colorFamily == null && darkColor) {
            colorFamily = "neutral";
        }
        return new SearchParams(
                gender,
                categoryIds,
                maxPrice,
                colorKeyword,
                darkColor,
                Boolean.TRUE.equals(nlu.getIsSale()),
                ProductSearchDictionary.productTerms(searchableText),
                styleTag,
                occasionTag,
                colorFamily,
                null,
                excludedProductIds);
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }

    private List<String> mergeList(List<String> values, String... extraValues) {
        List<String> merged = new ArrayList<>(values);
        for (String extra : extraValues) {
            if (extra != null && !extra.isBlank()) {
                merged.add(extra);
            }
        }
        return merged;
    }

    private String normalizeBlank(String value) {
        return value == null || value.isBlank() || "null".equalsIgnoreCase(value) ? null : value;
    }

    private boolean isDarkColorHint(String colorKeyword, String message) {
        String normalizedMessage = normalizeVi(message);
        return normalizedMessage.contains(" mau toi ")
                || normalizedMessage.contains(" tong toi ")
                || normalizedMessage.contains(" tone toi ")
                || normalizedMessage.contains(" den ")
                || normalizedMessage.contains(" black ")
                || normalizedMessage.contains(" navy ")
                || normalizedMessage.contains(" xam dam ")
                || normalizedMessage.contains(" nau dam ");
    }

    private boolean isGenericDarkColorKeyword(String value) {
        String normalized = VietnameseTextNormalizer.normalize(value == null ? "" : value);
        return "toi".equals(normalized) || "mau toi".equals(normalized) || "dark".equals(normalized);
    }

    private SearchParams extractParams(String lower, String normalized) {
        return extractParams(lower, normalized, List.of());
    }

    private SearchParams extractParams(String lower, String normalized, List<Long> excludedProductIds) {
        Gender gender = null;
        if (normalized.contains(" nam ")) {
            gender = Gender.MALE;
        } else if (normalized.contains(" nu ")) {
            gender = Gender.FEMALE;
        }

        BigDecimal maxPrice = extractMaxPrice(lower);
        String colorKeyword = extractColor(lower, normalized);
        String colorFamily = extractColorFamily(lower, normalized);
        boolean darkColor = normalized.contains("mau toi") || normalized.contains("tong toi") || normalized.contains("tone toi")
            || normalized.contains(" den ") || normalized.contains(" black ") || normalized.contains(" navy ")
            || normalized.contains(" xam dam ") || normalized.contains(" nau dam ");
        boolean saleOnly = normalized.contains("sale") || normalized.contains("giam gia") || normalized.contains("khuyen mai");
        List<Integer> categoryIds = categoryKeywordMapper.detectCategoryIds(lower);
        String styleTag = tagTranslationService.detectStyleTag(lower).orElse(null);
        String occasionTag = tagTranslationService.detectOccasionTag(lower).orElse(null);
        List<String> textTerms = ProductSearchDictionary.productTerms(lower);

        return new SearchParams(gender, categoryIds, maxPrice, colorKeyword, darkColor, saleOnly, textTerms, styleTag,
                occasionTag, colorFamily, null, excludedProductIds);
    }

    private BigDecimal extractMaxPrice(String lower) {
        Matcher matcher = Pattern.compile("(?:dưới|duoi|<=|<)\\s*(\\d+)\\s*(k|nghìn|ngàn|tr|triệu)?").matcher(lower);
        if (!matcher.find()) {
            return null;
        }
        BigDecimal value = new BigDecimal(matcher.group(1));
        String unit = matcher.group(2);
        if (unit != null && (unit.startsWith("tr") || unit.startsWith("tri"))) {
            return value.multiply(BigDecimal.valueOf(1_000_000));
        }
        return value.multiply(BigDecimal.valueOf(1_000));
    }

    private String extractColor(String lower, String normalized) {
        if (normalized.contains(" xam "))
            return "xám";
        if (normalized.contains(" den ") || normalized.contains(" black "))
            return "đen";
        if (normalized.contains(" trang ") || normalized.contains(" white "))
            return "trắng";
        if (normalized.contains(" xanh ") || normalized.contains(" navy "))
            return "xanh";
        if (containsWord(lower, "đỏ") || normalized.contains(" red "))
            return "đỏ";
        if (normalized.contains(" nau "))
            return "nâu";
        if (normalized.contains(" hong "))
            return "hồng";
        if (normalized.contains(" vang "))
            return "vàng";
        if (containsWord(lower, "tím") || containsColorToken(normalized, "tim"))
            return "tím";
        if (containsWord(lower, "be") || containsColorToken(normalized, "be"))
            return "be";
        if (normalized.contains(" cam ") || normalized.contains(" orange "))
            return "cam";
        return null;
    }

    private boolean containsWord(String text, String word) {
        if (text == null || word == null)
            return false;
        return Pattern.compile("(^|\\P{L})" + Pattern.quote(word) + "(\\P{L}|$)",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(text).find();
    }

    private boolean containsColorToken(String normalized, String colorToken) {
        return normalized.contains(" mau " + colorToken + " ")
                || normalized.contains(" mau sac " + colorToken + " ")
                || normalized.contains(" tone " + colorToken + " ")
                || normalized.contains(" tong " + colorToken + " ");
    }

    private String extractColorFamily(String lower, String normalized) {
        if (containsAnyNormalized(normalized, "den", "toi", "trang", "xam", "ghi", "kem", "nude")
                || containsWord(lower, "be")) {
            return "neutral";
        }
        if (containsWord(lower, "đỏ") || containsWord(lower, "hồng")
                || containsAnyNormalized(normalized, "cam", "vang", "gold", "lavender", "burgundy")
                || containsWord(lower, "tím") || containsColorToken(normalized, "tim")) {
            return "warm";
        }
        if (containsAnyNormalized(normalized, "xanh", "navy", "blue", "mint", "green", "teal")) {
            return "cool";
        }
        if (containsAnyNormalized(normalized, "nau", "camel", "olive", "reu", "gach", "dat")) {
            return "earth";
        }
        return null;
    }

    private String styleHint(String message) {
        String normalized = normalizeVi(message);
        if (normalized.contains(" thoang mat ") || normalized.contains(" breathable ")
                || normalized.contains(" mua he ")) {
            return "summer/breathable/casual";
        }
        return null;
    }

    private boolean containsAnyNormalized(String normalized, String... values) {
        for (String value : values) {
            if (normalized.contains(" " + value + " ")) {
                return true;
            }
        }
        return false;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String normalizeLike(String value) {
        return "%" + value.trim().toLowerCase(Locale.ROOT) + "%";
    }

    private List<String> expandColorKeywords(String keyword) {
        if (!hasText(keyword)) {
            return List.of();
        }
        String normalized = keyword.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "đen", "den", "black" -> List.of("đen", "den", "black");
            case "trắng", "trang", "white" -> List.of("trắng", "trang", "white");
            case "xám", "xam", "ghi", "gray", "grey" -> List.of("xám", "xam", "ghi", "gray", "grey");
            case "xanh", "blue", "green" -> List.of("xanh", "blue", "green");
            case "xanh dương", "xanh duong" -> List.of("xanh dương", "xanh duong", "blue");
            case "xanh lá", "xanh la" -> List.of("xanh lá", "xanh la", "green");
            case "đỏ", "do", "red" -> List.of("đỏ", "do", "red");
            case "hồng", "hong", "pink" -> List.of("hồng", "hong", "pink");
            case "vàng", "vang", "yellow" -> List.of("vàng", "vang", "yellow");
            case "nâu", "nau", "brown" -> List.of("nâu", "nau", "brown");
            case "be", "beige", "kem", "cream" -> List.of("be", "beige", "kem", "cream");
            case "tím", "tim", "purple", "lilac", "lavender" -> List.of("tím", "tim", "purple", "lilac", "lavender");
            case "cam", "orange" -> List.of("cam", "orange");
            default -> List.of(normalized);
        };
    }

    private List<ChatProductCard> mapRows(List<?> rows) {
        List<ChatProductCard> cards = new ArrayList<>();
        for (Object row : rows) {
            Object[] values = (Object[]) row;
            BigDecimal basePrice = toBigDecimal(values[3]);
            BigDecimal salePrice = toBigDecimal(values[4]);
            BigDecimal displayPrice = toBigDecimal(values[2]);
            Boolean isSale = values[5] instanceof Boolean bool ? bool : ((Number) values[5]).intValue() == 1;
            String categorySlug = values[10] != null ? values[10].toString() : null;
            String categoryName = values[11] != null ? values[11].toString() : null;
            String categoryRole = values[12] != null ? values[12].toString() : null;
            String parentCategoryName = values[13] != null ? values[13].toString() : null;
            String productName = (String) values[1];
            Long productId = ((Number) values[0]).longValue();
            cards.add(ChatProductCard.builder()
                    .id(productId)
                    .name(productName)
                    .description(values[22] != null ? values[22].toString() : null)
                    .displayPrice(displayPrice)
                    .price(basePrice)
                    .salePrice(Boolean.TRUE.equals(isSale) ? salePrice : null)
                    .isSale(Boolean.TRUE.equals(isSale))
                    .colorId(((Number) values[6]).longValue())
                    .colorName((String) values[7])
                    .imageUrl(CloudinaryUrlBuilder.listing((String) values[8]))
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
                    .matchReason("Phù hợp với yêu cầu và còn hàng trong shop")
                    .build());
        }
        enrichAvailableVariants(cards);
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
            return OBJECT_MAPPER.readValue(raw, new TypeReference<List<String>>() {
            });
        } catch (Exception ignored) {
            return List.of(raw);
        }
    }

    private void enrichAvailableVariants(List<ChatProductCard> cards) {
        for (ChatProductCard card : cards) {
            if (card.getId() == null || card.getColorId() == null) {
                card.setAvailableVariants(List.of());
                continue;
            }
            Query query = entityManager.createNativeQuery("""
                    SELECT pv.id, pv.size, pv.stock_quantity
                    FROM product_variants pv
                    WHERE pv.product_id = :productId
                      AND pv.color_id = :colorId
                      AND pv.stock_quantity > 0
                    ORDER BY
                      CASE pv.size
                        WHEN 'XS' THEN 1
                        WHEN 'S' THEN 2
                        WHEN 'M' THEN 3
                        WHEN 'L' THEN 4
                        WHEN 'XL' THEN 5
                        WHEN 'XXL' THEN 6
                        ELSE 99
                      END,
                      pv.size
                    """);
            query.setParameter("productId", card.getId());
            query.setParameter("colorId", card.getColorId());
            List<ChatProductVariantOption> variants = new ArrayList<>();
            for (Object row : query.getResultList()) {
                Object[] values = (Object[]) row;
                variants.add(ChatProductVariantOption.builder()
                        .variantId(((Number) values[0]).longValue())
                        .sizeName(values[1] != null ? values[1].toString() : null)
                        .stockQuantity(values[2] instanceof Number number ? number.intValue() : null)
                        .build());
            }
            card.setAvailableVariants(variants);
        }
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return new BigDecimal(value.toString());
    }

    private String resolveRole(String categoryRole, String categorySlug, String categoryName, String productName) {
        String normalizedRole = normalizeCategoryRole(categoryRole);
        if (normalizedRole != null) {
            return normalizedRole;
        }
        String text = normalizeVi((categorySlug == null ? "" : categorySlug) + " " +
                (categoryName == null ? "" : categoryName) + " " +
                (productName == null ? "" : productName));
        if (containsAny(text, " dam ", " dress ", " vay ", " chan vay "))
            return "dress";
        if (containsAny(text, " ao khoac ", " jacket ", " vest ", " blazer ", " cardigan "))
            return "outer";
        if (containsAny(text, " quan ", " jean ", " kaki ", " short ", " trousers ", " pants "))
            return "bottom";
        if (containsAny(text, " ao ", " shirt ", " polo ", " hoodie ", " tank ", " tee ", " tshirt "))
            return "top";
        return "accessory";
    }

    private boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private boolean sameCoreRole(String roleA, String roleB) {
        if (!isCoreRole(roleA) || !isCoreRole(roleB)) {
            return false;
        }
        return Objects.equals(roleA, roleB);
    }

    private boolean isCoreRole(String role) {
        return "top".equals(role) || "bottom".equals(role) || "dress".equals(role) || "outer".equals(role);
    }

    private String normalizeVi(String input) {
        return VietnameseTextNormalizer.padded(input);
    }

    private List<String> complementaryRoleNames(String role) {
        return switch (role == null ? "" : role) {
            case "top" -> List.of("BOTTOM", "OUTER");
            case "bottom" -> List.of("TOP", "OUTER");
            case "outer" -> List.of("TOP", "BOTTOM");
            case "dress" -> List.of("OUTER");
            default -> List.of("TOP", "BOTTOM", "OUTER");
        };
    }

    private String normalizeRole(String role) {
        if (role == null) {
            return null;
        }
        return switch (role) {
            case "outerwear" -> "outer";
            case "skirt" -> "bottom";
            default -> role;
        };
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

    private String formatContext(long total, List<ChatProductCard> cards) {
        if (cards.isEmpty()) {
            return "Total matched: 0\nNo products found.";
        }
        StringBuilder builder = new StringBuilder("Total matched: ").append(total)
                .append("\nProducts from database:\n");
        for (ChatProductCard card : cards) {
            builder.append("- [ID:").append(card.getId())
                    .append(", colorId:").append(card.getColorId())
                    .append("] ").append(card.getName())
                    .append(" | Price: ").append(card.getDisplayPrice())
                    .append(" | Stock: ").append(card.getTotalStock())
                    .append(" | Category: ").append(card.getCategoryName())
                    .append(" | Role: ").append(card.getCategoryRole())
                    .append(" | Gender: ").append(card.getGender())
                    .append(" | Color: ").append(card.getColorName())
                    .append(" | ColorFamily: ").append(card.getColorFamily())
                    .append(" | Fit: ").append(card.getFitType())
                    .append(" | Material: ").append(card.getMaterial())
                    .append(" | Season: ").append(card.getSeason())
                    .append(" | StyleTags: ").append(card.getStyleTags())
                    .append(" | OccasionTags: ").append(card.getOccasionTags())
                    .append(" | AvailableVariants: ").append(card.getAvailableVariants())
                    .append(" | Description: ").append(card.getDescription())
                    .append(" | Image: ").append(card.getImageUrl())
                    .append("\n");
        }
        return builder.toString();
    }

    private record SearchParams(Gender gender, List<Integer> categoryIds, BigDecimal maxPrice,
            String colorKeyword, boolean darkColor, boolean saleOnly,
            List<String> textTerms, String styleTag, String occasionTag, String colorFamily,
            String categoryRole, List<Long> excludedProductIds) {
        SearchParams withoutTextTerms() {
            return new SearchParams(gender, categoryIds, maxPrice, colorKeyword, darkColor, saleOnly,
                    List.of(), styleTag, occasionTag, colorFamily, categoryRole, excludedProductIds);
        }

        SearchParams withoutCategory() {
            return new SearchParams(gender, List.of(), maxPrice, colorKeyword, darkColor, saleOnly,
                    textTerms, styleTag, occasionTag, colorFamily, categoryRole, excludedProductIds);
        }

        SearchParams withoutGender() {
            return new SearchParams(null, categoryIds, maxPrice, colorKeyword, darkColor, saleOnly,
                    textTerms, styleTag, occasionTag, colorFamily, categoryRole, excludedProductIds);
        }

        SearchParams withoutColor() {
            return new SearchParams(gender, categoryIds, maxPrice, null, false, saleOnly,
                    textTerms, styleTag, occasionTag, null, categoryRole, excludedProductIds);
        }

        SearchParams withCategoryRole(String categoryRole) {
            return new SearchParams(gender, categoryIds, maxPrice, colorKeyword, darkColor, saleOnly,
                    textTerms, styleTag, occasionTag, colorFamily, categoryRole, excludedProductIds);
        }

        SearchParams roleFallback(String categoryRole) {
            return new SearchParams(gender, List.of(), null, null, false, false,
                    List.of(), null, null, null, categoryRole, excludedProductIds);
        }

        SearchParams outfitBase(String categoryRole) {
            return new SearchParams(gender, List.of(), null, colorKeyword, darkColor, false,
                    List.of(), null, occasionTag, colorFamily, categoryRole, excludedProductIds);
        }

        boolean hasColorConstraint() {
            return colorKeyword != null || darkColor || colorFamily != null;
        }

        boolean hasExactSearchCriteria() {
            return !categoryIds.isEmpty()
                    || maxPrice != null
                    || colorKeyword != null
                    || darkColor
                    || saleOnly
                    || !textTerms.isEmpty()
                    || styleTag != null
                    || occasionTag != null
                    || colorFamily != null
                    || categoryRole != null;
        }

    }

    public record ProductSearchResult(long total, List<ChatProductCard> products, String contextText,
            String fallbackLevel, String inferredRole) {
        public ProductSearchResult(long total, List<ChatProductCard> products, String contextText) {
            this(total, products, contextText, null, null);
        }
    }
}
