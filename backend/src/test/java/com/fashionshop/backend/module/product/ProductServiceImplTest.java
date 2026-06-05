package com.fashionshop.backend.module.product;

import com.fashionshop.backend.common.enums.Gender;
import com.fashionshop.backend.common.enums.ProductStatus;
import com.fashionshop.backend.common.enums.Role;
import com.fashionshop.backend.common.enums.UserStatus;
import com.fashionshop.backend.domain.Category;
import com.fashionshop.backend.domain.Product;
import com.fashionshop.backend.domain.User;
import com.fashionshop.backend.domain.repository.CategoryRepository;
import com.fashionshop.backend.domain.repository.ProductRepository;
import com.fashionshop.backend.exception.BusinessException;
import com.fashionshop.backend.exception.ErrorCode;
import com.fashionshop.backend.module.product.dto.request.ProductRequest;
import com.fashionshop.backend.module.product.dto.request.ProductStatusRequest;
import com.fashionshop.backend.module.product.dto.response.ProductDetailResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock private ProductRepository productRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private ProductPriceService productPriceService;
    @InjectMocks private ProductServiceImpl productService;

    private User mockUser;
    private Category mockCategory;
    private Product existingProduct;

    @BeforeEach
    void setUp() {
        mockUser = User.builder()
            .id(1L).email("admin@test.com")
            .role(Role.ADMIN).status(UserStatus.ACTIVE)
            .build();

        mockCategory = Category.builder()
            .id(1).name("Áo").slug("ao")
            .children(new ArrayList<>())
            .build();

        existingProduct = Product.builder()
            .id(1L).name("Áo Polo trắng")
            .basePrice(new BigDecimal("250000"))
            .isSale(false)
            .gender(Gender.MALE)
            .status(ProductStatus.ACTIVE)
            .category(mockCategory)
            .createdBy(mockUser)
            .variants(new ArrayList<>())
            .images(new ArrayList<>())
            .build();

        lenient().when(productPriceService.getEffectivePrice(any(Product.class)))
            .thenAnswer(inv -> inv.<Product>getArgument(0).getBasePrice());
        lenient().when(productPriceService.isOnSale(any(Product.class))).thenReturn(false);
        lenient().when(productPriceService.getDiscountPercent(any(Product.class))).thenReturn(0);
        lenient().when(productPriceService.getTotalStock(any(Product.class))).thenReturn(0L);
        lenient().when(productPriceService.getStockStatus(any(Product.class))).thenReturn("OUT_OF_STOCK");
    }

    // ================================================================
    // create
    // ================================================================

    @Test
    void create_savesProduct_withValidRequest() {
        ProductRequest request = buildValidRequest();
        when(categoryRepository.findById(1)).thenReturn(Optional.of(mockCategory));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            p.setId(10L);
            return p;
        });

        ProductDetailResponse result = productService.create(request, mockUser);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());

        Product saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("Áo Polo Test");
        assertThat(saved.getBasePrice()).isEqualByComparingTo("300000");
        assertThat(saved.getGender()).isEqualTo(Gender.MALE);
        assertThat(saved.getCategory()).isEqualTo(mockCategory);
        assertThat(saved.getCreatedBy()).isEqualTo(mockUser);
    }

    @Test
    void create_throwsNotFound_whenCategoryNotExists() {
        ProductRequest request = buildValidRequest();
        request.setCategoryId(99);
        when(categoryRepository.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.create(request, mockUser))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CATEGORY_NOT_FOUND);
    }

    @Test
    void create_throwsBadRequest_whenSalePriceInvalid() {
        ProductRequest request = buildValidRequest();
        request.setIsSale(true);
        request.setSalePrice(new BigDecimal("500000")); // >= basePrice

        assertThatThrownBy(() -> productService.create(request, mockUser))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_SALE_PRICE);
    }

    @Test
    void create_allowsNullSalePrice_whenNotOnSale() {
        ProductRequest request = buildValidRequest();
        request.setIsSale(false);
        request.setSalePrice(null);
        when(categoryRepository.findById(1)).thenReturn(Optional.of(mockCategory));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        // Không throw
        ProductDetailResponse result = productService.create(request, mockUser);
        assertThat(result).isNotNull();
    }

    // ================================================================
    // update
    // ================================================================

    @Test
    void update_updatesFields_correctly() {
        ProductRequest request = buildValidRequest();
        request.setName("Áo Polo Updated");
        when(productRepository.findById(1L)).thenReturn(Optional.of(existingProduct));
        when(categoryRepository.findById(1)).thenReturn(Optional.of(mockCategory));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        productService.update(1L, request);

        assertThat(existingProduct.getName()).isEqualTo("Áo Polo Updated");
    }

    @Test
    void update_throwsNotFound_whenProductNotExists() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.update(99L, buildValidRequest()))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRODUCT_NOT_FOUND);
    }

    @Test
    void update_throwsBadRequest_whenSalePriceInvalid() {
        ProductRequest request = buildValidRequest();
        request.setIsSale(true);
        request.setSalePrice(null);
        when(productRepository.findById(1L)).thenReturn(Optional.of(existingProduct));

        assertThatThrownBy(() -> productService.update(1L, request))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_SALE_PRICE);
    }

    // ================================================================
    // updateStatus
    // ================================================================

    @Test
    void updateStatus_changesStatus_toInactive() {
        ProductStatusRequest statusReq = new ProductStatusRequest();
        statusReq.setStatus(ProductStatus.INACTIVE);
        when(productRepository.findById(1L)).thenReturn(Optional.of(existingProduct));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        productService.updateStatus(1L, statusReq);

        assertThat(existingProduct.getStatus()).isEqualTo(ProductStatus.INACTIVE);
    }

    @Test
    void updateStatus_throwsNotFound_whenProductNotExists() {
        ProductStatusRequest statusReq = new ProductStatusRequest();
        statusReq.setStatus(ProductStatus.INACTIVE);
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.updateStatus(99L, statusReq))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRODUCT_NOT_FOUND);
    }

    // ================================================================
    // getByIdPublic
    // ================================================================

    @Test
    void getByIdPublic_returnsProduct_whenActive() {
        existingProduct.setStatus(ProductStatus.ACTIVE);
        when(productRepository.findById(1L)).thenReturn(Optional.of(existingProduct));

        ProductDetailResponse result = productService.getByIdPublic(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void getByIdPublic_throwsNotFound_whenInactive() {
        existingProduct.setStatus(ProductStatus.INACTIVE);
        when(productRepository.findById(1L)).thenReturn(Optional.of(existingProduct));

        assertThatThrownBy(() -> productService.getByIdPublic(1L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRODUCT_NOT_FOUND);
    }

    @Test
    void getByIdPublic_throwsNotFound_whenNotExists() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getByIdPublic(99L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRODUCT_NOT_FOUND);
    }

    // ================================================================
    // Fixtures
    // ================================================================

    private ProductRequest buildValidRequest() {
        ProductRequest r = new ProductRequest();
        r.setName("Áo Polo Test");
        r.setDescription("Mo ta san pham test du dai");
        r.setBasePrice(new BigDecimal("300000"));
        r.setIsSale(false);
        r.setCategoryId(1);
        r.setGender(Gender.MALE);
        r.setMaterial("Cotton");
        return r;
    }
}
