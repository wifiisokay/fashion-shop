package com.fashionshop.backend.module.cart.dto.response;

import com.fashionshop.backend.domain.CartItem;
import com.fashionshop.backend.domain.Product;
import com.fashionshop.backend.domain.ProductVariant;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class CartItemResponse {

    private Long variantId;
    private Long productId;
    private String productName;

    /** Tên màu từ ProductColor.colorName */
    private String color;

    /** Size: S/M/L/XL... */
    private String size;

    /** URL ảnh thẻ chính (color IS NULL, is_primary=true) — null nếu chưa có ảnh */
    private String primaryImageUrl;

    /** Giá 1 đơn vị (đã tính sale / adjustment) */
    private BigDecimal unitPrice;

    private Integer quantity;

    /** unitPrice × quantity */
    private BigDecimal subtotal;

    /** Tồn kho realtime — lấy từ variant.stockQuantity tại thời điểm load giỏ */
    private Integer stockQuantity;

    /** false nếu variant đã hết hàng sau khi user thêm vào giỏ */
    private boolean available;

    /** Cân nặng ước tính per item (gram) — dùng tính phí ship */
    private Integer estimatedWeight;

    // ============ Static factory ============

    public static CartItemResponse from(CartItem item, String primaryImageUrl) {
        ProductVariant variant = item.getVariant();
        Product product = variant.getProduct();

        BigDecimal unitPrice = resolveUnitPrice(product, variant);
        boolean available = variant.getStockQuantity() > 0;

        return CartItemResponse.builder()
            .variantId(variant.getId())
            .productId(product.getId())
            .productName(product.getName())
            .color(variant.getColor() != null ? variant.getColor().getColorName() : null)
            .size(variant.getSize())
            .primaryImageUrl(primaryImageUrl)
            .unitPrice(unitPrice)
            .quantity(item.getQuantity())
            .subtotal(unitPrice.multiply(BigDecimal.valueOf(item.getQuantity())))
            .stockQuantity(variant.getStockQuantity())
            .available(available)
            .estimatedWeight(product.getEstimatedWeight())
            .build();
    }

    /**
     * Tính đơn giá theo thứ tự ưu tiên:
     * 1. isSale=true + salePrice != null → salePrice
     * 2. priceAdjustment != null && != 0  → basePrice + priceAdjustment
     * 3. Mặc định                         → basePrice
     */
    private static BigDecimal resolveUnitPrice(Product product, ProductVariant variant) {
        if (Boolean.TRUE.equals(product.getIsSale()) && product.getSalePrice() != null) {
            return product.getSalePrice();
        }
        if (variant.getPriceAdjustment() != null
            && variant.getPriceAdjustment().compareTo(BigDecimal.ZERO) != 0) {
            return product.getBasePrice().add(variant.getPriceAdjustment());
        }
        return product.getBasePrice();
    }
}
