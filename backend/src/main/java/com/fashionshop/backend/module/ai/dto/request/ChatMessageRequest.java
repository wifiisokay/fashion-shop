package com.fashionshop.backend.module.ai.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChatMessageRequest {

    @NotBlank(message = "Nội dung tin nhắn không được trống")
    private String content;
}
