package com.fashionshop.backend.domain;

import com.fashionshop.backend.common.enums.Gender;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Sở thích người dùng — được học dần từ lịch sử chat.
 * Dùng để cá nhân hoá AI prompt mà không cần user nhắc lại mỗi lần.
 */
@Entity
@Table(name = "user_preferences")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPreference {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;


    /** Giới tính để filter sản phẩm (không cần user nhắc mỗi lần) */
    @Enumerated(EnumType.STRING)
    private Gender gender;

    /** Nhóm tuổi: 18-24, 25-30, 30-35 */
    @Column(name = "age_group", length = 10)
    private String ageGroup;

    /** Size: {"top": "L", "bottom": "32"} */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "size_info", columnDefinition = "json")
    private Map<String, String> sizeInfo;

    /** Màu sắc ưa thích: ["Tối", "Trung tính"] — tối đa 5 entries */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "color_pref", columnDefinition = "json")
    private List<String> colorPref;

    /** Style score: {"casual-basic": 3, "smart-casual": 2} */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "style_pref", columnDefinition = "json")
    private Map<String, Integer> stylePref;

    /** Ngân sách: {"min": 200000, "max": 500000} */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "budget_range", columnDefinition = "json")
    private Map<String, Long> budgetRange;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
