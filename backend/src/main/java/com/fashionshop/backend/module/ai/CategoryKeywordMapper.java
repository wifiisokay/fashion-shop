package com.fashionshop.backend.module.ai;

import com.fashionshop.backend.domain.Category;
import com.fashionshop.backend.domain.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryKeywordMapper {

    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final CategoryRepository categoryRepository;
    private volatile CategoryDictionary cachedDictionary;

    public List<Integer> detectCategoryIds(String message) {
        String normalized = normalizeVi(message);
        if (normalized.isBlank()) {
            return List.of();
        }

        String padded = " " + normalized + " ";
        CategoryDictionary dictionary = dictionary();
        for (PhraseRule rule : dictionary.rules()) {
            if (containsPhrase(padded, rule.phrase())) {
                log.info("[CATEGORY_MATCH] message='{}' phrase='{}' categoryIds={}", shorten(message), rule.phrase(), rule.categoryIds());
                return rule.categoryIds();
            }
        }
        return List.of();
    }

    private CategoryDictionary dictionary() {
        CategoryDictionary current = cachedDictionary;
        if (current != null && current.expiresAt().isAfter(Instant.now())) {
            return current;
        }
        synchronized (this) {
            current = cachedDictionary;
            if (current != null && current.expiresAt().isAfter(Instant.now())) {
                return current;
            }
            CategoryDictionary refreshed = buildDictionary();
            cachedDictionary = refreshed;
            return refreshed;
        }
    }

    private CategoryDictionary buildDictionary() {
        List<Category> categories = categoryRepository.findAll();
        Map<String, Integer> idBySlug = categories.stream()
            .filter(category -> category.getSlug() != null)
            .collect(Collectors.toMap(category -> normalizeVi(category.getSlug()), Category::getId, (a, b) -> a, LinkedHashMap::new));
        Map<Integer, List<Integer>> childrenByParent = categories.stream()
            .filter(category -> category.getParent() != null)
            .collect(Collectors.groupingBy(category -> category.getParent().getId(),
                LinkedHashMap::new,
                Collectors.mapping(Category::getId, Collectors.toList())));

        Map<String, List<Integer>> phrases = new LinkedHashMap<>();
        for (Category category : categories) {
            List<Integer> ids = idsWithChildren(category.getId(), childrenByParent);
            put(phrases, normalizeVi(category.getName()), ids);
            put(phrases, normalizeVi(category.getSlug()), ids);
        }
        addFashionAliases(phrases, idBySlug);

        List<PhraseRule> rules = phrases.entrySet().stream()
            .filter(entry -> !entry.getKey().isBlank() && !entry.getValue().isEmpty())
            .map(entry -> new PhraseRule(entry.getKey(), entry.getValue()))
            .sorted(Comparator
                .comparingInt((PhraseRule rule) -> tokenCount(rule.phrase())).reversed()
                .thenComparing(Comparator.comparingInt((PhraseRule rule) -> rule.phrase().length()).reversed()))
            .toList();

        log.info("[CATEGORY_DICTIONARY] categories={} rules={} ttlMinutes={}", categories.size(), rules.size(), CACHE_TTL.toMinutes());
        return new CategoryDictionary(rules, Instant.now().plus(CACHE_TTL));
    }

    private void addFashionAliases(Map<String, List<Integer>> phrases, Map<String, Integer> idBySlug) {
        alias(phrases, idBySlug, "ao thun nam", "ao-thun-nam");
        alias(phrases, idBySlug, "thun nam", "ao-thun-nam");
        alias(phrases, idBySlug, "ao thun nu", "ao-thun-nu");
        alias(phrases, idBySlug, "thun nu", "ao-thun-nu");
        aliasMany(phrases, idBySlug, List.of("ao thun", "thun"), "ao-thun-nam", "ao-thun-nu");

        aliasMany(phrases, idBySlug, List.of("ao polo nam", "ao polo", "polo nam", "polo"), "ao-polo-nam");

        aliasMany(phrases, idBySlug, List.of("ao so mi nam", "ao somi nam", "so mi nam", "somi nam"), "ao-somi-nam");
        aliasMany(phrases, idBySlug, List.of("ao so mi nu", "ao somi nu", "so mi nu", "somi nu"), "ao-somi-nu");
        aliasMany(phrases, idBySlug, List.of("ao so mi", "ao somi", "so mi", "somi"), "ao-somi-nam", "ao-somi-nu");

        aliasMany(phrases, idBySlug, List.of("ao khoac nam", "khoac nam"), "ao-khoac-nam");
        aliasMany(phrases, idBySlug, List.of("ao khoac nu", "khoac nu"), "ao-khoac-nu");
        aliasMany(phrases, idBySlug, List.of("ao khoac", "khoac"), "ao-khoac-nam", "ao-khoac-nu");

        aliasMany(phrases, idBySlug, List.of("quan dai nam", "quan tay nam", "kaki nam", "jeans nam", "jean nam"), "quan-dai-nam");
        aliasMany(phrases, idBySlug, List.of("quan ngan nam", "quan short nam", "short nam", "quan dui nam"), "quan-ngan-nam");
        aliasMany(phrases, idBySlug, List.of("quan dai nu", "quan tay nu", "jeans nu", "jean nu"), "quan-dai-nu");
        aliasMany(phrases, idBySlug, List.of("quan dai", "quan tay", "jeans", "jean"), "quan-dai-nam", "quan-dai-nu");
        aliasMany(phrases, idBySlug, List.of("quan ngan", "quan short", "short", "quan dui"), "quan-ngan-nam");

        aliasMany(phrases, idBySlug, List.of("vay", "dam", "chan vay", "vay dam", "vay va dam"), "vay-va-dam");

        aliasMany(phrases, idBySlug, List.of("ao"), "ao-thun-nam", "ao-polo-nam", "ao-somi-nam", "ao-khoac-nam",
            "ao-thun-nu", "ao-somi-nu", "ao-khoac-nu");
        aliasMany(phrases, idBySlug, List.of("quan"), "quan-dai-nam", "quan-ngan-nam", "quan-dai-nu");
    }

    private void alias(Map<String, List<Integer>> phrases, Map<String, Integer> idBySlug, String phrase, String slug) {
        aliasMany(phrases, idBySlug, List.of(phrase), slug);
    }

    private void aliasMany(Map<String, List<Integer>> phrases, Map<String, Integer> idBySlug, List<String> aliases, String... slugs) {
        List<Integer> ids = new ArrayList<>();
        for (String slug : slugs) {
            Integer id = idBySlug.get(normalizeVi(slug));
            if (id != null) {
                ids.add(id);
            }
        }
        for (String phrase : aliases) {
            put(phrases, normalizeVi(phrase), ids);
        }
    }

    private List<Integer> idsWithChildren(Integer id, Map<Integer, List<Integer>> childrenByParent) {
        Set<Integer> ids = new LinkedHashSet<>();
        ids.add(id);
        ids.addAll(childrenByParent.getOrDefault(id, List.of()));
        return ids.stream().toList();
    }

    private void put(Map<String, List<Integer>> phrases, String phrase, List<Integer> ids) {
        if (phrase == null || phrase.isBlank() || ids == null || ids.isEmpty()) {
            return;
        }
        phrases.put(phrase, ids.stream().distinct().toList());
    }

    private boolean containsPhrase(String paddedMessage, String phrase) {
        return paddedMessage.contains(" " + phrase + " ");
    }

    private int tokenCount(String phrase) {
        return phrase.isBlank() ? 0 : phrase.split("\\s+").length;
    }

    private String normalizeVi(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "")
            .replace('đ', 'd')
            .replace('Đ', 'D')
            .toLowerCase(Locale.ROOT)
            .replace('-', ' ')
            .replaceAll("[^\\p{Alnum}\\s]", " ")
            .replaceAll("\\s+", " ")
            .trim();
    }

    private String shorten(String value) {
        if (value == null) return "";
        String trimmed = value.replaceAll("\\s+", " ").trim();
        return trimmed.length() > 120 ? trimmed.substring(0, 120) + "..." : trimmed;
    }

    private record PhraseRule(String phrase, List<Integer> categoryIds) {
    }

    private record CategoryDictionary(List<PhraseRule> rules, Instant expiresAt) {
    }
}
