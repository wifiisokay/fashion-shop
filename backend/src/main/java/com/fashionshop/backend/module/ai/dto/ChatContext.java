package com.fashionshop.backend.module.ai.dto;

import com.fashionshop.backend.module.ai.InternalChatIntent;
import com.fashionshop.backend.module.ai.ChatIntent;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChatContext {
    private String originalMessage;
    private String normalizedMessage;
    private InternalChatIntent internalIntent;
    private ChatIntent responseIntent;
    private String gender;
    private Integer categoryId;
    private String categorySlug;
    private String categoryRole;
    private String productType;
    private Long productId;
    private Long colorId;
    private String colorName;
    private String colorFamily;
    private String occasionTag;
    private String occasionLabel;
    private String styleTag;
    private String season;
    private Long budgetMin;
    private Long budgetMax;
    private String mentionedProductName;
    private Long followUpTargetProductId;
    private Long followUpTargetColorId;
    private String lastIntent;
    private Long lastProductId;
    private Long lastColorId;
    private String lastFilters;
    private String questionType;
    private String action;
}
