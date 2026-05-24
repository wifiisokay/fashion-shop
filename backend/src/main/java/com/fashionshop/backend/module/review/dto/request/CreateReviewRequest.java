package com.fashionshop.backend.module.review.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateReviewRequest {
    @NotNull(message = "orderItemId không được để trống")
    private Long orderItemId;

    @NotNull(message = "rating không được để trống")
    @Min(value = 1, message = "Rating tối thiểu là 1")
    @Max(value = 5, message = "Rating tối đa là 5")
    private Integer rating;

    @Size(max = 1000, message = "Nội dung đánh giá tối đa 1000 ký tự")
    private String comment;
}
