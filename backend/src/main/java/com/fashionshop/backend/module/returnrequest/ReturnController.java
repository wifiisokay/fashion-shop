package com.fashionshop.backend.module.returnrequest;

import com.fashionshop.backend.common.ApiResponse;
import com.fashionshop.backend.common.PageResponse;
import com.fashionshop.backend.domain.User;
import com.fashionshop.backend.module.returnrequest.dto.request.CreateReturnRequest;
import com.fashionshop.backend.module.returnrequest.dto.response.ReturnResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/returns")
@RequiredArgsConstructor
@Tag(name = "Returns — Customer", description = "Yêu cầu đổi/trả hoặc khiếu nại của khách hàng")
public class ReturnController {

    private final ReturnService returnService;

    @PostMapping
    @Operation(summary = "Tạo yêu cầu trả hàng", security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<ReturnResponse>> create(
        @AuthenticationPrincipal User user,
        @Valid @RequestBody CreateReturnRequest request
    ) {
        ReturnResponse response = returnService.createReturn(user.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("Yêu cầu trả hàng đã được gửi", response));
    }

    @PostMapping(value = "/upload", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload ảnh minh chứng trả hàng", security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<java.util.Map<String, String>>> uploadEvidence(
        @RequestParam("file") org.springframework.web.multipart.MultipartFile file
    ) {
        String url = returnService.uploadEvidenceImage(file);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("Upload ảnh minh chứng thành công", java.util.Map.of("url", url)));
    }

    @GetMapping("/my")
    @Operation(summary = "Danh sách yêu cầu đổi/trả hoặc khiếu nại của tôi", security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<PageResponse<ReturnResponse>>> getMyReturns(
        @AuthenticationPrincipal User user,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(
            returnService.getMyReturns(user.getId(), page, size)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết yêu cầu trả hàng", security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<ReturnResponse>> getMyReturnById(
        @AuthenticationPrincipal User user,
        @PathVariable Long id
    ) {
        return ResponseEntity.ok(ApiResponse.success(
            returnService.getMyReturnById(user.getId(), id)));
    }
}
