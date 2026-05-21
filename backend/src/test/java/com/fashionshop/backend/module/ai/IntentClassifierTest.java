package com.fashionshop.backend.module.ai;

import com.fashionshop.backend.domain.ChatMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IntentClassifierTest {

    private final IntentClassifier classifier = new IntentClassifier();

    @Test
    void classifiesCoreVietnameseIntents() {
        assertThat(classifier.classify("tôi muốn trả hàng vì sai size", List.of())).isEqualTo(ChatIntent.RETURN_SUPPORT);
        assertThat(classifier.classify("đơn hàng của tôi đang ở đâu", List.of())).isEqualTo(ChatIntent.ORDER_INQUIRY);
        assertThat(classifier.classify("quần jeans này mặc với áo gì", List.of())).isEqualTo(ChatIntent.OUTFIT_SUGGEST);
        assertThat(classifier.classify("có áo thun nam màu đen dưới 300k không", List.of())).isEqualTo(ChatIntent.PRODUCT_SEARCH);
        assertThat(classifier.classify("phí ship tính thế nào", List.of())).isEqualTo(ChatIntent.GENERAL_SUPPORT);
        assertThat(classifier.classify("xin chào bạn", List.of())).isEqualTo(ChatIntent.CHITCHAT);
    }

    @Test
    void keepsPreviousProductIntentForShortFollowUp() {
        ChatMessage previous = ChatMessage.builder()
            .intent(ChatIntent.PRODUCT_SEARCH.name())
            .content("áo thun")
            .build();

        assertThat(classifier.classify("còn màu nào?", List.of(previous))).isEqualTo(ChatIntent.PRODUCT_SEARCH);
    }
}
