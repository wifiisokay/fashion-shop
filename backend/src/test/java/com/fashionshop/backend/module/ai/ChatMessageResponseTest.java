package com.fashionshop.backend.module.ai;

import com.fashionshop.backend.module.ai.dto.response.ChatMessageResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatMessageResponseTest {

    @Test
    void builderDefaultsListsToEmptyCollections() {
        ChatMessageResponse response = ChatMessageResponse.builder()
            .role("assistant")
            .content("ok")
            .build();

        assertThat(response.getProducts()).isEmpty();
        assertThat(response.getOutfitCombos()).isEmpty();
        assertThat(response.getStyleTips()).isEmpty();
        assertThat(response.getSuggestedQuestions()).isEmpty();
    }
}
