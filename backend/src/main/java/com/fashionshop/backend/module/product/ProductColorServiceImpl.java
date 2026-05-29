package com.fashionshop.backend.module.product;

import com.fashionshop.backend.domain.Product;
import com.fashionshop.backend.domain.ProductColor;
import com.fashionshop.backend.domain.repository.ProductColorRepository;
import com.fashionshop.backend.domain.repository.ProductRepository;
import com.fashionshop.backend.exception.BusinessException;
import com.fashionshop.backend.exception.ErrorCode;
import com.fashionshop.backend.module.product.dto.request.ProductColorRequest;
import com.fashionshop.backend.module.product.dto.response.ProductColorResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductColorServiceImpl implements ProductColorService {

    private final ProductColorRepository colorRepository;
    private final ProductRepository productRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ProductColorResponse> getByProductId(Long productId) {
        return colorRepository.findByProductIdOrderByDisplayOrderAsc(productId)
            .stream().map(ProductColorResponse::from).toList();
    }

    @Override
    @Transactional
    public ProductColorResponse create(Long productId, ProductColorRequest request) {
        Product product = findProductOrThrow(productId);

        String trimmedName = request.getColorName() != null ? request.getColorName().trim() : "";
        if (colorRepository.existsByProductIdAndColorName(productId, trimmedName)) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, HttpStatus.CONFLICT,
                "Đã tồn tại màu '" + request.getColorName() + "' cho sản phẩm này");
        }

        String trimmedCode = request.getColorCode() != null ? request.getColorCode().trim() : null;
        if (trimmedCode != null && trimmedCode.isEmpty()) {
            trimmedCode = null;
        }

        ProductColor color = ProductColor.builder()
            .product(product)
            .colorName(trimmedName)
            .colorCode(trimmedCode)
            .colorFamily(ColorFamilyDeriver.derive(trimmedCode))
            .displayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0)
            .build();

        ProductColor saved = colorRepository.save(color);
        return ProductColorResponse.from(saved);
    }

    @Override
    @Transactional
    public ProductColorResponse update(Long productId, Long colorId, ProductColorRequest request) {
        ProductColor color = findColorOrThrow(colorId, productId);
        String trimmedName = request.getColorName() != null ? request.getColorName().trim() : "";
        if (colorRepository.existsByProductIdAndColorNameAndIdNot(productId, trimmedName, colorId)) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, HttpStatus.CONFLICT,
                "Đã tồn tại màu '" + request.getColorName() + "' cho sản phẩm này");
        }

        String trimmedCode = request.getColorCode() != null ? request.getColorCode().trim() : null;
        if (trimmedCode != null && trimmedCode.isEmpty()) {
            trimmedCode = null;
        }

        color.setColorName(trimmedName);
        color.setColorCode(trimmedCode);
        color.setColorFamily(ColorFamilyDeriver.derive(trimmedCode));
        color.setDisplayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0);

        ProductColor saved = colorRepository.save(color);
        return ProductColorResponse.from(saved);
    }

    @Override
    @Transactional
    public void delete(Long productId, Long colorId) {
        ProductColor color = findColorOrThrow(colorId, productId);
        // CASCADE: xóa color → xóa variants thuộc color + ảnh SET NULL
        colorRepository.delete(color);
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

}
