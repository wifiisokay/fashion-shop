package com.fashionshop.backend.module.product.dto.response;

import com.fashionshop.backend.common.enums.CategoryRole;
import com.fashionshop.backend.domain.Category;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CategoryResponse {
    private Integer id;
    private String name;
    private String slug;
    private String description;
    private Integer parentId;
    private CategoryRole role;

    public static CategoryResponse from(Category category) {
        return CategoryResponse.builder()
            .id(category.getId())
            .name(category.getName())
            .slug(category.getSlug())
            .description(category.getDescription())
            .parentId(category.getParent() != null ? category.getParent().getId() : null)
            .role(category.getRole())
            .build();
    }
}
