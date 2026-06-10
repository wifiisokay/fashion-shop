package com.fashionshop.backend.module.cart;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fashionshop.backend.common.enums.ProductStatus;
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
import com.fashionshop.backend.module.product.ProductPriceService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartItemRepository cartItemRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductImageRepository imageRepository;
    private final UserRepository userRepository;
    private final ProductPriceService productPriceService;

    @Override
    @Transactional(readOnly = true)
    public CartSummaryResponse getCart(Long userId) {
        List<CartItem> items = cartItemRepository.findByUserIdWithDetails(userId);
        return buildSummary(items);
    }

    @Override
    @Transactional
    public CartSummaryResponse addItem(Long userId, AddToCartRequest request) {
        ProductVariant variant = variantRepository.findById(request.getVariantId())
            .orElseThrow(() -> new BusinessException(ErrorCode.VARIANT_NOT_FOUND, HttpStatus.NOT_FOUND));

        Optional<CartItem> existing = cartItemRepository.findByUserIdAndVariantId(userId, request.getVariantId());
        if (existing.isPresent()) {
            CartItem item = existing.get();
            int newQty = item.getQuantity() + request.getQuantity();
            validateStock(variant, newQty);
            item.setQuantity(newQty);
            cartItemRepository.save(item);
        } else {
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
    @Transactional
    public void clearByVariantIds(Long userId, List<Long> variantIds) {
        if (variantIds == null || variantIds.isEmpty()) {
            return;
        }
        cartItemRepository.deleteByUserIdAndVariantIds(userId, variantIds);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CartItem> getCartItems(Long userId) {
        return cartItemRepository.findByUserIdWithDetails(userId);
    }

    private CartSummaryResponse buildSummary(List<CartItem> items) {
        if (items.isEmpty()) {
            return CartSummaryResponse.empty();
        }

        List<CartItemResponse> responseItems = items.stream()
            .map(item -> {
                String imageUrl = resolveVariantImageUrl(item.getVariant());
                return CartItemResponse.from(item, imageUrl, productPriceService);
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

    private String resolveVariantImageUrl(ProductVariant variant) {
        if (variant == null || variant.getProduct() == null) {
            return null;
        }

        Long productId = variant.getProduct().getId();
        if (variant.getColor() != null) {
            var colorImage = imageRepository.findColorThumbnail(productId, variant.getColor().getId());
            if (colorImage.isPresent()) {
                return colorImage.get().getImageUrl();
            }
        }

        return imageRepository.findPrimaryByProductId(productId)
            .map(img -> img.getImageUrl())
            .orElse(null);
    }

    private void validateStock(ProductVariant variant, int requestedQty) {
        if (variant.getProduct() == null || variant.getProduct().getStatus() != ProductStatus.ACTIVE) {
            throw new BusinessException(
                ErrorCode.PRODUCT_OUT_OF_STOCK,
                HttpStatus.BAD_REQUEST,
                "San pham khong con ban"
            );
        }

        int stockQuantity = variant.getStockQuantity() != null ? variant.getStockQuantity() : 0;
        if (requestedQty > stockQuantity) {
            throw new BusinessException(
                ErrorCode.INSUFFICIENT_STOCK,
                HttpStatus.BAD_REQUEST,
                "Chi con " + stockQuantity + " san pham trong kho"
            );
        }
    }

    private CartItem findCartItemOrThrow(Long userId, Long variantId) {
        return cartItemRepository.findByUserIdAndVariantId(userId, variantId)
            .orElseThrow(() -> new BusinessException(
                ErrorCode.CART_ITEM_NOT_FOUND,
                HttpStatus.NOT_FOUND
            ));
    }
}
