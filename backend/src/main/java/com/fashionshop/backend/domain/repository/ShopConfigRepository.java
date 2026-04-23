package com.fashionshop.backend.domain.repository;

import com.fashionshop.backend.domain.ShopConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ShopConfigRepository extends JpaRepository<ShopConfig, Long> {

    /**
     * Lấy cấu hình kho duy nhất (id=1).
     * Nếu chưa có row, tạo mặc định.
     */
    default ShopConfig getConfig() {
        return findById(1L).orElseGet(() -> save(ShopConfig.builder().build()));
    }
}
