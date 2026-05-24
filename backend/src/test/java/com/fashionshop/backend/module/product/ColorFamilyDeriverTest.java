package com.fashionshop.backend.module.product;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ColorFamilyDeriverTest {

    @Test
    void derivesPlannedColorFamiliesFromHex() {
        assertThat(ColorFamilyDeriver.derive("#E6E6FA")).isEqualTo("warm"); // lavender
        assertThat(ColorFamilyDeriver.derive("#000080")).isEqualTo("cool"); // navy
        assertThat(ColorFamilyDeriver.derive("#A52A2A")).isEqualTo("earth"); // brown
        assertThat(ColorFamilyDeriver.derive("#F5F5DC")).isEqualTo("neutral"); // beige
        assertThat(ColorFamilyDeriver.derive("#808080")).isEqualTo("neutral"); // grey
        assertThat(ColorFamilyDeriver.derive("#000000")).isEqualTo("neutral"); // black
        assertThat(ColorFamilyDeriver.derive("#FFFFFF")).isEqualTo("neutral"); // white
        assertThat(ColorFamilyDeriver.derive(null)).isNull();
    }
}
