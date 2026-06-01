package com.fashionshop.backend.module.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VietnameseTextNormalizerTest {

    @Test
    void normalizesVietnameseTextAndBasicPunctuation() {
        assertThat(VietnameseTextNormalizer.normalize("  Áo NỈ, nữ - đi làm! "))
            .isEqualTo("ao ni nu di lam");
    }
}
