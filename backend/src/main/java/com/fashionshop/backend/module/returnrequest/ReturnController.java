package com.fashionshop.backend.module.returnrequest;

import com.fashionshop.backend.common.ApiResponse;
import com.fashionshop.backend.common.PageResponse;
import com.fashionshop.backend.domain.User;
import com.fashionshop.backend.module.returnrequest.dto.response.ReturnResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/returns")
@RequiredArgsConstructor
@Tag(name = "Returns — Customer", description = "Yêu cầu trả hàng của khách hàng")
public class ReturnController {

    private final ReturnService returnService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Tạo yêu cầu trả hàng", security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<ReturnResponse>> create(
        @AuthenticationPrincipal User user,
        @RequestParam Long orderId,
        @RequestParam String reason,
        @RequestParam(required = false) List<MultipartFile> images
    ) {
        ReturnResponse response = returnService.createReturn(user.getId(), orderId, reason, images);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("Yêu cầu trả hàng đã được gửi", response));
    }

    @GetMapping("/my")
    @Operation(summary = "Danh sách yêu cầu trả hàng của tôi", security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<PageResponse<ReturnResponse>>> getMyReturns(
        @AuthenticationPrincipal User user,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(
            returnService.getMyReturns(user.getId(), page, size)));
    }

    @GetMapping("/my/{id}")
    @Operation(summary = "Chi tiết yêu cầu trả hàng", security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<ReturnResponse>> getMyReturnById(
        @AuthenticationPrincipal User user,
        @PathVariable Long id
    ) {
        return ResponseEntity.ok(ApiResponse.success(
            returnService.getMyReturnById(user.getId(), id)));
    }
}
