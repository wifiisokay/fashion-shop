package com.fashionshop.backend.module.user.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserStatsResponse {
    private long totalUsers;
    private long activeUsers;
    private long lockedUsers;
    private long customerCount;
    private long employeeCount;
    private long adminCount;
}
