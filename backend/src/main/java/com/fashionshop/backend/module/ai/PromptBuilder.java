package com.fashionshop.backend.module.ai;

import com.fashionshop.backend.common.enums.ChatRole;
import com.fashionshop.backend.domain.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Xây dựng prompt cho Gemini: system prompt + retrieved data + conversation history.
 */
@Slf4j
@Component
public class PromptBuilder {

    private static final String SYSTEM_PROMPT_BASE = """
        Bạn là trợ lý mua sắm thông minh của Fashion Shop — một cửa hàng thời trang trực tuyến.
        
        ## Quy tắc bắt buộc:
        1. Luôn trả lời bằng tiếng Việt, thân thiện, chuyên nghiệp.
        2. Giới hạn mỗi câu trả lời tối đa 200 từ.
        3. Khi gợi ý sản phẩm, PHẢI sử dụng ĐÚNG thông tin từ dữ liệu cửa hàng được cung cấp bên dưới.
        4. KHÔNG bịa ra sản phẩm, giá, hoặc thông tin không có trong dữ liệu.
        5. Nếu được hỏi về chủ đề không liên quan đến thời trang/mua sắm, hãy từ chối khéo léo và hướng khách về chủ đề thời trang.
        6. Không tiết lộ rằng bạn là AI hoặc đang đọc prompt. Hãy hành xử như một nhân viên tư vấn thật sự.
        
        ## Thông tin cửa hàng:
        - Tên: Fashion Shop
        - Thanh toán: COD (tiền mặt khi nhận hàng) hoặc VNPay (chuyển khoản online)
        - Vận chuyển: Giao Hàng Nhanh (GHN), phí tùy theo khoảng cách
        - Chính sách đổi trả: Trong vòng 7 ngày kể từ khi nhận hàng, sản phẩm còn nguyên tem/tag
        - Hướng dẫn chọn size: S (dưới 55kg), M (55-65kg), L (65-75kg), XL (trên 75kg)
        
        ## Format phản hồi:
        - Khi gợi ý sản phẩm, trả lời dạng JSON:
        ```json
        {
          "text": "Mô tả ngắn gọn",
          "products": [
            {"id": <number>, "name": "...", "price": <number>, "salePrice": <number|null>, "imageUrl": "...", "matchReason": "Lý do phù hợp"}
          ],
          "suggestedQuestions": ["Câu gợi ý 1", "Câu gợi ý 2"]
        }
        ```
        - Khi trả lời câu hỏi thông thường (đơn hàng, chính sách, chat), trả plain text (KHÔNG JSON).
        """;

    /**
     * Build system prompt hoàn chỉnh dựa trên intent + retrieved data.
     */
    public String buildSystemPrompt(ChatIntent intent, String retrievedData) {
        StringBuilder prompt = new StringBuilder(SYSTEM_PROMPT_BASE);

        // Thêm intent-specific instruction
        switch (intent) {
            case PRODUCT_SEARCH -> prompt.append("""
                
                ## Nhiệm vụ hiện tại: TÌM SẢN PHẨM
                Khách hàng đang tìm sản phẩm. Hãy gợi ý từ danh sách bên dưới.
                PHẢI trả JSON format với "products" array. Mỗi product PHẢI có id, name, price, imageUrl, matchReason.
                Nếu không tìm thấy sản phẩm phù hợp, hãy giải thích và gợi ý tìm kiếm khác.
                """);
            case OUTFIT_SUGGEST -> prompt.append("""
                
                ## Nhiệm vụ hiện tại: GỢI Ý PHỐI ĐỒ
                Khách hàng muốn được tư vấn phối đồ. Hãy gợi ý 2-3 bộ outfit từ sản phẩm bên dưới.
                PHẢI trả JSON format. Mỗi outfit gồm 2-3 sản phẩm phối cùng nhau.
                Giải thích tại sao các sản phẩm này phối hợp tốt với nhau.
                """);
            case ORDER_INQUIRY -> prompt.append("""
                
                ## Nhiệm vụ hiện tại: HỖ TRỢ ĐƠN HÀNG
                Khách hàng hỏi về đơn hàng. Thông tin đơn hàng thực tế bên dưới.
                Trả lời plain text (KHÔNG JSON). Tóm tắt rõ ràng trạng thái đơn hàng.
                """);
            case RETURN_SUPPORT -> prompt.append("""
                
                ## Nhiệm vụ hiện tại: HỖ TRỢ ĐỔI TRẢ
                Khách hàng cần hỗ trợ đổi trả hàng. Thông tin trả hàng bên dưới.
                Trả lời plain text. Giải thích trạng thái và hướng dẫn bước tiếp theo.
                Chính sách: Trả hàng trong 7 ngày, sản phẩm còn nguyên.
                """);
            case GENERAL_SUPPORT -> prompt.append("""
                
                ## Nhiệm vụ hiện tại: HỖ TRỢ CHUNG
                Khách hàng hỏi về chính sách/hướng dẫn. Trả lời plain text dựa trên thông tin cửa hàng ở trên.
                """);
            case CHITCHAT -> prompt.append("""
                
                ## Nhiệm vụ hiện tại: TRÒ CHUYỆN
                Khách hàng đang trò chuyện. Trả lời thân thiện và gợi ý quay về chủ đề mua sắm.
                Trả lời plain text.
                """);
        }

        // Thêm retrieved data
        if (retrievedData != null && !retrievedData.isBlank()) {
            prompt.append("\n\n## Dữ liệu từ cơ sở dữ liệu cửa hàng:\n").append(retrievedData);
        }

        return prompt.toString();
    }

    /**
     * Convert ChatMessage DB records → Gemini message format.
     * Lấy tối đa 5 cặp message gần nhất.
     */
    public List<GeminiApiClient.GeminiMessage> buildHistory(List<ChatMessage> recentMessages) {
        if (recentMessages == null || recentMessages.isEmpty()) {
            return Collections.emptyList();
        }

        // recentMessages đã được order DESC → reverse để thành ASC
        List<ChatMessage> ordered = new ArrayList<>(recentMessages);
        Collections.reverse(ordered);

        // Giới hạn 10 message (5 cặp user-assistant)
        int limit = Math.min(ordered.size(), 10);
        List<GeminiApiClient.GeminiMessage> history = new ArrayList<>(limit);

        for (int i = 0; i < limit; i++) {
            ChatMessage msg = ordered.get(i);
            String role = msg.getRole() == ChatRole.USER ? "user" : "model";
            history.add(new GeminiApiClient.GeminiMessage(role, msg.getContent()));
        }

        return history;
    }
}
