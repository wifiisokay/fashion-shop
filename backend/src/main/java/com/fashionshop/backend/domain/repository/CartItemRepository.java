package com.fashionshop.backend.domain.repository;

import com.fashionshop.backend.domain.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    /**
     * Lấy toàn bộ cart items của user với JOIN FETCH đầy đủ —
     * tránh N+1 queries khi build CartSummaryResponse.
     */
    @Query("""
        SELECT ci FROM CartItem ci
        JOIN FETCH ci.variant v
        JOIN FETCH v.product p
        JOIN FETCH v.color c
        WHERE ci.user.id = :userId
        ORDER BY ci.addedAt DESC
    """)
    List<CartItem> findByUserIdWithDetails(@Param("userId") Long userId);

    /**
     * Tìm 1 item theo (userId, variantId).
     * UNIQUE constraint đảm bảo max 1 kết quả.
     */
    Optional<CartItem> findByUserIdAndVariantId(Long userId, Long variantId);

    /**
     * Xóa toàn bộ cart của 1 user — gọi sau khi tạo order thành công.
     */
    void deleteAllByUserId(Long userId);

    /**
     * Kiểm tra variant có đang nằm trong giỏ hàng nào không.
     * Dùng trong ProductVariantServiceImpl.delete() để enforce RESTRICT.
     */
    boolean existsByVariantId(Long variantId);
}
