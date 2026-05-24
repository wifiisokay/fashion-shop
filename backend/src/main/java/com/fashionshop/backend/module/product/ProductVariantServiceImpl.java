package com.fashionshop.backend.module.product;

import com.fashionshop.backend.domain.Product;
import com.fashionshop.backend.domain.ProductColor;
import com.fashionshop.backend.domain.ProductVariant;
import com.fashionshop.backend.domain.repository.CartItemRepository;
import com.fashionshop.backend.domain.repository.ProductColorRepository;
import com.fashionshop.backend.domain.repository.ProductRepository;
import com.fashionshop.backend.domain.repository.ProductVariantRepository;
import com.fashionshop.backend.exception.BusinessException;
import com.fashionshop.backend.exception.ErrorCode;
import com.fashionshop.backend.module.product.dto.request.ProductVariantRequest;
import com.fashionshop.backend.module.product.dto.request.StockUpdateRequest;
import com.fashionshop.backend.module.product.dto.response.ProductVariantResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductVariantServiceImpl implements ProductVariantService {

    private final ProductVariantRepository variantRepository;
    private final ProductRepository productRepository;
    private final ProductColorRepository colorRepository;
    private final CartItemRepository cartItemRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ProductVariantResponse> getByProductId(Long productId) {
        return variantRepository.findByProductId(productId)
            .stream().map(ProductVariantResponse::from).toList();
    }

    @Override
    @Transactional
    public ProductVariantResponse create(Long productId, ProductVariantRequest request) {
        Product product = findProductOrThrow(productId);
        ProductColor color = findColorOrThrow(request.getColorId(), productId);

        // Check UNIQUE(color_id, size)
        if (variantRepository.existsByColorIdAndSize(color.getId(), request.getSize())) {
            throw new BusinessException(ErrorCode.VARIANT_NOT_FOUND, HttpStatus.CONFLICT,
                "Đã tồn tại biến thể cùng màu '" + color.getColorName() + "' và size '" + request.getSize() + "'");
        }

        ProductVariant variant = ProductVariant.builder()
            .product(product)
            .color(color)
            .size(request.getSize().trim())
            .stockQuantity(request.getStockQuantity())
            .priceAdjustment(request.getPrice())
            .build();

        return ProductVariantResponse.from(variantRepository.save(variant));
    }

    @Override
    @Transactional
    public ProductVariantResponse update(Long productId, Long variantId, ProductVariantRequest request) {
        ProductVariant variant = findVariantOrThrow(variantId, productId);
        ProductColor color = findColorOrThrow(request.getColorId(), productId);

        // Check UNIQUE trừ chính variant đang sửa
        if (variantRepository.existsByColorIdAndSizeAndIdNot(color.getId(), request.getSize(), variantId)) {
            throw new BusinessException(ErrorCode.VARIANT_NOT_FOUND, HttpStatus.CONFLICT,
                "Đã tồn tại biến thể cùng màu '" + color.getColorName() + "' và size '" + request.getSize() + "'");
        }

        variant.setColor(color);
        variant.setSize(request.getSize().trim());
        variant.setStockQuantity(request.getStockQuantity());
        variant.setPriceAdjustment(request.getPrice());

        return ProductVariantResponse.from(variantRepository.save(variant));
    }

    @Override
    @Transactional
    public void delete(Long productId, Long variantId) {
        ProductVariant variant = findVariantOrThrow(variantId, productId);
        // Enforce RESTRICT: chặn xóa nếu variant đang có trong giỏ hàng của ai đó
        if (cartItemRepository.existsByVariantId(variantId)) {
            throw new BusinessException(ErrorCode.VARIANT_IN_CART, HttpStatus.CONFLICT,
                "Biến thể đang có trong giỏ hàng, không thể xóa");
        }
        variantRepository.delete(variant);
    }

    @Override
    @Transactional
    public ProductVariantResponse updateStock(Long productId, Long variantId, StockUpdateRequest request) {
        ProductVariant variant = findVariantOrThrow(variantId, productId);
        variant.setStockQuantity(request.getStockQuantity());
        return ProductVariantResponse.from(variantRepository.save(variant));
    }

    // ============ Private helpers ============

    private Product findProductOrThrow(Long id) {
        return productRepository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, HttpStatus.NOT_FOUND));
    }

    private ProductColor findColorOrThrow(Long colorId, Long productId) {
        ProductColor color = colorRepository.findById(colorId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, HttpStatus.NOT_FOUND,
                "Không tìm thấy màu sắc"));
        if (!color.getProduct().getId().equals(productId)) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, HttpStatus.NOT_FOUND,
                "Màu sắc không thuộc sản phẩm này");
        }
        return color;
    }

    private ProductVariant findVariantOrThrow(Long variantId, Long productId) {
        ProductVariant variant = variantRepository.findById(variantId)
            .orElseThrow(() -> new BusinessException(ErrorCode.VARIANT_NOT_FOUND, HttpStatus.NOT_FOUND));
        if (!variant.getProduct().getId().equals(productId)) {
            throw new BusinessException(ErrorCode.VARIANT_NOT_FOUND, HttpStatus.NOT_FOUND,
                "Biến thể không thuộc sản phẩm này");
        }
        return variant;
    }
}
