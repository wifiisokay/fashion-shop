package com.fashionshop.backend.module.ai.dto.response;

import com.fashionshop.backend.module.ai.InternalChatIntent;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChatContextDto {
    private InternalChatIntent internalIntent;
    private String occasionTag;
    private String occasionLabel;
    private String styleTag;
    private String season;
    private String gender;
    private Long productId;
    private Long colorId;
    private String productType;
}
