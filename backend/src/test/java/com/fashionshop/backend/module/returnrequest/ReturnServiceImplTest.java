package com.fashionshop.backend.module.returnrequest;

import com.fashionshop.backend.common.PageResponse;
import com.fashionshop.backend.common.enums.OrderStatus;
import com.fashionshop.backend.common.enums.PaymentMethod;
import com.fashionshop.backend.common.enums.PaymentStatus;
import com.fashionshop.backend.common.enums.ReturnStatus;
import com.fashionshop.backend.domain.Order;
import com.fashionshop.backend.domain.Payment;
import com.fashionshop.backend.domain.ReturnRequest;
import com.fashionshop.backend.domain.User;
import com.fashionshop.backend.domain.repository.OrderRepository;
import com.fashionshop.backend.domain.repository.ReturnRequestRepository;
import com.fashionshop.backend.domain.repository.UserRepository;
import com.fashionshop.backend.exception.BusinessException;
import com.fashionshop.backend.module.payment.PaymentService;
import com.fashionshop.backend.module.returnrequest.dto.response.ReturnResponse;
import com.fashionshop.backend.module.storage.StorageService;
import com.fashionshop.backend.module.storage.UploadResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReturnServiceImplTest {

    @Mock ReturnRequestRepository returnRepository;
    @Mock OrderRepository orderRepository;
    @Mock UserRepository userRepository;
    @Mock ReturnStatusService returnStatusService;
    @Mock StorageService storageService;
    @Mock PaymentService paymentService;

    @InjectMocks ReturnServiceImpl sut;

    // ==================== Helpers ====================

    private User mockUser(Long id) {
        return User.builder().id(id).fullName("Nguyễn Văn A").email("a@test.com").password("x").build();
    }

    private Order mockOrder(OrderStatus status, PaymentMethod pm, Long userId) {
        return Order.builder()
            .id(1L).user(mockUser(userId)).status(status).paymentMethod(pm)
            .subtotal(BigDecimal.valueOf(200000)).shippingFee(BigDecimal.valueOf(30000))
            .totalAmount(BigDecimal.valueOf(230000))
            .deliveredAt(LocalDateTime.now().minusDays(3)) // 3 ngày trước → trong window
            .items(new ArrayList<>())
            .build();
    }

    private Order mockDeliveredCodOrder(Long userId) {
        return mockOrder(OrderStatus.DELIVERED, PaymentMethod.COD, userId);
    }

    private Order mockDeliveredVnpayOrder(Long userId) {
        Order order = mockOrder(OrderStatus.DELIVERED, PaymentMethod.VNPAY, userId);
        Payment payment = Payment.builder().id(10L).order(order).status(PaymentStatus.SUCCESS).build();
        order.setPayment(payment);
        return order;
    }

    private ReturnRequest mockReturn(Long id, ReturnStatus status, Order order, Long userId) {
        return ReturnRequest.builder()
            .id(id).order(order).user(mockUser(userId)).reason("Hàng lỗi")
            .status(status).evidenceImages(new ArrayList<>())
            .createdAt(LocalDateTime.now())
            .build();
    }

    // ==================== createReturn ====================

    @Nested
    @DisplayName("createReturn")
    class CreateReturn {

        @Test
        @DisplayName("Tạo return thành công — đơn COD DELIVERED trong 7 ngày")
        void success_cod() {
            Order order = mockDeliveredCodOrder(1L);
            User user = mockUser(1L);

            when(orderRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(order));
            when(returnRepository.existsByOrderIdAndStatusIn(eq(1L), anyCollection())).thenReturn(false);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(returnRepository.save(any(ReturnRequest.class))).thenAnswer(inv -> {
                ReturnRequest r = inv.getArgument(0);
                r.setId(1L);
                r.setCreatedAt(LocalDateTime.now());
                return r;
            });
            when(returnStatusService.getLabel(ReturnStatus.PENDING)).thenReturn("Chờ xử lý");

            ReturnResponse response = sut.createReturn(1L, 1L, "Hàng bị lỗi", null);

            assertNotNull(response);
            assertEquals("PENDING", response.getStatus());
            // Order status phải chuyển sang RETURN_REQUESTED
            assertEquals(OrderStatus.RETURN_REQUESTED, order.getStatus());
            verify(orderRepository).save(order);
        }

        @Test
        @DisplayName("Tạo return thành công — có upload ảnh minh chứng")
        void success_withImages() {
            Order order = mockDeliveredCodOrder(1L);
            User user = mockUser(1L);
            MultipartFile file = new MockMultipartFile("img", "test.jpg", "image/jpeg", new byte[]{1, 2, 3});

            when(orderRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(order));
            when(returnRepository.existsByOrderIdAndStatusIn(eq(1L), anyCollection())).thenReturn(false);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(returnRepository.save(any(ReturnRequest.class))).thenAnswer(inv -> {
                ReturnRequest r = inv.getArgument(0);
                r.setId(5L);
                r.setCreatedAt(LocalDateTime.now());
                return r;
            });
            when(storageService.uploadImage(any(), eq("fashion-shop/returns/5")))
                .thenReturn(new UploadResult("https://cdn.test/img.jpg", "returns/5/abc"));
            when(returnStatusService.getLabel(ReturnStatus.PENDING)).thenReturn("Chờ xử lý");

            ReturnResponse response = sut.createReturn(1L, 1L, "Hàng sai màu", List.of(file));

            assertNotNull(response);
            verify(storageService).uploadImage(any(), eq("fashion-shop/returns/5"));
        }

        @Test
        @DisplayName("Lỗi — đơn không tồn tại hoặc không thuộc user")
        void fail_orderNotFound() {
            when(orderRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                () -> sut.createReturn(1L, 99L, "lỗi", null));
            assertEquals("ORDER_001", ex.getErrorCode().getCode());
        }

        @Test
        @DisplayName("Lỗi — đơn chưa giao (PENDING)")
        void fail_notDelivered() {
            Order order = mockOrder(OrderStatus.PENDING, PaymentMethod.COD, 1L);
            when(orderRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(order));

            BusinessException ex = assertThrows(BusinessException.class,
                () -> sut.createReturn(1L, 1L, "lỗi", null));
            assertEquals("RETURN_002", ex.getErrorCode().getCode());
        }

        @Test
        @DisplayName("Lỗi — quá 7 ngày (window expired)")
        void fail_windowExpired() {
            Order order = mockDeliveredCodOrder(1L);
            order.setDeliveredAt(LocalDateTime.now().minusDays(10)); // 10 ngày → hết window

            when(orderRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(order));

            BusinessException ex = assertThrows(BusinessException.class,
                () -> sut.createReturn(1L, 1L, "lỗi", null));
            assertEquals("RETURN_003", ex.getErrorCode().getCode());
        }

        @Test
        @DisplayName("Lỗi — đã có return đang xử lý")
        void fail_alreadyExists() {
            Order order = mockDeliveredCodOrder(1L);
            when(orderRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(order));
            when(returnRepository.existsByOrderIdAndStatusIn(eq(1L), anyCollection())).thenReturn(true);

            BusinessException ex = assertThrows(BusinessException.class,
                () -> sut.createReturn(1L, 1L, "lỗi", null));
            assertEquals("RETURN_004", ex.getErrorCode().getCode());
        }
    }

    // ==================== approveReturn ====================

    @Nested
    @DisplayName("approveReturn")
    class ApproveReturn {

        @Test
        @DisplayName("Duyệt return thành công — PENDING → APPROVED, order → RETURNING")
        void success() {
            Order order = mockOrder(OrderStatus.RETURN_REQUESTED, PaymentMethod.COD, 1L);
            ReturnRequest ret = mockReturn(1L, ReturnStatus.PENDING, order, 1L);
            User staff = mockUser(10L);

            when(returnRepository.findById(1L)).thenReturn(Optional.of(ret));
            when(userRepository.findById(10L)).thenReturn(Optional.of(staff));
            doNothing().when(returnStatusService).validateTransition(ReturnStatus.PENDING, ReturnStatus.APPROVED);

            sut.approveReturn(10L, 1L, "OK");

            assertEquals(ReturnStatus.APPROVED, ret.getStatus());
            assertEquals(OrderStatus.RETURNING, order.getStatus());
            assertEquals(staff, ret.getProcessedBy());
            verify(orderRepository).save(order);
        }

        @Test
        @DisplayName("Lỗi — return không tồn tại")
        void fail_notFound() {
            when(returnRepository.findById(99L)).thenReturn(Optional.empty());

            assertThrows(BusinessException.class, () -> sut.approveReturn(10L, 99L, "ok"));
        }
    }

    // ==================== rejectReturn ====================

    @Nested
    @DisplayName("rejectReturn")
    class RejectReturn {

        @Test
        @DisplayName("Từ chối return thành công — PENDING → REJECTED, order → DELIVERED")
        void success() {
            Order order = mockOrder(OrderStatus.RETURN_REQUESTED, PaymentMethod.COD, 1L);
            ReturnRequest ret = mockReturn(1L, ReturnStatus.PENDING, order, 1L);
            User staff = mockUser(10L);

            when(returnRepository.findById(1L)).thenReturn(Optional.of(ret));
            when(userRepository.findById(10L)).thenReturn(Optional.of(staff));
            doNothing().when(returnStatusService).validateTransition(ReturnStatus.PENDING, ReturnStatus.REJECTED);

            sut.rejectReturn(10L, 1L, "Hàng không bị lỗi");

            assertEquals(ReturnStatus.REJECTED, ret.getStatus());
            assertEquals(OrderStatus.DELIVERED, order.getStatus());
            assertEquals("Hàng không bị lỗi", ret.getAdminNote());
        }

        @Test
        @DisplayName("Lỗi — note bắt buộc khi reject")
        void fail_noNote() {
            BusinessException ex = assertThrows(BusinessException.class,
                () -> sut.rejectReturn(10L, 1L, ""));
            assertEquals("RETURN_006", ex.getErrorCode().getCode());
        }

        @Test
        @DisplayName("Lỗi — note null khi reject")
        void fail_nullNote() {
            BusinessException ex = assertThrows(BusinessException.class,
                () -> sut.rejectReturn(10L, 1L, null));
            assertEquals("RETURN_006", ex.getErrorCode().getCode());
        }
    }

    // ==================== receiveReturn ====================

    @Nested
    @DisplayName("receiveReturn")
    class ReceiveReturn {

        @Test
        @DisplayName("Xác nhận nhận hàng — APPROVED → RECEIVED")
        void success() {
            Order order = mockOrder(OrderStatus.RETURNING, PaymentMethod.COD, 1L);
            ReturnRequest ret = mockReturn(1L, ReturnStatus.APPROVED, order, 1L);
            User admin = mockUser(10L);

            when(returnRepository.findById(1L)).thenReturn(Optional.of(ret));
            when(userRepository.findById(10L)).thenReturn(Optional.of(admin));
            doNothing().when(returnStatusService).validateTransition(ReturnStatus.APPROVED, ReturnStatus.RECEIVED);

            sut.receiveReturn(10L, 1L);

            assertEquals(ReturnStatus.RECEIVED, ret.getStatus());
            assertEquals(admin, ret.getProcessedBy());
        }
    }

    // ==================== completeReturn ====================

    @Nested
    @DisplayName("completeReturn")
    class CompleteReturn {

        @Test
        @DisplayName("Hoàn tất COD — RECEIVED → COMPLETED, order → RETURNED, KHÔNG refund")
        void success_cod_noRefund() {
            Order order = mockOrder(OrderStatus.RETURNING, PaymentMethod.COD, 1L);
            ReturnRequest ret = mockReturn(1L, ReturnStatus.RECEIVED, order, 1L);
            User admin = mockUser(10L);

            when(returnRepository.findById(1L)).thenReturn(Optional.of(ret));
            when(userRepository.findById(10L)).thenReturn(Optional.of(admin));
            doNothing().when(returnStatusService).validateTransition(ReturnStatus.RECEIVED, ReturnStatus.COMPLETED);

            sut.completeReturn(10L, 1L, null);

            assertEquals(ReturnStatus.COMPLETED, ret.getStatus());
            assertEquals(OrderStatus.RETURNED, order.getStatus());
            // Không trigger refund cho COD
            verify(paymentService, never()).processRefund(anyLong(), anyString());
        }

        @Test
        @DisplayName("Hoàn tất VNPAY — RECEIVED → COMPLETED + trigger refund")
        void success_vnpay_refund() {
            Order order = mockDeliveredVnpayOrder(1L);
            order.setStatus(OrderStatus.RETURNING);
            ReturnRequest ret = mockReturn(1L, ReturnStatus.RECEIVED, order, 1L);
            User admin = mockUser(10L);

            when(returnRepository.findById(1L)).thenReturn(Optional.of(ret));
            when(userRepository.findById(10L)).thenReturn(Optional.of(admin));
            doNothing().when(returnStatusService).validateTransition(ReturnStatus.RECEIVED, ReturnStatus.COMPLETED);
            doNothing().when(paymentService).processRefund(10L, "Hoàn tiền trả hàng #1");

            sut.completeReturn(10L, 1L, BigDecimal.valueOf(200000));

            assertEquals(ReturnStatus.COMPLETED, ret.getStatus());
            assertEquals(BigDecimal.valueOf(200000), ret.getRefundAmount());
            verify(paymentService).processRefund(10L, "Hoàn tiền trả hàng #1");
        }

        @Test
        @DisplayName("Hoàn tất VNPAY — refund lỗi → return vẫn COMPLETED (graceful)")
        void success_vnpay_refundFails_graceful() {
            Order order = mockDeliveredVnpayOrder(1L);
            order.setStatus(OrderStatus.RETURNING);
            ReturnRequest ret = mockReturn(1L, ReturnStatus.RECEIVED, order, 1L);
            User admin = mockUser(10L);

            when(returnRepository.findById(1L)).thenReturn(Optional.of(ret));
            when(userRepository.findById(10L)).thenReturn(Optional.of(admin));
            doNothing().when(returnStatusService).validateTransition(ReturnStatus.RECEIVED, ReturnStatus.COMPLETED);
            doThrow(new RuntimeException("VNPay timeout")).when(paymentService)
                .processRefund(anyLong(), anyString());

            // Không throw → return vẫn COMPLETED
            assertDoesNotThrow(() -> sut.completeReturn(10L, 1L, BigDecimal.valueOf(200000)));
            assertEquals(ReturnStatus.COMPLETED, ret.getStatus());
            assertEquals(OrderStatus.RETURNED, order.getStatus());
        }

        @Test
        @DisplayName("Lỗi — refund amount âm")
        void fail_negativeRefund() {
            Order order = mockOrder(OrderStatus.RETURNING, PaymentMethod.COD, 1L);
            ReturnRequest ret = mockReturn(1L, ReturnStatus.RECEIVED, order, 1L);

            when(returnRepository.findById(1L)).thenReturn(Optional.of(ret));
            doNothing().when(returnStatusService).validateTransition(ReturnStatus.RECEIVED, ReturnStatus.COMPLETED);

            BusinessException ex = assertThrows(BusinessException.class,
                () -> sut.completeReturn(10L, 1L, BigDecimal.valueOf(-100)));
            assertEquals("RETURN_007", ex.getErrorCode().getCode());
        }
    }

    // ==================== Query methods ====================

    @Nested
    @DisplayName("Query — getMyReturns / getAllReturns / getById")
    class QueryMethods {

        @Test
        @DisplayName("getMyReturns — danh sách return của customer")
        void myReturns() {
            Order order = mockDeliveredCodOrder(1L);
            ReturnRequest ret = mockReturn(1L, ReturnStatus.PENDING, order, 1L);
            when(returnRepository.findByUserIdOrderByCreatedAtDesc(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(ret)));
            when(returnStatusService.getLabel(ReturnStatus.PENDING)).thenReturn("Chờ xử lý");

            PageResponse<ReturnResponse> result = sut.getMyReturns(1L, 0, 10);

            assertEquals(1, result.getContent().size());
            assertEquals("PENDING", result.getContent().get(0).getStatus());
        }

        @Test
        @DisplayName("getMyReturnById — chi tiết return của customer")
        void myReturnById() {
            Order order = mockDeliveredCodOrder(1L);
            ReturnRequest ret = mockReturn(1L, ReturnStatus.APPROVED, order, 1L);
            when(returnRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(ret));
            when(returnStatusService.getLabel(ReturnStatus.APPROVED)).thenReturn("Đã duyệt");

            ReturnResponse result = sut.getMyReturnById(1L, 1L);

            assertEquals("APPROVED", result.getStatus());
            assertEquals("Đã duyệt", result.getStatusLabel());
        }

        @Test
        @DisplayName("getMyReturnById — return không thuộc user → lỗi")
        void myReturnById_notFound() {
            when(returnRepository.findByIdAndUserId(1L, 2L)).thenReturn(Optional.empty());

            assertThrows(BusinessException.class, () -> sut.getMyReturnById(2L, 1L));
        }

        @Test
        @DisplayName("getAllReturns — không filter status")
        void allReturns_noFilter() {
            Order order = mockDeliveredCodOrder(1L);
            ReturnRequest ret = mockReturn(1L, ReturnStatus.PENDING, order, 1L);
            when(returnRepository.findAllByOrderByCreatedAtDesc(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(ret)));
            when(returnStatusService.getLabel(ReturnStatus.PENDING)).thenReturn("Chờ xử lý");

            PageResponse<ReturnResponse> result = sut.getAllReturns(null, 0, 10);

            assertEquals(1, result.getContent().size());
        }

        @Test
        @DisplayName("getAllReturns — filter theo PENDING")
        void allReturns_filterPending() {
            Order order = mockDeliveredCodOrder(1L);
            ReturnRequest ret = mockReturn(1L, ReturnStatus.PENDING, order, 1L);
            when(returnRepository.findByStatusOrderByCreatedAtDesc(eq(ReturnStatus.PENDING), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(ret)));
            when(returnStatusService.getLabel(ReturnStatus.PENDING)).thenReturn("Chờ xử lý");

            PageResponse<ReturnResponse> result = sut.getAllReturns(ReturnStatus.PENDING, 0, 10);

            assertEquals(1, result.getContent().size());
        }

        @Test
        @DisplayName("getReturnById — staff xem chi tiết")
        void returnById() {
            Order order = mockDeliveredCodOrder(1L);
            ReturnRequest ret = mockReturn(1L, ReturnStatus.RECEIVED, order, 1L);
            when(returnRepository.findById(1L)).thenReturn(Optional.of(ret));
            when(returnStatusService.getLabel(ReturnStatus.RECEIVED)).thenReturn("Đã nhận hàng");

            ReturnResponse result = sut.getReturnById(1L);

            assertEquals("RECEIVED", result.getStatus());
        }
    }
}
