package com.fashionshop.backend.module.shipping;

public record ShippingPackageEstimate(
    int weight,
    int length,
    int width,
    int height
) {}
