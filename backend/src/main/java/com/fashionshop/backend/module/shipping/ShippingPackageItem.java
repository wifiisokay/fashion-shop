package com.fashionshop.backend.module.shipping;

import com.fashionshop.backend.domain.Product;

public record ShippingPackageItem(
    Product product,
    int quantity
) {}
