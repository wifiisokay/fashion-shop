package com.fashionshop.backend.module.shipping;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fashionshop.backend.domain.Address;
import com.fashionshop.backend.domain.ShopConfig;
import com.fashionshop.backend.domain.repository.AddressRepository;
import com.fashionshop.backend.domain.repository.ShopConfigRepository;
import com.fashionshop.backend.exception.BusinessException;
import com.fashionshop.backend.exception.ErrorCode;
import com.fashionshop.backend.module.shipping.cache.ShippingCacheService;
import com.fashionshop.backend.module.shipping.dto.ghn.GhnFeeRequest;
import com.fashionshop.backend.module.shipping.dto.ghn.GhnFeeResponse;
import com.fashionshop.backend.module.shipping.dto.request.ShippingFeeRequest;
import com.fashionshop.backend.module.shipping.dto.response.ShippingFeeResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Luồng xử lý:
 * 1. Validate address ownership
 * 2. Check cache bằng key district:ward
 * 3. Cache HIT → trả ngay (cached=true)
 * 4. Cache MISS → gọi GHN available-services + fee → lưu cache → trả (cached=false)
 * 5. GHN lỗi/timeout → trả fallback (fallback=true)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShippingServiceImpl implements ShippingService {

    private final AddressRepository addressRepository;
    private final ShopConfigRepository shopConfigRepository;
    private final GhnApiClient ghnApiClient;
    private final ShippingCacheService cacheService;
    private final GhnProperties props;

    @Override
    public ShippingFeeResponse calculateShippingFee(Long userId, ShippingFeeRequest request) {
        // 1. Validate address ownership
        Address address = addressRepository.findByIdAndUser_Id(request.getAddressId(), userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.ADDRESS_NOT_BELONG_TO_USER,
                        HttpStatus.FORBIDDEN,
                        "Địa chỉ không thuộc về người dùng hiện tại"
                ));

        // 2. Check cache
        String cacheKey = ShippingCacheService.buildKey(address.getDistrictCode(), address.getWardCode());
        Optional<ShippingFeeResponse> cached = cacheService.get(cacheKey);
        if (cached.isPresent()) {
            ShippingFeeResponse cachedResp = cached.get();
            // Rebuild với cached=true (response gốc có thể có cached=false)
            return ShippingFeeResponse.builder()
                    .fee(cachedResp.getFee())
                    .estimatedDays(cachedResp.getEstimatedDays())
                    .estimatedDateText(buildEstimatedDateText(cachedResp.getEstimatedDays()))
                    .serviceName(cachedResp.getServiceName())
                    .cached(true)
                    .fallback(cachedResp.isFallback())
                    .build();
        }

        // 3. Cache MISS → gọi GHN
        try {
            return callGhnAndCache(address, request, cacheKey);
        } catch (Exception e) {
            log.warn("GHN API error, returning fallback fee. Error: {}", e.getMessage());
            return buildFallbackResponse();
        }
    }

    private ShippingFeeResponse callGhnAndCache(Address address, ShippingFeeRequest request, String cacheKey) {
        // Đọc địa chỉ kho từ DB
        ShopConfig shopConfig = shopConfigRepository.getConfig();

        // 3a. Lấy service_id
        int serviceId = ghnApiClient.getAvailableServiceId(
                shopConfig.getDistrictId(),
                address.getDistrictCode()
        );

        if (serviceId == -1) {
            log.warn("GHN: No available service for route {} -> {}",
                    shopConfig.getDistrictId(), address.getDistrictCode());
            return buildFallbackResponse();
        }

        // 3b. Tính phí ship
        GhnFeeRequest feeRequest = GhnFeeRequest.builder()
                .serviceId(serviceId)
                .insuranceValue(request.getOrderValue() != null ? request.getOrderValue() : 0)
                .fromDistrictId(shopConfig.getDistrictId())
            .fromWardCode(shopConfig.getWardCode())
                .toDistrictId(address.getDistrictCode())
                .toWardCode(address.getWardCode())
                .weight(props.getDefaults().getWeight())
                .length(props.getDefaults().getLength())
                .width(props.getDefaults().getWidth())
                .height(props.getDefaults().getHeight())
                .build();

        GhnFeeResponse ghnResponse = ghnApiClient.calculateFee(feeRequest);

        if (ghnResponse == null || ghnResponse.getData() == null || ghnResponse.getCode() != 200) {
            log.warn("GHN fee API returned invalid response");
            return buildFallbackResponse();
        }

        // 3c. Build response
        long fee = ghnResponse.getData().getTotal();
        int estimatedDays = estimateDays(ghnResponse.getData().getExpectedDeliveryTime());

        ShippingFeeResponse response = ShippingFeeResponse.builder()
                .fee(fee)
                .estimatedDays(estimatedDays)
                .estimatedDateText(buildEstimatedDateText(estimatedDays))
                .serviceName("Giao hàng chuẩn")
                .cached(false)
                .fallback(false)
                .build();

        // 3d. Save cache
        cacheService.put(cacheKey, response);

        return response;
    }

    private ShippingFeeResponse buildFallbackResponse() {
        return ShippingFeeResponse.builder()
                .fee(props.getFallback().getFee())
                .estimatedDays(props.getFallback().getDays())
                .estimatedDateText(buildEstimatedDateText(props.getFallback().getDays()))
                .serviceName("Giao hàng chuẩn")
                .cached(false)
                .fallback(true)
                .build();
    }

    /**
     * Tính estimatedDays từ GHN expected_delivery_time (epoch seconds hoặc ISO string).
     * Fallback: 3 ngày nếu parse fail.
     */
    private int estimateDays(String expectedDeliveryTime) {
        if (expectedDeliveryTime == null || expectedDeliveryTime.isBlank()) {
            return props.getFallback().getDays();
        }
        try {
            // GHN Sandbox thường trả epoch seconds
            long epoch = Long.parseLong(expectedDeliveryTime);
            LocalDate deliveryDate = LocalDate.ofEpochDay(epoch / 86400);
            int days = (int) (deliveryDate.toEpochDay() - LocalDate.now().toEpochDay());
            return Math.max(days, 1);
        } catch (NumberFormatException e) {
            return props.getFallback().getDays();
        }
    }

    /**
     * Format ngày giao dự kiến sang tiếng Việt.
     * Ví dụ: "Dự kiến giao Thứ 4, 23/04"
     */
    static String buildEstimatedDateText(int estimatedDays) {
        LocalDate target = LocalDate.now().plusDays(estimatedDays);
        String dayOfWeek = vietnameseDayOfWeek(target.getDayOfWeek());
        String dateStr = target.format(DateTimeFormatter.ofPattern("dd/MM"));
        return "Dự kiến giao " + dayOfWeek + ", " + dateStr;
    }

    private static String vietnameseDayOfWeek(DayOfWeek dow) {
        return switch (dow) {
            case MONDAY    -> "Thứ 2";
            case TUESDAY   -> "Thứ 3";
            case WEDNESDAY -> "Thứ 4";
            case THURSDAY  -> "Thứ 5";
            case FRIDAY    -> "Thứ 6";
            case SATURDAY  -> "Thứ 7";
            case SUNDAY    -> "Chủ Nhật";
        };
    }
}
