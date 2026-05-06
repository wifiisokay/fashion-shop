package com.fashionshop.backend.module.returnrequest;

import com.fashionshop.backend.common.PageResponse;
import com.fashionshop.backend.common.enums.OrderStatus;
import com.fashionshop.backend.common.enums.PaymentMethod;
import com.fashionshop.backend.common.enums.PaymentStatus;
import com.fashionshop.backend.common.enums.ReturnStatus;
import com.fashionshop.backend.domain.Order;
import com.fashionshop.backend.domain.ReturnRequest;
import com.fashionshop.backend.domain.User;
import com.fashionshop.backend.domain.repository.OrderRepository;
import com.fashionshop.backend.domain.repository.ReturnRequestRepository;
import com.fashionshop.backend.domain.repository.UserRepository;
import com.fashionshop.backend.exception.BusinessException;
import com.fashionshop.backend.exception.ErrorCode;
import com.fashionshop.backend.module.payment.PaymentService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReturnServiceImpl implements ReturnService {

    private final ReturnRequestRepository returnRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ReturnStatusService returnStatusService;
    private final StorageService storageService;
    private final PaymentService paymentService;

    private static final Set<ReturnStatus> ACTIVE_STATUSES = Set.of(
        ReturnStatus.PENDING, ReturnStatus.APPROVED, ReturnStatus.RECEIVED
    );

    // ================================================================
    // Customer — Tạo yêu cầu trả hàng
    // ================================================================

    @Override
    @Transactional
    public ReturnResponse createReturn(Long userId, Long orderId, String reason, List<MultipartFile> images) {
        // 1. Tìm order, verify ownership
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND, HttpStatus.NOT_FOUND));

        // 2. Verify status = DELIVERED hoặc COMPLETED
        if (order.getStatus() != OrderStatus.DELIVERED && order.getStatus() != OrderStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.RETURN_NOT_ELIGIBLE, HttpStatus.BAD_REQUEST);
        }

        // 3. Verify 7-day window
        if (order.getDeliveredAt() == null || order.getDeliveredAt().plusDays(7).isBefore(java.time.LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.RETURN_WINDOW_EXPIRED, HttpStatus.BAD_REQUEST);
        }

        // 4. Kiểm tra không có return active
        if (returnRepository.existsByOrderIdAndStatusIn(orderId, ACTIVE_STATUSES)) {
            throw new BusinessException(ErrorCode.RETURN_ALREADY_EXISTS, HttpStatus.CONFLICT);
        }

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, HttpStatus.NOT_FOUND));

        // 5. Tạo return trước để có ID cho Cloudinary folder
        ReturnRequest returnRequest = ReturnRequest.builder()
            .order(order)
            .user(user)
            .reason(reason)
            .status(ReturnStatus.PENDING)
            .build();
        returnRepository.save(returnRequest);

        // 6. Upload ảnh minh chứng (nếu có)
        if (images != null && !images.isEmpty()) {
            List<String> imageUrls = new ArrayList<>();
            String folder = "fashion-shop/returns/" + returnRequest.getId();
            for (MultipartFile image : images) {
                UploadResult result = storageService.uploadImage(image, folder);
                imageUrls.add(result.url());
            }
            returnRequest.setEvidenceImages(imageUrls);
        }

        // 7. Cập nhật order status → RETURN_REQUESTED
        order.setStatus(OrderStatus.RETURN_REQUESTED);
        orderRepository.save(order);

        log.info("Return created — userId={}, orderId={}, returnId={}", userId, orderId, returnRequest.getId());
        return ReturnResponse.from(returnRequest, returnStatusService.getLabel(returnRequest.getStatus()));
    }

    // ================================================================
    // Customer — Xem yêu cầu trả hàng
    // ================================================================

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

    // ================================================================
    // Staff / Admin — Danh sách và chi tiết
    // ================================================================

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
        ReturnRequest r = returnRepository.findById(returnId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RETURN_NOT_FOUND, HttpStatus.NOT_FOUND));
        return ReturnResponse.from(r, returnStatusService.getLabel(r.getStatus()));
    }

    // ================================================================
    // Staff — Duyệt / Từ chối
    // ================================================================

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

        // Cập nhật order → RETURNING
        r.getOrder().setStatus(OrderStatus.RETURNING);
        orderRepository.save(r.getOrder());

        log.info("Return approved — returnId={}, staffId={}", returnId, staffId);
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

        // Khôi phục order status về trạng thái trước (DELIVERED hoặc COMPLETED)
        // Vì order đang ở RETURN_REQUESTED, ta đưa về DELIVERED (hợp lý nhất)
        if (r.getOrder().getStatus() == OrderStatus.RETURN_REQUESTED) {
            r.getOrder().setStatus(OrderStatus.DELIVERED);
            orderRepository.save(r.getOrder());
        }

        log.info("Return rejected — returnId={}, staffId={}", returnId, staffId);
    }

    // ================================================================
    // Admin — Nhận hàng / Hoàn tất
    // ================================================================

    @Override
    @Transactional
    public void receiveReturn(Long adminId, Long returnId) {
        ReturnRequest r = findReturnOrThrow(returnId);
        returnStatusService.validateTransition(r.getStatus(), ReturnStatus.RECEIVED);

        r.setStatus(ReturnStatus.RECEIVED);
        r.setProcessedBy(findUserOrThrow(adminId));

        log.info("Return received — returnId={}, adminId={}", returnId, adminId);
    }

    @Override
    @Transactional
    public void completeReturn(Long adminId, Long returnId, BigDecimal refundAmount) {
        ReturnRequest r = findReturnOrThrow(returnId);
        returnStatusService.validateTransition(r.getStatus(), ReturnStatus.COMPLETED);

        // Validate refund amount
        if (refundAmount != null && refundAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCode.INVALID_REFUND_AMOUNT, HttpStatus.BAD_REQUEST);
        }

        r.setStatus(ReturnStatus.COMPLETED);
        r.setProcessedBy(findUserOrThrow(adminId));
        r.setRefundAmount(refundAmount);

        // Cập nhật order → RETURNED
        Order order = r.getOrder();
        order.setStatus(OrderStatus.RETURNED);
        orderRepository.save(order);

        // Trigger refund chỉ cho đơn VNPAY đã PAID
        if (order.getPaymentMethod() == PaymentMethod.VNPAY
            && order.getPayment() != null
            && order.getPayment().getStatus() == PaymentStatus.SUCCESS
            && refundAmount != null && refundAmount.compareTo(BigDecimal.ZERO) > 0) {
            try {
                paymentService.processRefund(order.getPayment().getId(), "Hoàn tiền trả hàng #" + returnId);
                log.info("Refund triggered — returnId={}, paymentId={}, amount={}",
                    returnId, order.getPayment().getId(), refundAmount);
            } catch (Exception e) {
                log.error("Refund failed — returnId={}, error={}", returnId, e.getMessage());
                // Không throw — return vẫn COMPLETED, admin xử lý refund thủ công nếu cần
            }
        }

        log.info("Return completed — returnId={}, adminId={}, refundAmount={}", returnId, adminId, refundAmount);
    }

    // ================================================================
    // Helpers
    // ================================================================

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
}
