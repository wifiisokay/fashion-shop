package com.fashionshop.backend.module.ai;

import com.fashionshop.backend.common.ApiResponse;
import com.fashionshop.backend.module.ai.dto.response.ChatMessageResponse;
import com.fashionshop.backend.module.ai.dto.response.ChatSessionResponse;
import com.fashionshop.backend.module.ai.dto.response.ChatStatsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin chat management endpoints.
 * Xem chat history user, thống kê, xóa session.
 */
@RestController
@RequestMapping("/api/admin/chat")
@RequiredArgsConstructor
public class AdminChatController {

    private final ChatService chatService;

    /**
     * Xem chat sessions của 1 user.
     */
    @GetMapping("/users/{userId}")
    public ResponseEntity<ApiResponse<Page<ChatSessionResponse>>> getUserSessions(
            @PathVariable Long userId,
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(chatService.getAdminUserSessions(userId, pageable)));
    }

    /**
     * Xem messages của 1 session.
     */
    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<ApiResponse<List<ChatMessageResponse>>> getSessionMessages(
            @PathVariable Long sessionId) {
        return ResponseEntity.ok(ApiResponse.success(chatService.getAdminSessionMessages(sessionId)));
    }

    /**
     * Thống kê chat: intent distribution, messages/day.
     */
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<ChatStatsResponse>> getChatStats() {
        return ResponseEntity.ok(ApiResponse.success(chatService.getChatStats()));
    }

    /**
     * Xóa session vi phạm.
     */
    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<ApiResponse<Void>> deleteSession(@PathVariable Long sessionId) {
        chatService.deleteSession(sessionId);
        return ResponseEntity.ok(ApiResponse.success("Đã xóa session"));
    }
}
