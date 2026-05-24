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
    private ProductImage thumbnailImage;
    private ProductImage galleryImage;
    private MultipartFile mockFile;

    @BeforeEach
    void setUp() {
        mockProduct = Product.builder()
            .id(1L).name("Ao Polo")
            .basePrice(new BigDecimal("300000"))
            .gender(Gender.MALE).status(ProductStatus.ACTIVE)
            .variants(new ArrayList<>()).images(new ArrayList<>())
            .build();

        mockColor = ProductColor.builder()
            .id(10L).product(mockProduct)
            .colorName("Den").colorCode("#000000")
            .build();

        thumbnailImage = ProductImage.builder()
            .id(1L).product(mockProduct).color(mockColor)
            .imageUrl("https://cdn.test.com/old-thumb.jpg")
            .publicId("old-thumb")
            .isPrimary(true).sortOrder(0)
            .build();

        galleryImage = ProductImage.builder()
            .id(2L).product(mockProduct).color(null)
            .imageUrl("https://cdn.test.com/gallery.jpg")
            .publicId("gallery")
            .isPrimary(false).sortOrder(1)
            .build();

        mockFile = mock(MultipartFile.class);
    }

    private void stubValidFile() {
        when(mockFile.getContentType()).thenReturn("image/jpeg");
        when(mockFile.getSize()).thenReturn(1024L);
        when(mockFile.isEmpty()).thenReturn(false);
    }

    @Test
    void uploadColorThumbnail_savesNewThumbnail_whenNoPreviousThumbnail() {
        stubValidFile();
        UploadResult uploadResult = new UploadResult("https://cdn.test.com/thumb.jpg", "thumb");
        when(productRepository.findById(1L)).thenReturn(Optional.of(mockProduct));
        when(colorRepository.findById(10L)).thenReturn(Optional.of(mockColor));
        when(imageRepository.findColorThumbnail(1L, 10L)).thenReturn(Optional.empty());
        when(storageService.uploadImage(eq(mockFile), eq("fashion-shop/products"))).thenReturn(uploadResult);
        when(imageRepository.save(any(ProductImage.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductImageResponse result = imageService.uploadColorThumbnail(1L, 10L, mockFile);

        ArgumentCaptor<ProductImage> captor = ArgumentCaptor.forClass(ProductImage.class);
        verify(imageRepository).save(captor.capture());
        assertThat(captor.getValue().getColor()).isEqualTo(mockColor);
        assertThat(captor.getValue().getIsPrimary()).isTrue();
        assertThat(captor.getValue().getSortOrder()).isZero();
        assertThat(result.getImageUrl()).isEqualTo("https://cdn.test.com/thumb.jpg");
    }

    @Test
    void uploadColorThumbnail_replacesOldThumbnailAndDeletesOldCloudinaryImage() {
        stubValidFile();
        UploadResult uploadResult = new UploadResult("https://cdn.test.com/new-thumb.jpg", "new-thumb");
        when(productRepository.findById(1L)).thenReturn(Optional.of(mockProduct));
        when(colorRepository.findById(10L)).thenReturn(Optional.of(mockColor));
        when(imageRepository.findColorThumbnail(1L, 10L)).thenReturn(Optional.of(thumbnailImage));
        when(storageService.uploadImage(any(), any())).thenReturn(uploadResult);
        when(imageRepository.save(any(ProductImage.class))).thenAnswer(inv -> inv.getArgument(0));

        imageService.uploadColorThumbnail(1L, 10L, mockFile);

        verify(storageService).deleteImage("old-thumb");
        assertThat(thumbnailImage.getImageUrl()).isEqualTo("https://cdn.test.com/new-thumb.jpg");
        assertThat(thumbnailImage.getPublicId()).isEqualTo("new-thumb");
        assertThat(thumbnailImage.getIsPrimary()).isTrue();
    }

    @Test
    void uploadColorThumbnail_throwsColorNotBelong_whenColorOfDifferentProduct() {
        Product otherProduct = Product.builder().id(2L).name("Khac").build();
        ProductColor otherColor = ProductColor.builder().id(20L).product(otherProduct).colorName("Xanh").build();
        when(productRepository.findById(1L)).thenReturn(Optional.of(mockProduct));
        when(colorRepository.findById(20L)).thenReturn(Optional.of(otherColor));

        assertThatThrownBy(() -> imageService.uploadColorThumbnail(1L, 20L, mockFile))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COLOR_NOT_BELONG);

        verify(storageService, never()).uploadImage(any(), any());
    }

    @Test
    void uploadGalleryImage_savesSharedGalleryImage() {
        stubValidFile();
        UploadResult uploadResult = new UploadResult("https://cdn.test.com/gallery-2.jpg", "gallery-2");
        when(productRepository.findById(1L)).thenReturn(Optional.of(mockProduct));
        when(imageRepository.findMaxSharedGallerySortOrder(1L)).thenReturn(1);
        when(storageService.uploadImage(eq(mockFile), eq("fashion-shop/products"))).thenReturn(uploadResult);
        when(imageRepository.save(any(ProductImage.class))).thenAnswer(inv -> inv.getArgument(0));

        imageService.uploadGalleryImage(1L, mockFile);

        ArgumentCaptor<ProductImage> captor = ArgumentCaptor.forClass(ProductImage.class);
        verify(imageRepository).save(captor.capture());
        assertThat(captor.getValue().getColor()).isNull();
        assertThat(captor.getValue().getIsPrimary()).isFalse();
        assertThat(captor.getValue().getSortOrder()).isEqualTo(2);
    }

    @Test
    void reorder_updatesSharedGallerySortOrder() {
        when(imageRepository.findById(2L)).thenReturn(Optional.of(galleryImage));
        when(imageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ProductImageResponse result = imageService.reorder(1L, 2L, 3);

        assertThat(result.getSortOrder()).isEqualTo(3);
    }

    @Test
    void reorder_rejectsColorThumbnail() {
        when(imageRepository.findById(1L)).thenReturn(Optional.of(thumbnailImage));

        assertThatThrownBy(() -> imageService.reorder(1L, 1L, 2))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.IMAGE_NOT_FOUND);
    }

    @Test
    void delete_removesDbAndCloudinary() {
        when(imageRepository.findById(2L)).thenReturn(Optional.of(galleryImage));

        imageService.delete(1L, 2L);

        verify(imageRepository).delete(galleryImage);
        verify(storageService).deleteImage("gallery");
    }

    @Test
    void getByProductId_returnsList() {
        when(imageRepository.findByProductIdOrderBySortOrderAsc(1L)).thenReturn(List.of(thumbnailImage, galleryImage));

        List<ProductImageResponse> result = imageService.getByProductId(1L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getColorId()).isEqualTo(10L);
        assertThat(result.get(1).getColorId()).isNull();
    }
}
