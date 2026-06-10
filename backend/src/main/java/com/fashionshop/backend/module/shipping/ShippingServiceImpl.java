package com.fashionshop.backend.module.shipping;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Service;

import com.fashionshop.backend.module.shipping.dto.request.ShippingFeeRequest;
import com.fashionshop.backend.module.shipping.dto.response.ShippingFeeResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ShippingServiceImpl implements ShippingService {

    private final ShippingCalculationService shippingCalculationService;

    @Override
    public ShippingFeeResponse calculateShippingFee(Long userId, ShippingFeeRequest request) {
        return shippingCalculationService.calculatePreviewResponse(userId, request.getAddressId(), request.getItems());
    }

    static String buildEstimatedDateText(int estimatedDays) {
        LocalDate target = LocalDate.now().plusDays(estimatedDays);
        String dayOfWeek = switch (target.getDayOfWeek()) {
            case MONDAY -> "Thứ 2";
            case TUESDAY -> "Thứ 3";
            case WEDNESDAY -> "Thứ 4";
            case THURSDAY -> "Thứ 5";
            case FRIDAY -> "Thứ 6";
            case SATURDAY -> "Thứ 7";
            case SUNDAY -> "Chủ Nhật";
        };
        return "Dự kiến giao " + dayOfWeek + ", " + target.format(DateTimeFormatter.ofPattern("dd/MM"));
    }
}
