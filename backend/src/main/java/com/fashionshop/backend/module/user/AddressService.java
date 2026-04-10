package com.fashionshop.backend.module.user;

import com.fashionshop.backend.module.user.dto.request.AddressRequest;
import com.fashionshop.backend.module.user.dto.response.AddressResponse;

import java.util.List;

/**
 * Contract cho Address business logic.
 * Controller phụ thuộc vào interface này — không phụ thuộc Impl (SOLID D).
 */
public interface AddressService {

    /**
     * Lấy tất cả địa chỉ của user.
     * Sort: isDefault DESC, createdAt DESC (default đứng đầu).
     * Trả list rỗng nếu user chưa có địa chỉ nào.
     */
    List<AddressResponse> getAddresses(Long userId);

    /**
     * Lấy một địa chỉ theo id, kiểm tra ownership.
     * Throw ADDRESS_NOT_FOUND nếu không tồn tại HOẶC không thuộc user.
     */
    AddressResponse getAddressById(Long addressId, Long userId);

    /**
     * Tạo địa chỉ mới cho user.
     * Tự động set isDefault=true nếu đây là địa chỉ đầu tiên.
     */
    AddressResponse createAddress(Long userId, AddressRequest request);

    /**
     * Cập nhật địa chỉ. Không cho phép unset default bằng update —
     * muốn đổi default phải dùng setDefault() trên địa chỉ khác.
     */
    AddressResponse updateAddress(Long addressId, Long userId, AddressRequest request);

    /**
     * Xóa địa chỉ. Nếu xóa địa chỉ default và còn địa chỉ khác,
     * tự động set địa chỉ mới nhất còn lại làm default.
     */
    void deleteAddress(Long addressId, Long userId);

    /**
     * Đặt địa chỉ làm mặc định. Unset tất cả địa chỉ default cũ trước.
     * No-op nếu địa chỉ đã là default.
     */
    AddressResponse setDefault(Long addressId, Long userId);
}
