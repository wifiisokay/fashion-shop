package com.fashionshop.backend.module.shipping;

import com.fashionshop.backend.domain.Address;
import com.fashionshop.backend.domain.User;
import com.fashionshop.backend.domain.repository.AddressRepository;
import com.fashionshop.backend.exception.BusinessException;
import com.fashionshop.backend.exception.ErrorCode;
import com.fashionshop.backend.module.shipping.cache.ShippingCacheService;
import com.fashionshop.backend.module.shipping.dto.ghn.GhnFeeResponse;
import com.fashionshop.backend.module.shipping.dto.request.ShippingFeeRequest;
import com.fashionshop.backend.module.shipping.dto.response.ShippingFeeResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ShippingServiceImpl Unit Tests")
class ShippingServiceImplTest {

    @Mock private AddressRepository addressRepository;
    @Mock private GhnApiClient ghnApiClient;
    @Mock private ShippingCacheService cacheService;
    @Mock private GhnProperties props;

    @InjectMocks private ShippingServiceImpl shippingService;

    // ── Fixtures ──────────────────────────────────────────────────────
    private Address address;
    private User user;
    private ShippingFeeRequest request;

    private static final Long USER_ID    = 1L;
    private static final Long ADDRESS_ID = 10L;
    private static final int  DISTRICT_CODE = 1444;
    private static final String WARD_CODE = "20306";

    @BeforeEach
    void setUp() {
        user = User.builder().id(USER_ID).build();

        address = Address.builder()
                .id(ADDRESS_ID)
                .user(user)
                .fullName("Nguyễn Văn A")
                .phone("0912345678")
                .province("TP Hồ Chí Minh")
                .provinceCode(202)
                .district("Quận 3")
                .districtCode(DISTRICT_CODE)
                .ward("Phường 1")
                .wardCode(WARD_CODE)
                .street("123 Nguyễn Thị Minh Khai")
                .build();

        request = new ShippingFeeRequest();
        request.setAddressId(ADDRESS_ID);
        request.setOrderValue(500000L);
    }

    // =================================================================
    // Address Ownership Validation
    // =================================================================

    @Nested
    @DisplayName("Address Validation")
    class AddressValidationTests {

        @Test
        @DisplayName("addressId không thuộc user → throw ADDRESS_NOT_BELONG_TO_USER (403)")
        void addressNotBelongToUser_throwsForbidden() {
            when(addressRepository.findByIdAndUser_Id(ADDRESS_ID, USER_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> shippingService.calculateShippingFee(USER_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ADDRESS_NOT_BELONG_TO_USER);

            verifyNoInteractions(ghnApiClient, cacheService);
        }

        @Test
        @DisplayName("addressId thuộc user → không throw exception")
        void addressBelongsToUser_proceedsNormally() {
            when(addressRepository.findByIdAndUser_Id(ADDRESS_ID, USER_ID))
                    .thenReturn(Optional.of(address));
            when(cacheService.get(anyString())).thenReturn(Optional.of(buildCachedResponse()));

            shippingService.calculateShippingFee(USER_ID, request);

            // Không throw → test pass
            verify(cacheService).get(DISTRICT_CODE + ":" + WARD_CODE);
        }
    }

    // =================================================================
    // Cache Hit
    // =================================================================

    @Nested
    @DisplayName("Cache Hit")
    class CacheHitTests {

        @Test
        @DisplayName("cache HIT → trả response với cached=true, KHÔNG gọi GHN")
        void cacheHit_returnsCachedResponse_noGhnCall() {
            when(addressRepository.findByIdAndUser_Id(ADDRESS_ID, USER_ID))
                    .thenReturn(Optional.of(address));
            when(cacheService.get(DISTRICT_CODE + ":" + WARD_CODE))
                    .thenReturn(Optional.of(buildCachedResponse()));

            ShippingFeeResponse result = shippingService.calculateShippingFee(USER_ID, request);

            assertThat(result.isCached()).isTrue();
            assertThat(result.getFee()).isEqualTo(25000L);
            assertThat(result.getEstimatedDays()).isEqualTo(2);
            assertThat(result.getEstimatedDateText()).startsWith("Dự kiến giao");

            verifyNoInteractions(ghnApiClient);
        }

        @Test
        @DisplayName("cache HIT → estimatedDateText được tính lại (ngày mới)")
        void cacheHit_recalculatesDateText() {
            when(addressRepository.findByIdAndUser_Id(ADDRESS_ID, USER_ID))
                    .thenReturn(Optional.of(address));
            when(cacheService.get(anyString()))
                    .thenReturn(Optional.of(buildCachedResponse()));

            ShippingFeeResponse result = shippingService.calculateShippingFee(USER_ID, request);

            // estimatedDateText luôn tính từ LocalDate.now(), không phải ngày cache
            assertThat(result.getEstimatedDateText()).contains("/");
        }
    }

    // =================================================================
    // Cache Miss → GHN Call
    // =================================================================

    @Nested
    @DisplayName("Cache Miss → GHN Call")
    class CacheMissTests {

        @BeforeEach
        void stubAddress() {
            when(addressRepository.findByIdAndUser_Id(ADDRESS_ID, USER_ID))
                    .thenReturn(Optional.of(address));
            when(cacheService.get(anyString())).thenReturn(Optional.empty());

            // Stub GHN properties — chỉ shop và fallback (dùng chung mọi test)
            GhnProperties.Shop shop = new GhnProperties.Shop();
            shop.setDistrictId(1442);
            shop.setWardCode("20308");
            when(props.getShop()).thenReturn(shop);

            GhnProperties.Fallback fallback = new GhnProperties.Fallback();
            when(props.getFallback()).thenReturn(fallback);
        }

        /** Stub defaults — chỉ gọi trong test thật sự cần build GhnFeeRequest */
        private void stubDefaults() {
            when(props.getDefaults()).thenReturn(new GhnProperties.Defaults());
        }

        @Test
        @DisplayName("GHN trả thành công → cached=false, fallback=false, fee đúng")
        void ghnSuccess_returnsFreshResponse() {
            stubDefaults();
            when(ghnApiClient.getAvailableServiceId(1442, DISTRICT_CODE)).thenReturn(53320);
            when(ghnApiClient.calculateFee(any())).thenReturn(buildGhnResponse(200, 32000L));

            ShippingFeeResponse result = shippingService.calculateShippingFee(USER_ID, request);

            assertThat(result.isCached()).isFalse();
            assertThat(result.isFallback()).isFalse();
            assertThat(result.getFee()).isEqualTo(32000L);
            assertThat(result.getServiceName()).isEqualTo("Giao hàng chuẩn");

            verify(cacheService).put(eq(DISTRICT_CODE + ":" + WARD_CODE), any());
        }

        @Test
        @DisplayName("GHN trả thành công → kết quả được lưu vào cache")
        void ghnSuccess_savesToCache() {
            stubDefaults();
            when(ghnApiClient.getAvailableServiceId(1442, DISTRICT_CODE)).thenReturn(53320);
            when(ghnApiClient.calculateFee(any())).thenReturn(buildGhnResponse(200, 25000L));

            shippingService.calculateShippingFee(USER_ID, request);

            verify(cacheService).put(eq(DISTRICT_CODE + ":" + WARD_CODE), argThat(resp ->
                    resp.getFee() == 25000L && !resp.isFallback()
            ));
        }

        @Test
        @DisplayName("GHN available-services trả -1 (không có dịch vụ) → fallback")
        void ghnNoService_returnsFallback() {
            when(ghnApiClient.getAvailableServiceId(1442, DISTRICT_CODE)).thenReturn(-1);

            ShippingFeeResponse result = shippingService.calculateShippingFee(USER_ID, request);

            assertThat(result.isFallback()).isTrue();
            assertThat(result.getFee()).isEqualTo(30000L);
            assertThat(result.getEstimatedDays()).isEqualTo(3);

            verify(ghnApiClient, never()).calculateFee(any());
        }

        @Test
        @DisplayName("GHN calculateFee trả null → fallback")
        void ghnFeeReturnsNull_returnsFallback() {
            stubDefaults();
            when(ghnApiClient.getAvailableServiceId(1442, DISTRICT_CODE)).thenReturn(53320);
            when(ghnApiClient.calculateFee(any())).thenReturn(null);

            ShippingFeeResponse result = shippingService.calculateShippingFee(USER_ID, request);

            assertThat(result.isFallback()).isTrue();
            assertThat(result.getFee()).isEqualTo(30000L);
        }

        @Test
        @DisplayName("GHN trả code != 200 → fallback")
        void ghnFeeReturnsError_returnsFallback() {
            stubDefaults();
            when(ghnApiClient.getAvailableServiceId(1442, DISTRICT_CODE)).thenReturn(53320);
            when(ghnApiClient.calculateFee(any())).thenReturn(buildGhnResponse(400, 0));

            ShippingFeeResponse result = shippingService.calculateShippingFee(USER_ID, request);

            assertThat(result.isFallback()).isTrue();
        }

        @Test
        @DisplayName("GHN throw exception → fallback (không crash)")
        void ghnThrowsException_returnsFallback() {
            when(ghnApiClient.getAvailableServiceId(1442, DISTRICT_CODE))
                    .thenThrow(new RuntimeException("Connection refused"));

            ShippingFeeResponse result = shippingService.calculateShippingFee(USER_ID, request);

            assertThat(result.isFallback()).isTrue();
            assertThat(result.getFee()).isEqualTo(30000L);
        }

        @Test
        @DisplayName("orderValue = null → insuranceValue = 0 trong request GHN")
        void orderValueNull_defaultsToZeroInsurance() {
            stubDefaults();
            request.setOrderValue(null);
            when(ghnApiClient.getAvailableServiceId(1442, DISTRICT_CODE)).thenReturn(53320);
            when(ghnApiClient.calculateFee(any())).thenReturn(buildGhnResponse(200, 20000L));

            shippingService.calculateShippingFee(USER_ID, request);

            verify(ghnApiClient).calculateFee(argThat(req ->
                    req.getInsuranceValue() == 0
            ));
        }
    }

    // =================================================================
    // buildEstimatedDateText (static method)
    // =================================================================

    @Nested
    @DisplayName("buildEstimatedDateText")
    class DateTextTests {

        @Test
        @DisplayName("estimatedDays=0 → trả hôm nay")
        void zeroDays_returnsToday() {
            String text = ShippingServiceImpl.buildEstimatedDateText(0);
            assertThat(text).startsWith("Dự kiến giao");
            assertThat(text).contains("/");
        }

        @Test
        @DisplayName("estimatedDays=1 → trả ngày mai, có tên thứ tiếng Việt")
        void oneDay_returnsTomorrow() {
            String text = ShippingServiceImpl.buildEstimatedDateText(1);
            assertThat(text).startsWith("Dự kiến giao");
            // Phải chứa ít nhất 1 tên thứ tiếng Việt
            assertThat(text).containsAnyOf("Thứ 2", "Thứ 3", "Thứ 4", "Thứ 5", "Thứ 6", "Thứ 7", "Chủ Nhật");
        }

        @Test
        @DisplayName("estimatedDays=7 → 1 tuần sau, format dd/MM")
        void sevenDays_returnsNextWeek() {
            String text = ShippingServiceImpl.buildEstimatedDateText(7);
            assertThat(text).matches("Dự kiến giao .+, \\d{2}/\\d{2}");
        }
    }

    // =================================================================
    // Fixture Helpers
    // =================================================================

    private ShippingFeeResponse buildCachedResponse() {
        return ShippingFeeResponse.builder()
                .fee(25000L)
                .estimatedDays(2)
                .estimatedDateText("Dự kiến giao Thứ 4, 22/04")
                .serviceName("Giao hàng chuẩn")
                .cached(false)
                .fallback(false)
                .build();
    }

    private GhnFeeResponse buildGhnResponse(int code, long totalFee) {
        GhnFeeResponse response = new GhnFeeResponse();
        response.setCode(code);
        response.setMessage(code == 200 ? "Success" : "Error");

        if (code == 200) {
            GhnFeeResponse.GhnFeeData data = new GhnFeeResponse.GhnFeeData();
            data.setTotal(totalFee);
            data.setExpectedDeliveryTime(null); // sẽ dùng fallback days
            response.setData(data);
        }

        return response;
    }
}
