package com.fashionshop.backend.module.user.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Request body cho tạo / cập nhật địa chỉ giao hàng.
 * wardCode là String — GHN API trả dạng "20194", không phải int.
 */
@Getter
@Setter
public class AddressRequest {

    @NotBlank(message = "Họ tên người nhận không được để trống")
    @Size(max = 100, message = "Họ tên tối đa 100 ký tự")
    private String fullName;

    @NotBlank(message = "Số điện thoại không được để trống")
    @Pattern(
        regexp = "^(0[3|5|7|8|9])+([0-9]{8})$",
        message = "Số điện thoại không hợp lệ (VN 10 số, bắt đầu 03/05/07/08/09)"
    )
    private String phone;

    @NotBlank(message = "Tỉnh/Thành phố không được để trống")
    @Size(max = 100, message = "Tên tỉnh/thành tối đa 100 ký tự")
    private String province;

    @NotNull(message = "Mã tỉnh/thành không được để trống")
    private Integer provinceCode;

    @NotBlank(message = "Quận/Huyện không được để trống")
    @Size(max = 100, message = "Tên quận/huyện tối đa 100 ký tự")
    private String district;

    @NotNull(message = "Mã quận/huyện không được để trống")
    private Integer districtCode;

    @NotBlank(message = "Phường/Xã không được để trống")
    @Size(max = 100, message = "Tên phường/xã tối đa 100 ký tự")
    private String ward;

    /**
     * wardCode là String — GHN dùng dạng "20194".
     * KHÔNG được ép sang int.
     */
    @NotBlank(message = "Mã phường/xã không được để trống")
    @Size(max = 20, message = "Mã phường/xã tối đa 20 ký tự")
    private String wardCode;

    @NotBlank(message = "Địa chỉ cụ thể không được để trống")
    @Size(max = 255, message = "Địa chỉ tối đa 255 ký tự")
    private String street;

    /**
     * Đánh dấu làm địa chỉ mặc định.
     * @JsonProperty bắt buộc để Jackson dùng "isDefault" (không bị strip thành "default").
     */
    @JsonProperty("isDefault")
    private boolean isDefault = false;
}
