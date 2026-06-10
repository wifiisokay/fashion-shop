package com.fashionshop.backend.module.ai.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatProductCard {

    private Long id;
    private Long colorId;
    private String name;
    private String description;
    private BigDecimal price;
    private BigDecimal salePrice;
    private BigDecimal displayPrice;
    private Boolean isSale;
    private String imageUrl;
    private String url;
    private String role;
    private String reason;
    private String categorySlug;
    private String categoryName;
    private String categoryRole;
    private String parentCategoryName;
    private String gender;
    private String colorName;
    private String colorCode;
    private String colorFamily;
    private String fitType;
    private Long totalStock;
    private String matchReason;
    private java.util.List<ChatProductVariantOption> availableVariants;
    private java.util.List<String> styleTags;
    private java.util.List<String> occasionTags;
    private String material;
    private String season;
}
