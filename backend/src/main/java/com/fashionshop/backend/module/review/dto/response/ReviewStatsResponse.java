package com.fashionshop.backend.module.review.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@Builder
public class ReviewStatsResponse {
    private Double avgRating;
    private Integer totalReviews;
    private Map<Integer, Integer> breakdown; // {5: 60, 4: 25, 3: 10, 2: 3, 1: 2}
}
