package com.fashionshop.backend.module.user;

import com.fashionshop.backend.common.ApiResponse;
import com.fashionshop.backend.domain.User;
import com.fashionshop.backend.module.user.dto.request.AddressRequest;
import com.fashionshop.backend.module.user.dto.response.AddressResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AddressController — quản lý địa chỉ giao hàng của customer.
 *
 * Thiết kế:
 * - Inject AddressService (interface) — không phụ thuộc Impl (SOLID D)
 * - Lấy userId từ @AuthenticationPrincipal User (User implements UserDetails)
 *   → không cần query DB thêm lần 2 để lấy userId
 * - Controller chỉ xử lý HTTP layer — mọi business logic trong Service
 */
@RestController
@RequestMapping("/api/user/addresses")
@RequiredArgsConstructor
@Tag(
    name = "User Addresses",
    description = "Quản lý địa chỉ giao hàng. Yêu cầu xác thực."
)
public class AddressController {

    private final AddressService addressService;

    // ============================================================
    // GET /api/user/addresses
    // ============================================================

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(
        summary = "Lấy danh sách địa chỉ",
        description = "Trả về tất cả địa chỉ của user hiện tại. Địa chỉ mặc định đứng đầu.",
        security = @SecurityRequirement(name = "cookieAuth")
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Danh sách địa chỉ"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    })
    public ResponseEntity<ApiResponse<List<AddressResponse>>> getAddresses(
        @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.ok(ApiResponse.success(
            addressService.getAddresses(currentUser.getId())
        ));
    }

    // ============================================================
    // GET /api/user/addresses/{id}
    // ============================================================

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(
        summary = "Lấy một địa chỉ",
        description = "Trả về địa chỉ theo id. Trả 404 nếu không tồn tại hoặc không thuộc user.",
        security = @SecurityRequirement(name = "cookieAuth")
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Địa chỉ tìm thấy"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy địa chỉ"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    })
    public ResponseEntity<ApiResponse<AddressResponse>> getAddressById(
        @PathVariable Long id,
        @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.ok(ApiResponse.success(
            addressService.getAddressById(id, currentUser.getId())
        ));
    }

    // ============================================================
    // POST /api/user/addresses
    // ============================================================

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(
        summary = "Tạo địa chỉ mới",
        description = "Tạo địa chỉ giao hàng mới. Tự động set isDefault=true nếu là địa chỉ đầu tiên.",
        security = @SecurityRequirement(name = "cookieAuth")
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Tạo thành công"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    })
    public ResponseEntity<ApiResponse<AddressResponse>> createAddress(
        @Valid @RequestBody AddressRequest request,
        @AuthenticationPrincipal User currentUser
    ) {
        AddressResponse response = addressService.createAddress(currentUser.getId(), request);
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success("Tạo địa chỉ thành công", response));
    }

    // ============================================================
    // PUT /api/user/addresses/{id}
    // ============================================================

    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(
        summary = "Cập nhật địa chỉ",
        description = "Cập nhật thông tin địa chỉ. Không thể unset isDefault bằng endpoint này — dùng PATCH /{id}/default.",
        security = @SecurityRequirement(name = "cookieAuth")
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Cập nhật thành công"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy địa chỉ"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    })
    public ResponseEntity<ApiResponse<AddressResponse>> updateAddress(
        @PathVariable Long id,
        @Valid @RequestBody AddressRequest request,
        @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.ok(ApiResponse.success(
            "Cập nhật địa chỉ thành công",
            addressService.updateAddress(id, currentUser.getId(), request)
        ));
    }

    // ============================================================
    // DELETE /api/user/addresses/{id}
    // ============================================================

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(
        summary = "Xóa địa chỉ",
        description = "Xóa địa chỉ. Nếu xóa địa chỉ mặc định và còn địa chỉ khác, tự động chỉ định default mới.",
        security = @SecurityRequirement(name = "cookieAuth")
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Xóa thành công"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy địa chỉ"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    })
    public ResponseEntity<ApiResponse<Void>> deleteAddress(
        @PathVariable Long id,
        @AuthenticationPrincipal User currentUser
    ) {
        addressService.deleteAddress(id, currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success("Xóa địa chỉ thành công"));
    }

    // ============================================================
    // PATCH /api/user/addresses/{id}/default
    // ============================================================

    @PatchMapping("/{id}/default")
    @PreAuthorize("isAuthenticated()")
    @Operation(
        summary = "Đặt địa chỉ mặc định",
        description = "Đặt địa chỉ này làm mặc định. Tự động unset tất cả địa chỉ mặc định cũ.",
        security = @SecurityRequirement(name = "cookieAuth")
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Đặt mặc định thành công"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy địa chỉ"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    })
    public ResponseEntity<ApiResponse<AddressResponse>> setDefault(
        @PathVariable Long id,
        @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.ok(ApiResponse.success(
            "Đặt địa chỉ mặc định thành công",
            addressService.setDefault(id, currentUser.getId())
        ));
    }
}
