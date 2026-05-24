package com.fashionshop.backend.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Cấu hình kho hàng shop — singleton row (id=1).
 * Lưu district_id + ward_code theo mã GHN master-data,
 * dùng cho ShippingService khi tính phí vận chuyển.
 */
@Entity
@Table(name = "shop_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShopConfig {

    @Id
    @Column(nullable = false)
    @Builder.Default
    private Long id = 1L;

    @Column(name = "province_id", nullable = false)
    @Builder.Default
    private int provinceId = 202; // TP.HCM

    @Column(name = "district_id", nullable = false)
    @Builder.Default
    private int districtId = 1442;

    @Column(name = "ward_code", nullable = false, length = 20)
    @Builder.Default
    private String wardCode = "20308";

    @Column(name = "province_name", length = 100)
    private String provinceName;

    @Column(name = "district_name", length = 100)
    private String districtName;

    @Column(name = "ward_name", length = 100)
    private String wardName;

    @Column(length = 255)
    private String street;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
