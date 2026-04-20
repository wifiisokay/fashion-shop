package com.fashionshop.backend.module.cart.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class CartSummaryResponse {

    /** Danh sách sản phẩm trong giỏ */
    private List<CartItemResponse> items;

    /** Tổng số lượng sản phẩm (sum of quantity) */
    private Integer totalItems;

    /** Tổng tiền hàng (sum of subtotal — chưa gồm phí ship) */
    private BigDecimal totalPrice;

    /** true nếu có ít nhất 1 item đã hết hàng sau khi add vào giỏ */
    private boolean hasUnavailableItems;

    /** Trả về giỏ hàng rỗng — dùng sau clearCart() hoặc khi user chưa có item nào */
    public static CartSummaryResponse empty() {
        return CartSummaryResponse.builder()
            .items(List.of())
            .totalItems(0)
            .totalPrice(BigDecimal.ZERO)
            .hasUnavailableItems(false)
            .build();
    }
}
