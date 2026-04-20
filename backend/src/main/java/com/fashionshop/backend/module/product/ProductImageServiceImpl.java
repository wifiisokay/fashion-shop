package com.fashionshop.backend.module.product;

import com.fashionshop.backend.domain.Product;
import com.fashionshop.backend.domain.ProductColor;
import com.fashionshop.backend.domain.ProductImage;
import com.fashionshop.backend.domain.repository.ProductColorRepository;
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
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductImageServiceImpl implements ProductImageService {

    private static final String CLOUDINARY_FOLDER = "fashion-shop/products";
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final int MAX_IMAGES_PER_COLOR = 5;
    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final ProductImageRepository imageRepository;
    private final ProductRepository productRepository;
    private final ProductColorRepository colorRepository;
    private final StorageService storageService;

    @Override
    @Transactional(readOnly = true)
    public List<ProductImageResponse> getByProductId(Long productId) {
        return imageRepository.findByProductIdOrderBySortOrderAsc(productId)
            .stream().map(ProductImageResponse::from).toList();
    }

    @Override
    @Transactional
    public ProductImageResponse uploadPrimary(Long productId, MultipartFile file) {
        Product product = findProductOrThrow(productId);
        validateFile(file);

        // Upload lên Cloudinary trước
        UploadResult uploadResult = storageService.uploadImage(file, CLOUDINARY_FOLDER);

        try {
            // Tìm và xóa ảnh primary cũ (cả DB + Cloudinary)
            List<ProductImage> oldPrimaries = imageRepository.findAllPrimaryByProductId(productId);
            for (ProductImage old : oldPrimaries) {
                try {
                    storageService.deleteImage(old.getPublicId());
                } catch (Exception ex) {
                    log.warn("Không thể xóa ảnh Cloudinary cũ publicId={}", old.getPublicId(), ex);
                }
                imageRepository.delete(old);
            }

            // Save ảnh mới
            ProductImage image = ProductImage.builder()
                .product(product)
                .color(null)
                .imageUrl(uploadResult.url())
                .publicId(uploadResult.publicId())
                .isPrimary(true)
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
    public ProductImageResponse uploadColorImage(Long productId, Long colorId, MultipartFile file) {
        Product product = findProductOrThrow(productId);
        validateFile(file);

        // Validate color tồn tại và thuộc product
        ProductColor color = findColorOrThrow(colorId, productId);

        // Enforce giới hạn 5 ảnh / màu
        long currentCount = imageRepository.countByColorId(colorId);
        if (currentCount >= MAX_IMAGES_PER_COLOR) {
            throw new BusinessException(ErrorCode.IMAGE_LIMIT_EXCEEDED, HttpStatus.BAD_REQUEST);
        }

        // Tính sort_order tự động
        int nextSortOrder = imageRepository.findMaxSortOrderByColorId(colorId) + 1;

        // Upload lên Cloudinary
        UploadResult uploadResult = storageService.uploadImage(file, CLOUDINARY_FOLDER);

        try {
            ProductImage image = ProductImage.builder()
                .product(product)
                .color(color)
                .imageUrl(uploadResult.url())
                .publicId(uploadResult.publicId())
                .isPrimary(false)
                .sortOrder(nextSortOrder)
                .build();

            return ProductImageResponse.from(imageRepository.save(image));

        } catch (Exception ex) {
            log.warn("DB save thất bại, cleanup Cloudinary publicId={}", uploadResult.publicId());
            storageService.deleteImage(uploadResult.publicId());
            throw ex;
        }
    }

    @Override
    @Transactional
    public ProductImageResponse reorder(Long productId, Long imageId, Integer newSortOrder) {
        ProductImage image = findImageOrThrow(imageId, productId);

        if (image.getColor() == null) {
            throw new BusinessException(ErrorCode.IMAGE_NOT_FOUND, HttpStatus.BAD_REQUEST,
                "Không thể đổi thứ tự ảnh thẻ chính");
        }

        image.setSortOrder(newSortOrder);
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

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE, HttpStatus.BAD_REQUEST, "File ảnh không được để trống");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE, HttpStatus.BAD_REQUEST,
                "File ảnh tối đa 5MB, file hiện tại: " + (file.getSize() / 1024 / 1024) + "MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE, HttpStatus.BAD_REQUEST,
                "Chỉ chấp nhận ảnh JPEG, PNG hoặc WebP. Loại file hiện tại: " + contentType);
        }
    }

    private Product findProductOrThrow(Long id) {
        return productRepository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, HttpStatus.NOT_FOUND));
    }

    private ProductColor findColorOrThrow(Long colorId, Long productId) {
        ProductColor color = colorRepository.findById(colorId)
            .orElseThrow(() -> new BusinessException(ErrorCode.COLOR_NOT_FOUND, HttpStatus.NOT_FOUND));
        if (!color.getProduct().getId().equals(productId)) {
            throw new BusinessException(ErrorCode.COLOR_NOT_BELONG, HttpStatus.BAD_REQUEST);
        }
        return color;
    }

    private ProductImage findImageOrThrow(Long imageId, Long productId) {
        ProductImage image = imageRepository.findById(imageId)
            .orElseThrow(() -> new BusinessException(ErrorCode.IMAGE_NOT_FOUND, HttpStatus.NOT_FOUND));
        if (!image.getProduct().getId().equals(productId)) {
            throw new BusinessException(ErrorCode.IMAGE_NOT_FOUND, HttpStatus.NOT_FOUND,
                "Ảnh không thuộc sản phẩm này");
        }
        return image;
    }
}
