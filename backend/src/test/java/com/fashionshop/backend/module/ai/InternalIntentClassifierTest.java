package com.fashionshop.backend.module.ai;

import com.fashionshop.backend.module.ai.dto.ProductContextDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InternalIntentClassifierTest {

    private final InternalIntentClassifier classifier = new InternalIntentClassifier(new IntentClassifier());

    @Test
    void routesClearFastPathIntentsWithoutNluNeed() {
        assertThat(classifier.classify("xin chao ban", null, List.of(), null, null).internalIntent())
            .isEqualTo(InternalChatIntent.SMALLTALK);
        assertThat(classifier.classify("chinh sach doi tra nhu the nao", null, List.of(), null, null).internalIntent())
            .isEqualTo(InternalChatIntent.SHOP_POLICY);
        assertThat(classifier.classify("ke toi nghe ve bong da", null, List.of(), null, null).internalIntent())
            .isEqualTo(InternalChatIntent.OUT_OF_SCOPE);
    }

    @Test
    void routesProductAndOccasionOutfitRequests() {
        assertThat(classifier.classify("Goi y ao thun nam", null, List.of(), null, null).internalIntent())
            .isEqualTo(InternalChatIntent.PRODUCT_SEARCH);
        assertThat(classifier.classify("Toi muon mua ao so mi thanh lich", null, List.of(), null, "elegant").internalIntent())
            .isEqualTo(InternalChatIntent.PRODUCT_SEARCH);
        assertThat(classifier.classify("ao polo nam mac voi gi", null, List.of(), null, null).internalIntent())
            .isEqualTo(InternalChatIntent.OUTFIT_BY_PRODUCT);
        assertThat(classifier.classify("Goi y phoi do ao thoang mat mau toi", null, List.of(), null, "casual").internalIntent())
            .isNotEqualTo(InternalChatIntent.SMALLTALK);
        assertThat(classifier.classify("Goi y phoi do ao thun nam", null, List.of(), null, null).internalIntent())
            .isEqualTo(InternalChatIntent.OUTFIT_BY_PRODUCT);
        assertThat(classifier.classify("ao polo nam di choi mac voi gi", null, List.of(), "hangout", null).internalIntent())
            .isEqualTo(InternalChatIntent.OUTFIT_BY_PRODUCT_AND_OCCASION);
        assertThat(classifier.classify("cho toi ao de mac di choi", null, List.of(), "hangout", null).internalIntent())
            .isEqualTo(InternalChatIntent.OUTFIT_BY_OCCASION);
    }

    @Test
    void routesProductDetailWhenProductContextExists() {
        ProductContextDto context = ProductContextDto.builder().productId(10L).build();

        assertThat(classifier.classify("san pham nay chat lieu gi", context, List.of(), null, null).internalIntent())
            .isEqualTo(InternalChatIntent.PRODUCT_DETAIL_QA);
        assertThat(classifier.classify("ao nay mac vao dip nao", context, List.of(), null, null).internalIntent())
            .isEqualTo(InternalChatIntent.PRODUCT_DETAIL_QA);
    }

    @Test
    void routesGenericOutfitButtonWithProductContextWithoutOccasion() {
        ProductContextDto context = ProductContextDto.builder().productId(10L).colorId(3L).build();

        assertThat(classifier.classify("San pham nay nen mac voi gi?", context, List.of(), null, null).internalIntent())
            .isEqualTo(InternalChatIntent.OUTFIT_BY_PRODUCT);
    }
}
