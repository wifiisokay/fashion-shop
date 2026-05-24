package com.fashionshop.backend.module.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TagTranslationServiceTest {

    private final TagTranslationService service = new TagTranslationService();

    @Test
    void detectsOccasionAndStyleTags() {
        assertThat(service.detectOccasionTag("tìm đồ đi làm")).contains("work");
        assertThat(service.detectStyleTag("phong cách thanh lịch")).contains("smart-casual");
        assertThat(service.detectStyleTag("set đồ tối giản")).contains("minimal");
    }
}
