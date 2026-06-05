package com.fashionshop.backend.module.order;

import com.fashionshop.backend.common.PageResponse;
import com.fashionshop.backend.common.enums.OrderStatus;
import com.fashionshop.backend.module.order.dto.request.CancelOrderRequest;
import com.fashionshop.backend.module.order.dto.request.ConfirmPackingRequest;
import com.fashionshop.backend.module.order.dto.request.CreateOrderRequest;
import com.fashionshop.backend.module.order.dto.request.UpdateOrderStatusRequest;
import com.fashionshop.backend.module.order.dto.response.CreateOrderResponse;
import com.fashionshop.backend.module.order.dto.response.OrderDetailResponse;
import com.fashionshop.backend.module.order.dto.response.OrderStatsResponse;
import com.fashionshop.backend.module.order.dto.response.OrderSummaryResponse;
import com.fashionshop.backend.module.order.dto.response.StaffShippingPreviewResponse;

public interface OrderService {

    // ====== Customer ======
    CreateOrderResponse createOrder(Long userId, CreateOrderRequest request, String ipAddress);
    PageResponse<OrderSummaryResponse> getMyOrders(Long userId, OrderStatus status, String keyword, int page, int size);
    OrderDetailResponse getMyOrderById(Long userId, Long orderId);
    void cancelOrder(Long userId, Long orderId, CancelOrderRequest request);

    // ====== Staff / Admin ======
    PageResponse<OrderSummaryResponse> getAllOrders(OrderStatus status, String keyword, Long categoryId, int page, int size);
    OrderStatsResponse getOrderStats();
    OrderDetailResponse getOrderById(Long orderId);
    OrderDetailResponse updateOrderStatus(Long orderId, UpdateOrderStatusRequest request);
    void staffCancelOrder(Long orderId, CancelOrderRequest request);
    StaffShippingPreviewResponse previewActualShippingFee(Long orderId, ConfirmPackingRequest request);
    OrderDetailResponse confirmPacking(Long orderId, ConfirmPackingRequest request);
    void confirmCompleted(Long orderId);
}
