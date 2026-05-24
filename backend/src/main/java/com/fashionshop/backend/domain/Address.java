package com.fashionshop.backend.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Entity ánh xạ bảng addresses.
 * wardCode là String vì GHN API trả dạng "20194", không phải int.
 *
 * KHÔNG dùng @Data — gây vòng lặp với JPA lazy load (user.addresses.user.addresses...).
 */
@Entity
@Table(name = "addresses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(nullable = false, length = 15)
    private String phone;

    @Column(nullable = false, length = 100)
    private String province;

    @Column(name = "province_code", nullable = false)
    private int provinceCode;

    @Column(nullable = false, length = 100)
    private String district;

    @Column(name = "district_code", nullable = false)
    private int districtCode;

    @Column(nullable = false, length = 100)
    private String ward;

    /**
     * wardCode là String — GHN trả dạng "20194".
     * KHÔNG ép sang int.
     */
    @Column(name = "ward_code", nullable = false, length = 20)
    private String wardCode;

    @Column(nullable = false, length = 255)
    private String street;

    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private boolean isDefault = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
