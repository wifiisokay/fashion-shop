package com.fashionshop.backend.module.returnrequest;

import com.fashionshop.backend.common.ApiResponse;
import com.fashionshop.backend.common.PageResponse;
import com.fashionshop.backend.common.enums.ReturnRequestType;
import com.fashionshop.backend.domain.User;
import com.fashionshop.backend.exception.BusinessException;
import com.fashionshop.backend.exception.ErrorCode;
import com.fashionshop.backend.module.returnrequest.dto.request.CreateReturnItemRequest;
import com.fashionshop.backend.module.returnrequest.dto.response.ReturnResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
@Tag(name = "Returns — Customer", description = "Yêu cầu đổi/trả hoặc khiếu nại của khách hàng")
public class ReturnController {

    private final ReturnService returnService;
    private final ObjectMapper objectMapper;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Tạo yêu cầu đổi/trả hoặc khiếu nại", security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<ReturnResponse>> create(
        @AuthenticationPrincipal User user,
        @RequestParam Long orderId,
        @RequestParam(required = false, defaultValue = "RETURN") ReturnRequestType requestType,
        @RequestParam String reason,
        @RequestParam(required = false) String itemsJson,
        @RequestParam(required = false) List<MultipartFile> images
    ) {
        ReturnResponse response = returnService.createReturn(
            user.getId(), orderId, requestType, reason, parseItems(itemsJson), images);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("Yêu cầu đổi/trả hoặc khiếu nại đã được gửi", response));
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

    @GetMapping("/my/{id}")
    @Operation(summary = "Chi tiết yêu cầu đổi/trả hoặc khiếu nại", security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<ReturnResponse>> getMyReturnById(
        @AuthenticationPrincipal User user,
        @PathVariable Long id
    ) {
        return ResponseEntity.ok(ApiResponse.success(
            returnService.getMyReturnById(user.getId(), id)));
    }

    private List<CreateReturnItemRequest> parseItems(String itemsJson) {
        if (itemsJson == null || itemsJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(itemsJson, new TypeReference<List<CreateReturnItemRequest>>() {});
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "itemsJson không hợp lệ");
        }
    }
}
