package com.fashionshop.backend.module.product;

import com.fashionshop.backend.common.enums.Gender;
import com.fashionshop.backend.common.enums.ProductStatus;
import com.fashionshop.backend.domain.Product;
import com.fashionshop.backend.domain.ProductImage;
import com.fashionshop.backend.domain.repository.ProductImageRepository;
import com.fashionshop.backend.domain.repository.ProductRepository;
import com.fashionshop.backend.exception.BusinessException;
import com.fashionshop.backend.exception.ErrorCode;
import com.fashionshop.backend.module.product.dto.response.ProductImageResponse;
import com.fashionshop.backend.module.storage.StorageService;
import com.fashionshop.backend.module.storage.UploadResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductImageServiceImplTest {

    @Mock private ProductImageRepository imageRepository;
    @Mock private ProductRepository productRepository;
    @Mock private StorageService storageService;
    @InjectMocks private ProductImageServiceImpl imageService;

    private Product mockProduct;
    private ProductImage image1;
    private MultipartFile mockFile;

    @BeforeEach
    void setUp() {
        mockProduct = Product.builder()
            .id(1L).name("Áo Polo")
            .basePrice(new BigDecimal("300000"))
            .gender(Gender.MALE).status(ProductStatus.ACTIVE)
            .variants(new ArrayList<>()).images(new ArrayList<>())
            .build();

        image1 = ProductImage.builder()
            .id(1L).product(mockProduct)
            .imageUrl("https://cdn.test.com/img1.jpg")
            .publicId("fashion-shop/products/uuid-1")
            .isPrimary(true).sortOrder(0)
            .build();

        mockFile = mock(MultipartFile.class);
    }

    // ================================================================
    // upload
    // ================================================================

    @Test
    void upload_savesImage_withValidFile() {
        UploadResult uploadResult = new UploadResult("https://cdn.test.com/new.jpg", "fashion-shop/products/uuid-new");
        when(productRepository.findById(1L)).thenReturn(Optional.of(mockProduct));
        when(storageService.uploadImage(eq(mockFile), eq("fashion-shop/products"))).thenReturn(uploadResult);
        when(imageRepository.save(any(ProductImage.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductImageResponse result = imageService.upload(1L, mockFile, null, false);

        ArgumentCaptor<ProductImage> captor = ArgumentCaptor.forClass(ProductImage.class);
        verify(imageRepository).save(captor.capture());

        ProductImage saved = captor.getValue();
        assertThat(saved.getImageUrl()).isEqualTo("https://cdn.test.com/new.jpg");
        assertThat(saved.getPublicId()).isEqualTo("fashion-shop/products/uuid-new");
        assertThat(saved.getIsPrimary()).isFalse();
    }

    @Test
    void upload_clearsPrimary_whenIsPrimaryTrue() {
        UploadResult uploadResult = new UploadResult("https://cdn.test.com/primary.jpg", "uuid-p");
        when(productRepository.findById(1L)).thenReturn(Optional.of(mockProduct));
        when(storageService.uploadImage(any(), any())).thenReturn(uploadResult);
        when(imageRepository.save(any(ProductImage.class))).thenAnswer(inv -> inv.getArgument(0));

        imageService.upload(1L, mockFile, null, true);

        verify(imageRepository).clearPrimaryByProductId(1L);

        ArgumentCaptor<ProductImage> captor = ArgumentCaptor.forClass(ProductImage.class);
        verify(imageRepository).save(captor.capture());
        assertThat(captor.getValue().getIsPrimary()).isTrue();
    }

    @Test
    void upload_deletesCloudinary_whenDbSaveFails() {
        UploadResult uploadResult = new UploadResult("https://cdn.test.com/fail.jpg", "uuid-fail");
        when(productRepository.findById(1L)).thenReturn(Optional.of(mockProduct));
        when(storageService.uploadImage(any(), any())).thenReturn(uploadResult);
        when(imageRepository.save(any(ProductImage.class))).thenThrow(new RuntimeException("DB error"));

        assertThatThrownBy(() -> imageService.upload(1L, mockFile, null, false))
            .isInstanceOf(RuntimeException.class);

        // Verify cleanup: Cloudinary image phải được xóa khi DB save fail
        verify(storageService).deleteImage("uuid-fail");
    }

    @Test
    void upload_throwsNotFound_whenProductNotExists() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> imageService.upload(99L, mockFile, null, false))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRODUCT_NOT_FOUND);

        verify(storageService, never()).uploadImage(any(), any());
    }

    // ================================================================
    // setPrimary
    // ================================================================

    @Test
    void setPrimary_setsFlag_correctly() {
        ProductImage nonPrimary = ProductImage.builder()
            .id(2L).product(mockProduct)
            .imageUrl("https://cdn.test.com/img2.jpg").publicId("uuid-2")
            .isPrimary(false).sortOrder(1)
            .build();

        when(imageRepository.findById(2L)).thenReturn(Optional.of(nonPrimary));
        when(imageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        imageService.setPrimary(1L, 2L);

        verify(imageRepository).clearPrimaryByProductId(1L);
        assertThat(nonPrimary.getIsPrimary()).isTrue();
    }

    @Test
    void setPrimary_throwsNotFound_whenImageNotBelongToProduct() {
        // image1 thuộc product 1, nhưng request gửi productId=99
        when(imageRepository.findById(1L)).thenReturn(Optional.of(image1));

        assertThatThrownBy(() -> imageService.setPrimary(99L, 1L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRODUCT_NOT_FOUND);
    }

    // ================================================================
    // delete
    // ================================================================

    @Test
    void delete_removesDbAndCloudinary() {
        when(imageRepository.findById(1L)).thenReturn(Optional.of(image1));

        imageService.delete(1L, 1L);

        verify(imageRepository).delete(image1);
        verify(storageService).deleteImage("fashion-shop/products/uuid-1");
    }

    @Test
    void delete_throwsNotFound_whenImageNotExists() {
        when(imageRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> imageService.delete(1L, 99L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRODUCT_NOT_FOUND);
    }

    // ================================================================
    // getByProductId
    // ================================================================

    @Test
    void getByProductId_returnsList() {
        when(imageRepository.findByProductIdOrderBySortOrderAsc(1L)).thenReturn(List.of(image1));

        List<ProductImageResponse> result = imageService.getByProductId(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getImageUrl()).isEqualTo("https://cdn.test.com/img1.jpg");
        assertThat(result.get(0).getIsPrimary()).isTrue();
    }
}
