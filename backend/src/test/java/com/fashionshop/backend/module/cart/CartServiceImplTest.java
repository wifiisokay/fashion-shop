package com.fashionshop.backend.module.cart;

import com.fashionshop.backend.common.enums.Gender;
import com.fashionshop.backend.common.enums.ProductStatus;
import com.fashionshop.backend.domain.CartItem;
import com.fashionshop.backend.domain.Product;
import com.fashionshop.backend.domain.ProductColor;
import com.fashionshop.backend.domain.ProductImage;
import com.fashionshop.backend.domain.ProductVariant;
import com.fashionshop.backend.domain.User;
import com.fashionshop.backend.domain.repository.CartItemRepository;
import com.fashionshop.backend.domain.repository.ProductImageRepository;
import com.fashionshop.backend.domain.repository.ProductVariantRepository;
import com.fashionshop.backend.domain.repository.UserRepository;
import com.fashionshop.backend.exception.BusinessException;
import com.fashionshop.backend.exception.ErrorCode;
import com.fashionshop.backend.module.cart.dto.request.AddToCartRequest;
import com.fashionshop.backend.module.cart.dto.request.UpdateCartRequest;
import com.fashionshop.backend.module.cart.dto.response.CartItemResponse;
import com.fashionshop.backend.module.cart.dto.response.CartSummaryResponse;
import com.fashionshop.backend.module.product.ProductPriceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
@DisplayName("CartServiceImpl Unit Tests")
class CartServiceImplTest {

    @Mock private CartItemRepository cartItemRepository;
    @Mock private ProductVariantRepository variantRepository;
    @Mock private ProductImageRepository imageRepository;
    @Mock private UserRepository userRepository;
    @Mock private ProductPriceService productPriceService;
    @InjectMocks private CartServiceImpl cartService;

    // ── Fixtures ──────────────────────────────────────────────────────
    private Product           product;
    private ProductColor      color;
    private ProductVariant    variant;      // stock=10, no sale, no adjustment
    private ProductVariant    saleVariant;  // stock=5,  isSale=true, salePrice set
    private ProductVariant    adjVariant;   // stock=3,  priceAdjustment=50_000
    private ProductVariant    emptyVariant; // stock=0
    private User              user;
    private CartItem          existingItem; // existing cart item, qty=2, linked to variant

    private static final Long USER_ID    = 1L;
    private static final Long VARIANT_ID = 10L;

    @BeforeEach
    void setUp() {
        product = Product.builder()
            .id(100L)
            .name("Áo Thun Basic")
            .basePrice(new BigDecimal("300000"))
            .salePrice(null)
            .isSale(false)
            .gender(Gender.UNISEX)
            .status(ProductStatus.ACTIVE)
            .variants(new ArrayList<>())
            .images(new ArrayList<>())
            .build();

        color = ProductColor.builder()
            .id(20L)
            .product(product)
            .colorName("Đen")
            .colorCode("#000000")
            .build();

        // ── Variant bình thường (stock=10) ──
        variant = ProductVariant.builder()
            .id(VARIANT_ID)
            .product(product)
            .color(color)
            .size("M")
            .stockQuantity(10)
            .priceAdjustment(null)
            .build();

        // ── Variant có sale (stock=5) ──
        Product saleProduct = Product.builder()
            .id(101L).name("Áo Khoác Sale")
            .basePrice(new BigDecimal("500000"))
            .salePrice(new BigDecimal("350000"))
            .isSale(true)
            .gender(Gender.MALE).status(ProductStatus.ACTIVE)
            .variants(new ArrayList<>()).images(new ArrayList<>())
            .build();
        saleVariant = ProductVariant.builder()
            .id(11L).product(saleProduct).color(color)
            .size("L").stockQuantity(5).priceAdjustment(null)
            .build();

        // ── Variant có priceAdjustment (stock=3) ──
        adjVariant = ProductVariant.builder()
            .id(12L).product(product).color(color)
            .size("XL").stockQuantity(3)
            .priceAdjustment(new BigDecimal("50000"))
            .build();

        // ── Variant hết hàng (stock=0) ──
        emptyVariant = ProductVariant.builder()
            .id(13L).product(product).color(color)
            .size("XXL").stockQuantity(0).priceAdjustment(null)
            .build();

        user = User.builder().id(USER_ID).build();

        existingItem = CartItem.builder()
            .id(1L).user(user).variant(variant).quantity(2)
            .build();

        lenient().when(productPriceService.getFinalUnitPrice(any(Product.class), any(ProductVariant.class)))
            .thenAnswer(inv -> {
                Product p = inv.getArgument(0);
                ProductVariant v = inv.getArgument(1);
                BigDecimal price = Boolean.TRUE.equals(p.getIsSale()) && p.getSalePrice() != null
                    ? p.getSalePrice()
                    : p.getBasePrice();
                return v.getPriceAdjustment() != null ? price.add(v.getPriceAdjustment()) : price;
            });
    }

    // =================================================================
    // getCart
    // =================================================================

    @Nested
    @DisplayName("getCart")
    class GetCartTests {

        @Test
        @DisplayName("giỏ rỗng → trả CartSummaryResponse.empty()")
        void getCart_emptyCart_returnsEmptySummary() {
            when(cartItemRepository.findByUserIdWithDetails(USER_ID)).thenReturn(List.of());

            CartSummaryResponse result = cartService.getCart(USER_ID);

            assertThat(result.getItems()).isEmpty();
            assertThat(result.getTotalItems()).isZero();
            assertThat(result.getTotalPrice()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.isHasUnavailableItems()).isFalse();
        }

        @Test
        @DisplayName("giỏ có item → totalItems, totalPrice tính đúng")
        void getCart_withItems_calculatesTotalsCorrectly() {
            // qty=2, unitPrice=300_000 → subtotal=600_000
            CartItem item = CartItem.builder()
                .id(1L).user(user).variant(variant).quantity(2).build();
            when(cartItemRepository.findByUserIdWithDetails(USER_ID)).thenReturn(List.of(item));
            when(imageRepository.findPrimaryByProductId(100L)).thenReturn(Optional.empty());

            CartSummaryResponse result = cartService.getCart(USER_ID);

            assertThat(result.getTotalItems()).isEqualTo(2);
            assertThat(result.getTotalPrice()).isEqualByComparingTo("600000");
            assertThat(result.getItems()).hasSize(1);
        }

        @Test
        @DisplayName("giỏ có nhiều item → totalItems = tổng quantity")
        void getCart_multipleItems_sumsTotalItemsCorrectly() {
            CartItem item1 = CartItem.builder().id(1L).user(user).variant(variant).quantity(3).build();
            CartItem item2 = CartItem.builder().id(2L).user(user).variant(adjVariant).quantity(1).build();
            when(cartItemRepository.findByUserIdWithDetails(USER_ID)).thenReturn(List.of(item1, item2));
            when(imageRepository.findPrimaryByProductId(anyLong())).thenReturn(Optional.empty());

            CartSummaryResponse result = cartService.getCart(USER_ID);

            assertThat(result.getTotalItems()).isEqualTo(4); // 3 + 1
        }

        @Test
        @DisplayName("item hết hàng → available=false, hasUnavailableItems=true")
        void getCart_outOfStockItem_marksUnavailable() {
            CartItem item = CartItem.builder()
                .id(1L).user(user).variant(emptyVariant).quantity(1).build();
            when(cartItemRepository.findByUserIdWithDetails(USER_ID)).thenReturn(List.of(item));
            when(imageRepository.findPrimaryByProductId(anyLong())).thenReturn(Optional.empty());

            CartSummaryResponse result = cartService.getCart(USER_ID);

            assertThat(result.isHasUnavailableItems()).isTrue();
            assertThat(result.getItems().get(0).isAvailable()).isFalse();
        }

        @Test
        @DisplayName("có primaryImageUrl → được gán vào CartItemResponse")
        void getCart_hasPrimaryImage_setsImageUrl() {
            CartItem item = CartItem.builder().id(1L).user(user).variant(variant).quantity(1).build();
            ProductImage image = ProductImage.builder()
                .id(5L).imageUrl("https://cdn.test.com/img.jpg")
                .isPrimary(true).sortOrder(0).product(product).build();
            when(cartItemRepository.findByUserIdWithDetails(USER_ID)).thenReturn(List.of(item));
            when(imageRepository.findPrimaryByProductId(100L)).thenReturn(Optional.of(image));

            CartSummaryResponse result = cartService.getCart(USER_ID);

            assertThat(result.getItems().get(0).getPrimaryImageUrl())
                .isEqualTo("https://cdn.test.com/img.jpg");
        }
    }

    // =================================================================
    // addItem
    // =================================================================

    @Nested
    @DisplayName("addItem")
    class AddItemTests {

        @Test
        @DisplayName("thêm mới variant chưa có trong giỏ → tạo CartItem")
        void addItem_newVariant_createsCartItem() {
            AddToCartRequest req = buildAddRequest(VARIANT_ID, 2);
            when(variantRepository.findById(VARIANT_ID)).thenReturn(Optional.of(variant));
            when(cartItemRepository.findByUserIdAndVariantId(USER_ID, VARIANT_ID))
                .thenReturn(Optional.empty());
            when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
            when(cartItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            stubGetCartEmpty();

            cartService.addItem(USER_ID, req);

            ArgumentCaptor<CartItem> captor = ArgumentCaptor.forClass(CartItem.class);
            verify(cartItemRepository).save(captor.capture());
            assertThat(captor.getValue().getQuantity()).isEqualTo(2);
            assertThat(captor.getValue().getVariant()).isEqualTo(variant);
        }

        @Test
        @DisplayName("thêm variant đã có trong giỏ → cộng dồn quantity")
        void addItem_existingVariant_mergesQuantity() {
            AddToCartRequest req = buildAddRequest(VARIANT_ID, 3);
            when(variantRepository.findById(VARIANT_ID)).thenReturn(Optional.of(variant));
            when(cartItemRepository.findByUserIdAndVariantId(USER_ID, VARIANT_ID))
                .thenReturn(Optional.of(existingItem)); // qty=2 hiện tại
            when(cartItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            stubGetCartEmpty();

            cartService.addItem(USER_ID, req);

            // qty mới = 2 + 3 = 5 (< stock=10 → ok)
            verify(cartItemRepository).save(existingItem);
            assertThat(existingItem.getQuantity()).isEqualTo(5);
        }

        @Test
        @DisplayName("thêm mới nhưng quantity > stock → throw INSUFFICIENT_STOCK")
        void addItem_newVariant_exceedsStock_throwsInsufficientStock() {
            AddToCartRequest req = buildAddRequest(VARIANT_ID, 11); // stock=10
            when(variantRepository.findById(VARIANT_ID)).thenReturn(Optional.of(variant));
            when(cartItemRepository.findByUserIdAndVariantId(USER_ID, VARIANT_ID))
                .thenReturn(Optional.empty());

            assertThatThrownBy(() -> cartService.addItem(USER_ID, req))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INSUFFICIENT_STOCK);

            verify(cartItemRepository, never()).save(any());
        }

        @Test
        @DisplayName("merge nhưng tổng quantity > stock → throw INSUFFICIENT_STOCK")
        void addItem_mergeExceedsStock_throwsInsufficientStock() {
            // existingItem.qty=2, thêm 9 → tổng=11 > stock=10
            AddToCartRequest req = buildAddRequest(VARIANT_ID, 9);
            when(variantRepository.findById(VARIANT_ID)).thenReturn(Optional.of(variant));
            when(cartItemRepository.findByUserIdAndVariantId(USER_ID, VARIANT_ID))
                .thenReturn(Optional.of(existingItem));

            assertThatThrownBy(() -> cartService.addItem(USER_ID, req))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INSUFFICIENT_STOCK);

            verify(cartItemRepository, never()).save(any());
        }

        @Test
        @DisplayName("variantId không tồn tại → throw VARIANT_NOT_FOUND")
        void addItem_variantNotFound_throwsNotFound() {
            when(variantRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> cartService.addItem(USER_ID, buildAddRequest(99L, 1)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VARIANT_NOT_FOUND);
        }

        @Test
        @DisplayName("message INSUFFICIENT_STOCK kèm số lượng còn lại")
        void addItem_insufficientStock_messageContainsStockCount() {
            AddToCartRequest req = buildAddRequest(VARIANT_ID, 99); // stock=10
            when(variantRepository.findById(VARIANT_ID)).thenReturn(Optional.of(variant));
            when(cartItemRepository.findByUserIdAndVariantId(USER_ID, VARIANT_ID))
                .thenReturn(Optional.empty());

            assertThatThrownBy(() -> cartService.addItem(USER_ID, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("10");
        }

        @Test
        @DisplayName("thêm đúng boundary: quantity = stock → thành công")
        void addItem_quantityEqualsStock_succeeds() {
            AddToCartRequest req = buildAddRequest(VARIANT_ID, 10); // bằng stock
            when(variantRepository.findById(VARIANT_ID)).thenReturn(Optional.of(variant));
            when(cartItemRepository.findByUserIdAndVariantId(USER_ID, VARIANT_ID))
                .thenReturn(Optional.empty());
            when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
            when(cartItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            stubGetCartEmpty();

            cartService.addItem(USER_ID, req);

            verify(cartItemRepository).save(any(CartItem.class));
        }
    }

    // =================================================================
    // updateItem
    // =================================================================

    @Nested
    @DisplayName("updateItem")
    class UpdateItemTests {

        @Test
        @DisplayName("cập nhật quantity hợp lệ → save và trả summary mới")
        void updateItem_validQty_updatesSuccessfully() {
            UpdateCartRequest req = buildUpdateRequest(5);
            when(cartItemRepository.findByUserIdAndVariantId(USER_ID, VARIANT_ID))
                .thenReturn(Optional.of(existingItem)); // stock=10
            when(cartItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            stubGetCartEmpty();

            cartService.updateItem(USER_ID, VARIANT_ID, req);

            assertThat(existingItem.getQuantity()).isEqualTo(5);
            verify(cartItemRepository).save(existingItem);
        }

        @Test
        @DisplayName("quantity mới > stock → throw INSUFFICIENT_STOCK")
        void updateItem_exceedsStock_throwsInsufficientStock() {
            UpdateCartRequest req = buildUpdateRequest(11); // stock=10
            when(cartItemRepository.findByUserIdAndVariantId(USER_ID, VARIANT_ID))
                .thenReturn(Optional.of(existingItem));

            assertThatThrownBy(() -> cartService.updateItem(USER_ID, VARIANT_ID, req))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INSUFFICIENT_STOCK);

            verify(cartItemRepository, never()).save(any());
        }

        @Test
        @DisplayName("item không trong giỏ → throw CART_ITEM_NOT_FOUND")
        void updateItem_itemNotFound_throwsNotFound() {
            when(cartItemRepository.findByUserIdAndVariantId(USER_ID, VARIANT_ID))
                .thenReturn(Optional.empty());

            assertThatThrownBy(() -> cartService.updateItem(USER_ID, VARIANT_ID, buildUpdateRequest(1)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CART_ITEM_NOT_FOUND);
        }

        @Test
        @DisplayName("cập nhật đúng boundary: quantity = stock → thành công")
        void updateItem_quantityEqualsStock_succeeds() {
            UpdateCartRequest req = buildUpdateRequest(10); // bằng stock
            when(cartItemRepository.findByUserIdAndVariantId(USER_ID, VARIANT_ID))
                .thenReturn(Optional.of(existingItem));
            when(cartItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            stubGetCartEmpty();

            cartService.updateItem(USER_ID, VARIANT_ID, req);

            assertThat(existingItem.getQuantity()).isEqualTo(10);
        }
    }

    // =================================================================
    // removeItem
    // =================================================================

    @Nested
    @DisplayName("removeItem")
    class RemoveItemTests {

        @Test
        @DisplayName("xóa item có trong giỏ → cartItemRepository.delete được gọi")
        void removeItem_existingItem_deletesSuccessfully() {
            when(cartItemRepository.findByUserIdAndVariantId(USER_ID, VARIANT_ID))
                .thenReturn(Optional.of(existingItem));
            stubGetCartEmpty();

            cartService.removeItem(USER_ID, VARIANT_ID);

            verify(cartItemRepository).delete(existingItem);
        }

        @Test
        @DisplayName("item không trong giỏ → throw CART_ITEM_NOT_FOUND")
        void removeItem_itemNotFound_throwsNotFound() {
            when(cartItemRepository.findByUserIdAndVariantId(USER_ID, VARIANT_ID))
                .thenReturn(Optional.empty());

            assertThatThrownBy(() -> cartService.removeItem(USER_ID, VARIANT_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CART_ITEM_NOT_FOUND);

            verify(cartItemRepository, never()).delete(any());
        }
    }

    // =================================================================
    // clearCart
    // =================================================================

    @Nested
    @DisplayName("clearCart")
    class ClearCartTests {

        @Test
        @DisplayName("gọi deleteAllByUserId với đúng userId")
        void clearCart_callsDeleteAll() {
            cartService.clearCart(USER_ID);

            verify(cartItemRepository).deleteAllByUserId(USER_ID);
        }

        @Test
        @DisplayName("không gọi bất kỳ repo nào khác khi clear")
        void clearCart_noOtherRepoInteraction() {
            cartService.clearCart(USER_ID);

            verify(cartItemRepository).deleteAllByUserId(USER_ID);
            verifyNoInteractions(variantRepository, imageRepository, userRepository);
        }
    }

    // =================================================================
    // getCartItems (trả entity cho OrderService)
    // =================================================================

    @Nested
    @DisplayName("getCartItems")
    class GetCartItemsTests {

        @Test
        @DisplayName("trả về List<CartItem> entity — không phải DTO")
        void getCartItems_returnsEntityList() {
            when(cartItemRepository.findByUserIdWithDetails(USER_ID))
                .thenReturn(List.of(existingItem));

            List<CartItem> result = cartService.getCartItems(USER_ID);

            assertThat(result).hasSize(1);
            assertThat(result.get(0)).isSameAs(existingItem);
        }

        @Test
        @DisplayName("giỏ rỗng → trả List rỗng")
        void getCartItems_emptyCart_returnsEmptyList() {
            when(cartItemRepository.findByUserIdWithDetails(USER_ID)).thenReturn(List.of());

            List<CartItem> result = cartService.getCartItems(USER_ID);

            assertThat(result).isEmpty();
        }
    }

    // =================================================================
    // resolveUnitPrice (kiểm tra qua getCart response)
    // =================================================================

    @Nested
    @DisplayName("resolveUnitPrice")
    class UnitPriceTests {

        @Test
        @DisplayName("isSale=false, priceAdjustment=null → unitPrice = basePrice")
        void unitPrice_default_usesBasePrice() {
            CartItem item = CartItem.builder().id(1L).user(user).variant(variant).quantity(1).build();
            when(cartItemRepository.findByUserIdWithDetails(USER_ID)).thenReturn(List.of(item));
            when(imageRepository.findPrimaryByProductId(anyLong())).thenReturn(Optional.empty());

            CartSummaryResponse result = cartService.getCart(USER_ID);

            assertThat(result.getItems().get(0).getUnitPrice())
                .isEqualByComparingTo("300000");
        }

        @Test
        @DisplayName("isSale=true + salePrice set → unitPrice = salePrice")
        void unitPrice_saleActive_usesSalePrice() {
            CartItem item = CartItem.builder().id(1L).user(user).variant(saleVariant).quantity(1).build();
            when(cartItemRepository.findByUserIdWithDetails(USER_ID)).thenReturn(List.of(item));
            when(imageRepository.findPrimaryByProductId(anyLong())).thenReturn(Optional.empty());

            CartSummaryResponse result = cartService.getCart(USER_ID);

            assertThat(result.getItems().get(0).getUnitPrice())
                .isEqualByComparingTo("350000"); // salePrice
        }

        @Test
        @DisplayName("isSale=false, priceAdjustment=50_000 → unitPrice = basePrice + adjustment")
        void unitPrice_withAdjustment_addsToBasePrice() {
            CartItem item = CartItem.builder().id(1L).user(user).variant(adjVariant).quantity(1).build();
            when(cartItemRepository.findByUserIdWithDetails(USER_ID)).thenReturn(List.of(item));
            when(imageRepository.findPrimaryByProductId(anyLong())).thenReturn(Optional.empty());

            CartSummaryResponse result = cartService.getCart(USER_ID);

            assertThat(result.getItems().get(0).getUnitPrice())
                .isEqualByComparingTo("350000"); // 300_000 + 50_000
        }

        @Test
        @DisplayName("subtotal = unitPrice × quantity")
        void unitPrice_subtotalIsUnitPriceTimesQuantity() {
            CartItem item = CartItem.builder().id(1L).user(user).variant(variant).quantity(3).build();
            when(cartItemRepository.findByUserIdWithDetails(USER_ID)).thenReturn(List.of(item));
            when(imageRepository.findPrimaryByProductId(anyLong())).thenReturn(Optional.empty());

            CartSummaryResponse result = cartService.getCart(USER_ID);

            CartItemResponse itemResp = result.getItems().get(0);
            assertThat(itemResp.getSubtotal()).isEqualByComparingTo("900000"); // 300_000 × 3
        }
    }

    // =================================================================
    // Fixtures helpers
    // =================================================================

    private AddToCartRequest buildAddRequest(Long variantId, int qty) {
        AddToCartRequest r = new AddToCartRequest();
        r.setVariantId(variantId);
        r.setQuantity(qty);
        return r;
    }

    private UpdateCartRequest buildUpdateRequest(int qty) {
        UpdateCartRequest r = new UpdateCartRequest();
        r.setQuantity(qty);
        return r;
    }

    /**
     * Stub getCart() ở cuối mỗi write-method call — trả giỏ rỗng.
     * Giúp test chỉ tập trung vào phần mutate, không cần mock phức tạp cho phần build summary.
     */
    private void stubGetCartEmpty() {
        when(cartItemRepository.findByUserIdWithDetails(USER_ID)).thenReturn(List.of());
    }
}
