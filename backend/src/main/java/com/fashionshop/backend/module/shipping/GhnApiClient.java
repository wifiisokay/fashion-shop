package com.fashionshop.backend.module.shipping;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.fashionshop.backend.module.shipping.dto.ghn.GhnFeeRequest;
import com.fashionshop.backend.module.shipping.dto.ghn.GhnFeeResponse;

import lombok.extern.slf4j.Slf4j;

/**
 * HTTP client gọi GHN API v2.
 * Timeout: connect 3s, read 5s.
 * Header tự động set: token, ShopId, Content-Type.
 */
@Slf4j
@Component
public class GhnApiClient {

    private final RestTemplate restTemplate;
    private final GhnProperties props;

    public GhnApiClient(RestTemplateBuilder builder, GhnProperties props) {
        this.props = props;
        this.restTemplate = builder
                .connectTimeout(Duration.ofSeconds(3))
                .readTimeout(Duration.ofSeconds(5))
                .build();

        if (props.getToken() == null || props.getToken().isBlank()) {
            log.warn("GHN runtime config warning: token is empty/null");
        }
        if (props.getShopId() == null || props.getShopId().isBlank()) {
            log.warn("GHN runtime config warning: shopId is empty/null");
        }
    }

    /**
     * Gọi GHN /v2/shipping-order/available-services để lấy service_id đầu tiên.
     * @return service_id hoặc -1 nếu không tìm được
     */
    public int getAvailableServiceId(int fromDistrictId, int toDistrictId) {
        String url = props.getBaseUrl() + "/v2/shipping-order/available-services";

        Map<String, Object> body = Map.of(
                "shop_id", Integer.parseInt(props.getShopId()),
                "from_district", fromDistrictId,
                "to_district", toDistrictId
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, buildHeaders());

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity,
                    new ParameterizedTypeReference<>() {}
            );

            if (response.getBody() != null && response.getBody().get("data") instanceof List<?> services) {
                if (!services.isEmpty() && services.getFirst() instanceof Map<?, ?> first) {
                    Object serviceId = first.get("service_id");
                    if (serviceId instanceof Number num) {
                        return num.intValue();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("GHN available-services call failed: {}", e.getMessage());
        }
        return -1;
    }

    /**
     * Gọi GHN /v2/shipping-order/fee để tính phí ship.
     * @return GhnFeeResponse hoặc null nếu lỗi
     */
    public GhnFeeResponse calculateFee(GhnFeeRequest request) {
        String url = props.getBaseUrl() + "/v2/shipping-order/fee";

        HttpEntity<GhnFeeRequest> entity = new HttpEntity<>(request, buildHeaders());

        try {
            ResponseEntity<GhnFeeResponse> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, GhnFeeResponse.class
            );
            return response.getBody();
        } catch (Exception e) {
            throw new IllegalStateException("GHN calculate-fee failed: " + e.getMessage(), e);
        }
    }

    /**
     * GHN master-data: danh sách tỉnh/thành.
     * GET /master-data/province (no body)
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getProvinces() {
        String url = props.getBaseUrl() + "/master-data/province";
        HttpEntity<String> entity = new HttpEntity<>("", buildHeaders());
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, new ParameterizedTypeReference<>() {}
            );
            if (response.getBody() != null && response.getBody().get("data") instanceof List<?> data) {
                return (List<Map<String, Object>>) (List<?>) data;
            }
        } catch (Exception e) {
            log.warn("GHN get provinces failed: {}", e.getMessage());
        }
        return List.of();
    }

    /**
     * GHN master-data: danh sách quận/huyện theo tỉnh.
     * POST /master-data/district — body: {"province_id": X}
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getDistricts(int provinceId) {
        String url = props.getBaseUrl() + "/master-data/district";
        Map<String, Object> body = Map.of("province_id", provinceId);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, buildHeaders());
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {}
            );
            if (response.getBody() != null && response.getBody().get("data") instanceof List<?> data) {
                return (List<Map<String, Object>>) (List<?>) data;
            }
        } catch (Exception e) {
            log.warn("GHN get districts failed: {}", e.getMessage());
        }
        return List.of();
    }

    /**
     * GHN master-data: danh sách phường/xã theo quận.
     * POST /master-data/ward — body: {"district_id": X}
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getWards(int districtId) {
        String url = props.getBaseUrl() + "/master-data/ward";
        Map<String, Object> body = Map.of("district_id", districtId);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, buildHeaders());
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {}
            );
            if (response.getBody() != null && response.getBody().get("data") instanceof List<?> data) {
                return (List<Map<String, Object>>) (List<?>) data;
            }
        } catch (Exception e) {
            log.warn("GHN get wards failed: {}", e.getMessage());
        }
        return List.of();
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("token", props.getToken());
        headers.set("ShopId", props.getShopId());
        return headers;
    }

}
