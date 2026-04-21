package com.fashionshop.backend.domain.repository;

import com.fashionshop.backend.domain.Address;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository cho Address entity.
 * Tất cả query dùng user.id (derived từ ManyToOne relationship) —
 * Spring Data JPA tự resolve thành JOIN.
 */
@Repository
public interface AddressRepository extends JpaRepository<Address, Long> {

    /**
     * Danh sách địa chỉ của user: default đứng đầu, sau đó sort theo created_at mới nhất.
     */
    List<Address> findByUser_IdOrderByIsDefaultDescCreatedAtDesc(Long userId);

    /**
     * Tìm địa chỉ theo id VÀ kiểm tra ownership cùng lúc.
     * Trả empty nếu address không tồn tại HOẶC không thuộc user — bảo mật, tránh lộ thông tin.
     */
    Optional<Address> findByIdAndUser_Id(Long id, Long userId);

    /**
     * Kiểm tra user đã có địa chỉ nào chưa — dùng khi create để xác định first address.
     */
    boolean existsByUser_Id(Long userId);

    /**
     * Đếm số địa chỉ của user — dùng khi delete để quyết định có cần set default mới không.
     */
    long countByUser_Id(Long userId);

    /**
     * Lấy tất cả địa chỉ đang là default của user — thường chỉ có 1.
     * Dùng để unset trước khi set default mới.
     */
    List<Address> findByUser_IdAndIsDefaultTrue(Long userId);
}
