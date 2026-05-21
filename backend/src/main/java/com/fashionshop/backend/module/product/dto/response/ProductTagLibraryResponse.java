package com.fashionshop.backend.module.product.dto.response;

import com.fashionshop.backend.module.product.ProductTagLibrary;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Set;

/**
 * Toàn bộ tag library — trả về cho FE build UI (dropdown, chip selector...).
 * Dùng tại GET /api/admin/products/tag-library.
 */
@Getter
@Builder
public class ProductTagLibraryResponse {

    private List<String> styleTags;
    private List<String> occasionTags;
    private List<String> fitTypes;
    private List<String> colorFamilies;
    private List<String> seasons;

    public static ProductTagLibraryResponse fromLibrary() {
        return ProductTagLibraryResponse.builder()
                .styleTags(sorted(ProductTagLibrary.STYLE_TAGS))
                .occasionTags(sorted(ProductTagLibrary.OCCASION_TAGS))
                .fitTypes(sorted(ProductTagLibrary.FIT_TYPES))
                .colorFamilies(sorted(ProductTagLibrary.COLOR_FAMILIES))
                .seasons(sorted(ProductTagLibrary.SEASONS))
                .build();
    }

    private static List<String> sorted(Set<String> set) {
        return set.stream().sorted().toList();
    }
}
