package com.fashionshop.backend.module.shipping;

import java.util.List;

import org.springframework.stereotype.Service;

import com.fashionshop.backend.domain.Product;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ShippingPackageEstimator {

    private final GhnProperties props;

    public ShippingPackageEstimate estimate(List<ShippingPackageItem> items) {
        GhnProperties.Defaults defaults = props.getDefaults();
        int totalWeight = 0;

        for (ShippingPackageItem item : items) {
            if (item == null || item.product() == null || item.quantity() <= 0) {
                continue;
            }
            Product product = item.product();
            int quantity = item.quantity();

            int itemWeight = positiveOrDefault(product.getEstimatedWeight(), defaults.getWeight());

            totalWeight += itemWeight * quantity;
        }

        return new ShippingPackageEstimate(
                Math.max(totalWeight, defaults.getWeight()),
                defaults.getLength(),
                defaults.getWidth(),
                defaults.getHeight());
    }

    private int positiveOrDefault(Integer value, int defaultValue) {
        return value != null && value > 0 ? value : defaultValue;
    }

}
