package com.fashionshop.backend.module.returnrequest;

import com.fashionshop.backend.common.PageResponse;
import com.fashionshop.backend.common.enums.OrderPaymentStatus;
import com.fashionshop.backend.common.enums.OrderStatus;
import com.fashionshop.backend.common.enums.PaymentStatus;
import com.fashionshop.backend.common.enums.ReturnRequestType;
import com.fashionshop.backend.common.enums.ReturnStatus;
import com.fashionshop.backend.domain.Order;
import com.fashionshop.backend.domain.OrderItem;
import com.fashionshop.backend.domain.ReturnItem;
import com.fashionshop.backend.domain.ReturnRequest;
import com.fashionshop.backend.domain.User;
import com.fashionshop.backend.domain.repository.OrderRepository;
import com.fashionshop.backend.domain.repository.PaymentRepository;
import com.fashionshop.backend.domain.repository.ReturnItemRepository;
import com.fashionshop.backend.domain.repository.ReturnRequestRepository;
import com.fashionshop.backend.domain.repository.UserRepository;
import com.fashionshop.backend.exception.BusinessException;
import com.fashionshop.backend.exception.ErrorCode;
import com.fashionshop.backend.module.returnrequest.dto.request.CreateReturnItemRequest;
import com.fashionshop.backend.module.returnrequest.dto.response.ReturnDashboardResponse;
import com.fashionshop.backend.module.returnrequest.dto.response.ReturnDashboardResponse.Alerts;
import com.fashionshop.backend.module.returnrequest.dto.response.ReturnDashboardResponse.ChartPoint;
import com.fashionshop.backend.module.returnrequest.dto.response.ReturnDashboardResponse.Summary;
import com.fashionshop.backend.module.returnrequest.dto.response.ReturnDashboardResponse.TopReturnedProduct;
import com.fashionshop.backend.module.returnrequest.dto.response.ReturnResponse;
import com.fashionshop.backend.module.storage.StorageService;
import com.fashionshop.backend.module.storage.UploadResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReturnServiceImpl implements ReturnService {

    private final ReturnRequestRepository returnRepository;
    private final ReturnItemRepository returnItemRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final ReturnStatusService returnStatusService;
    private final StorageService storageService;

    private static final Set<ReturnStatus> ACTIVE_STATUSES = Set.of(
        ReturnStatus.PENDING, ReturnStatus.APPROVED, ReturnStatus.RECEIVED
    );
    private static final Set<ReturnStatus> QUANTITY_STATUSES = Set.of(
        ReturnStatus.PENDING, ReturnStatus.APPROVED, ReturnStatus.RECEIVED, ReturnStatus.COMPLETED
    );

    @Override
    @Transactional
    public ReturnResponse createReturn(Long userId, Long orderId, ReturnRequestType requestType, String reason,
                                       List<CreateReturnItemRequest> items, List<MultipartFile> images) {
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND, HttpStatus.NOT_FOUND));

        if (order.getStatus() != OrderStatus.DELIVERED && order.getStatus() != OrderStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.RETURN_NOT_ELIGIBLE, HttpStatus.BAD_REQUEST);
        }
        if (order.getDeliveredAt() == null || order.getDeliveredAt().plusDays(7).isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.RETURN_WINDOW_EXPIRED, HttpStatus.BAD_REQUEST);
        }
        if (returnRepository.existsByOrderIdAndStatusIn(orderId, ACTIVE_STATUSES)) {
            throw new BusinessException(ErrorCode.RETURN_ALREADY_EXISTS, HttpStatus.CONFLICT);
        }

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, HttpStatus.NOT_FOUND));

        List<CreateReturnItemRequest> requestedItems = normalizeRequestedItems(order, items);
        validateReturnItems(order, requestedItems);

        ReturnRequest returnRequest = ReturnRequest.builder()
            .order(order)
            .user(user)
            .reason(buildReason(requestType != null ? requestType : ReturnRequestType.RETURN, reason))
            .status(ReturnStatus.PENDING)
            .previousOrderStatus(order.getStatus().name())
            .build();
        requestedItems.stream()
            .map(item -> buildReturnItem(returnRequest, order, item))
            .forEach(returnRequest.getItems()::add);

        returnRepository.save(returnRequest);

        if (images != null && !images.isEmpty()) {
            List<String> imageUrls = new ArrayList<>();
            String folder = "fashion-shop/returns/" + returnRequest.getId();
            for (MultipartFile image : images) {
                UploadResult result = storageService.uploadImage(image, folder);
                imageUrls.add(result.url());
            }
            returnRequest.setEvidenceImages(imageUrls);
        }

        order.setStatus(OrderStatus.RETURN_REQUESTED);
        orderRepository.save(order);

        log.info("Return created userId={}, orderId={}, returnId={}", userId, orderId, returnRequest.getId());
        return ReturnResponse.from(returnRequest, returnStatusService.getLabel(returnRequest.getStatus()));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReturnResponse> getMyReturns(Long userId, int page, int size) {
        Page<ReturnRequest> returns = returnRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));
        return buildPageResponse(returns);
    }

    @Override
    @Transactional(readOnly = true)
    public ReturnResponse getMyReturnById(Long userId, Long returnId) {
        ReturnRequest r = returnRepository.findByIdAndUserId(returnId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RETURN_NOT_FOUND, HttpStatus.NOT_FOUND));
        return ReturnResponse.from(r, returnStatusService.getLabel(r.getStatus()));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReturnResponse> getAllReturns(ReturnStatus status, int page, int size) {
        Page<ReturnRequest> returns = (status != null)
            ? returnRepository.findByStatusOrderByCreatedAtDesc(status, PageRequest.of(page, size))
            : returnRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));
        return buildPageResponse(returns);
    }

    @Override
    @Transactional(readOnly = true)
    public ReturnResponse getReturnById(Long returnId) {
        ReturnRequest r = findReturnOrThrow(returnId);
        return ReturnResponse.from(r, returnStatusService.getLabel(r.getStatus()));
    }

    @Override
    @Transactional
    public void approveReturn(Long staffId, Long returnId, String note) {
        ReturnRequest r = findReturnOrThrow(returnId);
        returnStatusService.validateTransition(r.getStatus(), ReturnStatus.APPROVED);

        r.setStatus(ReturnStatus.APPROVED);
        r.setProcessedBy(findUserOrThrow(staffId));
        if (note != null && !note.isBlank()) {
            r.setAdminNote(note);
        }

        r.getOrder().setStatus(OrderStatus.RETURNING);
        orderRepository.save(r.getOrder());
        log.info("Return approved returnId={}, staffId={}", returnId, staffId);
    }

    @Override
    @Transactional
    public void rejectReturn(Long staffId, Long returnId, String note) {
        if (note == null || note.isBlank()) {
            throw new BusinessException(ErrorCode.RETURN_REJECT_NOTE_REQUIRED, HttpStatus.BAD_REQUEST);
        }

        ReturnRequest r = findReturnOrThrow(returnId);
        returnStatusService.validateTransition(r.getStatus(), ReturnStatus.REJECTED);

        r.setStatus(ReturnStatus.REJECTED);
        r.setProcessedBy(findUserOrThrow(staffId));
        r.setAdminNote(note);

        if (r.getOrder().getStatus() == OrderStatus.RETURN_REQUESTED) {
            r.getOrder().setStatus(resolvePreviousOrderStatus(r));
            orderRepository.save(r.getOrder());
        }

        log.info("Return rejected returnId={}, staffId={}", returnId, staffId);
    }

    @Override
    @Transactional
    public void receiveReturn(Long adminId, Long returnId) {
        ReturnRequest r = findReturnOrThrow(returnId);
        returnStatusService.validateTransition(r.getStatus(), ReturnStatus.RECEIVED);

        r.setStatus(ReturnStatus.RECEIVED);
        r.setProcessedBy(findUserOrThrow(adminId));
        log.info("Return received returnId={}, adminId={}", returnId, adminId);
    }

    @Override
    @Transactional
    public void completeReturn(Long adminId, Long returnId, BigDecimal refundAmount, String note) {
        ReturnRequest r = findReturnOrThrow(returnId);
        returnStatusService.validateTransition(r.getStatus(), ReturnStatus.COMPLETED);

        if (refundAmount != null && refundAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCode.INVALID_REFUND_AMOUNT, HttpStatus.BAD_REQUEST);
        }
        ReturnRequestType requestType = resolveRequestType(r.getReason());
        BigDecimal finalRefundAmount = refundAmount;
        if (requestType == ReturnRequestType.EXCHANGE) {
            if (refundAmount != null && refundAmount.compareTo(BigDecimal.ZERO) > 0) {
                throw new BusinessException(ErrorCode.RETURN_EXCHANGE_REFUND_NOT_ALLOWED, HttpStatus.BAD_REQUEST);
            }
            if (note == null || note.isBlank()) {
                throw new BusinessException(ErrorCode.RETURN_EXCHANGE_NOTE_REQUIRED, HttpStatus.BAD_REQUEST);
            }
            finalRefundAmount = BigDecimal.ZERO;
        }

        r.setStatus(ReturnStatus.COMPLETED);
        r.setProcessedBy(findUserOrThrow(adminId));
        r.setRefundAmount(finalRefundAmount);
        if (note != null && !note.isBlank()) {
            r.setAdminNote(note.trim());
        }

        Order order = r.getOrder();
        order.setStatus(OrderStatus.RETURNED);
        if (requestType != ReturnRequestType.EXCHANGE) {
            syncManualRefundIfFullReturn(order, finalRefundAmount, r.getAdminNote());
        }
        orderRepository.save(order);

        log.info("Return completed manually returnId={}, adminId={}, type={}, refundAmount={}",
            returnId, adminId, requestType, finalRefundAmount);
    }

    @Override
    @Transactional(readOnly = true)
    public ReturnDashboardResponse getAdminDashboard() {
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime tomorrowStart = LocalDate.now().plusDays(1).atStartOfDay();
        LocalDateTime over24h = LocalDateTime.now().minusHours(24);
        LocalDateTime over3Days = LocalDateTime.now().minusDays(3);

        return buildDashboard(
            monthStart,
            returnRepository.findTop5ByStatusInOrderByCreatedAtAsc(List.of(ReturnStatus.PENDING, ReturnStatus.APPROVED, ReturnStatus.RECEIVED)),
            Alerts.builder()
                .pendingOver24h(returnRepository.countByStatusAndCreatedAtBefore(ReturnStatus.PENDING, over24h))
                .approvedOver3Days(returnRepository.countByStatusAndCreatedAtBefore(ReturnStatus.APPROVED, over3Days))
                .receivedNotCompleted(returnRepository.countByStatus(ReturnStatus.RECEIVED))
                .build(),
            tomorrowStart
        );
    }

    @Override
    @Transactional(readOnly = true)
    public ReturnDashboardResponse getStaffDashboard() {
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime tomorrowStart = LocalDate.now().plusDays(1).atStartOfDay();
        LocalDateTime over24h = LocalDateTime.now().minusHours(24);

        return buildDashboard(
            monthStart,
            returnRepository.findTop5ByStatusOrderByCreatedAtAsc(ReturnStatus.PENDING),
            Alerts.builder()
                .pendingOver24h(returnRepository.countByStatusAndCreatedAtBefore(ReturnStatus.PENDING, over24h))
                .approvedOver3Days(0)
                .receivedNotCompleted(0)
                .build(),
            tomorrowStart
        );
    }

    private ReturnRequest findReturnOrThrow(Long returnId) {
        return returnRepository.findById(returnId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RETURN_NOT_FOUND, HttpStatus.NOT_FOUND));
    }

    private User findUserOrThrow(Long userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, HttpStatus.NOT_FOUND));
    }

    private PageResponse<ReturnResponse> buildPageResponse(Page<ReturnRequest> page) {
        List<ReturnResponse> content = page.getContent().stream()
            .map(r -> ReturnResponse.from(r, returnStatusService.getLabel(r.getStatus())))
            .toList();
        return PageResponse.from(content, page);
    }

    private List<CreateReturnItemRequest> normalizeRequestedItems(Order order, List<CreateReturnItemRequest> requestedItems) {
        if (requestedItems != null && !requestedItems.isEmpty()) {
            return requestedItems;
        }
        if (order.getItems() == null || order.getItems().isEmpty()) {
            throw new BusinessException(ErrorCode.RETURN_ITEM_REQUIRED, HttpStatus.BAD_REQUEST);
        }
        return order.getItems().stream()
            .map(item -> {
                CreateReturnItemRequest request = new CreateReturnItemRequest();
                request.setOrderItemId(item.getId());
                request.setQuantity(item.getQuantity());
                return request;
            })
            .toList();
    }

    private void validateReturnItems(Order order, List<CreateReturnItemRequest> requestedItems) {
        if (requestedItems == null || requestedItems.isEmpty()) {
            throw new BusinessException(ErrorCode.RETURN_ITEM_REQUIRED, HttpStatus.BAD_REQUEST);
        }
        Map<Long, OrderItem> orderItems = order.getItems().stream()
            .collect(Collectors.toMap(OrderItem::getId, Function.identity()));

        for (CreateReturnItemRequest request : requestedItems) {
            if (request.getOrderItemId() == null || request.getQuantity() == null || request.getQuantity() < 1) {
                throw new BusinessException(ErrorCode.INVALID_QUANTITY, HttpStatus.BAD_REQUEST);
            }
            OrderItem orderItem = orderItems.get(request.getOrderItemId());
            if (orderItem == null) {
                throw new BusinessException(ErrorCode.RETURN_ITEM_NOT_IN_ORDER, HttpStatus.BAD_REQUEST);
            }
            long alreadyReturned = defaultLong(returnItemRepository.sumQuantityByOrderItemAndStatuses(orderItem.getId(), QUANTITY_STATUSES));
            long available = orderItem.getQuantity() - alreadyReturned;
            if (request.getQuantity() > available) {
                throw new BusinessException(ErrorCode.RETURN_QUANTITY_EXCEEDED, HttpStatus.BAD_REQUEST);
            }
        }
    }

    private ReturnItem buildReturnItem(ReturnRequest returnRequest, Order order, CreateReturnItemRequest request) {
        OrderItem orderItem = order.getItems().stream()
            .filter(item -> item.getId().equals(request.getOrderItemId()))
            .findFirst()
            .orElseThrow(() -> new BusinessException(ErrorCode.RETURN_ITEM_NOT_IN_ORDER, HttpStatus.BAD_REQUEST));
        BigDecimal unitPrice = orderItem.getUnitPrice();
        return ReturnItem.builder()
            .returnRequest(returnRequest)
            .orderItem(orderItem)
            .productId(orderItem.getProductId())
            .variantId(orderItem.getVariant() != null ? orderItem.getVariant().getId() : null)
            .productName(orderItem.getProductName())
            .colorName(orderItem.getColorName())
            .size(orderItem.getSize())
            .imageUrl(orderItem.getImageUrl())
            .quantity(request.getQuantity())
            .unitPrice(unitPrice)
            .subtotal(unitPrice.multiply(BigDecimal.valueOf(request.getQuantity())))
            .build();
    }

    private String buildReason(ReturnRequestType requestType, String reason) {
        String trimmed = reason != null ? reason.trim() : "";
        return toReasonPrefix(requestType) + (trimmed.isBlank() ? "" : " " + trimmed);
    }

    private String toReasonPrefix(ReturnRequestType requestType) {
        return switch (requestType) {
            case EXCHANGE -> "[ĐỔI HÀNG]";
            case COMPLAINT -> "[KHIẾU NẠI]";
            case RETURN -> "[TRẢ HÀNG]";
        };
    }

    private ReturnRequestType resolveRequestType(String reason) {
        if (reason != null) {
            if (reason.startsWith("[ĐỔI HÀNG]")) {
                return ReturnRequestType.EXCHANGE;
            }
            if (reason.startsWith("[KHIẾU NẠI]")) {
                return ReturnRequestType.COMPLAINT;
            }
        }
        return ReturnRequestType.RETURN;
    }

    private OrderStatus resolvePreviousOrderStatus(ReturnRequest request) {
        try {
            if (request.getPreviousOrderStatus() != null && !request.getPreviousOrderStatus().isBlank()) {
                return OrderStatus.valueOf(request.getPreviousOrderStatus());
            }
        } catch (IllegalArgumentException ignored) {
            log.warn("Invalid previousOrderStatus returnId={}, value={}", request.getId(), request.getPreviousOrderStatus());
        }
        return OrderStatus.DELIVERED;
    }

    private void syncManualRefundIfFullReturn(Order order, BigDecimal refundAmount, String note) {
        if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        if (!isFullReturn(order)) {
            log.info("Manual refund recorded as partial return orderId={}, refundAmount={}", order.getId(), refundAmount);
            return;
        }
        paymentRepository.findTopByOrderIdAndStatusOrderByCreatedAtDesc(order.getId(), PaymentStatus.SUCCESS)
            .ifPresent(payment -> {
                payment.setStatus(PaymentStatus.REFUNDED);
                payment.setRefundedAt(LocalDateTime.now());
                payment.setRefundReason(note);
                order.setPaymentStatus(OrderPaymentStatus.REFUNDED);
            });
    }

    private boolean isFullReturn(Order order) {
        if (order.getItems() == null || order.getItems().isEmpty()) {
            return false;
        }
        for (OrderItem orderItem : order.getItems()) {
            long completedReturnedQty = defaultLong(returnItemRepository.sumCompletedQuantityByOrderItemId(orderItem.getId()));
            if (completedReturnedQty < orderItem.getQuantity()) {
                return false;
            }
        }
        return true;
    }

    private ReturnDashboardResponse buildDashboard(LocalDateTime monthStart, List<ReturnRequest> queue,
                                                   Alerts alerts, LocalDateTime tomorrowStart) {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        List<ReturnStatus> processingStatuses = List.of(ReturnStatus.APPROVED, ReturnStatus.RECEIVED);
        Summary summary = Summary.builder()
            .pending(returnRepository.countByStatus(ReturnStatus.PENDING))
            .processing(returnRepository.countByStatusIn(processingStatuses))
            .rejectedThisMonth(returnRepository.countByStatusAndUpdatedAtAfter(ReturnStatus.REJECTED, monthStart))
            .completedThisMonth(returnRepository.countByStatusAndUpdatedAtAfter(ReturnStatus.COMPLETED, monthStart))
            .processedRefundAmountThisMonth(returnRepository.sumCompletedRefundAmountSince(monthStart))
            .returnItemQuantityThisMonth(defaultLong(returnItemRepository.sumCompletedQuantitySince(monthStart)))
            .returnItemValueThisMonth(returnItemRepository.sumCompletedValueSince(monthStart))
            .pendingOver24h(alerts.getPendingOver24h())
            .approvedToday(returnRepository.countByStatusAndUpdatedAtBetween(ReturnStatus.APPROVED, todayStart, tomorrowStart))
            .rejectedToday(returnRepository.countByStatusAndUpdatedAtBetween(ReturnStatus.REJECTED, todayStart, tomorrowStart))
            .build();

        return ReturnDashboardResponse.builder()
            .summary(summary)
            .alerts(alerts)
            .statusChart(toChart(returnRepository.getStatusDistribution()))
            .typeChart(toChart(returnRepository.getTypeDistribution()))
            .queue(queue.stream()
                .sorted(Comparator.comparing(ReturnRequest::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(r -> ReturnResponse.from(r, returnStatusService.getLabel(r.getStatus())))
                .toList())
            .topReturnedProducts(returnItemRepository.findTopReturnedProducts(monthStart).stream()
                .map(this::toTopReturnedProduct)
                .toList())
            .build();
    }

    private List<ChartPoint> toChart(Collection<Object[]> rows) {
        return rows.stream()
            .map(row -> ChartPoint.builder()
                .label(row[0] != null ? row[0].toString() : "UNKNOWN")
                .value(((Number) row[1]).longValue())
                .build())
            .toList();
    }

    private TopReturnedProduct toTopReturnedProduct(Object[] row) {
        return TopReturnedProduct.builder()
            .productId(row[0] != null ? ((Number) row[0]).longValue() : null)
            .productName(row[1] != null ? row[1].toString() : null)
            .returnedQuantity(row[2] != null ? ((Number) row[2]).longValue() : 0)
            .returnedValue(row[3] != null ? (BigDecimal) row[3] : BigDecimal.ZERO)
            .requestCount(row[4] != null ? ((Number) row[4]).longValue() : 0)
            .build();
    }

    private long defaultLong(Long value) {
        return value != null ? value : 0;
    }
}
