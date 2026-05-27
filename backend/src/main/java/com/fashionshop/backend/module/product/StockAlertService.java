package com.fashionshop.backend.module.product;

import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fashionshop.backend.module.product.dto.response.StockAlertItem;
import com.fashionshop.backend.module.product.dto.response.StockAlertResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StockAlertService {

    private final NamedParameterJdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public StockAlertResponse getStockAlerts(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 50));
        MapSqlParameterSource params = new MapSqlParameterSource("limit", safeLimit);

        long lowStockCount = count("""
            SELECT COUNT(*)
            FROM product_variants pv
            JOIN products p ON p.id = pv.product_id
            WHERE p.status = 'ACTIVE'
              AND pv.stock_quantity > 0
              AND pv.stock_quantity <= p.low_stock_threshold
            """);
        long outOfStockCount = count("""
            SELECT COUNT(*)
            FROM product_variants pv
            JOIN products p ON p.id = pv.product_id
            WHERE p.status = 'ACTIVE'
              AND pv.stock_quantity = 0
            """);

        return StockAlertResponse.builder()
            .lowStockCount(lowStockCount)
            .outOfStockCount(outOfStockCount)
            .lowStockItems(queryItems("""
                SELECT p.id AS product_id,
                       p.name AS product_name,
                       pc.id AS color_id,
                       pc.color_name,
                       pv.id AS variant_id,
                       pv.size,
                       pv.stock_quantity,
                       p.low_stock_threshold
                FROM product_variants pv
                JOIN products p ON p.id = pv.product_id
                LEFT JOIN product_colors pc ON pc.id = pv.color_id
                WHERE p.status = 'ACTIVE'
                  AND pv.stock_quantity > 0
                  AND pv.stock_quantity <= p.low_stock_threshold
                ORDER BY pv.stock_quantity ASC, p.name ASC
                LIMIT :limit
                """, params))
            .outOfStockItems(queryItems("""
                SELECT p.id AS product_id,
                       p.name AS product_name,
                       pc.id AS color_id,
                       pc.color_name,
                       pv.id AS variant_id,
                       pv.size,
                       pv.stock_quantity,
                       p.low_stock_threshold
                FROM product_variants pv
                JOIN products p ON p.id = pv.product_id
                LEFT JOIN product_colors pc ON pc.id = pv.color_id
                WHERE p.status = 'ACTIVE'
                  AND pv.stock_quantity = 0
                ORDER BY p.name ASC, pv.size ASC
                LIMIT :limit
                """, params))
            .build();
    }

    private long count(String sql) {
        Number value = jdbc.queryForObject(sql, new MapSqlParameterSource(), Number.class);
        return value != null ? value.longValue() : 0L;
    }

    private List<StockAlertItem> queryItems(String sql, MapSqlParameterSource params) {
        return jdbc.query(sql, params, (rs, rowNum) -> StockAlertItem.builder()
            .productId(rs.getLong("product_id"))
            .productName(rs.getString("product_name"))
            .colorId(rs.getObject("color_id") != null ? rs.getLong("color_id") : null)
            .colorName(rs.getString("color_name"))
            .variantId(rs.getLong("variant_id"))
            .size(rs.getString("size"))
            .stockQuantity(rs.getInt("stock_quantity"))
            .threshold(rs.getInt("low_stock_threshold"))
            .build());
    }
}
