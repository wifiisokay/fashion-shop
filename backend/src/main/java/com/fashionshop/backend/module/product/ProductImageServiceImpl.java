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
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;
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
    public ProductImageResponse uploadColorThumbnail(Long productId, Long colorId, MultipartFile file) {
        Product product = findProductOrThrow(productId);
        ProductColor color = findColorOrThrow(colorId, productId);
        validateFile(file);

        UploadResult uploadResult = storageService.uploadImage(file, CLOUDINARY_FOLDER);
        ProductImage image = imageRepository.findColorThumbnail(productId, colorId)
            .orElseGet(() -> ProductImage.builder()
                .product(product)
                .color(color)
                .isPrimary(true)
                .sortOrder(0)
                .build());

        String oldPublicId = image.getPublicId();
        image.setProduct(product);
        image.setColor(color);
        image.setImageUrl(uploadResult.url());
        image.setPublicId(uploadResult.publicId());
        image.setIsPrimary(true);
        image.setSortOrder(0);

        try {
            ProductImage saved = imageRepository.save(image);
            deleteOldCloudinaryImage(oldPublicId, uploadResult.publicId());
            return ProductImageResponse.from(saved);
        } catch (Exception ex) {
            cleanupUploadedImage(uploadResult.publicId());
            throw ex;
        }
    }

    @Override
    @Transactional
    public ProductImageResponse uploadGalleryImage(Long productId, MultipartFile file) {
        Product product = findProductOrThrow(productId);
        validateFile(file);

        int nextSortOrder = imageRepository.findMaxSharedGallerySortOrder(productId) + 1;
        UploadResult uploadResult = storageService.uploadImage(file, CLOUDINARY_FOLDER);

        try {
            ProductImage image = ProductImage.builder()
                .product(product)
                .color(null)
                .imageUrl(uploadResult.url())
                .publicId(uploadResult.publicId())
                .isPrimary(false)
                .sortOrder(nextSortOrder)
                .build();

            return ProductImageResponse.from(imageRepository.save(image));
        } catch (Exception ex) {
            cleanupUploadedImage(uploadResult.publicId());
            throw ex;
        }
    }

    @Override
    @Transactional
    public ProductImageResponse reorder(Long productId, Long imageId, Integer newSortOrder) {
        ProductImage image = findImageOrThrow(imageId, productId);

        if (image.getColor() != null || Boolean.TRUE.equals(image.getIsPrimary())) {
            throw new BusinessException(ErrorCode.IMAGE_NOT_FOUND, HttpStatus.BAD_REQUEST,
                "Chi co the doi thu tu anh gallery chung");
        }
        if (newSortOrder == null || newSortOrder < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST,
                "sortOrder phai lon hon hoac bang 0");
        }

        image.setSortOrder(newSortOrder);
        return ProductImageResponse.from(imageRepository.save(image));
    }

    @Override
    @Transactional
    public void delete(Long productId, Long imageId) {
        ProductImage image = findImageOrThrow(imageId, productId);
        String publicId = image.getPublicId();

        imageRepository.delete(image);

        try {
            storageService.deleteImage(publicId);
        } catch (Exception ex) {
            log.warn("Cannot delete Cloudinary image publicId={}, leaving orphan", publicId, ex);
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE, HttpStatus.BAD_REQUEST, "File anh khong duoc de trong");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE, HttpStatus.BAD_REQUEST,
                "File anh toi da 5MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE, HttpStatus.BAD_REQUEST,
                "Chi chap nhan anh JPEG, PNG hoac WebP");
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
                "Anh khong thuoc san pham nay");
        }
        return image;
    }

    private void deleteOldCloudinaryImage(String oldPublicId, String newPublicId) {
        if (oldPublicId == null || oldPublicId.equals(newPublicId)) {
            return;
        }
        try {
            storageService.deleteImage(oldPublicId);
        } catch (Exception ex) {
            log.warn("Cannot delete old Cloudinary image publicId={}", oldPublicId, ex);
        }
    }

    private void cleanupUploadedImage(String publicId) {
        try {
            storageService.deleteImage(publicId);
        } catch (Exception cleanupEx) {
            log.warn("Cannot cleanup uploaded Cloudinary image publicId={}", publicId, cleanupEx);
        }
    }
}
