package com.fashionshop.backend.module.product;

import com.fashionshop.backend.domain.Product;
import com.fashionshop.backend.domain.ProductColor;
import com.fashionshop.backend.domain.repository.ProductColorRepository;
import com.fashionshop.backend.domain.repository.ProductRepository;
import com.fashionshop.backend.exception.BusinessException;
import com.fashionshop.backend.exception.ErrorCode;
import com.fashionshop.backend.module.product.dto.request.ProductColorRequest;
import com.fashionshop.backend.module.product.dto.response.ProductColorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductColorServiceImplTest {

    @Mock
    private ProductColorRepository colorRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductColorServiceImpl colorService;

    private Product mockProduct;
    private ProductColor mockColor;

    @BeforeEach
    void setUp() {
        mockProduct = Product.builder()
            .id(1L)
            .name("Áo Polo")
            .build();

        mockColor = ProductColor.builder()
            .id(10L)
            .product(mockProduct)
            .colorName("Trắng")
            .colorCode("#FFFFFF")
            .colorFamily("neutral")
            .displayOrder(1)
            .build();
    }

    @Test
    void create_savesColor_withValidRequest() {
        ProductColorRequest request = new ProductColorRequest();
        request.setColorName("Đen");
        request.setColorCode("#000000");
        request.setDisplayOrder(2);

        when(productRepository.findById(1L)).thenReturn(Optional.of(mockProduct));
        when(colorRepository.existsByProductIdAndColorName(1L, "Đen")).thenReturn(false);
        when(colorRepository.save(any(ProductColor.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductColorResponse response = colorService.create(1L, request);

        assertThat(response.getColorName()).isEqualTo("Đen");
        assertThat(response.getColorCode()).isEqualTo("#000000");
        assertThat(response.getColorFamily()).isEqualTo("neutral");
        assertThat(response.getDisplayOrder()).isEqualTo(2);

        ArgumentCaptor<ProductColor> captor = ArgumentCaptor.forClass(ProductColor.class);
        verify(colorRepository).save(captor.capture());
        ProductColor saved = captor.getValue();
        assertThat(saved.getProduct()).isEqualTo(mockProduct);
    }

    @Test
    void create_throwsConflict_whenDuplicateName() {
        ProductColorRequest request = new ProductColorRequest();
        request.setColorName("Trắng");

        when(productRepository.findById(1L)).thenReturn(Optional.of(mockProduct));
        when(colorRepository.existsByProductIdAndColorName(1L, "Trắng")).thenReturn(true);

        assertThatThrownBy(() -> colorService.create(1L, request))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("status", HttpStatus.CONFLICT);

        verify(colorRepository, never()).save(any());
    }

    @Test
    void update_updatesFields_correctly() {
        ProductColorRequest request = new ProductColorRequest();
        request.setColorName("Trắng Mới");
        request.setColorCode("#FAFAFA");
        request.setDisplayOrder(5);

        when(colorRepository.findById(10L)).thenReturn(Optional.of(mockColor));
        when(colorRepository.existsByProductIdAndColorNameAndIdNot(1L, "Trắng Mới", 10L)).thenReturn(false);
        when(colorRepository.save(any(ProductColor.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductColorResponse response = colorService.update(1L, 10L, request);

        assertThat(response.getColorName()).isEqualTo("Trắng Mới");
        assertThat(response.getColorCode()).isEqualTo("#FAFAFA");
        assertThat(response.getColorFamily()).isEqualTo("neutral");
        assertThat(response.getDisplayOrder()).isEqualTo(5);
    }
}
