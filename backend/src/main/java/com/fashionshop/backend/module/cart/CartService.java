package com.fashionshop.backend.module.cart;

import com.fashionshop.backend.domain.CartItem;
import com.fashionshop.backend.module.cart.dto.request.AddToCartRequest;
import com.fashionshop.backend.module.cart.dto.request.UpdateCartRequest;
import com.fashionshop.backend.module.cart.dto.response.CartSummaryResponse;

import java.util.List;

public interface CartService {

    /** Lấy toàn bộ giỏ hàng của user */
    CartSummaryResponse getCart(Long userId);

    /**
     * Thêm variant vào giỏ.
     * Nếu variant đã có → cộng dồn quantity (merge).
     */
    CartSummaryResponse addItem(Long userId, AddToCartRequest request);

    /** Cập nhật số lượng 1 item (set cứng, không cộng dồn) */
    CartSummaryResponse updateItem(Long userId, Long variantId, UpdateCartRequest request);

    /** Xóa 1 item khỏi giỏ */
    CartSummaryResponse removeItem(Long userId, Long variantId);

    /**
     * Xóa toàn bộ giỏ hàng.
     * Dùng nội bộ: OrderService gọi sau khi tạo order thành công.
     * Cũng expose qua DELETE /api/cart để user tự clear.
     */
    void clearCart(Long userId);

    /**
     * Lấy danh sách CartItem entity — dùng cho OrderService tạo order_items.
     * Không dùng DTO để OrderService có thể snapshot đầy đủ data.
     */
    List<CartItem> getCartItems(Long userId);
}
