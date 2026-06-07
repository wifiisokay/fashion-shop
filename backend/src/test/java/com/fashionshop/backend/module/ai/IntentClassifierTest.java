package com.fashionshop.backend.module.ai;

import com.fashionshop.backend.domain.ChatMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IntentClassifierTest {

    private final IntentClassifier classifier = new IntentClassifier();

    @Test
    void classifiesCoreVietnameseIntents() {
        assertThat(classifier.classify("toi muon tra hang vi sai size", List.of())).isEqualTo(ChatIntent.RETURN_SUPPORT);
        assertThat(classifier.classify("don hang cua toi dang o dau", List.of())).isEqualTo(ChatIntent.ORDER_INQUIRY);
        assertThat(classifier.classify("quan jeans nay mac voi ao gi", List.of())).isEqualTo(ChatIntent.OUTFIT_SUGGEST);
        assertThat(classifier.classify("co ao thun nam mau den duoi 300k khong", List.of())).isEqualTo(ChatIntent.PRODUCT_SEARCH);
        assertThat(classifier.classify("phi ship tinh the nao", List.of())).isEqualTo(ChatIntent.GENERAL_SUPPORT);
        assertThat(classifier.classify("xin chao ban", List.of())).isEqualTo(ChatIntent.CHITCHAT);
    }

    @Test
    void keepsPreviousProductIntentForShortFollowUp() {
        ChatMessage previous = ChatMessage.builder()
            .intent(ChatIntent.PRODUCT_SEARCH.name())
            .content("ao thun")
            .build();

        assertThat(classifier.classify("con mau nao?", List.of(previous))).isEqualTo(ChatIntent.PRODUCT_SEARCH);
    }

    @Test
    void guardsOutOfScopeBeforeProductSearch() {
        assertThat(classifier.classify("du doan doi bong vo dich WC 2026", List.of()))
            .isEqualTo(ChatIntent.OUT_OF_SCOPE);
        assertThat(classifier.classify("Giai phuong trinh bac 2 nhu nao?", List.of()))
            .isEqualTo(ChatIntent.OUT_OF_SCOPE);
        assertThat(classifier.classify("Ban co the viet code tim kiem nhi phan khong?", List.of()))
            .isEqualTo(ChatIntent.OUT_OF_SCOPE);
        assertThat(classifier.classify("Thu do nuoc Phap la gi?", List.of()))
            .isEqualTo(ChatIntent.OUT_OF_SCOPE);
        assertThat(classifier.classify("Dich cau nay sang tieng Anh", List.of()))
            .isEqualTo(ChatIntent.OUT_OF_SCOPE);
    }

    @Test
    void allowsOnlyTrueSmalltalkAsChitchat() {
        assertThat(classifier.classify("xin chao", List.of()))
            .isEqualTo(ChatIntent.CHITCHAT);
        assertThat(classifier.classify("ban giup duoc gi", List.of()))
            .isEqualTo(ChatIntent.CHITCHAT);
        assertThat(classifier.classify("ok", List.of()))
            .isEqualTo(ChatIntent.CHITCHAT);
        assertThat(classifier.classify("noi cho toi nghe ve world cup", List.of()))
            .isEqualTo(ChatIntent.OUT_OF_SCOPE);
    }

    @Test
    void keepsFashionIntentWhenFashionSignalExistsWithGeneralWords() {
        assertThat(classifier.classify("toi muon tim ao thun nam", List.of()))
            .isEqualTo(ChatIntent.PRODUCT_SEARCH);
        assertThat(classifier.classify("Tim ao so mi nam di lam", List.of()))
            .isEqualTo(ChatIntent.PRODUCT_SEARCH);
        assertThat(classifier.classify("Ao polo nam phoi voi quan gi?", List.of()))
            .isEqualTo(ChatIntent.OUTFIT_SUGGEST);
    }
}
