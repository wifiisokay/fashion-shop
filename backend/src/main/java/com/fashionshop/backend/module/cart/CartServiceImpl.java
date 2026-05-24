package com.fashionshop.backend.module.cart;

import com.fashionshop.backend.domain.CartItem;
import com.fashionshop.backend.domain.ProductVariant;
import com.fashionshop.backend.domain.User;
import com.fashionshop.backend.domain.repository.CartItemRepository;
import com.fashionshop.backend.domain.repository.ProductImageRepository;
import com.fashionshop.backend.domain.repository.ProductVariantRepository;
import com.fashionshop.backend.domain.repository.UserRepository;
import com.fashionshop.backend.exception.BusinessException;
import com.fashionshop.backend.exception.ErrorCode;
import com.fashionshop.backend.module.cart.dto.request.AddToCartRequest;
import com.fashionshop.backend.module.cart.dto.request.UpdateCartRequest;
import com.fashionshop.backend.module.cart.dto.response.CartItemResponse;
import com.fashionshop.backend.module.cart.dto.response.CartSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartItemRepository cartItemRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductImageRepository imageRepository;
    private final UserRepository userRepository;

    // ===================== PUBLIC =====================

    @Override
    @Transactional(readOnly = true)
    public CartSummaryResponse getCart(Long userId) {
        List<CartItem> items = cartItemRepository.findByUserIdWithDetails(userId);
        return buildSummary(items);
    }

    @Override
    @Transactional
    public CartSummaryResponse addItem(Long userId, AddToCartRequest request) {
        // Validate variant tồn tại
        ProductVariant variant = variantRepository.findById(request.getVariantId())
            .orElseThrow(() -> new BusinessException(ErrorCode.VARIANT_NOT_FOUND, HttpStatus.NOT_FOUND));

        Optional<CartItem> existing =
            cartItemRepository.findByUserIdAndVariantId(userId, request.getVariantId());

        if (existing.isPresent()) {
            // Merge: cộng dồn quantity
            CartItem item = existing.get();
            int newQty = item.getQuantity() + request.getQuantity();
            validateStock(variant, newQty);
            item.setQuantity(newQty);
            cartItemRepository.save(item);
        } else {
            // Tạo mới
            validateStock(variant, request.getQuantity());
            User userRef = userRepository.getReferenceById(userId);
            CartItem newItem = CartItem.builder()
                .user(userRef)
                .variant(variant)
                .quantity(request.getQuantity())
                .build();
            cartItemRepository.save(newItem);
        }

        return getCart(userId);
    }

    @Override
    @Transactional
    public CartSummaryResponse updateItem(Long userId, Long variantId, UpdateCartRequest request) {
        CartItem item = findCartItemOrThrow(userId, variantId);
        validateStock(item.getVariant(), request.getQuantity());
        item.setQuantity(request.getQuantity());
        cartItemRepository.save(item);
        return getCart(userId);
    }

    @Override
    @Transactional
    public CartSummaryResponse removeItem(Long userId, Long variantId) {
        CartItem item = findCartItemOrThrow(userId, variantId);
        cartItemRepository.delete(item);
        return getCart(userId);
    }

    @Override
    @Transactional
    public void clearCart(Long userId) {
        cartItemRepository.deleteAllByUserId(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CartItem> getCartItems(Long userId) {
        return cartItemRepository.findByUserIdWithDetails(userId);
    }

    // ===================== PRIVATE =====================

    /**
     * Build CartSummaryResponse từ danh sách CartItem entity.
     * Với mỗi item, lookup primaryImage 1 lần theo productId.
     */
    private CartSummaryResponse buildSummary(List<CartItem> items) {
        if (items.isEmpty()) {
            return CartSummaryResponse.empty();
        }

        List<CartItemResponse> responseItems = items.stream()
            .map(item -> {
                String imageUrl = imageRepository
                    .findPrimaryByProductId(item.getVariant().getProduct().getId())
                    .map(img -> img.getImageUrl())
                    .orElse(null);
                return CartItemResponse.from(item, imageUrl);
            })
            .toList();

        int totalItems = responseItems.stream()
            .mapToInt(CartItemResponse::getQuantity)
            .sum();

        BigDecimal totalPrice = responseItems.stream()
            .map(CartItemResponse::getSubtotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        boolean hasUnavailable = responseItems.stream()
            .anyMatch(i -> !i.isAvailable());

        return CartSummaryResponse.builder()
            .items(responseItems)
            .totalItems(totalItems)
            .totalPrice(totalPrice)
            .hasUnavailableItems(hasUnavailable)
            .build();
    }

    /**
     * Validate tồn kho — throw INSUFFICIENT_STOCK với message kèm số còn lại.
     */
    private void validateStock(ProductVariant variant, int requestedQty) {
        if (requestedQty > variant.getStockQuantity()) {
            throw new BusinessException(
                ErrorCode.INSUFFICIENT_STOCK,
                HttpStatus.BAD_REQUEST,
                "Chỉ còn " + variant.getStockQuantity() + " sản phẩm trong kho"
            );
        }
    }

    /**
     * Tìm CartItem theo (userId, variantId) — throw CART_ITEM_NOT_FOUND nếu không có.
     */
    private CartItem findCartItemOrThrow(Long userId, Long variantId) {
        return cartItemRepository.findByUserIdAndVariantId(userId, variantId)
            .orElseThrow(() -> new BusinessException(
                ErrorCode.CART_ITEM_NOT_FOUND,
                HttpStatus.NOT_FOUND
            ));
    }
}
