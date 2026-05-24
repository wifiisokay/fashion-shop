package com.fashionshop.backend.module.ai.dto.request;

import com.fashionshop.backend.module.ai.dto.ProductContextDto;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class GuestChatRequest {

    @NotBlank(message = "Nội dung tin nhắn không được trống")
    private String content;

    private Long productId;

    private Long colorId;

    private ProductContextDto productContext;

    /** Lịch sử chat tạm (browser session — không lưu DB) */
    private List<GuestMessage> history = new ArrayList<>();

    @Getter
    @Setter
    public static class GuestMessage {
        private String role; // "user" | "model"
        private String text;
    }
}
