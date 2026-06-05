package com.fashionshop.backend.module.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fashionshop.backend.common.PageResponse;
import com.fashionshop.backend.common.enums.OrderPaymentStatus;
import com.fashionshop.backend.common.enums.OrderStatus;
import com.fashionshop.backend.common.enums.PaymentMethod;
import com.fashionshop.backend.common.enums.PaymentStatus;
import com.fashionshop.backend.common.enums.ProductStatus;
import com.fashionshop.backend.domain.Address;
import com.fashionshop.backend.domain.Order;
import com.fashionshop.backend.domain.OrderItem;
import com.fashionshop.backend.domain.Payment;
import com.fashionshop.backend.domain.Product;
import com.fashionshop.backend.domain.ProductImage;
import com.fashionshop.backend.domain.ProductVariant;
import com.fashionshop.backend.domain.Review;
import com.fashionshop.backend.domain.User;
import com.fashionshop.backend.domain.repository.AddressRepository;
import com.fashionshop.backend.domain.repository.OrderRepository;
import com.fashionshop.backend.domain.repository.PaymentRepository;
import com.fashionshop.backend.domain.repository.ProductImageRepository;
import com.fashionshop.backend.domain.repository.ProductVariantRepository;
import com.fashionshop.backend.domain.repository.ReturnRequestRepository;
import com.fashionshop.backend.domain.repository.ReviewRepository;
import com.fashionshop.backend.domain.repository.UserRepository;
import com.fashionshop.backend.exception.BusinessException;
import com.fashionshop.backend.exception.ErrorCode;
import com.fashionshop.backend.module.cart.CartService;
import com.fashionshop.backend.module.order.dto.request.CancelOrderRequest;
import com.fashionshop.backend.module.order.dto.request.ConfirmPackingRequest;
import com.fashionshop.backend.module.order.dto.request.CreateOrderRequest;
import com.fashionshop.backend.module.order.dto.request.OrderItemRequest;
import com.fashionshop.backend.module.order.dto.request.UpdateOrderStatusRequest;
import com.fashionshop.backend.module.order.dto.response.CreateOrderResponse;
import com.fashionshop.backend.module.order.dto.response.OrderDetailResponse;
import com.fashionshop.backend.module.order.dto.response.OrderStatsResponse;
import com.fashionshop.backend.module.order.dto.response.OrderSummaryResponse;
import com.fashionshop.backend.module.order.dto.response.StaffShippingPreviewResponse;
import com.fashionshop.backend.module.payment.PaymentService;
import com.fashionshop.backend.module.product.ProductPriceService;
import com.fashionshop.backend.module.returnrequest.ReturnStatusService;
import com.fashionshop.backend.module.shipping.ActualShippingFeeResult;
import com.fashionshop.backend.module.shipping.CheckoutShippingFeeResult;
import com.fashionshop.backend.module.shipping.ShippingCalculationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final int MAX_ITEMS_PER_ORDER = 20;
    private static final int MAX_QUANTITY_PER_ITEM = 10;
    private static final int DUPLICATE_GUARD_SECONDS = 30;

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductImageRepository imageRepository;
    private final AddressRepository addressRepository;
    private final UserRepository userRepository;
    private final ReviewRepository reviewRepository;
    private final ReturnRequestRepository returnRequestRepository;
    private final CartService cartService;
    private final OrderStatusService statusService;
    private final PaymentService paymentService;
    private final ReturnStatusService returnStatusService;
    private final ProductPriceService productPriceService;
    private final ShippingCalculationService shippingCalculationService;

    @Override
    @Transactional
    public CreateOrderResponse createOrder(Long userId, CreateOrderRequest request, String ipAddress) {
        orderRepository.findFirstByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(
                userId, LocalDateTime.now().minusSeconds(DUPLICATE_GUARD_SECONDS))
                .ifPresent(o -> {
                    throw new BusinessException(ErrorCode.DUPLICATE_ORDER, HttpStatus.BAD_REQUEST,
                            "Vui long doi " + DUPLICATE_GUARD_SECONDS + " giay truoc khi dat don tiep");
                });

        Map<Long, Integer> requestedItems = normalizeOrderItems(request.getItems());
        if (requestedItems.size() > MAX_ITEMS_PER_ORDER) {
            throw new BusinessException(ErrorCode.MAX_ITEMS_EXCEEDED, HttpStatus.BAD_REQUEST,
                    "Moi don hang toi da " + MAX_ITEMS_PER_ORDER + " san pham");
        }

        Address address = addressRepository.findByIdAndUser_Id(request.getAddressId(), userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.ADDRESS_NOT_BELONG_TO_USER, HttpStatus.FORBIDDEN));

        CheckoutShippingFeeResult shippingResult = shippingCalculationService.calculateCheckoutFee(
                userId, request.getAddressId(), requestedItems);
        BigDecimal shippingFee = BigDecimal.valueOf(shippingResult.shippingFee());
        OrderStatus initialStatus = request.getPaymentMethod() == PaymentMethod.COD
                ? OrderStatus.PENDING
                : OrderStatus.AWAITING_PAYMENT;

        User userRef = userRepository.getReferenceById(userId);
        Order order = Order.builder()
                .user(userRef)
                .status(initialStatus)
                .paymentMethod(request.getPaymentMethod())
                .paymentStatus(OrderPaymentStatus.UNPAID)
                .shippingFee(shippingFee)
                .note(request.getNote())
                .addressSnapshot(buildAddressSnapshot(address))
                .build();

        BigDecimal subtotal = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();

        for (Map.Entry<Long, Integer> entry : requestedItems.entrySet()) {
            Long variantId = entry.getKey();
            int quantity = entry.getValue();

            ProductVariant variant = variantRepository.findById(variantId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.VARIANT_NOT_FOUND, HttpStatus.NOT_FOUND));

            if (quantity > MAX_QUANTITY_PER_ITEM) {
                throw new BusinessException(ErrorCode.MAX_QUANTITY_EXCEEDED, HttpStatus.BAD_REQUEST,
                        "San pham " + variant.getProduct().getName() + " toi da " + MAX_QUANTITY_PER_ITEM + " cai/don");
            }

            Product product = variant.getProduct();
            if (product.getStatus() != ProductStatus.ACTIVE) {
                throw new BusinessException(ErrorCode.PRODUCT_OUT_OF_STOCK, HttpStatus.BAD_REQUEST,
                        "San pham " + product.getName() + " khong con ban");
            }

            int affected = variantRepository.decreaseStock(variantId, quantity);
            if (affected == 0) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK, HttpStatus.BAD_REQUEST,
                        "San pham " + product.getName() + " (" + variant.getSize() + ") khong du ton kho");
            }

            BigDecimal unitPrice = productPriceService.getFinalUnitPrice(product, variant);
            BigDecimal itemSubtotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
            String imageUrl = resolveVariantImageUrl(variant);

            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .variant(variant)
                    .productId(product.getId())
                    .productName(product.getName())
                    .colorName(variant.getColor() != null ? variant.getColor().getColorName() : null)
                    .size(variant.getSize())
                    .imageUrl(imageUrl)
                    .unitPrice(unitPrice)
                    .quantity(quantity)
                    .subtotal(itemSubtotal)
                    .build();

            orderItems.add(orderItem);
            subtotal = subtotal.add(itemSubtotal);
        }

        order.setSubtotal(subtotal);
        order.setTotalAmount(subtotal.add(shippingFee));
        order.setItems(orderItems);

        order.setExpectedDeliveryDate(shippingResult.expectedDeliveryDate());

        Order savedOrder = orderRepository.save(order);

        Payment payment = Payment.builder()
                .order(savedOrder)
                .method(request.getPaymentMethod())
                .status(PaymentStatus.PENDING)
                .amount(savedOrder.getTotalAmount())
                .build();
        paymentRepository.save(payment);

        cartService.clearByVariantIds(userId, new ArrayList<>(requestedItems.keySet()));

        log.info("Order #{} created by user {} - status={}, total={}",
                savedOrder.getId(), userId, initialStatus, savedOrder.getTotalAmount());

        String paymentUrl = null;
        if (request.getPaymentMethod() == PaymentMethod.VNPAY && ipAddress != null) {
            paymentUrl = paymentService.createVnPayPaymentUrl(savedOrder.getId(), ipAddress);
        }

        return CreateOrderResponse.builder()
                .orderId(savedOrder.getId())
                .status(savedOrder.getStatus())
                .totalAmount(savedOrder.getTotalAmount())
                .paymentUrl(paymentUrl)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> getMyOrders(Long userId, OrderStatus status, String keyword,
            int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        String kw = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim() : null;

        Page<Order> orders;
        if (kw != null) {
            orders = orderRepository.findCustomerOrders(userId, status, kw, pageable);
        } else {
            orders = status != null
                    ? orderRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, status, pageable)
                    : orderRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        }

        return buildPageResponse(orders);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderDetailResponse getMyOrderById(Long userId, Long orderId) {
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND, HttpStatus.NOT_FOUND));

        List<Long> itemIds = order.getItems().stream().map(OrderItem::getId).toList();
        Map<Long, Review> itemIdToReview = new HashMap<>();
        reviewRepository.findByOrderItemIdIn(itemIds)
                .forEach(r -> itemIdToReview.put(r.getOrderItem().getId(), r));

        ReturnInfo returnInfo = loadLatestReturnInfo(orderId);

        return OrderDetailResponse.from(order, statusService.getLabel(order.getStatus()),
                itemIdToReview, returnInfo.returnId(), returnInfo.status(), returnInfo.statusLabel(),
                returnInfo.reason(), returnInfo.adminNote(), returnInfo.evidenceImages(), returnInfo.refundAmount(),
                null, null, null);
    }

    @Override
    @Transactional
    public void cancelOrder(Long userId, Long orderId, CancelOrderRequest request) {
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND, HttpStatus.NOT_FOUND));

        if (!statusService.canCustomerCancel(order.getStatus())) {
            throw new BusinessException(ErrorCode.ORDER_CANNOT_CANCEL, HttpStatus.BAD_REQUEST,
                    "Chi co the huy don khi dang cho xac nhan");
        }

        restoreStock(order);
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelReason(request != null ? request.getReason() : null);
        orderRepository.save(order);

        log.info("Order #{} cancelled by customer {}", orderId, userId);
    }

    @Override
    @Transactional
    public void confirmCompleted(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND, HttpStatus.NOT_FOUND));

        if (order.getStatus() != OrderStatus.SHIPPING) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, HttpStatus.BAD_REQUEST,
                    "Chi co the hoan thanh don khi don dang giao");
        }

        LocalDateTime now = LocalDateTime.now();
        order.setStatus(OrderStatus.COMPLETED);
        order.setDeliveredAt(now);
        order.setCompletedAt(now);

        paymentRepository.findByOrderId(orderId).ifPresent(payment -> {
            if (payment.getMethod() == PaymentMethod.COD
                    && payment.getStatus() == PaymentStatus.PENDING) {
                payment.setStatus(PaymentStatus.SUCCESS);
                payment.setPaidAt(LocalDateTime.now());
                order.setPaymentStatus(OrderPaymentStatus.PAID);
                paymentRepository.save(payment);
                log.info("COD payment #{} confirmed for order #{}", payment.getId(), orderId);
            }
        });

        orderRepository.save(order);
        log.info("Order #{} confirmed completed by admin", orderId);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> getAllOrders(OrderStatus status, String keyword,
            Long categoryId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        String kw = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;
        Page<Order> orders = orderRepository.searchOrders(status, kw, categoryId, pageable);
        return buildPageResponse(orders);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderStatsResponse getOrderStats() {
        return OrderStatsResponse.builder()
                .totalOrders(orderRepository.count())
                .pendingCount(orderRepository.countByStatus(OrderStatus.PENDING))
                .confirmedCount(orderRepository.countByStatus(OrderStatus.CONFIRMED))
                .shippingCount(orderRepository.countByStatus(OrderStatus.SHIPPING))
                .deliveredCount(orderRepository.countByStatus(OrderStatus.DELIVERED))
                .completedCount(orderRepository.countByStatus(OrderStatus.COMPLETED))
                .cancelledCount(orderRepository.countByStatus(OrderStatus.CANCELLED))
                .returnCount(returnRequestRepository.count())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public OrderDetailResponse getOrderById(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND, HttpStatus.NOT_FOUND));

        ReturnInfo returnInfo = loadLatestReturnInfo(orderId);

        return OrderDetailResponse.from(order, statusService.getLabel(order.getStatus()),
                Map.of(), returnInfo.returnId(), returnInfo.status(), returnInfo.statusLabel(),
                returnInfo.reason(), returnInfo.adminNote(), returnInfo.evidenceImages(), returnInfo.refundAmount(),
                null, null, null);
    }

    @Override
    @Transactional
    public OrderDetailResponse updateOrderStatus(Long orderId, UpdateOrderStatusRequest request) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND, HttpStatus.NOT_FOUND));

        OrderStatus oldStatus = order.getStatus();
        OrderStatus newStatus = request.getStatus();

        if (newStatus == OrderStatus.CANCELLED) {
            CancelOrderRequest cancelReq = new CancelOrderRequest();
            cancelReq.setReason(request.getCancelReason());
            staffCancelOrder(orderId, cancelReq);
            order = orderRepository.findById(orderId).orElseThrow();
            return OrderDetailResponse.from(order, statusService.getLabel(order.getStatus()));
        }

        if (newStatus == OrderStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, HttpStatus.BAD_REQUEST,
                    "Chi admin moi duoc xac nhan hoan thanh don hang");
        }
        if (newStatus == OrderStatus.DELIVERED) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, HttpStatus.BAD_REQUEST,
                    "Khong con su dung trang thai DELIVERED trong flow moi");
        }

        statusService.validateTransition(order.getStatus(), newStatus);
        guardVnPayPaidBeforeProcessing(order, newStatus);

        if (newStatus == OrderStatus.SHIPPING && !Boolean.TRUE.equals(order.getPackingConfirmed())) {
            throw new BusinessException(ErrorCode.PACKING_NOT_CONFIRMED, HttpStatus.BAD_REQUEST);
        }

        order.setStatus(newStatus);

        orderRepository.save(order);
        log.info("Order #{} status updated: {} -> {}", orderId, oldStatus, newStatus);

        return OrderDetailResponse.from(order, statusService.getLabel(order.getStatus()));
    }

    @Override
    @Transactional
    public void staffCancelOrder(Long orderId, CancelOrderRequest request) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND, HttpStatus.NOT_FOUND));

        if (!statusService.canStaffCancel(order.getStatus())) {
            throw new BusinessException(ErrorCode.ORDER_CANNOT_CANCEL, HttpStatus.BAD_REQUEST,
                    "Khong the huy don o trang thai " + statusService.getLabel(order.getStatus()));
        }

        if (request == null || request.getReason() == null || request.getReason().isBlank()) {
            throw new BusinessException(ErrorCode.ORDER_CANCEL_REASON_REQUIRED, HttpStatus.BAD_REQUEST);
        }

        restoreStock(order);
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelReason(request.getReason());
        orderRepository.save(order);

        paymentRepository.findByOrderId(orderId).ifPresent(payment -> {
            if (payment.getMethod() == PaymentMethod.VNPAY
                    && payment.getStatus() == PaymentStatus.SUCCESS) {
                paymentService.processRefund(payment.getId(),
                        "Don hang bi huy: " + request.getReason());
                log.info("Auto-refund triggered for order #{}", orderId);
            } else if (payment.getStatus() == PaymentStatus.PENDING) {
                payment.setStatus(PaymentStatus.FAILED);
                paymentRepository.save(payment);
            }
        });

        log.info("Order #{} cancelled by staff. Reason: {}", orderId, request.getReason());
    }

    @Override
    @Transactional(readOnly = true)
    public StaffShippingPreviewResponse previewActualShippingFee(Long orderId, ConfirmPackingRequest request) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND, HttpStatus.NOT_FOUND));
        ActualShippingFeeResult estimate = shippingCalculationService.calculateActualFee(order, request);
        return StaffShippingPreviewResponse.builder()
                .provider(ShippingCalculationService.PROVIDER)
                .customerShippingFee(order.getShippingFee())
                .actualGhnFee(estimate.actualShippingFee())
                .difference(estimate.shippingFeeDifference())
                .actualWeight(estimate.actualWeight())
                .packageLength(estimate.packageLength())
                .packageWidth(estimate.packageWidth())
                .packageHeight(estimate.packageHeight())
                .build();
    }

    @Override
    @Transactional
    public OrderDetailResponse confirmPacking(Long orderId, ConfirmPackingRequest request) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND, HttpStatus.NOT_FOUND));

        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.PACKING_INVALID_STATUS, HttpStatus.BAD_REQUEST);
        }

        ActualShippingFeeResult estimate = shippingCalculationService.calculateActualFee(order, request);

        order.setPackageLength(request.getPackageLength());
        order.setPackageWidth(request.getPackageWidth());
        order.setPackageHeight(request.getPackageHeight());
        order.setActualWeight(request.getActualWeight());

        if (request.getPackingNote() != null && !request.getPackingNote().isBlank()) {
            String existingNote = order.getNote() != null ? order.getNote() + "\n" : "";
            order.setNote(existingNote + "[Dong goi] " + request.getPackingNote());
        }

        order.setPackingConfirmed(true);
        order.setStatus(OrderStatus.SHIPPING);
        orderRepository.save(order);

        log.info("Order #{} packing confirmed: {}x{}x{} cm, actual={}g",
                orderId, request.getPackageLength(), request.getPackageWidth(), request.getPackageHeight(),
                request.getActualWeight());

        return OrderDetailResponse.from(order, statusService.getLabel(order.getStatus()),
                Map.of(), null, null, null, null, null, null, null,
                estimate.actualShippingFee(), estimate.shippingFeeDifference(), null);
    }

    private Map<Long, Integer> normalizeOrderItems(List<OrderItemRequest> items) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException(ErrorCode.ORDER_ITEMS_EMPTY, HttpStatus.BAD_REQUEST);
        }

        Map<Long, Integer> merged = new LinkedHashMap<>();
        for (OrderItemRequest item : items) {
            if (item == null || item.getVariantId() == null || item.getQuantity() < 1) {
                throw new BusinessException(ErrorCode.ORDER_ITEMS_EMPTY, HttpStatus.BAD_REQUEST);
            }
            merged.merge(item.getVariantId(), item.getQuantity(), Integer::sum);
        }
        return merged;
    }

    private void guardVnPayPaidBeforeProcessing(Order order, OrderStatus newStatus) {
        boolean processingStatus = newStatus == OrderStatus.CONFIRMED
                || newStatus == OrderStatus.SHIPPING
                || newStatus == OrderStatus.COMPLETED;
        if (processingStatus
                && order.getPaymentMethod() == PaymentMethod.VNPAY
                && order.getPaymentStatus() != OrderPaymentStatus.PAID) {
            throw new BusinessException(ErrorCode.ORDER_PAYMENT_NOT_PAID, HttpStatus.BAD_REQUEST,
                    "Don VNPay chua thanh toan thanh cong, khong the xu ly don.");
        }
    }

    private Map<String, Object> buildAddressSnapshot(Address address) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("fullName", address.getFullName());
        snapshot.put("phone", address.getPhone());
        snapshot.put("province", address.getProvince());
        snapshot.put("district", address.getDistrict());
        snapshot.put("ward", address.getWard());
        snapshot.put("districtCode", address.getDistrictCode());
        snapshot.put("wardCode", address.getWardCode());
        snapshot.put("street", address.getStreet());
        snapshot.put("fullAddress", address.getStreet() + ", " + address.getWard()
                + ", " + address.getDistrict() + ", " + address.getProvince());
        return snapshot;
    }

    private void restoreStock(Order order) {
        for (OrderItem item : order.getItems()) {
            if (item.getVariant() != null) {
                variantRepository.increaseStock(item.getVariant().getId(), item.getQuantity());
            }
        }
    }

    private String resolveVariantImageUrl(ProductVariant variant) {
        if (variant == null || variant.getProduct() == null) {
            return null;
        }

        Long productId = variant.getProduct().getId();
        if (variant.getColor() != null) {
            var colorImage = imageRepository.findColorThumbnail(productId, variant.getColor().getId());
            if (colorImage.isPresent()) {
                return colorImage.get().getImageUrl();
            }
        }

        return imageRepository.findPrimaryByProductId(productId)
                .map(ProductImage::getImageUrl)
                .orElse(null);
    }

    private ReturnInfo loadLatestReturnInfo(Long orderId) {
        var latestReturn = returnRequestRepository.findFirstByOrderIdOrderByCreatedAtDesc(orderId);
        if (latestReturn.isEmpty()) {
            return ReturnInfo.empty();
        }

        var ret = latestReturn.get();
        return new ReturnInfo(
                ret.getId(),
                ret.getStatus().name(),
                returnStatusService.getLabel(ret.getStatus()),
                ret.getReason(),
                ret.getAdminNote(),
                ret.getEvidenceImages(),
                ret.getRefundAmount());
    }

    private PageResponse<OrderSummaryResponse> buildPageResponse(Page<Order> orders) {
        var content = orders.getContent().stream()
                .map(o -> OrderSummaryResponse.from(o, statusService.getLabel(o.getStatus())))
                .toList();

        return PageResponse.from(content, orders);
    }

    private record ReturnInfo(
            Long returnId,
            String status,
            String statusLabel,
            String reason,
            String adminNote,
            List<String> evidenceImages,
            BigDecimal refundAmount) {
        private static ReturnInfo empty() {
            return new ReturnInfo(null, null, null, null, null, null, null);
        }
    }
}
