package com.fashionshop.backend.module.product;

import com.fashionshop.backend.common.enums.Gender;
import com.fashionshop.backend.common.enums.ProductStatus;
import com.fashionshop.backend.domain.Product;
import com.fashionshop.backend.domain.ProductColor;
import com.fashionshop.backend.domain.ProductVariant;
import com.fashionshop.backend.domain.repository.ProductColorRepository;
import com.fashionshop.backend.domain.repository.ProductRepository;
import com.fashionshop.backend.domain.repository.ProductVariantRepository;
import com.fashionshop.backend.exception.BusinessException;
import com.fashionshop.backend.exception.ErrorCode;
import com.fashionshop.backend.module.product.dto.request.ProductVariantRequest;
import com.fashionshop.backend.module.product.dto.response.ProductVariantResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductVariantServiceImplTest {

    @Mock private ProductVariantRepository variantRepository;
    @Mock private ProductRepository productRepository;
    @Mock private ProductColorRepository colorRepository;
    @InjectMocks private ProductVariantServiceImpl variantService;

    private Product mockProduct;
    private ProductColor mockColor;
    private ProductVariant variant1;
    private ProductVariant variant2;

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
            .colorName("Trắng").colorCode("#FFFFFF")
            .build();

        ProductColor blackColor = ProductColor.builder()
            .id(11L).product(mockProduct)
            .colorName("Đen").colorCode("#000000")
            .build();

        variant1 = ProductVariant.builder()
            .id(1L).product(mockProduct)
            .color(mockColor).size("M").stockQuantity(10)
            .build();

        variant2 = ProductVariant.builder()
            .id(2L).product(mockProduct)
            .color(blackColor).size("L").stockQuantity(5)
            .priceAdjustment(new BigDecimal("50000"))
            .build();
    }

    // ================================================================
    // getByProductId
    // ================================================================

    @Test
    void getByProductId_returnsList() {
        when(variantRepository.findByProductId(1L)).thenReturn(List.of(variant1, variant2));

        List<ProductVariantResponse> result = variantService.getByProductId(1L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getColorName()).isEqualTo("Trắng");
        assertThat(result.get(1).getPriceAdjustment()).isEqualByComparingTo("50000");
    }

    @Test
    void getByProductId_returnsEmpty_whenNoVariants() {
        when(variantRepository.findByProductId(1L)).thenReturn(Collections.emptyList());

        List<ProductVariantResponse> result = variantService.getByProductId(1L);

        assertThat(result).isEmpty();
    }

    // ================================================================
    // create
    // ================================================================

    @Test
    void create_savesVariant_withValidRequest() {
        ProductVariantRequest request = buildVariantRequest(10L, "XL", 20, null);
        when(productRepository.findById(1L)).thenReturn(Optional.of(mockProduct));
        when(colorRepository.findById(10L)).thenReturn(Optional.of(mockColor));
        when(variantRepository.existsByColorIdAndSize(10L, "XL")).thenReturn(false);
        when(variantRepository.save(any(ProductVariant.class))).thenAnswer(inv -> inv.getArgument(0));

        variantService.create(1L, request);

        ArgumentCaptor<ProductVariant> captor = ArgumentCaptor.forClass(ProductVariant.class);
        verify(variantRepository).save(captor.capture());

        ProductVariant saved = captor.getValue();
        assertThat(saved.getColor().getColorName()).isEqualTo("Trắng");
        assertThat(saved.getSize()).isEqualTo("XL");
        assertThat(saved.getStockQuantity()).isEqualTo(20);
        assertThat(saved.getProduct()).isEqualTo(mockProduct);
    }

    @Test
    void create_throwsConflict_whenDuplicateColorSize() {
        ProductVariantRequest request = buildVariantRequest(10L, "M", 5, null);
        when(productRepository.findById(1L)).thenReturn(Optional.of(mockProduct));
        when(colorRepository.findById(10L)).thenReturn(Optional.of(mockColor));
        when(variantRepository.existsByColorIdAndSize(10L, "M")).thenReturn(true);

        assertThatThrownBy(() -> variantService.create(1L, request))
            .isInstanceOf(BusinessException.class);

        verify(variantRepository, never()).save(any());
    }

    @Test
    void create_throwsNotFound_whenProductNotExists() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> variantService.create(99L, buildVariantRequest(10L, "S", 1, null)))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRODUCT_NOT_FOUND);
    }

    // ================================================================
    // update
    // ================================================================

    @Test
    void update_updatesFields_correctly() {
        ProductVariantRequest request = buildVariantRequest(10L, "L", 15, new BigDecimal("30000"));
        when(variantRepository.findById(1L)).thenReturn(Optional.of(variant1));
        when(colorRepository.findById(10L)).thenReturn(Optional.of(mockColor));
        when(variantRepository.existsByColorIdAndSizeAndIdNot(10L, "L", 1L))
            .thenReturn(false);
        when(variantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        variantService.update(1L, 1L, request);

        assertThat(variant1.getColor().getColorName()).isEqualTo("Trắng");
        assertThat(variant1.getSize()).isEqualTo("L");
        assertThat(variant1.getStockQuantity()).isEqualTo(15);
        assertThat(variant1.getPriceAdjustment()).isEqualByComparingTo("30000");
    }

    @Test
    void update_throwsConflict_whenDuplicateColorSizeOnOther() {
        ProductVariantRequest request = buildVariantRequest(11L, "L", 10, null);
        ProductColor blackColor = ProductColor.builder()
            .id(11L).product(mockProduct).colorName("Đen").build();
        when(variantRepository.findById(1L)).thenReturn(Optional.of(variant1));
        when(colorRepository.findById(11L)).thenReturn(Optional.of(blackColor));
        when(variantRepository.existsByColorIdAndSizeAndIdNot(11L, "L", 1L))
            .thenReturn(true);

        assertThatThrownBy(() -> variantService.update(1L, 1L, request))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void update_throwsNotFound_whenVariantNotBelongToProduct() {
        when(variantRepository.findById(1L)).thenReturn(Optional.of(variant1));

        assertThatThrownBy(() -> variantService.update(99L, 1L, buildVariantRequest(10L, "S", 1, null)))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VARIANT_NOT_FOUND);
    }

    // ================================================================
    // delete
    // ================================================================

    @Test
    void delete_deletesVariant_whenValid() {
        when(variantRepository.findById(1L)).thenReturn(Optional.of(variant1));

        variantService.delete(1L, 1L);

        verify(variantRepository).delete(variant1);
    }

    @Test
    void delete_throwsNotFound_whenVariantNotExists() {
        when(variantRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> variantService.delete(1L, 99L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VARIANT_NOT_FOUND);
    }

    // ================================================================
    // Fixtures
    // ================================================================

    private ProductVariantRequest buildVariantRequest(Long colorId, String size,
                                                       Integer stock, BigDecimal price) {
        ProductVariantRequest r = new ProductVariantRequest();
        r.setColorId(colorId);
        r.setSize(size);
        r.setStockQuantity(stock);
        r.setPrice(price);
        return r;
    }
}
