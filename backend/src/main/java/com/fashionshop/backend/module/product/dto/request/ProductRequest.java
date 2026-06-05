package com.fashionshop.backend.module.product.dto.request;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.fashionshop.backend.common.enums.Gender;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductRequest {

    @NotBlank(message = "Product name is required")
    @Size(max = 200, message = "Product name must be at most 200 characters")
    private String name;

    @Size(min = 20, message = "Description must be at least 20 characters")
    private String description;

    @NotNull(message = "Base price is required")
    @DecimalMin(value = "0", inclusive = false, message = "Base price must be greater than 0")
    private BigDecimal basePrice;

    private BigDecimal salePrice;
    private LocalDateTime saleStartAt;
    private LocalDateTime saleEndAt;

    @NotNull(message = "isSale is required")
    private Boolean isSale;

    @NotNull(message = "categoryId is required")
    private Integer categoryId;

    @NotNull(message = "Gender is required")
    private Gender gender;

    private String material;

    @Min(value = 1, message = "Estimated weight must be greater than 0")
    @Max(value = 50000, message = "Estimated weight must be at most 50kg")
    private Integer estimatedWeight;

    @Min(value = 0, message = "Low stock threshold must not be negative")
    private Integer lowStockThreshold;

    @Size(max = 4, message = "Choose at most 4 style tags")
    private List<String> styleTags;

    @Size(max = 4, message = "Choose at most 4 occasion tags")
    private List<String> occasionTags;

    private String fitType;
    private String season;
}
