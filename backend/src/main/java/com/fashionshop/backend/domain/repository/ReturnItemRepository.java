package com.fashionshop.backend.domain.repository;

import com.fashionshop.backend.common.enums.ReturnStatus;
import com.fashionshop.backend.domain.ReturnItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface ReturnItemRepository extends JpaRepository<ReturnItem, Long> {

    List<ReturnItem> findByReturnRequestId(Long returnId);

    @Query("""
        SELECT COALESCE(SUM(ri.quantity), 0)
        FROM ReturnItem ri
        JOIN ri.returnRequest r
        WHERE ri.orderItem.id = :orderItemId
          AND r.status IN :statuses
    """)
    Long sumQuantityByOrderItemAndStatuses(
        @Param("orderItemId") Long orderItemId,
        @Param("statuses") Collection<ReturnStatus> statuses
    );

    @Query("""
        SELECT COALESCE(SUM(ri.quantity), 0)
        FROM ReturnItem ri
        JOIN ri.returnRequest r
        WHERE r.status = 'COMPLETED'
          AND r.updatedAt >= :startDate
    """)
    Long sumCompletedQuantitySince(@Param("startDate") LocalDateTime startDate);

    @Query("""
        SELECT COALESCE(SUM(ri.subtotal), 0)
        FROM ReturnItem ri
        JOIN ri.returnRequest r
        WHERE r.status = 'COMPLETED'
          AND r.updatedAt >= :startDate
    """)
    BigDecimal sumCompletedValueSince(@Param("startDate") LocalDateTime startDate);

    @Query(value = """
        SELECT ri.product_id, ri.product_name, SUM(ri.quantity), SUM(ri.subtotal), COUNT(DISTINCT r.id)
        FROM return_items ri
        JOIN returns r ON r.id = ri.return_id
        WHERE r.created_at >= :startDate
        GROUP BY ri.product_id, ri.product_name
        ORDER BY SUM(ri.quantity) DESC
        LIMIT 5
        """, nativeQuery = true)
    List<Object[]> findTopReturnedProducts(@Param("startDate") LocalDateTime startDate);
}
