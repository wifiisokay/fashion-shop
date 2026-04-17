package com.fashionshop.backend.module.product.dto.response;

import com.fashionshop.backend.domain.Category;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Cây danh mục 2 cấp: mỗi category cha chứa mảng children[].
 */
@Getter
@Builder
public class CategoryTreeResponse {
    private Integer id;
    private String name;
    private String slug;
    private String description;
    private List<CategoryResponse> children;

    public static CategoryTreeResponse from(Category parent) {
        List<CategoryResponse> childList = parent.getChildren() != null
            ? parent.getChildren().stream().map(CategoryResponse::from).toList()
            : List.of();

        return CategoryTreeResponse.builder()
            .id(parent.getId())
            .name(parent.getName())
            .slug(parent.getSlug())
            .description(parent.getDescription())
            .children(childList)
            .build();
    }
}
