package com.fashionshop.backend.module.ai;

import com.fashionshop.backend.domain.Category;
import com.fashionshop.backend.domain.repository.CategoryRepository;
import com.fashionshop.backend.common.enums.CategoryRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
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

    public Optional<CategoryRole> detectRole(String message) {
        String normalized = VietnameseTextNormalizer.padded(message);
        if (normalized.isBlank()) {
            return Optional.empty();
        }
        if (containsAny(normalized, " khoac ", " jacket ", " blazer ", " cardigan ", " outer ")) {
            return Optional.of(CategoryRole.OUTER);
        }
        if (containsAny(normalized, " vay ", " dam ", " dress ", " skirt ")) {
            return Optional.of(CategoryRole.DRESS);
        }
        if (containsAny(normalized, " quan ", " jean ", " jeans ", " denim ", " kaki ", " tay ",
                " trousers ", " short ", " shorts ", " bottom ")) {
            return Optional.of(CategoryRole.BOTTOM);
        }
        if (containsAny(normalized, " ao ", " thun ", " polo ", " so mi ", " ba lo ", " tank top ",
                " tanktop ", " shirt ", " tee ", " tshirt ", " hoodie ", " top ")) {
            return Optional.of(CategoryRole.TOP);
        }
        List<Integer> categoryIds = detectCategoryIds(message);
        return findRoleByCategoryIds(categoryIds);
    }

    public Optional<CategoryRole> findRoleByCategoryIds(List<Integer> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return Optional.empty();
        }
        Set<Integer> ids = new LinkedHashSet<>(categoryIds);
        return rules().stream()
                .flatMap(rule -> rule.categoryIds().stream())
                .filter(ids::contains)
                .findFirst()
                .flatMap(id -> categories().stream()
                        .filter(category -> id.equals(category.getId()))
                        .map(this::effectiveRole)
                        .filter(role -> role != null && role != CategoryRole.ROOT)
                        .findFirst());
    }

    public void invalidateCache() {
        cachedRules = null;
    }

    private List<CategoryRule> rules() {
        List<CategoryRule> snapshot = cachedRules;
        if (snapshot != null) {
            return snapshot;
        }

        List<Category> categories = categories();
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

    private List<Category> categories() {
        return categoryRepository.findAll();
    }

    private void addSynonymRules(List<CategoryRule> rules, List<Category> categories) {
        addAliases(rules, categories, List.of("ao thun"), List.of("ao phong", "t shirt", "tshirt", "tee"));
        addAliases(rules, categories, List.of("ao polo"), List.of("polo", "ao co co"));
        addAliases(rules, categories, List.of("ao so mi"), List.of("so mi", "shirt"));
        addAliases(rules, categories, List.of("quan dai"), List.of("quan jean", "quan denim", "jeans", "denim"));
        addAliases(rules, categories, List.of("quan ngan"), List.of("quan short", "short", "quan dui"));
        addAliases(rules, categories, List.of("ao khoac"), List.of("jacket", "blazer", "cardigan", "bomber", "puffer", "windbreaker"));
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

    private boolean containsAny(String normalizedMessage, String... tokens) {
        for (String token : tokens) {
            if (normalizedMessage.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private CategoryRole effectiveRole(Category category) {
        if (category == null) {
            return null;
        }
        if (category.getRole() != null && category.getRole() != CategoryRole.ROOT) {
            return category.getRole();
        }
        return category.getParent() != null ? effectiveRole(category.getParent()) : category.getRole();
    }

    private record CategoryRule(String keyword, List<Integer> categoryIds) {
    }
}
