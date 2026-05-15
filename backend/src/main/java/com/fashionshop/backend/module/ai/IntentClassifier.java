package com.fashionshop.backend.module.ai;

import com.fashionshop.backend.domain.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Rule-based intent classifier — phân loại tin nhắn theo keyword matching.
 * Đọc 3 message gần nhất để tham khảo context (prior context).
 */
@Slf4j
@Component
public class IntentClassifier {

    private static final Map<ChatIntent, Set<String>> INTENT_KEYWORDS = Map.of(
        ChatIntent.PRODUCT_SEARCH, Set.of(
            "áo", "quần", "váy", "giày", "dép", "túi", "mũ", "nón", "kính",
            "tìm", "xem", "mua", "còn hàng", "giá", "sale", "giảm giá", "khuyến mãi",
            "nam", "nữ", "unisex", "trẻ em", "sản phẩm", "hàng mới",
            "chất liệu", "cotton", "polyester", "đen", "trắng", "xanh", "đỏ",
            "màu", "rẻ", "đắt", "bao nhiêu", "có không", "còn không"
        ),
        ChatIntent.OUTFIT_SUGGEST, Set.of(
            "phối", "phối đồ", "mặc với", "kết hợp", "outfit", "mix",
            "set đồ", "bộ đồ", "đồng bộ", "matching", "phong cách",
            "mặc gì", "nên mặc", "gợi ý", "tư vấn", "mix match"
        ),
        ChatIntent.ORDER_INQUIRY, Set.of(
            "đơn hàng", "đơn", "giao hàng", "vận chuyển", "theo dõi",
            "mã đơn", "order", "đã đặt", "đang giao", "khi nào nhận",
            "trạng thái đơn", "xác nhận đơn", "hủy đơn", "đã giao"
        ),
        ChatIntent.RETURN_SUPPORT, Set.of(
            "trả hàng", "đổi hàng", "hoàn tiền", "hoàn trả", "refund",
            "không vừa", "sai size", "lỗi", "hư", "hỏng", "trả lại",
            "đổi trả", "return", "bị lỗi", "sai màu", "sai sản phẩm"
        ),
        ChatIntent.GENERAL_SUPPORT, Set.of(
            "chính sách", "thanh toán", "size", "kích thước", "đo",
            "hướng dẫn", "phí ship", "phí vận chuyển", "liên hệ",
            "bảng size", "cách đo", "vnpay", "cod", "miễn phí ship",
            "thời gian giao", "giờ làm việc", "cửa hàng"
        )
    );

    /**
     * Classify intent từ message + prior context.
     *
     * @param message        tin nhắn mới
     * @param recentMessages 3 message gần nhất (có thể null/empty)
     * @return intent phù hợp nhất
     */
    public ChatIntent classify(String message, List<ChatMessage> recentMessages) {
        String normalizedMsg = message.toLowerCase().trim();

        // Tính score cho mỗi intent
        ChatIntent bestIntent = ChatIntent.CHITCHAT;
        int bestScore = 0;

        for (Map.Entry<ChatIntent, Set<String>> entry : INTENT_KEYWORDS.entrySet()) {
            int score = 0;
            for (String keyword : entry.getValue()) {
                if (normalizedMsg.contains(keyword.toLowerCase())) {
                    score++;
                }
            }
            if (score > bestScore) {
                bestScore = score;
                bestIntent = entry.getKey();
            }
        }

        // Nếu không match rõ ràng, kiểm tra prior context
        if (bestScore == 0 && recentMessages != null && !recentMessages.isEmpty()) {
            bestIntent = inferFromContext(normalizedMsg, recentMessages);
        }

        log.debug("Classified message '{}' → {} (score={})", message, bestIntent, bestScore);
        return bestIntent;
    }

    /**
     * Suy luận intent từ context khi message hiện tại không rõ ràng.
     * Ví dụ: user hỏi "cái đỏ" sau khi AI vừa gợi ý SP → PRODUCT_SEARCH
     */
    private ChatIntent inferFromContext(String message, List<ChatMessage> recent) {
        // Lấy intent gần nhất (nếu có)
        for (int i = recent.size() - 1; i >= 0; i--) {
            String lastIntent = recent.get(i).getIntent();
            if (lastIntent != null) {
                try {
                    ChatIntent prev = ChatIntent.valueOf(lastIntent);
                    // Nếu message ngắn + context trước là product/outfit → giữ intent đó
                    if (message.length() < 30 &&
                        (prev == ChatIntent.PRODUCT_SEARCH || prev == ChatIntent.OUTFIT_SUGGEST)) {
                        return prev;
                    }
                } catch (IllegalArgumentException ignored) {}
            }
        }
        return ChatIntent.CHITCHAT;
    }
}
