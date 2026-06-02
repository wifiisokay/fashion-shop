package com.fashionshop.backend.module.product.dto.response;

import java.util.List;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class StockAlertResponse {
    private long lowStockCount;
    private long outOfStockCount;
    private List<StockAlertItem> lowStockItems;
    private List<StockAlertItem> outOfStockItems;
}
