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
import com.fashionshop.backend.domain.Address;
import com.fashionshop.backend.domain.CartItem;
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
import com.fashionshop.backend.module.order.dto.request.UpdateOrderStatusRequest;
import com.fashionshop.backend.module.order.dto.response.CreateOrderResponse;
import com.fashionshop.backend.module.order.dto.response.OrderDetailResponse;
import com.fashionshop.backend.module.order.dto.response.OrderStatsResponse;
import com.fashionshop.backend.module.order.dto.response.OrderSummaryResponse;
import com.fashionshop.backend.module.order.shipping.PackingShippingEstimate;
import com.fashionshop.backend.module.order.shipping.ShippingFeeEstimator;
import com.fashionshop.backend.module.payment.PaymentService;
import com.fashionshop.backend.module.returnrequest.ReturnStatusService;

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
    private final ShippingFeeEstimator shippingFeeEstimator;

    // ================================================================
    // Customer — Tạo đơn hàng
    // ================================================================

    @Override
    @Transactional
    public CreateOrderResponse createOrder(Long userId, CreateOrderRequest request, String ipAddress) {
        // 0. Duplicate order guard
        orderRepository.findFirstByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(
            userId, LocalDateTime.now().minusSeconds(DUPLICATE_GUARD_SECONDS))
            .ifPresent(o -> {
                throw new BusinessException(ErrorCode.DUPLICATE_ORDER, HttpStatus.BAD_REQUEST,
                    "Vui lòng đợi " + DUPLICATE_GUARD_SECONDS + " giây trước khi đặt đơn tiếp");
            });

        // 1. Load cart items + validate limits
        List<CartItem> cartItems = cartService.getCartItems(userId);
        if (cartItems.isEmpty()) {
            throw new BusinessException(ErrorCode.CART_EMPTY, HttpStatus.BAD_REQUEST);
        }
        if (cartItems.size() > MAX_ITEMS_PER_ORDER) {
            throw new BusinessException(ErrorCode.MAX_ITEMS_EXCEEDED, HttpStatus.BAD_REQUEST,
                "Mỗi đơn hàng tối đa " + MAX_ITEMS_PER_ORDER + " sản phẩm");
        }

        // 2. Load + validate address
        Address address = addressRepository.findByIdAndUser_Id(request.getAddressId(), userId)
            .orElseThrow(() -> new BusinessException(
                ErrorCode.ADDRESS_NOT_BELONG_TO_USER, HttpStatus.FORBIDDEN));

        // 3. Build order
        BigDecimal shippingFee = BigDecimal.valueOf(request.getShippingFee());
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

        // 4. Process items: validate stock + snapshot + deduct
        BigDecimal subtotal = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();

        for (CartItem cartItem : cartItems) {
            // Pessimistic lock trên variant
            ProductVariant variant = variantRepository.findByIdForUpdate(cartItem.getVariant().getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.VARIANT_NOT_FOUND, HttpStatus.NOT_FOUND));

            // Validate quantity limit per item
            if (cartItem.getQuantity() > MAX_QUANTITY_PER_ITEM) {
                throw new BusinessException(ErrorCode.MAX_QUANTITY_EXCEEDED, HttpStatus.BAD_REQUEST,
                    "Sản phẩm " + variant.getProduct().getName() + " tối đa " + MAX_QUANTITY_PER_ITEM + " cái/đơn");
            }

            // Validate stock
            if (variant.getStockQuantity() < cartItem.getQuantity()) {
                throw new BusinessException(
                    ErrorCode.INSUFFICIENT_STOCK, HttpStatus.BAD_REQUEST,
                    "Sản phẩm " + variant.getProduct().getName() + " (" + variant.getSize()
                        + ") chỉ còn " + variant.getStockQuantity() + " sản phẩm");
            }

            // Tính giá tại thời điểm mua
            Product product = variant.getProduct();
            BigDecimal unitPrice = calculateUnitPrice(product, variant);
            BigDecimal itemSubtotal = unitPrice.multiply(BigDecimal.valueOf(cartItem.getQuantity()));

                String imageUrl = resolveVariantImageUrl(variant);

            // Snapshot order item
            OrderItem orderItem = OrderItem.builder()
                .order(order)
                .variant(variant)
                .productId(product.getId())
                .productName(product.getName())
                .colorName(variant.getColor() != null ? variant.getColor().getColorName() : null)
                .size(variant.getSize())
                .imageUrl(imageUrl)
                .unitPrice(unitPrice)
                .quantity(cartItem.getQuantity())
                .subtotal(itemSubtotal)
                .build();

            orderItems.add(orderItem);
            subtotal = subtotal.add(itemSubtotal);

            // Trừ stock
            variant.setStockQuantity(variant.getStockQuantity() - cartItem.getQuantity());
            variantRepository.save(variant);
        }

        order.setSubtotal(subtotal);
        order.setTotalAmount(subtotal.add(shippingFee));
        order.setItems(orderItems);

        // Set expected delivery date
        if (request.getEstimatedDays() != null) {
            order.setExpectedDeliveryDate(java.time.LocalDate.now().plusDays(request.getEstimatedDays()));
        }

        Order savedOrder = orderRepository.save(order);

        // 5. Tạo Payment record
        Payment payment = Payment.builder()
            .order(savedOrder)
            .method(request.getPaymentMethod())
            .status(PaymentStatus.PENDING)
            .amount(savedOrder.getTotalAmount())
            .build();
        paymentRepository.save(payment);

        // 6. Clear cart
        cartService.clearCart(userId);

        log.info("Order #{} created by user {} — status={}, total={}",
            savedOrder.getId(), userId, initialStatus, savedOrder.getTotalAmount());

        // 7. Tạo VNPay payment URL nếu phương thức là VNPAY
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

    // ================================================================
    // Customer — Xem đơn hàng
    // ================================================================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> getMyOrders(Long userId, OrderStatus status, String keyword,
                                                           int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);

        // Chuẩn bị keyword (nếu có)
        String kw = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim() : null;

        Page<Order> orders;
        if (kw != null) {
            orders = orderRepository.findCustomerOrders(userId, status, kw, pageable);
        } else {
            orders = (status != null)
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

        // Batch query review info cho canReview flag và review details
        List<Long> itemIds = order.getItems().stream().map(OrderItem::getId).toList();

        // Map orderItemId -> Review
        Map<Long, Review> itemIdToReview = new HashMap<>();
        reviewRepository.findByOrderItemIdIn(itemIds)
            .forEach(r -> itemIdToReview.put(r.getOrderItem().getId(), r));

        // Query return status nếu có
        Long returnId = null;
        String returnStatus = null;
        String returnStatusLabel = null;
        String returnReason = null;
        String returnAdminNote = null;
        List<String> returnEvidenceImages = null;
        java.math.BigDecimal returnRefundAmount = null;
        var latestReturn = returnRequestRepository.findFirstByOrderIdOrderByCreatedAtDesc(orderId);
        if (latestReturn.isPresent()) {
            var ret = latestReturn.get();
            returnId = ret.getId();
            returnStatus = ret.getStatus().name();
            returnStatusLabel = returnStatusService.getLabel(ret.getStatus());
            returnReason = ret.getReason();
            returnAdminNote = ret.getAdminNote();
            returnEvidenceImages = ret.getEvidenceImages();
            returnRefundAmount = ret.getRefundAmount();
        }

        PackingShippingEstimate estimate = shippingFeeEstimator.estimateFromOrder(order);

        return OrderDetailResponse.from(order, statusService.getLabel(order.getStatus()),
            itemIdToReview, returnId, returnStatus, returnStatusLabel,
            returnReason, returnAdminNote, returnEvidenceImages, returnRefundAmount,
            estimate != null ? estimate.estimatedShippingFee() : null,
            estimate != null ? estimate.shippingFeeDifference() : null,
            estimate != null ? estimate.warnings() : null);
    }

    // ================================================================
    // Customer — Hủy đơn
    // ================================================================

    @Override
    @Transactional
    public void cancelOrder(Long userId, Long orderId, CancelOrderRequest request) {
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND, HttpStatus.NOT_FOUND));

        if (!statusService.canCustomerCancel(order.getStatus())) {
            throw new BusinessException(ErrorCode.ORDER_CANNOT_CANCEL, HttpStatus.BAD_REQUEST,
                "Chỉ có thể hủy đơn khi đang ở trạng thái Chờ thanh toán hoặc Chờ xác nhận");
        }

        restoreStock(order);
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelReason(request != null ? request.getReason() : null);
        orderRepository.save(order);

        log.info("Order #{} cancelled by customer {}", orderId, userId);
    }

    // ================================================================
    // Customer — Xác nhận nhận hàng
    // ================================================================

    @Override
    @Transactional
    public void confirmReceived(Long userId, Long orderId) {
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND, HttpStatus.NOT_FOUND));

        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, HttpStatus.BAD_REQUEST,
                "Chỉ có thể xác nhận nhận hàng khi đơn ở trạng thái Đã giao");
        }

        order.setStatus(OrderStatus.COMPLETED);

        // COD: khách xác nhận nhận hàng = đã thanh toán cho shipper → payment SUCCESS
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
        log.info("Order #{} confirmed received by customer {}", orderId, userId);
    }

    // ================================================================
    // Staff / Admin — Xem đơn hàng
    // ================================================================

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
            .returnCount(
                orderRepository.countByStatus(OrderStatus.RETURN_REQUESTED)
                + orderRepository.countByStatus(OrderStatus.RETURNING)
                + orderRepository.countByStatus(OrderStatus.RETURNED))
            .build();
    }

    @Override
    @Transactional(readOnly = true)
    public OrderDetailResponse getOrderById(Long orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND, HttpStatus.NOT_FOUND));

        Long returnId = null;
        String returnStatus = null;
        String returnStatusLabel = null;
        String returnReason = null;
        String returnAdminNote = null;
        List<String> returnEvidenceImages = null;
        java.math.BigDecimal returnRefundAmount = null;
        var latestReturn = returnRequestRepository.findFirstByOrderIdOrderByCreatedAtDesc(orderId);
        if (latestReturn.isPresent()) {
            var ret = latestReturn.get();
            returnId = ret.getId();
            returnStatus = ret.getStatus().name();
            returnStatusLabel = returnStatusService.getLabel(ret.getStatus());
            returnReason = ret.getReason();
            returnAdminNote = ret.getAdminNote();
            returnEvidenceImages = ret.getEvidenceImages();
            returnRefundAmount = ret.getRefundAmount();
        }

        PackingShippingEstimate estimate = shippingFeeEstimator.estimateFromOrder(order);

        return OrderDetailResponse.from(order, statusService.getLabel(order.getStatus()),
            Map.of(), returnId, returnStatus, returnStatusLabel,
            returnReason, returnAdminNote, returnEvidenceImages, returnRefundAmount,
            estimate != null ? estimate.estimatedShippingFee() : null,
            estimate != null ? estimate.shippingFeeDifference() : null,
            estimate != null ? estimate.warnings() : null);
    }

    // ================================================================
    // Staff / Admin — Cập nhật trạng thái
    // ================================================================

    @Override
    @Transactional
    public OrderDetailResponse updateOrderStatus(Long orderId, UpdateOrderStatusRequest request) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND, HttpStatus.NOT_FOUND));

        OrderStatus newStatus = request.getStatus();

        // Nếu chuyển sang CANCELLED → dùng staffCancelOrder
        if (newStatus == OrderStatus.CANCELLED) {
            CancelOrderRequest cancelReq = new CancelOrderRequest();
            cancelReq.setReason(request.getCancelReason());
            staffCancelOrder(orderId, cancelReq);
            // Reload sau khi cancel
            order = orderRepository.findById(orderId).orElseThrow();
            return OrderDetailResponse.from(order, statusService.getLabel(order.getStatus()));
        }

        statusService.validateTransition(order.getStatus(), newStatus);
        guardVnPayPaidBeforeProcessing(order, newStatus);

        // Bắt buộc xác nhận đóng gói trước khi giao hàng
        if (newStatus == OrderStatus.SHIPPING && !Boolean.TRUE.equals(order.getPackingConfirmed())) {
            throw new BusinessException(ErrorCode.PACKING_NOT_CONFIRMED, HttpStatus.BAD_REQUEST);
        }

        order.setStatus(newStatus);

        // Set deliveredAt khi chuyển sang DELIVERED
        if (newStatus == OrderStatus.DELIVERED) {
            order.setDeliveredAt(LocalDateTime.now());
        }

        orderRepository.save(order);
        log.info("Order #{} status updated: {} → {}", orderId, order.getStatus(), newStatus);

        return OrderDetailResponse.from(order, statusService.getLabel(order.getStatus()));
    }

    // ================================================================
    // Staff / Admin — Hủy đơn (bắt buộc cancelReason)
    // ================================================================

    @Override
    @Transactional
    public void staffCancelOrder(Long orderId, CancelOrderRequest request) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND, HttpStatus.NOT_FOUND));

        if (!statusService.canStaffCancel(order.getStatus())) {
            throw new BusinessException(ErrorCode.ORDER_CANNOT_CANCEL, HttpStatus.BAD_REQUEST,
                "Không thể hủy đơn ở trạng thái " + statusService.getLabel(order.getStatus()));
        }

        if (request == null || request.getReason() == null || request.getReason().isBlank()) {
            throw new BusinessException(ErrorCode.ORDER_CANCEL_REASON_REQUIRED, HttpStatus.BAD_REQUEST);
        }

        restoreStock(order);
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelReason(request.getReason());
        orderRepository.save(order);

        // Auto-refund VNPay nếu đã thanh toán
        paymentRepository.findByOrderId(orderId).ifPresent(payment -> {
            if (payment.getMethod() == PaymentMethod.VNPAY
                    && payment.getStatus() == PaymentStatus.SUCCESS) {
                paymentService.processRefund(payment.getId(),
                    "Đơn hàng bị hủy: " + request.getReason());
                log.info("Auto-refund triggered for order #{}", orderId);
            } else if (payment.getStatus() == PaymentStatus.PENDING) {
                payment.setStatus(PaymentStatus.FAILED);
                paymentRepository.save(payment);
            }
        });

        log.info("Order #{} cancelled by staff. Reason: {}", orderId, request.getReason());
    }

    // ================================================================
    // Staff / Admin — Xác nhận đóng gói
    // ================================================================

    @Override
    @Transactional
    public OrderDetailResponse confirmPacking(Long orderId, ConfirmPackingRequest request) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND, HttpStatus.NOT_FOUND));

        // Chỉ cho phép đóng gói khi CONFIRMED
        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.PACKING_INVALID_STATUS, HttpStatus.BAD_REQUEST);
        }

        PackingShippingEstimate estimate = shippingFeeEstimator.estimate(order, request);

        // Set kích thước
        order.setPackageLength(request.getLength());
        order.setPackageWidth(request.getWidth());
        order.setPackageHeight(request.getHeight());
        order.setActualWeight(request.getActualWeight());

        order.setVolumetricWeight(estimate.volumetricWeight());
        order.setChargeableWeight(estimate.chargeableWeight());

        // Ghi chú đóng gói vào note (append)
        if (request.getPackingNote() != null && !request.getPackingNote().isBlank()) {
            String existingNote = order.getNote() != null ? order.getNote() + "\n" : "";
            order.setNote(existingNote + "[Đóng gói] " + request.getPackingNote());
        }

        order.setPackingConfirmed(true);
        orderRepository.save(order);

        log.info("Order #{} packing confirmed: {}x{}x{} cm, actual={}g, chargeable={}g",
            orderId, request.getLength(), request.getWidth(), request.getHeight(),
            request.getActualWeight(), order.getChargeableWeight());

        return OrderDetailResponse.from(order, statusService.getLabel(order.getStatus()),
            Map.of(), null, null, null, null, null, null, null,
            estimate.estimatedShippingFee(), estimate.shippingFeeDifference(), estimate.warnings());
    }

    // ================================================================
    // Private helpers
    // ================================================================

    private void guardVnPayPaidBeforeProcessing(Order order, OrderStatus newStatus) {
        boolean processingStatus = newStatus == OrderStatus.CONFIRMED
            || newStatus == OrderStatus.SHIPPING
            || newStatus == OrderStatus.DELIVERED
            || newStatus == OrderStatus.COMPLETED;
        if (processingStatus
            && order.getPaymentMethod() == PaymentMethod.VNPAY
            && order.getPaymentStatus() != OrderPaymentStatus.PAID) {
            throw new BusinessException(ErrorCode.ORDER_PAYMENT_NOT_PAID, HttpStatus.BAD_REQUEST,
                "Đơn VNPay chưa thanh toán thành công, không thể xử lý đơn.");
        }
    }

    private BigDecimal calculateUnitPrice(Product product, ProductVariant variant) {
        BigDecimal basePrice = Boolean.TRUE.equals(product.getIsSale()) && product.getSalePrice() != null
            ? product.getSalePrice()
            : product.getBasePrice();

        if (variant.getPriceAdjustment() != null) {
            basePrice = basePrice.add(variant.getPriceAdjustment());
        }
        return basePrice;
    }

    private Map<String, Object> buildAddressSnapshot(Address address) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("fullName", address.getFullName());
        snapshot.put("phone", address.getPhone());
        snapshot.put("province", address.getProvince());
        snapshot.put("district", address.getDistrict());
        snapshot.put("ward", address.getWard());
        snapshot.put("street", address.getStreet());
        snapshot.put("fullAddress", address.getStreet() + ", " + address.getWard()
            + ", " + address.getDistrict() + ", " + address.getProvince());
        return snapshot;
    }

    /** Hoàn stock cho tất cả items trong đơn hàng. */
    private void restoreStock(Order order) {
        for (OrderItem item : order.getItems()) {
            if (item.getVariant() != null) {
                ProductVariant variant = item.getVariant();
                variant.setStockQuantity(variant.getStockQuantity() + item.getQuantity());
                variantRepository.save(variant);
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
    private PageResponse<OrderSummaryResponse> buildPageResponse(Page<Order> orders) {
        var content = orders.getContent().stream()
            .map(o -> OrderSummaryResponse.from(o, statusService.getLabel(o.getStatus())))
            .toList();

        return PageResponse.from(content, orders);
    }
}
