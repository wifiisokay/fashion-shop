package com.fashionshop.backend.module.ai.dto.response;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSessionResponse {

    private Long sessionId;
    private LocalDate sessionDate;
    private int messageCount;
    private LocalDateTime createdAt;
}
