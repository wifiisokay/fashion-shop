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
    private String name;
    private BigDecimal price;
    private BigDecimal salePrice;
    private String imageUrl;
    private String matchReason;
}
