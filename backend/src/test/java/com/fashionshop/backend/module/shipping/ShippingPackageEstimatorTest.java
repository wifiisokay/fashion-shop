package com.fashionshop.backend.module.shipping;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fashionshop.backend.domain.Product;

class ShippingPackageEstimatorTest {

    private final ShippingPackageEstimator estimator = new ShippingPackageEstimator(ghnProperties());

    @Test
    void estimate_usesProductWeightAndDefaultDimensions() {
        Product product = Product.builder()
            .estimatedWeight(700)
            .build();

        ShippingPackageEstimate result = estimator.estimate(List.of(new ShippingPackageItem(product, 2)));

        assertThat(result.weight()).isEqualTo(1400);
        assertThat(result.length()).isEqualTo(30);
        assertThat(result.width()).isEqualTo(20);
        assertThat(result.height()).isEqualTo(8);
    }

    @Test
    void estimate_usesDefaultWeightWhenProductWeightMissing() {
        Product missing = Product.builder().build();

        ShippingPackageEstimate result = estimator.estimate(List.of(
            new ShippingPackageItem(missing, 3)
        ));

        assertThat(result.weight()).isEqualTo(1500);
        assertThat(result.length()).isEqualTo(30);
        assertThat(result.width()).isEqualTo(20);
        assertThat(result.height()).isEqualTo(8);
    }

    private static GhnProperties ghnProperties() {
        GhnProperties properties = new GhnProperties();
        GhnProperties.Defaults defaults = new GhnProperties.Defaults();
        defaults.setWeight(500);
        defaults.setLength(30);
        defaults.setWidth(20);
        defaults.setHeight(8);
        properties.setDefaults(defaults);
        return properties;
    }
}
