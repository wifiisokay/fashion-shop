package com.fashionshop.backend.module.product;

import com.fashionshop.backend.domain.Product;
import com.fashionshop.backend.domain.ProductImage;
import com.fashionshop.backend.domain.repository.ProductImageRepository;
import com.fashionshop.backend.domain.repository.ProductRepository;
import com.fashionshop.backend.exception.BusinessException;
import com.fashionshop.backend.exception.ErrorCode;
import com.fashionshop.backend.module.product.dto.response.ProductImageResponse;
import com.fashionshop.backend.module.storage.StorageService;
import com.fashionshop.backend.module.storage.UploadResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductImageServiceImpl implements ProductImageService {

    private static final String CLOUDINARY_FOLDER = "fashion-shop/products";

    private final ProductImageRepository imageRepository;
    private final ProductRepository productRepository;
    private final StorageService storageService;

    @Override
    @Transactional(readOnly = true)
    public List<ProductImageResponse> getByProductId(Long productId) {
        return imageRepository.findByProductIdOrderBySortOrderAsc(productId)
            .stream().map(ProductImageResponse::from).toList();
    }

    @Override
    @Transactional
    public ProductImageResponse upload(Long productId, MultipartFile file, Long variantId, Boolean isPrimary) {
        Product product = findProductOrThrow(productId);

        // 1. Upload lên Cloudinary
        UploadResult uploadResult = storageService.uploadImage(file, CLOUDINARY_FOLDER);

        try {
            // 2. Nếu isPrimary → clear primary cũ
            if (Boolean.TRUE.equals(isPrimary)) {
                imageRepository.clearPrimaryByProductId(productId);
            }

            // 3. Save DB
            ProductImage image = ProductImage.builder()
                .product(product)
                .variantId(variantId)
                .imageUrl(uploadResult.url())
                .publicId(uploadResult.publicId())
                .isPrimary(Boolean.TRUE.equals(isPrimary))
                .sortOrder(0)
                .build();

            return ProductImageResponse.from(imageRepository.save(image));

        } catch (Exception ex) {
            // Rollback: DB fail → xóa ảnh vừa upload trên Cloudinary
            log.warn("DB save thất bại, cleanup Cloudinary publicId={}", uploadResult.publicId());
            storageService.deleteImage(uploadResult.publicId());
            throw ex;
        }
    }

    @Override
    @Transactional
    public ProductImageResponse setPrimary(Long productId, Long imageId) {
        ProductImage image = findImageOrThrow(imageId, productId);
        imageRepository.clearPrimaryByProductId(productId);
        image.setIsPrimary(true);
        return ProductImageResponse.from(imageRepository.save(image));
    }

    @Override
    @Transactional
    public void delete(Long productId, Long imageId) {
        ProductImage image = findImageOrThrow(imageId, productId);
        String publicId = image.getPublicId();

        // Xóa DB trước
        imageRepository.delete(image);

        // Xóa Cloudinary — log nếu fail, không rollback
        try {
            storageService.deleteImage(publicId);
        } catch (Exception ex) {
            log.warn("Không thể xóa ảnh Cloudinary publicId={} — orphan chấp nhận được", publicId, ex);
        }
    }

    // ============ Private helpers ============

    private Product findProductOrThrow(Long id) {
        return productRepository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, HttpStatus.NOT_FOUND));
    }

    private ProductImage findImageOrThrow(Long imageId, Long productId) {
        ProductImage image = imageRepository.findById(imageId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, HttpStatus.NOT_FOUND,
                "Không tìm thấy ảnh"));
        if (!image.getProduct().getId().equals(productId)) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, HttpStatus.NOT_FOUND,
                "Ảnh không thuộc sản phẩm này");
        }
        return image;
    }
}
