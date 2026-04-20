package com.fashionshop.backend.module.product;

import com.fashionshop.backend.common.enums.Gender;
import com.fashionshop.backend.common.enums.ProductStatus;
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
    @Mock private ProductColorRepository colorRepository;
    @Mock private StorageService storageService;
    @InjectMocks private ProductImageServiceImpl imageService;

    private Product mockProduct;
    private ProductColor mockColor;
    private ProductImage primaryImage;
    private MultipartFile mockFile;

    @BeforeEach
    void setUp() {
        mockProduct = Product.builder()
            .id(1L).name("Áo Polo")
            .basePrice(new BigDecimal("300000"))
            .gender(Gender.MALE).status(ProductStatus.ACTIVE)
            .variants(new ArrayList<>()).images(new ArrayList<>())
            .build();

        mockColor = ProductColor.builder()
            .id(10L).product(mockProduct)
            .colorName("Đen").colorCode("#000000")
            .build();

        primaryImage = ProductImage.builder()
            .id(1L).product(mockProduct)
            .imageUrl("https://cdn.test.com/old-primary.jpg")
            .publicId("fashion-shop/products/uuid-old-primary")
            .isPrimary(true).sortOrder(0)
            .build();

        mockFile = mock(MultipartFile.class);
    }

    /** Stub mockFile trả content-type và size hợp lệ — bắt buộc gọi trong mọi test có upload. */
    private void stubValidFile() {
        when(mockFile.getContentType()).thenReturn("image/jpeg");
        when(mockFile.getSize()).thenReturn(1024L);
        when(mockFile.isEmpty()).thenReturn(false);
    }

    // ================================================================
    // uploadPrimary
    // ================================================================

    @Test
    void uploadPrimary_savesNewImage_whenNoPreviousPrimary() {
        stubValidFile();
        UploadResult uploadResult = new UploadResult("https://cdn.test.com/primary.jpg", "uuid-primary");
        when(productRepository.findById(1L)).thenReturn(Optional.of(mockProduct));
        when(storageService.uploadImage(eq(mockFile), eq("fashion-shop/products"))).thenReturn(uploadResult);
        when(imageRepository.findAllPrimaryByProductId(1L)).thenReturn(List.of());
        when(imageRepository.save(any(ProductImage.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductImageResponse result = imageService.uploadPrimary(1L, mockFile);

        assertThat(result.getIsPrimary()).isTrue();
        assertThat(result.getImageUrl()).isEqualTo("https://cdn.test.com/primary.jpg");
        verify(storageService, never()).deleteImage(anyString()); // không có ảnh cũ để xóa
    }

    @Test
    void uploadPrimary_replacesOldImage_whenAlreadyExists() {
        stubValidFile();
        UploadResult uploadResult = new UploadResult("https://cdn.test.com/new-primary.jpg", "uuid-new");
        when(productRepository.findById(1L)).thenReturn(Optional.of(mockProduct));
        when(storageService.uploadImage(any(), any())).thenReturn(uploadResult);
        when(imageRepository.findAllPrimaryByProductId(1L)).thenReturn(List.of(primaryImage));
        when(imageRepository.save(any(ProductImage.class))).thenAnswer(inv -> inv.getArgument(0));

        imageService.uploadPrimary(1L, mockFile);

        // Verify ảnh cũ bị xóa trên Cloudinary
        verify(storageService).deleteImage("fashion-shop/products/uuid-old-primary");
        // Verify record cũ bị xóa trong DB
        verify(imageRepository).delete(primaryImage);
        // Verify record mới được save
        ArgumentCaptor<ProductImage> captor = ArgumentCaptor.forClass(ProductImage.class);
        verify(imageRepository).save(captor.capture());
        assertThat(captor.getValue().getIsPrimary()).isTrue();
        assertThat(captor.getValue().getSortOrder()).isEqualTo(0);
        assertThat(captor.getValue().getColor()).isNull();
    }

    @Test
    void uploadPrimary_cleansUpCloudinary_whenDbSaveFails() {
        stubValidFile();
        UploadResult uploadResult = new UploadResult("https://cdn.test.com/fail.jpg", "uuid-fail");
        when(productRepository.findById(1L)).thenReturn(Optional.of(mockProduct));
        when(storageService.uploadImage(any(), any())).thenReturn(uploadResult);
        when(imageRepository.findAllPrimaryByProductId(1L)).thenReturn(List.of());
        when(imageRepository.save(any(ProductImage.class))).thenThrow(new RuntimeException("DB error"));

        assertThatThrownBy(() -> imageService.uploadPrimary(1L, mockFile))
            .isInstanceOf(RuntimeException.class);

        verify(storageService).deleteImage("uuid-fail");
    }

    @Test
    void uploadPrimary_throwsNotFound_whenProductNotExists() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> imageService.uploadPrimary(99L, mockFile))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRODUCT_NOT_FOUND);

        verify(storageService, never()).uploadImage(any(), any());
    }

    // ================================================================
    // uploadColorImage
    // ================================================================

    @Test
    void uploadColorImage_savesImage_withAutoSortOrder() {
        stubValidFile();
        UploadResult uploadResult = new UploadResult("https://cdn.test.com/color.jpg", "uuid-color");
        when(productRepository.findById(1L)).thenReturn(Optional.of(mockProduct));
        when(colorRepository.findById(10L)).thenReturn(Optional.of(mockColor));
        when(imageRepository.countByColorId(10L)).thenReturn(2L);
        when(imageRepository.findMaxSortOrderByColorId(10L)).thenReturn(2);
        when(storageService.uploadImage(eq(mockFile), eq("fashion-shop/products"))).thenReturn(uploadResult);
        when(imageRepository.save(any(ProductImage.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductImageResponse result = imageService.uploadColorImage(1L, 10L, mockFile);

        ArgumentCaptor<ProductImage> captor = ArgumentCaptor.forClass(ProductImage.class);
        verify(imageRepository).save(captor.capture());

        ProductImage saved = captor.getValue();
        assertThat(saved.getColor()).isEqualTo(mockColor);
        assertThat(saved.getIsPrimary()).isFalse();
        assertThat(saved.getSortOrder()).isEqualTo(3); // MAX(2) + 1
    }

    @Test
    void uploadColorImage_throwsLimitExceeded_whenColorHas5Images() {
        stubValidFile();
        when(productRepository.findById(1L)).thenReturn(Optional.of(mockProduct));
        when(colorRepository.findById(10L)).thenReturn(Optional.of(mockColor));
        when(imageRepository.countByColorId(10L)).thenReturn(5L);

        assertThatThrownBy(() -> imageService.uploadColorImage(1L, 10L, mockFile))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.IMAGE_LIMIT_EXCEEDED);

        verify(storageService, never()).uploadImage(any(), any());
    }

    @Test
    void uploadColorImage_throwsColorNotFound_whenColorNotExists() {
        stubValidFile();
        when(productRepository.findById(1L)).thenReturn(Optional.of(mockProduct));
        when(colorRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> imageService.uploadColorImage(1L, 99L, mockFile))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COLOR_NOT_FOUND);
    }

    @Test
    void uploadColorImage_throwsColorNotBelong_whenColorOfDifferentProduct() {
        stubValidFile();
        Product otherProduct = Product.builder().id(2L).name("Khác").build();
        ProductColor otherColor = ProductColor.builder().id(20L).product(otherProduct).colorName("Xanh").build();
        when(productRepository.findById(1L)).thenReturn(Optional.of(mockProduct));
        when(colorRepository.findById(20L)).thenReturn(Optional.of(otherColor));

        assertThatThrownBy(() -> imageService.uploadColorImage(1L, 20L, mockFile))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COLOR_NOT_BELONG);
    }

    @Test
    void uploadColorImage_cleansUpCloudinary_whenDbSaveFails() {
        stubValidFile();
        UploadResult uploadResult = new UploadResult("https://cdn.test.com/fail.jpg", "uuid-fail");
        when(productRepository.findById(1L)).thenReturn(Optional.of(mockProduct));
        when(colorRepository.findById(10L)).thenReturn(Optional.of(mockColor));
        when(imageRepository.countByColorId(10L)).thenReturn(0L);
        when(imageRepository.findMaxSortOrderByColorId(10L)).thenReturn(0);
        when(storageService.uploadImage(any(), any())).thenReturn(uploadResult);
        when(imageRepository.save(any(ProductImage.class))).thenThrow(new RuntimeException("DB error"));

        assertThatThrownBy(() -> imageService.uploadColorImage(1L, 10L, mockFile))
            .isInstanceOf(RuntimeException.class);

        verify(storageService).deleteImage("uuid-fail");
    }

    // ================================================================
    // reorder
    // ================================================================

    @Test
    void reorder_updatesSortOrder() {
        ProductImage colorImage = ProductImage.builder()
            .id(5L).product(mockProduct).color(mockColor)
            .imageUrl("https://cdn.test.com/c1.jpg").publicId("uuid-c1")
            .isPrimary(false).sortOrder(1)
            .build();

        when(imageRepository.findById(5L)).thenReturn(Optional.of(colorImage));
        when(imageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ProductImageResponse result = imageService.reorder(1L, 5L, 3);

        assertThat(result.getSortOrder()).isEqualTo(3);
    }

    @Test
    void reorder_throwsError_whenImageIsPrimary() {
        // Primary images (color=null) cannot be reordered
        when(imageRepository.findById(1L)).thenReturn(Optional.of(primaryImage));

        assertThatThrownBy(() -> imageService.reorder(1L, 1L, 2))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.IMAGE_NOT_FOUND);
    }

    // ================================================================
    // delete
    // ================================================================

    @Test
    void delete_removesDbAndCloudinary() {
        when(imageRepository.findById(1L)).thenReturn(Optional.of(primaryImage));

        imageService.delete(1L, 1L);

        verify(imageRepository).delete(primaryImage);
        verify(storageService).deleteImage("fashion-shop/products/uuid-old-primary");
    }

    @Test
    void delete_throwsImageNotFound_whenImageNotExists() {
        when(imageRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> imageService.delete(1L, 99L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.IMAGE_NOT_FOUND);
    }

    @Test
    void delete_throwsImageNotFound_whenImageNotBelongToProduct() {
        when(imageRepository.findById(1L)).thenReturn(Optional.of(primaryImage));

        assertThatThrownBy(() -> imageService.delete(99L, 1L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.IMAGE_NOT_FOUND);
    }

    // ================================================================
    // getByProductId
    // ================================================================

    @Test
    void getByProductId_returnsList() {
        when(imageRepository.findByProductIdOrderBySortOrderAsc(1L)).thenReturn(List.of(primaryImage));

        List<ProductImageResponse> result = imageService.getByProductId(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getImageUrl()).isEqualTo("https://cdn.test.com/old-primary.jpg");
        assertThat(result.get(0).getIsPrimary()).isTrue();
    }
}
