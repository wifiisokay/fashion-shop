package com.fashionshop.backend.module.ai;

import com.fashionshop.backend.domain.Category;
import com.fashionshop.backend.domain.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CategoryKeywordMapper {

    private final CategoryRepository categoryRepository;

    private volatile List<CategoryRule> cachedRules;

    public List<Integer> detectCategoryIds(String message) {
        String normalized = VietnameseTextNormalizer.normalize(message);
        if (normalized.isBlank()) {
            return List.of();
        }

        for (CategoryRule rule : rules()) {
            if (containsPhrase(normalized, rule.keyword())) {
                return rule.categoryIds();
            }
        }
        return List.of();
    }

    public void invalidateCache() {
        cachedRules = null;
    }

    private List<CategoryRule> rules() {
        List<CategoryRule> snapshot = cachedRules;
        if (snapshot != null) {
            return snapshot;
        }

        List<Category> categories = categoryRepository.findAll();
        List<CategoryRule> dynamicRules = new ArrayList<>();
        for (Category category : categories) {
            List<Integer> categoryIds = expandWithChildren(category, categories);
            addRule(dynamicRules, category.getName(), categoryIds);
            addRule(dynamicRules, category.getSlug(), categoryIds);
        }

        addSynonymRules(dynamicRules, categories);
        dynamicRules.sort(Comparator.comparingInt((CategoryRule rule) -> rule.keyword().length()).reversed());
        cachedRules = List.copyOf(dynamicRules);
        return cachedRules;
    }

    private void addSynonymRules(List<CategoryRule> rules, List<Category> categories) {
        addAliases(rules, categories, List.of("ao thun"), List.of("ao phong", "t shirt", "tshirt", "tee"));
        addAliases(rules, categories, List.of("ao polo"), List.of("polo", "ao co co"));
        addAliases(rules, categories, List.of("ao so mi"), List.of("so mi", "shirt"));
        addAliases(rules, categories, List.of("quan jean", "quan denim"), List.of("jeans", "denim"));
        addAliases(rules, categories, List.of("quan short"), List.of("short", "quan ngan", "quan dui"));
        addAliases(rules, categories, List.of("vay", "dam", "chan vay"), List.of("dress", "skirt"));
        addAliases(rules, categories, List.of("hoodie", "ao ni"), List.of("hoodie", "ao ni"));
    }

    private void addAliases(
            List<CategoryRule> rules,
            List<Category> categories,
            List<String> categoryHints,
            List<String> aliases
    ) {
        Set<Integer> ids = categories.stream()
                .filter(category -> categoryHints.stream().anyMatch(hint -> matchesCategory(category, hint)))
                .flatMap(category -> expandWithChildren(category, categories).stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (ids.isEmpty()) {
            return;
        }
        for (String alias : aliases) {
            addRule(rules, alias, List.copyOf(ids));
        }
    }

    private boolean matchesCategory(Category category, String hint) {
        return VietnameseTextNormalizer.normalize(category.getName()).contains(hint)
                || VietnameseTextNormalizer.normalize(category.getSlug()).contains(hint);
    }

    private void addRule(List<CategoryRule> rules, String keyword, List<Integer> categoryIds) {
        String normalizedKeyword = VietnameseTextNormalizer.normalize(keyword);
        if (!normalizedKeyword.isBlank() && !categoryIds.isEmpty()) {
            rules.add(new CategoryRule(normalizedKeyword, categoryIds));
        }
    }

    private List<Integer> expandWithChildren(Category category, List<Category> categories) {
        Set<Integer> ids = new LinkedHashSet<>();
        collectCategoryIds(category.getId(), categories, ids);
        return List.copyOf(ids);
    }

    private void collectCategoryIds(Integer categoryId, List<Category> categories, Set<Integer> ids) {
        if (!ids.add(categoryId)) {
            return;
        }
        categories.stream()
                .filter(category -> category.getParent() != null && categoryId.equals(category.getParent().getId()))
                .forEach(category -> collectCategoryIds(category.getId(), categories, ids));
    }

    private boolean containsPhrase(String normalizedMessage, String phrase) {
        return (" " + normalizedMessage + " ").contains(" " + phrase + " ");
    }

    private record CategoryRule(String keyword, List<Integer> categoryIds) {
    }
}
