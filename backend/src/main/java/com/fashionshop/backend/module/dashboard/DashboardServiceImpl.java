package com.fashionshop.backend.module.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fashionshop.backend.common.enums.OrderStatus;
import com.fashionshop.backend.common.enums.ProductStatus;
import com.fashionshop.backend.common.enums.ReturnStatus;
import com.fashionshop.backend.common.enums.Role;
import com.fashionshop.backend.domain.repository.OrderRepository;
import com.fashionshop.backend.domain.repository.ProductRepository;
import com.fashionshop.backend.domain.repository.ReturnRequestRepository;
import com.fashionshop.backend.domain.repository.UserRepository;
import com.fashionshop.backend.exception.BusinessException;
import com.fashionshop.backend.exception.ErrorCode;
import com.fashionshop.backend.module.dashboard.dto.AdminDashboardResponse;
import com.fashionshop.backend.module.dashboard.dto.AdminDashboardResponse.DailyRevenuePoint;
import com.fashionshop.backend.module.dashboard.dto.AdminDashboardResponse.DashboardCharts;
import com.fashionshop.backend.module.dashboard.dto.AdminDashboardResponse.DashboardDateRange;
import com.fashionshop.backend.module.dashboard.dto.AdminDashboardResponse.OrderSummary;
import com.fashionshop.backend.module.dashboard.dto.AdminDashboardResponse.OverviewSummary;
import com.fashionshop.backend.module.dashboard.dto.AdminDashboardResponse.ProductAnalytics;
import com.fashionshop.backend.module.dashboard.dto.AdminDashboardResponse.ProductMetric;
import com.fashionshop.backend.module.dashboard.dto.AdminDashboardResponse.ReturnQueueItem;
import com.fashionshop.backend.module.dashboard.dto.AdminDashboardResponse.ReturnSummary;
import com.fashionshop.backend.module.dashboard.dto.AdminDashboardResponse.RevenueSummary;
import com.fashionshop.backend.module.dashboard.dto.DashboardStatsResponse;
import com.fashionshop.backend.module.dashboard.dto.DashboardStatsResponse.OrderStatusCount;
import com.fashionshop.backend.module.dashboard.dto.DashboardStatsResponse.PackingStats;
import com.fashionshop.backend.module.dashboard.dto.DashboardStatsResponse.RevenuePoint;
import com.fashionshop.backend.module.dashboard.dto.DashboardStatsResponse.ReturnStats;
import com.fashionshop.backend.module.dashboard.dto.DashboardStatsResponse.Totals;
import com.fashionshop.backend.module.product.StockAlertService;
import com.fashionshop.backend.module.product.dto.response.StockAlertResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final ReturnRequestRepository returnRequestRepository;
    private final NamedParameterJdbcTemplate jdbc;
    private final StockAlertService stockAlertService;

    private static final String FINALIZED_ORDER_WHERE = """
        WHERE o.status IN ('COMPLETED', 'RETURNED')
          AND o.payment_status IN ('PAID', 'REFUNDED')
          AND o.created_at >= :fromDate
          AND o.created_at < :toDateExclusive
          AND NOT EXISTS (
              SELECT 1
              FROM returns r
              WHERE r.order_id = o.id
                AND r.status IN ('PENDING', 'APPROVED', 'RECEIVED')
          )
        """;

    @Override
    @Transactional(readOnly = true)
    public DashboardStatsResponse getDashboardStats() {
        // 1. Totals
        BigDecimal totalRevenue = orderRepository.getTotalRevenue();
        if (totalRevenue == null) totalRevenue = BigDecimal.ZERO;
        
        long totalOrders = orderRepository.count();
        long totalCustomers = userRepository.countByRole(Role.CUSTOMER);
        long totalProducts = productRepository.countByStatus(ProductStatus.ACTIVE);

        Totals totals = Totals.builder()
            .totalRevenue(totalRevenue)
            .totalOrders(totalOrders)
            .totalCustomers(totalCustomers)
            .totalProducts(totalProducts)
            .build();

        // 2. Revenue Trend (last 30 days)
        LocalDateTime startDate = LocalDateTime.now().minusDays(30);
        List<Object[]> trendRaw = orderRepository.getRevenueTrend(startDate);
        
        List<RevenuePoint> revenueTrend = trendRaw.stream().map(row -> {
            // Function DATE returns java.sql.Date usually
            String dateStr = row[0].toString(); 
            BigDecimal revenue = (BigDecimal) row[1];
            return new RevenuePoint(dateStr, revenue);
        }).collect(Collectors.toList());

        // 3. Order Status Distribution
        List<Object[]> statusRaw = orderRepository.getOrderStatusDistribution();
        List<OrderStatusCount> statusDistribution = statusRaw.stream().map(row -> {
            String status = row[0].toString();
            long count = ((Number) row[1]).longValue();
            return new OrderStatusCount(status, count);
        }).collect(Collectors.toList());

        // 4. Packing / Shipping fee stats
        long confirmedNotPacked = orderRepository.countByStatusAndPackingConfirmed(OrderStatus.CONFIRMED, false);
        long confirmedPacked = orderRepository.countByStatusAndPackingConfirmed(OrderStatus.CONFIRMED, true);

        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        List<OrderStatus> feeStatuses = List.of(OrderStatus.SHIPPING, OrderStatus.DELIVERED, OrderStatus.COMPLETED);
        BigDecimal shippingFeeTotalThisMonth = orderRepository
            .sumShippingFeeByCreatedAtAfterAndStatusIn(monthStart, feeStatuses);
        BigDecimal shippingFeeAvgThisMonth = orderRepository
            .avgShippingFeeByCreatedAtAfterAndStatusIn(monthStart, feeStatuses);

        PackingStats packingStats = PackingStats.builder()
            .confirmedNotPacked(confirmedNotPacked)
            .confirmedPacked(confirmedPacked)
            .shippingFeeTotalThisMonth(shippingFeeTotalThisMonth)
            .shippingFeeAvgThisMonth(shippingFeeAvgThisMonth)
            .build();

        ReturnStats returnStats = ReturnStats.builder()
            .pending(returnRequestRepository.countByStatus(ReturnStatus.PENDING))
            .processing(returnRequestRepository.countByStatusIn(List.of(ReturnStatus.APPROVED, ReturnStatus.RECEIVED)))
            .completedThisMonth(returnRequestRepository.countByStatusAndUpdatedAtAfter(ReturnStatus.COMPLETED, monthStart))
            .refundAmountThisMonth(returnRequestRepository.sumCompletedRefundAmountSince(monthStart))
            .build();

        return DashboardStatsResponse.builder()
            .totals(totals)
            .revenueTrend(revenueTrend)
            .orderStatusDistribution(statusDistribution)
            .packingStats(packingStats)
            .returnStats(returnStats)
            .build();
    }

    @Override
    @Transactional(readOnly = true)
    public AdminDashboardResponse getAdminDashboard(LocalDate from, LocalDate to) {
        LocalDate effectiveTo = to != null ? to : LocalDate.now();
        LocalDate effectiveFrom = from != null ? from : effectiveTo.withDayOfMonth(1);
        validateRange(effectiveFrom, effectiveTo);

        LocalDateTime fromDate = effectiveFrom.atStartOfDay();
        LocalDateTime toDateExclusive = effectiveTo.plusDays(1).atStartOfDay();
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("fromDate", fromDate)
            .addValue("toDateExclusive", toDateExclusive);

        BigDecimal finalizedGrossRevenue = queryBigDecimal("""
            SELECT COALESCE(SUM(o.total_amount), 0)
            FROM orders o
            """ + FINALIZED_ORDER_WHERE, params);
        BigDecimal processedRefundAmount = queryBigDecimal("""
            SELECT COALESCE(SUM(r.refund_amount), 0)
            FROM returns r
            WHERE r.status = 'COMPLETED'
              AND r.updated_at >= :fromDate
              AND r.updated_at < :toDateExclusive
            """, params);
        BigDecimal pendingRevenue = queryBigDecimal("""
            SELECT COALESCE(SUM(o.total_amount), 0)
            FROM orders o
            WHERE o.status IN ('DELIVERED', 'RETURN_REQUESTED', 'RETURNING')
              AND o.payment_status IN ('PAID', 'REFUNDED')
              AND o.created_at >= :fromDate
              AND o.created_at < :toDateExclusive
            """, params);
        long finalizedOrderCount = queryLong("""
            SELECT COUNT(*)
            FROM orders o
            """ + FINALIZED_ORDER_WHERE, params);

        BigDecimal netRevenue = finalizedGrossRevenue.subtract(processedRefundAmount);
        long pendingOrderCount = queryLong("""
            SELECT COUNT(*)
            FROM orders
            WHERE status IN ('PENDING', 'CONFIRMED')
              AND created_at >= :fromDate
              AND created_at < :toDateExclusive
            """, params);
        long shippingOrderCount = queryLong("""
            SELECT COUNT(*)
            FROM orders
            WHERE status IN ('SHIPPING', 'DELIVERED')
              AND created_at >= :fromDate
              AND created_at < :toDateExclusive
            """, params);
        long pendingReturnCount = queryLong("SELECT COUNT(*) FROM returns WHERE status = 'PENDING'", params);
        StockAlertResponse stockAlerts = stockAlertService.getStockAlerts(5);
        long lowStockProductCount = stockAlerts.getLowStockCount();
        long activeProductCount = queryLong("SELECT COUNT(*) FROM products WHERE status = 'ACTIVE'", params);
        Map<String, Long> orderStatusDistribution = countMap("""
            SELECT o.status AS label, COUNT(*) AS total
            FROM orders o
            WHERE o.created_at >= :fromDate
              AND o.created_at < :toDateExclusive
            GROUP BY o.status
            """, params);
        Map<String, Long> returnStatusDistribution = countMap("""
            SELECT r.status AS label, COUNT(*) AS total
            FROM returns r
            WHERE r.created_at >= :fromDate
              AND r.created_at < :toDateExclusive
            GROUP BY r.status
            """, params);
        OverviewSummary overview = OverviewSummary.builder()
            .netRevenue(netRevenue)
            .finalizedGrossRevenue(finalizedGrossRevenue)
            .processedRefundAmount(processedRefundAmount)
            .pendingRevenue(pendingRevenue)
            .finalizedOrderCount(finalizedOrderCount)
            .pendingOrderCount(pendingOrderCount)
            .shippingOrderCount(shippingOrderCount)
            .pendingReturnCount(pendingReturnCount)
            .lowStockProductCount(lowStockProductCount)
            .activeProductCount(activeProductCount)
            .build();

        RevenueSummary revenue = RevenueSummary.builder()
            .finalizedGrossRevenue(finalizedGrossRevenue)
            .processedRefundAmount(processedRefundAmount)
            .netRevenue(netRevenue)
            .codRevenue(BigDecimal.ZERO)
            .vnpayRevenue(BigDecimal.ZERO)
            .finalizedOrderCount(finalizedOrderCount)
            .pendingRevenue(pendingRevenue)
            .build();

        ReturnSummary returns = ReturnSummary.builder()
            .pendingReturns(queryLong("SELECT COUNT(*) FROM returns WHERE status = 'PENDING'", params))
            .processingReturns(queryLong("SELECT COUNT(*) FROM returns WHERE status IN ('APPROVED', 'RECEIVED')", params))
            .rejectedReturns(queryLong("""
                SELECT COUNT(*) FROM returns
                WHERE status = 'REJECTED' AND updated_at >= :fromDate AND updated_at < :toDateExclusive
                """, params))
            .completedReturns(queryLong("""
                SELECT COUNT(*) FROM returns
                WHERE status = 'COMPLETED' AND updated_at >= :fromDate AND updated_at < :toDateExclusive
                """, params))
            .pendingOver24h(queryLong("""
                SELECT COUNT(*) FROM returns
                WHERE status = 'PENDING' AND created_at < DATE_SUB(NOW(), INTERVAL 24 HOUR)
                """, params))
            .approvedOver3Days(queryLong("""
                SELECT COUNT(*) FROM returns
                WHERE status = 'APPROVED' AND updated_at < DATE_SUB(NOW(), INTERVAL 3 DAY)
                """, params))
            .receivedNotCompleted(queryLong("SELECT COUNT(*) FROM returns WHERE status = 'RECEIVED'", params))
            .processedRefundAmount(processedRefundAmount)
            .returnItemQuantity(queryLong("""
                SELECT COALESCE(SUM(ri.quantity), 0)
                FROM return_items ri
                JOIN returns r ON r.id = ri.return_id
                WHERE r.status IN ('APPROVED', 'RECEIVED', 'COMPLETED')
                  AND r.created_at >= :fromDate
                  AND r.created_at < :toDateExclusive
                """, params))
            .returnItemValue(queryBigDecimal("""
                SELECT COALESCE(SUM(ri.subtotal), 0)
                FROM return_items ri
                JOIN returns r ON r.id = ri.return_id
                WHERE r.status IN ('APPROVED', 'RECEIVED', 'COMPLETED')
                  AND r.created_at >= :fromDate
                  AND r.created_at < :toDateExclusive
                """, params))
            .queue(returnQueue())
            .build();

        ProductAnalytics products = ProductAnalytics.builder()
            .topSellingProducts(topSellingProducts(params))
            .topRevenueProducts(List.of())
            .topReturnedProducts(List.of())
            .build();

        DashboardCharts charts = DashboardCharts.builder()
            .dailyRevenue(dailyRevenue(params))
            .paymentMethodRevenue(List.of())
            .orderStatusDistribution(orderStatusDistribution)
            .returnStatusDistribution(returnStatusDistribution)
            .returnTypeDistribution(Map.of())
            .build();

        return AdminDashboardResponse.builder()
            .dateRange(DashboardDateRange.builder().from(effectiveFrom).to(effectiveTo).build())
            .overview(overview)
            .revenue(revenue)
            .orders(OrderSummary.builder().orderStatusDistribution(orderStatusDistribution).build())
            .returns(returns)
            .products(products)
            .charts(charts)
            .stockAlerts(stockAlerts)
            .build();
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "from phải nhỏ hơn hoặc bằng to");
        }
        if (ChronoUnit.DAYS.between(from, to) > 365) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "Khoảng thời gian dashboard không được quá 365 ngày");
        }
    }

    private BigDecimal queryBigDecimal(String sql, MapSqlParameterSource params) {
        BigDecimal value = jdbc.queryForObject(sql, params, BigDecimal.class);
        return value != null ? value : BigDecimal.ZERO;
    }

    private long queryLong(String sql, MapSqlParameterSource params) {
        Number value = jdbc.queryForObject(sql, params, Number.class);
        return value != null ? value.longValue() : 0L;
    }

    private Map<String, Long> countMap(String sql, MapSqlParameterSource params) {
        Map<String, Long> result = new LinkedHashMap<>();
        jdbc.query(sql, params, (RowCallbackHandler) rs -> {
            result.put(rs.getString("label"), rs.getLong("total"));
        });
        return result;
    }

    private List<DailyRevenuePoint> dailyRevenue(MapSqlParameterSource params) {
        return jdbc.query("""
            SELECT DATE(o.created_at) AS revenue_date,
                   COALESCE(SUM(o.total_amount), 0) AS revenue,
                   COUNT(*) AS order_count
            FROM orders o
            """ + FINALIZED_ORDER_WHERE + """
            GROUP BY DATE(o.created_at)
            ORDER BY DATE(o.created_at)
            """, params, (rs, rowNum) -> DailyRevenuePoint.builder()
            .date(rs.getString("revenue_date"))
            .revenue(defaultBigDecimal(rs.getBigDecimal("revenue")))
            .orderCount(rs.getLong("order_count"))
            .build());
    }

    private List<ProductMetric> topSellingProducts(MapSqlParameterSource params) {
        return jdbc.query("""
            SELECT oi.product_id,
                   oi.product_name,
                   COALESCE(SUM(oi.quantity), 0) AS sold_quantity,
                   COALESCE(SUM(oi.subtotal), 0) AS revenue
            FROM order_items oi
            JOIN orders o ON o.id = oi.order_id
            """ + FINALIZED_ORDER_WHERE + """
              AND oi.product_id IS NOT NULL
            GROUP BY oi.product_id, oi.product_name
            ORDER BY sold_quantity DESC
            LIMIT 5
            """, params, (rs, rowNum) -> ProductMetric.builder()
            .productId(rs.getLong("product_id"))
            .productName(rs.getString("product_name"))
            .quantity(rs.getLong("sold_quantity"))
            .revenue(defaultBigDecimal(rs.getBigDecimal("revenue")))
            .build());
    }

    private List<ReturnQueueItem> returnQueue() {
        return jdbc.query("""
            SELECT r.id AS return_id,
                   r.order_id,
                   u.full_name AS customer_name,
                   CASE
                       WHEN r.reason LIKE '[TRẢ HÀNG]%' THEN 'Trả hàng'
                       WHEN r.reason LIKE '[ĐỔI HÀNG]%' THEN 'Đổi hàng'
                       WHEN r.reason LIKE '[KHIẾU NẠI]%' THEN 'Khiếu nại'
                       ELSE 'Khác'
                   END AS request_type_label,
                   r.status,
                   r.created_at
            FROM returns r
            JOIN users u ON u.id = r.user_id
            WHERE r.status IN ('PENDING', 'APPROVED', 'RECEIVED')
            ORDER BY CASE r.status WHEN 'PENDING' THEN 0 WHEN 'APPROVED' THEN 1 ELSE 2 END,
                     r.created_at ASC
            LIMIT 10
            """, new MapSqlParameterSource(), (rs, rowNum) -> ReturnQueueItem.builder()
            .returnId(rs.getLong("return_id"))
            .orderId(rs.getLong("order_id"))
            .customerName(rs.getString("customer_name"))
            .requestTypeLabel(rs.getString("request_type_label"))
            .status(rs.getString("status"))
            .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
            .build());
    }

    private BigDecimal defaultBigDecimal(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
