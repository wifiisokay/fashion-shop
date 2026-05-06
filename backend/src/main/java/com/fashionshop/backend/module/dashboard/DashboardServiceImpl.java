package com.fashionshop.backend.module.dashboard;

import com.fashionshop.backend.common.enums.OrderStatus;
import com.fashionshop.backend.common.enums.ProductStatus;
import com.fashionshop.backend.common.enums.Role;
import com.fashionshop.backend.domain.repository.OrderRepository;
import com.fashionshop.backend.domain.repository.ProductRepository;
import com.fashionshop.backend.domain.repository.UserRepository;
import com.fashionshop.backend.module.dashboard.dto.DashboardStatsResponse;
import com.fashionshop.backend.module.dashboard.dto.DashboardStatsResponse.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

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

        return DashboardStatsResponse.builder()
            .totals(totals)
            .revenueTrend(revenueTrend)
            .orderStatusDistribution(statusDistribution)
            .build();
    }
}
