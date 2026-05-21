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
    private BigDecimal price;
    private BigDecimal salePrice;
    private BigDecimal displayPrice;
    private String imageUrl;
    private String url;
    private String role;
    private String reason;
    private String categorySlug;
    private String gender;
    private String colorName;
    private String colorCode;
    private String colorFamily;
    private Long totalStock;
    private String matchReason;
}
