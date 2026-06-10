package com.fashionshop.backend.module.ai;

import com.fashionshop.backend.domain.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;

/**
 * Rule-based intent classifier — phân loại tin nhắn theo keyword matching.
 * Đọc 3 message gần nhất để tham khảo context (prior context).
 */
@Slf4j
@Component
public class IntentClassifier {

    private static final Set<String> STRONG_OUTFIT_PHRASES = Set.of(
        "goi y phoi", "phoi", "phoi do", "phoi voi", "mix do", "mac voi", "ket hop", "mix match",
        "outfit", "nen mac gi", "mac gi"
    );

    private static final Set<String> SMALLTALK_PHRASES = Set.of(
        "xin chao", "chao ban", "hello", "hi", "cam on", "thanks", "thank you",
        "ban la ai", "ban giup duoc gi", "giup duoc gi", "ok", "duoc roi"
    );

    // Thương hiệu cạnh tranh / chủ đề ngoài phạm vi
    private static final Set<String> OUT_OF_SCOPE_BRANDS = Set.of(
        "zara", "h&m", "hm", "gucci", "louis vuitton", "lv", "chanel", "prada",
        "nike", "adidas", "uniqlo", "pull&bear", "mango", "shein", "temu",
        "bershka", "forever21", "calvin klein", "tommy hilfiger"
    );

    // Câu hỏi hoàn toàn ngoài phạm vi thương mại
    private static final Set<String> OUT_OF_SCOPE_TOPICS = Set.of(
        "thoi tiet", "tin tuc", "bong da", "the thao", "chinh tri", "covid",
        "nau an", "cong thuc", "bai tap", "hoc tap", "tieng anh", "toan",
        "lam the nao de", "lich su", "dia ly", "khoa hoc",
        "phuong trinh", "giai phuong trinh", "bac 2", "can bac", "bai toan",
        "giai toan", "dao ham", "tich phan",
        "lap trinh", "viet code", "code", "thuat toan", "binary search",
        "tim kiem nhi phan", "java", "python", "javascript", "sql",
        "thu do", "nuoc phap", "dich cau", "dich sang", "translate",
        "du doan", "doi bong", "vo dich", "world cup", "wc 2026",
        "vat ly", "hoa hoc", "y te", "bac si", "thuoc", "benh", "phap luat", "luat", "hop dong"
    );

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

    private static final List<ChatIntent> PRIORITY = List.of(
        ChatIntent.RETURN_SUPPORT,
        ChatIntent.ORDER_INQUIRY,
        ChatIntent.OUTFIT_SUGGEST,
        ChatIntent.PRODUCT_SEARCH,
        ChatIntent.GENERAL_SUPPORT
    );

    /**
     * Classify intent từ message + prior context.
     *
     * @param message        tin nhắn mới
     * @param recentMessages 3 message gần nhất (có thể null/empty)
     * @return intent phù hợp nhất
     */
    public ChatIntent classify(String message, List<ChatMessage> recentMessages) {
        return classifyDetailed(message, recentMessages).intent();
    }

    public ClassificationResult classifyDetailed(String message, List<ChatMessage> recentMessages) {
        String normalizedMsg = normalizeVi(message);

        // Guard ngoài phạm vi phải chạy trước product search để tránh các câu toán/code/dịch thuật
        // bị hiểu nhầm thành tìm sản phẩm vì chứa từ khóa chung như "tìm".
        if (isOutOfScope(normalizedMsg)) {
            log.info("[AI_INTENT] message='{}' normalized='{}' intent={} reason=out_of_scope_guard",
                shorten(message), normalizedMsg.trim(), ChatIntent.OUT_OF_SCOPE);
            return new ClassificationResult(ChatIntent.OUT_OF_SCOPE, "out_of_scope_guard", normalizedMsg.trim());
        }

        if (isSmalltalk(normalizedMsg)) {
            log.info("[AI_INTENT] message='{}' normalized='{}' intent={} reason=smalltalk",
                shorten(message), normalizedMsg.trim(), ChatIntent.CHITCHAT);
            return new ClassificationResult(ChatIntent.CHITCHAT, "smalltalk", normalizedMsg.trim());
        }

        if (isStandaloneNonCommerceQuestion(message, normalizedMsg)) {
            log.info("[AI_INTENT] message='{}' normalized='{}' intent={} reason=standalone_non_commerce",
                shorten(message), normalizedMsg.trim(), ChatIntent.OUT_OF_SCOPE);
            return new ClassificationResult(ChatIntent.OUT_OF_SCOPE, "standalone_non_commerce", normalizedMsg.trim());
        }

        if (containsStrongOutfitSignal(normalizedMsg)) {
            log.info("[AI_INTENT] message='{}' normalized='{}' intent={} reason=strong_outfit_signal",
                shorten(message), normalizedMsg.trim(), ChatIntent.OUTFIT_SUGGEST);
            return new ClassificationResult(ChatIntent.OUTFIT_SUGGEST, "strong_outfit_signal", normalizedMsg.trim());
        }

        // Tính score cho mỗi intent
        ChatIntent bestIntent = ChatIntent.CHITCHAT;
        int bestScore = 0;
        Map<ChatIntent, Integer> scores = new LinkedHashMap<>();

        for (ChatIntent candidate : PRIORITY) {
            Set<String> keywords = INTENT_KEYWORDS.get(candidate);
            int score = 0;
            for (String keyword : keywords) {
                if (normalizedMsg.contains(normalizeVi(keyword))) {
                    score++;
                }
            }
            scores.put(candidate, score);
            if (score > bestScore) {
                bestScore = score;
                bestIntent = candidate;
            }
        }

        // Nếu không match rõ ràng, kiểm tra prior context
        String reason = bestScore > 0 ? "keyword_score" : "no_keyword";
        if (bestScore == 0 && recentMessages != null && !recentMessages.isEmpty()) {
            bestIntent = inferFromContext(normalizedMsg, recentMessages);
            reason = bestIntent == ChatIntent.CHITCHAT ? "context_ignored" : "context_followup";
        }
        if (bestScore == 0 && bestIntent == ChatIntent.CHITCHAT) {
            bestIntent = ChatIntent.OUT_OF_SCOPE;
            reason = "no_in_scope_signal";
        }

        log.debug("Classified message '{}' → {} (score={})", message, bestIntent, bestScore);
        log.info("[AI_INTENT] message='{}' normalized='{}' intent={} reason={} bestScore={} scores={} recentCount={}",
            shorten(message), normalizedMsg.trim(), bestIntent, reason, bestScore, scores,
            recentMessages == null ? 0 : recentMessages.size());
        return new ClassificationResult(bestIntent, reason, normalizedMsg.trim());
    }

    /**
     * Suy luận intent từ context khi message hiện tại không rõ ràng.
     * Ví dụ: user hỏi "cái đỏ" sau khi AI vừa gợi ý SP → PRODUCT_SEARCH
     */
    private ChatIntent inferFromContext(String message, List<ChatMessage> recent) {
        // Lấy intent gần nhất (nếu có)
        for (int i = 0; i < recent.size(); i++) {
            String lastIntent = recent.get(i).getIntent();
            if (lastIntent != null) {
                try {
                    ChatIntent prev = ChatIntent.valueOf(lastIntent);
                    // Nếu message ngắn + context trước là product/outfit → giữ intent đó
                    if (message.length() < 30 && hasCommerceFollowUpSignal(message) &&
                        (prev == ChatIntent.PRODUCT_SEARCH || prev == ChatIntent.OUTFIT_SUGGEST)) {
                        log.info("[AI_INTENT_CONTEXT] normalized='{}' previousIntent={} decision=keep_previous",
                            message.trim(), prev);
                        return prev;
                    }
                } catch (IllegalArgumentException ignored) {}
            }
        }
        log.info("[AI_INTENT_CONTEXT] normalized='{}' decision=out_of_scope", message.trim());
        return ChatIntent.CHITCHAT;
    }

    private boolean isSmalltalk(String normalizedMsg) {
        for (String phrase : SMALLTALK_PHRASES) {
            if (normalizedMsg.contains(" " + phrase + " ")) {
                return true;
            }
        }
        return false;
    }

    private boolean isStandaloneNonCommerceQuestion(String rawMessage, String normalizedMsg) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return true;
        }
        boolean hasCommerceKeyword = PRIORITY.stream()
            .flatMap(intent -> INTENT_KEYWORDS.get(intent).stream())
            .anyMatch(keyword -> normalizedMsg.contains(normalizeVi(keyword)));
        if (hasCommerceKeyword || containsStrongOutfitSignal(normalizedMsg)) {
            return false;
        }
        String compact = rawMessage.replaceAll("\\s+", "");
        boolean looksLikeArithmetic = compact.matches(".*\\d+[+\\-*/xX=÷]\\d+.*")
            || compact.matches(".*\\d+[+\\-*/xX÷]\\d+=\\?.*");
        boolean looksLikeGeneralQuestion = normalizedMsg.contains(" la gi ")
            || normalizedMsg.contains(" la bao nhieu ")
            || normalizedMsg.contains(" tai sao ")
            || normalizedMsg.contains(" nhu the nao ");
        return looksLikeArithmetic || looksLikeGeneralQuestion;
    }

    private boolean isOutOfScope(String normalizedMsg) {
        boolean hasInScopeSignal = hasFashionOrShopSignal(normalizedMsg);
        // Mention thương hiệu cạnh tranh
        for (String brand : OUT_OF_SCOPE_BRANDS) {
            if (normalizedMsg.contains(brand)) {
                return true;
            }
        }
        // Chủ đề hoàn toàn ngoài phạm vi
        for (String topic : OUT_OF_SCOPE_TOPICS) {
            if (normalizedMsg.contains(topic) && !hasInScopeSignal) {
                return true;
            }
        }
        return false;
    }

    private boolean hasFashionOrShopSignal(String normalizedMsg) {
        if (containsStrongOutfitSignal(normalizedMsg)) {
            return true;
        }
        return containsAny(normalizedMsg,
            " shop ", " fashion shop ", " san pham ", " hang ", " don hang ", " thanh toan ",
            " doi tra ", " hoan tien ", " ship ", " giao hang ", " vnpay ", " cod ",
            " ao ", " quan ", " vay ", " dam ", " khoac ", " polo ", " so mi ", " thun ",
            " size ", " mau ", " chat lieu ", " phoi ", " outfit ", " mac voi ");
    }

    private boolean hasCommerceFollowUpSignal(String normalizedMsg) {
        return containsAny(normalizedMsg,
            " cai ", " mau ", " size ", " gia ", " con ", " het ", " sp ", " san pham ",
            " do ", " den ", " trang ", " xanh ", " nau ", " hong ", " vang ",
            " nam ", " nu ", " unisex ", " re ", " dat ", " sale ");
    }

    private boolean containsAny(String value, String... tokens) {
        for (String token : tokens) {
            if (value.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private String shorten(String value) {
        if (value == null) return "";
        String trimmed = value.replaceAll("\\s+", " ").trim();
        return trimmed.length() > 120 ? trimmed.substring(0, 120) + "..." : trimmed;
    }

    private boolean containsStrongOutfitSignal(String normalizedMsg) {
        for (String phrase : STRONG_OUTFIT_PHRASES) {
            if (normalizedMsg.contains(phrase)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeVi(String input) {
        if (input == null) {
            return "";
        }
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "")
            .replace('đ', 'd')
            .replace('Đ', 'D')
            .toLowerCase(Locale.ROOT)
            .trim();
        return " " + normalized + " ";
    }

    public record ClassificationResult(ChatIntent intent, String reason, String normalizedMessage) {
    }
}
