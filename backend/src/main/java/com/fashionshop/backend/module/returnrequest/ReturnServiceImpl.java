package com.fashionshop.backend.module.returnrequest;

import com.fashionshop.backend.common.PageResponse;
import com.fashionshop.backend.common.enums.OrderPaymentStatus;
import com.fashionshop.backend.common.enums.OrderStatus;
import com.fashionshop.backend.common.enums.ReturnStatus;
import com.fashionshop.backend.domain.Order;
import com.fashionshop.backend.domain.OrderItem;
import com.fashionshop.backend.domain.ReturnItem;
import com.fashionshop.backend.domain.ReturnRequest;
import com.fashionshop.backend.domain.User;
import com.fashionshop.backend.domain.repository.OrderRepository;
import com.fashionshop.backend.domain.repository.ProductVariantRepository;
import com.fashionshop.backend.domain.repository.ReturnItemRepository;
import com.fashionshop.backend.domain.repository.ReturnRequestRepository;
import com.fashionshop.backend.domain.repository.UserRepository;
import com.fashionshop.backend.exception.BusinessException;
import com.fashionshop.backend.exception.ErrorCode;
import com.fashionshop.backend.module.returnrequest.dto.request.CreateReturnRequest;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReturnServiceImpl implements ReturnService {

    private final ReturnRequestRepository returnRepository;
    private final ReturnItemRepository returnItemRepository;
    private final OrderRepository orderRepository;
    private final ProductVariantRepository variantRepository;
    private final UserRepository userRepository;
    private final ReturnStatusService returnStatusService;
    private final StorageService storageService;

    @Override
    @Transactional
    public ReturnResponse createReturn(Long userId, CreateReturnRequest request) {
        if (request == null || request.getOrderId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST);
        }
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new BusinessException(ErrorCode.RETURN_ITEM_REQUIRED, HttpStatus.BAD_REQUEST);
        }

        Order order = orderRepository.findByIdAndUserId(request.getOrderId(), userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND, HttpStatus.NOT_FOUND));

        if (order.getStatus() != OrderStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.RETURN_NOT_ELIGIBLE, HttpStatus.BAD_REQUEST);
        }
        if (order.getDeliveredAt() == null || order.getDeliveredAt().plusDays(7).isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.RETURN_WINDOW_EXPIRED, HttpStatus.BAD_REQUEST);
        }
        if (order.getPaymentStatus() == OrderPaymentStatus.REFUNDED) {
            throw new BusinessException(ErrorCode.RETURN_NOT_ELIGIBLE, HttpStatus.BAD_REQUEST,
                "Don hang da hoan tien");
        }
        if (returnRepository.existsByOrderId(order.getId())) {
            throw new BusinessException(ErrorCode.RETURN_ALREADY_EXISTS, HttpStatus.CONFLICT);
        }

        List<String> evidenceImages = request.getEvidenceImages() != null
            ? new ArrayList<>(request.getEvidenceImages())
            : new ArrayList<>();

        ReturnRequest returnRequest = ReturnRequest.builder()
            .order(order)
            .user(order.getUser())
            .reason(request.getReason().trim())
            .status(ReturnStatus.REQUESTED)
            .refundAmount(BigDecimal.ZERO)
            .evidenceImages(evidenceImages)
            .items(new ArrayList<>())
            .build();

        BigDecimal totalRefund = BigDecimal.ZERO;
        for (CreateReturnRequest.ReturnItemRequest itemReq : request.getItems()) {
            OrderItem matchedItem = order.getItems().stream()
                .filter(oi -> oi.getId().equals(itemReq.getOrderItemId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.RETURN_ITEM_NOT_IN_ORDER, HttpStatus.BAD_REQUEST));

            if (itemReq.getQuantity() <= 0 || itemReq.getQuantity() > matchedItem.getQuantity()) {
                throw new BusinessException(ErrorCode.RETURN_QUANTITY_EXCEEDED, HttpStatus.BAD_REQUEST);
            }

            BigDecimal subtotal = matchedItem.getUnitPrice().multiply(BigDecimal.valueOf(itemReq.getQuantity()));
            totalRefund = totalRefund.add(subtotal);

            ReturnItem returnItem = ReturnItem.builder()
                .returnRequest(returnRequest)
                .orderItem(matchedItem)
                .productId(matchedItem.getProductId())
                .variantId(matchedItem.getVariant() != null ? matchedItem.getVariant().getId() : null)
                .productName(matchedItem.getProductName())
                .colorName(matchedItem.getColorName())
                .size(matchedItem.getSize())
                .imageUrl(matchedItem.getImageUrl())
                .quantity(itemReq.getQuantity())
                .unitPrice(matchedItem.getUnitPrice())
                .subtotal(subtotal)
                .build();

            returnRequest.getItems().add(returnItem);
        }

        returnRequest.setRefundAmount(totalRefund);
        returnRepository.save(returnRequest);

        log.info("Return created userId={}, orderId={}, returnId={}, refundAmount={}", userId, order.getId(), returnRequest.getId(), totalRefund);
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

        log.info("Return rejected returnId={}, staffId={}", returnId, staffId);
    }

    @Override
    @Transactional
    public void markReceived(Long adminId, Long returnId) {
        ReturnRequest r = findReturnOrThrow(returnId);
        returnStatusService.validateTransition(r.getStatus(), ReturnStatus.RECEIVED);

        increaseStockFromReturnItems(r);

        r.setStatus(ReturnStatus.RECEIVED);
        r.setProcessedBy(findUserOrThrow(adminId));
        r.setReceivedAt(LocalDateTime.now());
        log.info("Return received returnId={}, adminId={}", returnId, adminId);
    }

    @Override
    @Transactional
    public void completeReturn(Long adminId, Long returnId, String note) {
        ReturnRequest r = findReturnOrThrow(returnId);
        returnStatusService.validateTransition(r.getStatus(), ReturnStatus.COMPLETED);

        r.setStatus(ReturnStatus.COMPLETED);
        r.setProcessedBy(findUserOrThrow(adminId));
        r.setRefundedAt(LocalDateTime.now());
        if (note != null && !note.isBlank()) {
            r.setAdminNote(note.trim());
        }

        Order order = r.getOrder();
        order.setPaymentStatus(OrderPaymentStatus.REFUNDED);

        log.info("Return completed returnId={}, adminId={}", returnId, adminId);
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
            returnRepository.findTop5ByStatusInOrderByCreatedAtAsc(List.of(ReturnStatus.REQUESTED, ReturnStatus.APPROVED, ReturnStatus.RECEIVED)),
            Alerts.builder()
                .pendingOver24h(returnRepository.countByStatusAndCreatedAtBefore(ReturnStatus.REQUESTED, over24h))
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
            returnRepository.findTop5ByStatusOrderByCreatedAtAsc(ReturnStatus.REQUESTED),
            Alerts.builder()
                .pendingOver24h(returnRepository.countByStatusAndCreatedAtBefore(ReturnStatus.REQUESTED, over24h))
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

    private void increaseStockFromReturnItems(ReturnRequest returnRequest) {
        List<ReturnItem> items = returnItemRepository.findByReturnRequestId(returnRequest.getId());
        if (items == null || items.isEmpty()) {
            throw new BusinessException(ErrorCode.RETURN_ITEM_REQUIRED, HttpStatus.BAD_REQUEST);
        }

        for (ReturnItem item : items) {
            Long variantId = item.getVariantId();
            if (variantId == null && item.getOrderItem() != null && item.getOrderItem().getVariant() != null) {
                variantId = item.getOrderItem().getVariant().getId();
            }
            if (variantId == null) {
                throw new BusinessException(ErrorCode.VARIANT_NOT_FOUND, HttpStatus.BAD_REQUEST);
            }
            int updated = variantRepository.increaseStock(variantId, item.getQuantity());
            if (updated == 0) {
                throw new BusinessException(ErrorCode.VARIANT_NOT_FOUND, HttpStatus.BAD_REQUEST);
            }
        }
    }

    private ReturnDashboardResponse buildDashboard(LocalDateTime monthStart, List<ReturnRequest> queue,
                                                   Alerts alerts, LocalDateTime tomorrowStart) {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        List<ReturnStatus> processingStatuses = List.of(ReturnStatus.APPROVED, ReturnStatus.RECEIVED);
        Summary summary = Summary.builder()
            .pending(returnRepository.countByStatus(ReturnStatus.REQUESTED))
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

    @Override
    public String uploadEvidenceImage(org.springframework.web.multipart.MultipartFile file) {
        UploadResult result = storageService.uploadImage(file, "fashion-shop/returns");
        return result.url();
    }
}
