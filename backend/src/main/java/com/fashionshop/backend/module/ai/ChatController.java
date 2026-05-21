package com.fashionshop.backend.module.ai;

import com.fashionshop.backend.common.ApiResponse;
import com.fashionshop.backend.domain.User;
import com.fashionshop.backend.module.ai.dto.request.ChatMessageRequest;
import com.fashionshop.backend.module.ai.dto.request.GuestChatRequest;
import com.fashionshop.backend.module.ai.dto.response.ChatMessageResponse;
import com.fashionshop.backend.module.ai.dto.response.ChatSessionResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Customer chat endpoints.
 * - Authenticated: gửi message, xem session, xem lịch sử
 * - Guest: gửi message giới hạn (không lưu DB)
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /**
     * Gửi tin nhắn → nhận AI response (authenticated).
     */
    @PostMapping("/message")
    public ResponseEntity<ApiResponse<ChatMessageResponse>> sendMessage(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody ChatMessageRequest request) {
        ChatMessageResponse response = chatService.processMessage(user.getId(), request.getContent(), request.getProductId(), request.getColorId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Guest chat — không cần đăng nhập, giới hạn intent.
     */
    @PostMapping("/guest/message")
    public ResponseEntity<ApiResponse<ChatMessageResponse>> sendGuestMessage(
            @Valid @RequestBody GuestChatRequest request) {
        ChatMessageResponse response = chatService.processGuestMessage(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Lấy/tạo session hôm nay + messages.
     */
    @GetMapping("/session/today")
    public ResponseEntity<ApiResponse<ChatSessionResponse>> getTodaySession(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.success(chatService.getTodaySession(user.getId())));
    }

    /**
     * Lấy messages của session hôm nay.
     */
    @GetMapping("/messages/today")
    public ResponseEntity<ApiResponse<List<ChatMessageResponse>>> getTodayMessages(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.success(chatService.getTodayMessages(user.getId())));
    }

    /**
     * Danh sách sessions (phân trang).
     */
    @GetMapping("/sessions")
    public ResponseEntity<ApiResponse<Page<ChatSessionResponse>>> getSessions(
            @AuthenticationPrincipal User user,
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(chatService.getUserSessions(user.getId(), pageable)));
    }

    /**
     * Messages của 1 session cụ thể.
     */
    @GetMapping("/sessions/{id}")
    public ResponseEntity<ApiResponse<List<ChatMessageResponse>>> getSessionMessages(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(chatService.getSessionMessages(id, user.getId())));
    }
}
