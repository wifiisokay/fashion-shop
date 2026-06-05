package com.fashionshop.backend.module.ai;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ProductTextResolverService {

    private static final double MATCH_THRESHOLD = 60;
    private static final Set<String> LOW_VALUE_TOKENS = Set.of(
        "ao", "nu", "nam", "do", "gi", "nay", "la", "co", "khong", "voi", "mac", "phoi", "goi", "y"
    );
    private static final List<String> INTENT_STOPWORDS = List.of(
        "trong mua he nay", "mua he nay", "nen mac voi gi", "goi y phoi do",
        "mac cung gi", "mac voi gi", "phoi do", "goi y", "con size", "co khong", "la gi"
    );

    @PersistenceContext
    private EntityManager entityManager;

    public Optional<ProductMatch> resolveProductFromMessage(String message, String genderHint) {
        String clean = cleanProductText(message);
        if (clean.isBlank()) {
            logEmpty(message, clean, genderHint);
            return Optional.empty();
        }

        List<ProductMatch> matches = queryCandidates(genderHint).stream()
            .map(candidate -> scoreCandidate(clean, genderHint, candidate))
            .filter(match -> match.score() >= MATCH_THRESHOLD)
            .sorted(Comparator.comparing(ProductMatch::exactOrContains).reversed()
                .thenComparing(ProductMatch::score, Comparator.reverseOrder())
                .thenComparing(ProductMatch::totalStock, Comparator.reverseOrder()))
            .toList();

        Optional<ProductMatch> best = matches.stream().findFirst();
        if (matches.size() > 1 && !matches.get(0).exactOrContains()
                && matches.get(0).score() - matches.get(1).score() <= 10) {
            log.info("[AI_PRODUCT_TEXT_RESOLVE_EMPTY] message='{}' clean='{}' genderHint={} reason=ambiguous topScores={}/{}",
                shorten(message), clean, genderHint, matches.get(0).score(), matches.get(1).score());
            return Optional.empty();
        }
        if (best.isPresent()) {
            ProductMatch match = best.get();
            log.info("[AI_PRODUCT_TEXT_RESOLVE] message='{}' clean='{}' genderHint={} bestProductId={} bestScore={} reason='{}'",
                shorten(message), clean, genderHint, match.productId(), match.score(), match.matchReason());
        } else {
            logEmpty(message, clean, genderHint);
        }
        return best;
    }

    public String detectGenderHint(String message) {
        String normalized = VietnameseTextNormalizer.padded(message);
        if (normalized.contains(" nu ")) {
            return "FEMALE";
        }
        if (normalized.contains(" nam ")) {
            return "MALE";
        }
        return null;
    }

    String cleanProductText(String message) {
        String clean = VietnameseTextNormalizer.normalize(message);
        for (String stopword : INTENT_STOPWORDS) {
            clean = (" " + clean + " ").replace(" " + stopword + " ", " ").trim();
        }
        return String.join(" ", Arrays.stream(clean.split("\\s+"))
            .filter(token -> !Set.of("gi", "nay").contains(token))
            .toList());
    }

    ProductMatch scoreCandidate(String cleanMessage, String genderHint, ProductCandidate candidate) {
        String productName = VietnameseTextNormalizer.normalize(candidate.productName());
        String description = VietnameseTextNormalizer.normalize(candidate.description());
        String categoryName = VietnameseTextNormalizer.normalize(candidate.categoryName());
        String categorySlug = VietnameseTextNormalizer.normalize(candidate.categorySlug());
        Set<String> messageTokens = meaningfulTokens(cleanMessage);
        Set<String> productTokens = meaningfulTokens(productName);

        double score = 0;
        String reason = "token_overlap";
        boolean contains = cleanMessage.contains(productName) || productName.contains(cleanMessage);
        if (cleanMessage.contains(productName)) {
            score += 100;
            reason = "message_contains_name";
        } else if (productName.contains(cleanMessage)) {
            score += 80;
            reason = "name_contains_message";
        }
        for (String token : productTokens) {
            if (messageTokens.contains(token)) {
                score += 5;
                if (token.length() >= 5) {
                    score += 10;
                }
            }
        }
        if ((!categoryName.isBlank() && cleanMessage.contains(categoryName))
                || (!categorySlug.isBlank() && cleanMessage.contains(categorySlug))) {
            score += 15;
        }
        for (String token : messageTokens) {
            if (description.contains(token)) {
                score += 2;
            }
        }
        if (genderHint != null && genderHint.equalsIgnoreCase(candidate.gender())) {
            score += 20;
        }
        return new ProductMatch(candidate.productId(), candidate.productName(), candidate.gender(), score,
            reason, candidate.totalStock(), contains);
    }

    private List<ProductCandidate> queryCandidates(String genderHint) {
        StringBuilder sql = new StringBuilder("""
            SELECT p.id, p.name, p.description, p.gender, c.name, c.slug, SUM(pv.stock_quantity)
            FROM products p
            LEFT JOIN categories c ON c.id = p.category_id
            JOIN product_variants pv ON pv.product_id = p.id
            WHERE p.status = 'ACTIVE'
              AND pv.stock_quantity > 0
            """);
        if (genderHint != null && !genderHint.isBlank()) {
            sql.append(" AND p.gender = :gender");
        }
        sql.append(" GROUP BY p.id, p.name, p.description, p.gender, c.name, c.slug");
        Query query = entityManager.createNativeQuery(sql.toString());
        if (genderHint != null && !genderHint.isBlank()) {
            query.setParameter("gender", genderHint.toUpperCase(Locale.ROOT));
        }
        List<ProductCandidate> candidates = new ArrayList<>();
        for (Object row : query.getResultList()) {
            Object[] values = (Object[]) row;
            candidates.add(new ProductCandidate(
                ((Number) values[0]).longValue(),
                (String) values[1],
                values[2] != null ? values[2].toString() : "",
                values[3] != null ? values[3].toString() : null,
                values[4] != null ? values[4].toString() : "",
                values[5] != null ? values[5].toString() : "",
                values[6] instanceof Number number ? number.longValue() : 0L
            ));
        }
        return candidates;
    }

    private Set<String> meaningfulTokens(String text) {
        Set<String> tokens = new LinkedHashSet<>();
        for (String token : VietnameseTextNormalizer.normalize(text).split("\\s+")) {
            if (token.length() >= 3 && !LOW_VALUE_TOKENS.contains(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private void logEmpty(String message, String clean, String genderHint) {
        log.info("[AI_PRODUCT_TEXT_RESOLVE_EMPTY] message='{}' clean='{}' genderHint={}",
            shorten(message), clean, genderHint);
    }

    private String shorten(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.replaceAll("\\s+", " ").trim();
        return trimmed.length() > 160 ? trimmed.substring(0, 160) + "..." : trimmed;
    }

    public record ProductMatch(Long productId, String productName, String gender, Double score,
                               String matchReason, Long totalStock, boolean exactOrContains) {
    }

    record ProductCandidate(Long productId, String productName, String description, String gender,
                            String categoryName, String categorySlug, Long totalStock) {
    }
}
