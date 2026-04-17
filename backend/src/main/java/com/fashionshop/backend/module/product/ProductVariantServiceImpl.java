package com.fashionshop.backend.module.product;

import com.fashionshop.backend.domain.Product;
import com.fashionshop.backend.domain.ProductVariant;
import com.fashionshop.backend.domain.repository.ProductRepository;
import com.fashionshop.backend.domain.repository.ProductVariantRepository;
import com.fashionshop.backend.exception.BusinessException;
import com.fashionshop.backend.exception.ErrorCode;
import com.fashionshop.backend.module.product.dto.request.ProductVariantRequest;
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

        // Check UNIQUE(product_id, color, size)
        if (variantRepository.existsByProductIdAndColorAndSize(productId, request.getColor(), request.getSize())) {
            throw new BusinessException(ErrorCode.VARIANT_NOT_FOUND, HttpStatus.CONFLICT,
                "Đã tồn tại biến thể cùng màu '" + request.getColor() + "' và size '" + request.getSize() + "'");
        }

        ProductVariant variant = ProductVariant.builder()
            .product(product)
            .color(request.getColor().trim())
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

        // Check UNIQUE trừ chính variant đang sửa
        if (variantRepository.existsByProductIdAndColorAndSizeAndIdNot(
                productId, request.getColor(), request.getSize(), variantId)) {
            throw new BusinessException(ErrorCode.VARIANT_NOT_FOUND, HttpStatus.CONFLICT,
                "Đã tồn tại biến thể cùng màu '" + request.getColor() + "' và size '" + request.getSize() + "'");
        }

        variant.setColor(request.getColor().trim());
        variant.setSize(request.getSize().trim());
        variant.setStockQuantity(request.getStockQuantity());
        variant.setPriceAdjustment(request.getPrice());

        return ProductVariantResponse.from(variantRepository.save(variant));
    }

    @Override
    @Transactional
    public void delete(Long productId, Long variantId) {
        ProductVariant variant = findVariantOrThrow(variantId, productId);
        // TODO: Khi cart/order module sẵn sàng — check usage trước khi xóa
        variantRepository.delete(variant);
    }

    // ============ Private helpers ============

    private Product findProductOrThrow(Long id) {
        return productRepository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, HttpStatus.NOT_FOUND));
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
