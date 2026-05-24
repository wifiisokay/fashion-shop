package com.fashionshop.backend.module.order;

import com.fashionshop.backend.common.PageResponse;
import com.fashionshop.backend.common.enums.OrderPaymentStatus;
import com.fashionshop.backend.common.enums.OrderStatus;
import com.fashionshop.backend.common.enums.PaymentMethod;
import com.fashionshop.backend.common.enums.PaymentStatus;
import com.fashionshop.backend.domain.*;
import com.fashionshop.backend.domain.repository.*;
import com.fashionshop.backend.exception.BusinessException;
import com.fashionshop.backend.module.cart.CartService;
import com.fashionshop.backend.module.order.dto.request.CancelOrderRequest;
import com.fashionshop.backend.module.order.dto.request.ConfirmPackingRequest;
import com.fashionshop.backend.module.order.dto.request.CreateOrderRequest;
import com.fashionshop.backend.module.order.dto.request.UpdateOrderStatusRequest;
import com.fashionshop.backend.module.order.dto.response.CreateOrderResponse;
import com.fashionshop.backend.module.order.dto.response.OrderDetailResponse;
import com.fashionshop.backend.module.order.dto.response.OrderSummaryResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock OrderRepository orderRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock ProductVariantRepository variantRepository;
    @Mock ProductImageRepository imageRepository;
    @Mock AddressRepository addressRepository;
    @Mock UserRepository userRepository;
    @Mock CartService cartService;
    @Mock OrderStatusService statusService;

    @InjectMocks OrderServiceImpl sut;

    // ==================== Helpers ====================

    private User mockUser() {
        return User.builder().id(1L).build();
    }

    private Address mockAddress() {
        return Address.builder()
            .id(10L).user(mockUser())
            .fullName("Nguyễn Văn A").phone("0901234567")
            .province("TP HCM").district("Quận 1").ward("Phường Bến Nghé")
            .provinceCode(202).districtCode(1442).wardCode("20308")
            .street("123 Nguyễn Huệ")
            .build();
    }

    private Product mockProduct() {
        return Product.builder()
            .id(100L).name("Áo thun Basic").basePrice(BigDecimal.valueOf(200000))
            .isSale(false)
            .build();
    }

    private ProductColor mockColor() {
        return ProductColor.builder().id(5L).colorName("Đen").build();
    }

    private ProductVariant mockVariant(int stock) {
        return ProductVariant.builder()
            .id(50L).product(mockProduct()).color(mockColor())
            .size("M").stockQuantity(stock)
            .build();
    }

    private CartItem mockCartItem(ProductVariant variant, int qty) {
        return CartItem.builder()
            .id(1L).user(mockUser()).variant(variant).quantity(qty)
            .build();
    }

    private CreateOrderRequest codRequest() {
        CreateOrderRequest req = new CreateOrderRequest();
        req.setAddressId(10L);
        req.setPaymentMethod(PaymentMethod.COD);
        req.setShippingFee(30000L);
        req.setNote("Giao giờ hành chính");
        return req;
    }

    private Order mockOrder(OrderStatus status) {
        Order order = Order.builder()
            .id(1L).user(mockUser()).status(status)
            .paymentMethod(PaymentMethod.COD)
            .subtotal(BigDecimal.valueOf(200000))
            .shippingFee(BigDecimal.valueOf(30000))
            .totalAmount(BigDecimal.valueOf(230000))
            .addressSnapshot(Map.of("fullName", "Test"))
            .items(new ArrayList<>())
            .build();
        return order;
    }

    // ==================== createOrder ====================

    @Nested
    @DisplayName("createOrder")
    class CreateOrder {

        @Test
        @DisplayName("COD — tạo thành công, status = PENDING, stock trừ")
        void cod_success() {
            ProductVariant variant = mockVariant(10);
            List<CartItem> cartItems = List.of(mockCartItem(variant, 2));

            when(cartService.getCartItems(1L)).thenReturn(cartItems);
            when(addressRepository.findByIdAndUser_Id(10L, 1L)).thenReturn(Optional.of(mockAddress()));
            when(variantRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(variant));
            when(imageRepository.findPrimaryByProductId(100L)).thenReturn(Optional.empty());
            when(userRepository.getReferenceById(1L)).thenReturn(mockUser());
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
                Order o = inv.getArgument(0);
                o.setId(1L);
                return o;
            });
            when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

            CreateOrderResponse result = sut.createOrder(1L, codRequest(), null);

            assertEquals(OrderStatus.PENDING, result.getStatus());
            assertNotNull(result.getOrderId());
            assertNull(result.getPaymentUrl());
            assertEquals(8, variant.getStockQuantity()); // 10 - 2
            verify(cartService).clearCart(1L);
            verify(paymentRepository).save(argThat(payment ->
                payment.getOrder().getPaymentStatus() == OrderPaymentStatus.UNPAID));
        }

        @Test
        @DisplayName("VNPay — status = AWAITING_PAYMENT")
        void vnpay_success() {
            ProductVariant variant = mockVariant(5);
            CreateOrderRequest req = codRequest();
            req.setPaymentMethod(PaymentMethod.VNPAY);

            when(cartService.getCartItems(1L)).thenReturn(List.of(mockCartItem(variant, 1)));
            when(addressRepository.findByIdAndUser_Id(10L, 1L)).thenReturn(Optional.of(mockAddress()));
            when(variantRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(variant));
            when(imageRepository.findPrimaryByProductId(100L)).thenReturn(Optional.empty());
            when(userRepository.getReferenceById(1L)).thenReturn(mockUser());
            when(orderRepository.save(any())).thenAnswer(inv -> {
                Order o = inv.getArgument(0);
                o.setId(2L);
                return o;
            });
            when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CreateOrderResponse result = sut.createOrder(1L, req, null);

            assertEquals(OrderStatus.AWAITING_PAYMENT, result.getStatus());
            verify(paymentRepository).save(argThat(payment ->
                payment.getOrder().getPaymentStatus() == OrderPaymentStatus.UNPAID));
        }

        @Test
        @DisplayName("Giỏ trống — throw CART_EMPTY")
        void emptyCart_throws() {
            when(cartService.getCartItems(1L)).thenReturn(List.of());

            BusinessException ex = assertThrows(BusinessException.class,
                () -> sut.createOrder(1L, codRequest(), null));
            assertEquals("CART_003", ex.getErrorCode().getCode());
        }

        @Test
        @DisplayName("Không đủ stock — throw INSUFFICIENT_STOCK")
        void insufficientStock_throws() {
            ProductVariant variant = mockVariant(1); // chỉ còn 1
            when(cartService.getCartItems(1L)).thenReturn(List.of(mockCartItem(variant, 5))); // mua 5
            when(addressRepository.findByIdAndUser_Id(10L, 1L)).thenReturn(Optional.of(mockAddress()));
            when(variantRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(variant));
            when(userRepository.getReferenceById(1L)).thenReturn(mockUser());

            BusinessException ex = assertThrows(BusinessException.class,
                () -> sut.createOrder(1L, codRequest(), null));
            assertEquals("CART_002", ex.getErrorCode().getCode());
        }

        @Test
        @DisplayName("Address không thuộc user — throw ADDRESS_NOT_BELONG")
        void addressNotBelong_throws() {
            when(cartService.getCartItems(1L)).thenReturn(List.of(mockCartItem(mockVariant(10), 1)));
            when(addressRepository.findByIdAndUser_Id(10L, 1L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                () -> sut.createOrder(1L, codRequest(), null));
            assertEquals("SHIPPING_002", ex.getErrorCode().getCode());
        }
    }

    // ==================== cancelOrder (Customer) ====================

    @Nested
    @DisplayName("cancelOrder — Customer")
    class CancelOrderCustomer {

        @Test
        @DisplayName("Hủy từ PENDING — OK, hoàn stock")
        void fromPending_success() {
            Order order = mockOrder(OrderStatus.PENDING);
            ProductVariant variant = mockVariant(8);
            OrderItem item = OrderItem.builder().variant(variant).quantity(2).build();
            order.setItems(List.of(item));

            when(orderRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(order));
            when(statusService.canCustomerCancel(OrderStatus.PENDING)).thenReturn(true);

            CancelOrderRequest req = new CancelOrderRequest();
            req.setReason("Đổi ý");
            sut.cancelOrder(1L, 1L, req);

            assertEquals(OrderStatus.CANCELLED, order.getStatus());
            assertEquals("Đổi ý", order.getCancelReason());
            assertEquals(10, variant.getStockQuantity()); // 8 + 2
        }

        @Test
        @DisplayName("Hủy từ CONFIRMED — throw ORDER_CANNOT_CANCEL")
        void fromConfirmed_throws() {
            Order order = mockOrder(OrderStatus.CONFIRMED);
            when(orderRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(order));
            when(statusService.canCustomerCancel(OrderStatus.CONFIRMED)).thenReturn(false);

            assertThrows(BusinessException.class,
                () -> sut.cancelOrder(1L, 1L, new CancelOrderRequest()));
        }
    }

    // ==================== staffCancelOrder ====================

    @Nested
    @DisplayName("confirmReceived")
    class ConfirmReceived {

        @Test
        @DisplayName("COD DELIVERED → COMPLETED + payment SUCCESS + order payment PAID")
        void codDelivered_setsPaid() {
            Order order = mockOrder(OrderStatus.DELIVERED);
            Payment payment = Payment.builder()
                .id(10L)
                .order(order)
                .method(PaymentMethod.COD)
                .status(PaymentStatus.PENDING)
                .build();

            when(orderRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(order));
            when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.of(payment));
            when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            sut.confirmReceived(1L, 1L);

            assertEquals(OrderStatus.COMPLETED, order.getStatus());
            assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
            assertNotNull(payment.getPaidAt());
            assertEquals(OrderPaymentStatus.PAID, order.getPaymentStatus());
        }
    }

    @Nested
    @DisplayName("staffCancelOrder")
    class StaffCancel {

        @Test
        @DisplayName("Staff hủy CONFIRMED — OK (có reason)")
        void withReason_success() {
            Order order = mockOrder(OrderStatus.CONFIRMED);
            order.setItems(List.of());
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(statusService.canStaffCancel(OrderStatus.CONFIRMED)).thenReturn(true);

            CancelOrderRequest req = new CancelOrderRequest();
            req.setReason("Hết hàng");
            sut.staffCancelOrder(1L, req);

            assertEquals(OrderStatus.CANCELLED, order.getStatus());
        }

        @Test
        @DisplayName("Staff hủy không có reason — throw ORDER_CANCEL_REASON_REQUIRED")
        void noReason_throws() {
            Order order = mockOrder(OrderStatus.PENDING);
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(statusService.canStaffCancel(OrderStatus.PENDING)).thenReturn(true);

            BusinessException ex = assertThrows(BusinessException.class,
                () -> sut.staffCancelOrder(1L, new CancelOrderRequest()));
            assertEquals("ORDER_005", ex.getErrorCode().getCode());
        }
    }

    // ==================== updateOrderStatus ====================

    @Nested
    @DisplayName("updateOrderStatus — Staff")
    class UpdateStatus {

        @Test
        @DisplayName("PENDING → CONFIRMED: OK")
        void pendingToConfirmed() {
            Order order = mockOrder(OrderStatus.PENDING);
            order.setItems(List.of());
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(statusService.getLabel(any())).thenReturn("Đã xác nhận");
            when(orderRepository.save(any())).thenReturn(order);

            UpdateOrderStatusRequest req = new UpdateOrderStatusRequest();
            req.setStatus(OrderStatus.CONFIRMED);

            OrderDetailResponse result = sut.updateOrderStatus(1L, req);
            assertEquals("CONFIRMED", result.getStatus());
            assertEquals("UNPAID", result.getPaymentStatus());
        }

        @Test
        @DisplayName("VNPay UNPAID không được chuyển sang CONFIRMED")
        void vnpayUnpaidToConfirmed_throws() {
            Order order = mockOrder(OrderStatus.PENDING);
            order.setPaymentMethod(PaymentMethod.VNPAY);
            order.setPaymentStatus(OrderPaymentStatus.UNPAID);
            order.setItems(List.of());
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

            UpdateOrderStatusRequest req = new UpdateOrderStatusRequest();
            req.setStatus(OrderStatus.CONFIRMED);

            BusinessException ex = assertThrows(BusinessException.class,
                () -> sut.updateOrderStatus(1L, req));
            assertEquals("ORDER_015", ex.getErrorCode().getCode());
        }

        @Test
        @DisplayName("SHIPPING → DELIVERED: set deliveredAt")
        void shippingToDelivered_setsDeliveredAt() {
            Order order = mockOrder(OrderStatus.SHIPPING);
            order.setItems(List.of());
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(statusService.getLabel(any())).thenReturn("Đã giao hàng");
            when(orderRepository.save(any())).thenReturn(order);

            UpdateOrderStatusRequest req = new UpdateOrderStatusRequest();
            req.setStatus(OrderStatus.DELIVERED);

            sut.updateOrderStatus(1L, req);
            assertNotNull(order.getDeliveredAt());
        }
    }

    // ==================== confirmPacking ====================

    @Nested
    @DisplayName("confirmPacking")
    class ConfirmPacking {

        @Test
        @DisplayName("CONFIRMED — đóng gói thành công, tính volumetric weight")
        void confirmed_success() {
            Order order = mockOrder(OrderStatus.CONFIRMED);
            order.setItems(List.of());
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(statusService.getLabel(any())).thenReturn("Đã xác nhận");
            when(orderRepository.save(any())).thenReturn(order);

            ConfirmPackingRequest req = new ConfirmPackingRequest();
            req.setLength(30);
            req.setWidth(20);
            req.setHeight(10);
            req.setActualWeight(500);

            OrderDetailResponse result = sut.confirmPacking(1L, req);

            assertTrue(order.getPackingConfirmed());
            assertEquals(30, order.getPackageLength());
            // volumetric = 30*20*10*1000/5000 = 1200g
            assertEquals(1200, order.getVolumetricWeight());
            // chargeable = max(500, 1200) = 1200
            assertEquals(1200, order.getChargeableWeight());
        }

        @Test
        @DisplayName("Actual weight > volumetric — chargeable = actual")
        void actualGreaterThanVolumetric() {
            Order order = mockOrder(OrderStatus.CONFIRMED);
            order.setItems(List.of());
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(statusService.getLabel(any())).thenReturn("Đã xác nhận");
            when(orderRepository.save(any())).thenReturn(order);

            ConfirmPackingRequest req = new ConfirmPackingRequest();
            req.setLength(10);
            req.setWidth(10);
            req.setHeight(5);
            req.setActualWeight(2000);

            sut.confirmPacking(1L, req);

            // volumetric = 10*10*5*1000/5000 = 100g
            assertEquals(100, order.getVolumetricWeight());
            // chargeable = max(2000, 100) = 2000
            assertEquals(2000, order.getChargeableWeight());
        }

        @Test
        @DisplayName("PENDING — throw PACKING_INVALID_STATUS")
        void pendingStatus_throws() {
            Order order = mockOrder(OrderStatus.PENDING);
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

            ConfirmPackingRequest req = new ConfirmPackingRequest();
            req.setLength(30); req.setWidth(20); req.setHeight(10); req.setActualWeight(500);

            BusinessException ex = assertThrows(BusinessException.class,
                () -> sut.confirmPacking(1L, req));
            assertEquals("ORDER_010", ex.getErrorCode().getCode());
        }

        @Test
        @DisplayName("CONFIRMED → SHIPPING mà chưa packing — throw PACKING_NOT_CONFIRMED")
        void shippingWithoutPacking_throws() {
            Order order = mockOrder(OrderStatus.CONFIRMED);
            order.setPackingConfirmed(false);
            order.setItems(List.of());
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

            UpdateOrderStatusRequest req = new UpdateOrderStatusRequest();
            req.setStatus(OrderStatus.SHIPPING);

            BusinessException ex = assertThrows(BusinessException.class,
                () -> sut.updateOrderStatus(1L, req));
            assertEquals("ORDER_009", ex.getErrorCode().getCode());
        }
    }

    // ==================== getMyOrders ====================

    @Nested
    @DisplayName("getMyOrders")
    class GetMyOrders {

        @Test
        @DisplayName("Trả đúng danh sách đơn của user")
        void returnsUserOrders() {
            Order order = mockOrder(OrderStatus.PENDING);
            order.setItems(List.of());
            when(orderRepository.findByUserIdOrderByCreatedAtDesc(eq(1L), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(order)));
            when(statusService.getLabel(any())).thenReturn("Chờ xác nhận");

            PageResponse<OrderSummaryResponse> result = sut.getMyOrders(1L, null, null, 0, 10);

            assertEquals(1, result.getContent().size());
            assertEquals("PENDING", result.getContent().get(0).getStatus());
            assertEquals("UNPAID", result.getContent().get(0).getPaymentStatus());
        }
    }
}
