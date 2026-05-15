package com.fashionshop.backend.module.ai;

import com.fashionshop.backend.module.ai.dto.request.GuestChatRequest;
import com.fashionshop.backend.module.ai.dto.response.ChatMessageResponse;
import com.fashionshop.backend.module.ai.dto.response.ChatSessionResponse;
import com.fashionshop.backend.module.ai.dto.response.ChatStatsResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ChatService {

    /** Customer: gửi tin nhắn, nhận phản hồi AI */
    ChatMessageResponse processMessage(Long userId, String content);

    /** Guest: gửi tin nhắn giới hạn (không lưu DB) */
    ChatMessageResponse processGuestMessage(GuestChatRequest request);

    /** Lấy/tạo session hôm nay + toàn bộ messages */
    ChatSessionResponse getTodaySession(Long userId);

    /** Lấy messages của session hôm nay */
    List<ChatMessageResponse> getTodayMessages(Long userId);

    /** Danh sách sessions phân trang */
    Page<ChatSessionResponse> getUserSessions(Long userId, Pageable pageable);

    /** Messages của 1 session cụ thể */
    List<ChatMessageResponse> getSessionMessages(Long sessionId, Long userId);

    // === Admin ===

    /** Admin: xem chat history của 1 user */
    Page<ChatSessionResponse> getAdminUserSessions(Long userId, Pageable pageable);

    /** Admin: xem messages của 1 session */
    List<ChatMessageResponse> getAdminSessionMessages(Long sessionId);

    /** Admin: thống kê chat */
    ChatStatsResponse getChatStats();

    /** Admin: xóa session */
    void deleteSession(Long sessionId);
}
