package com.fashionshop.backend.module.ai;

import com.fashionshop.backend.module.ai.dto.ChatContext;
import com.fashionshop.backend.module.ai.dto.StyleAnswerResult;
import com.fashionshop.backend.module.ai.dto.response.ChatProductCard;
import com.fashionshop.backend.module.ai.dto.response.OutfitComboResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class RuleBasedStyleAnswerComposer {

    public StyleAnswerResult compose(ChatContext context, ChatProductCard anchor, List<OutfitComboResponse> combos) {
        StringBuilder content = new StringBuilder();
        if (context != null && context.getOccasionLabel() != null) {
            content.append("Gợi ý nhanh cho dịp ").append(context.getOccasionLabel()).append(". ");
        }
        if (anchor != null) {
            content.append("Mình đã chọn các món đồ để phối với ").append(anchor.getName()).append(" cho bạn. ");
        } else if (combos != null && !combos.isEmpty()) {
            content.append("Mình đã chọn vài combo phù hợp với yêu cầu của bạn. ");
        } else {
            content.append("Mình sẽ gợi ý phong cách phù hợp để bạn tham khảo. ");
        }
        content.append("Bạn có thể thêm màu sắc, ngân sách hoặc phong cách cụ thể để mình lọc sát hơn.");

        List<String> tips = new ArrayList<>();
        tips.add("Ưu tiên màu trung tính để dễ phối");
        tips.add("Cân bằng form: rộng + ôm hoặc ôm + rộng");
        if (context != null && context.getStyleTag() != null) {
            tips.add("Phong cách phù hợp: " + context.getStyleTag());
        }

        List<String> questions = new ArrayList<>();
        questions.add("Gợi ý thêm outfit khác");
        if (anchor != null) {
            questions.add("Màu nào dễ phối với " + anchor.getName() + "?");
        } else {
            questions.add("Muốn phong cách thanh lịch hay năng động?");
        }

        return StyleAnswerResult.builder()
            .content(content.toString())
            .styleTips(tips)
            .suggestedQuestions(questions)
            .build();
    }
}
