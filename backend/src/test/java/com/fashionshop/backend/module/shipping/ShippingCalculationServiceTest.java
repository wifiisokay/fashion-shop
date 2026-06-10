package com.fashionshop.backend.module.shipping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fashionshop.backend.common.enums.ProductStatus;
import com.fashionshop.backend.domain.Address;
import com.fashionshop.backend.domain.Order;
import com.fashionshop.backend.domain.Product;
import com.fashionshop.backend.domain.ProductVariant;
import com.fashionshop.backend.domain.ShopConfig;
import com.fashionshop.backend.domain.repository.AddressRepository;
import com.fashionshop.backend.domain.repository.CartItemRepository;
import com.fashionshop.backend.domain.repository.ProductVariantRepository;
import com.fashionshop.backend.domain.repository.ShopConfigRepository;
import com.fashionshop.backend.exception.BusinessException;
import com.fashionshop.backend.module.order.dto.request.ConfirmPackingRequest;
import com.fashionshop.backend.module.product.ProductPriceService;
import com.fashionshop.backend.module.shipping.cache.ShippingCacheService;
import com.fashionshop.backend.module.shipping.dto.ghn.GhnFeeRequest;
import com.fashionshop.backend.module.shipping.dto.ghn.GhnFeeResponse;

@ExtendWith(MockitoExtension.class)
class ShippingCalculationServiceTest {

    @Mock private AddressRepository addressRepository;
    @Mock private CartItemRepository cartItemRepository;
    @Mock private ProductVariantRepository variantRepository;
    @Mock private ShopConfigRepository shopConfigRepository;
    @Mock private GhnApiClient ghnApiClient;
    @Mock private GhnProperties props;
    @Mock private ShippingPackageEstimator packageEstimator;
    @Mock private ShippingCacheService cacheService;
    @Mock private ProductPriceService productPriceService;

    @InjectMocks
    private ShippingCalculationService service;

    private Address address;
    private Product product;
    private ProductVariant variant;
    private ShopConfig shopConfig;

    @BeforeEach
    void setUp() {
        address = Address.builder()
            .id(10L)
            .districtCode(1444)
            .wardCode("20306")
            .build();
        product = Product.builder()
            .id(1L)
            .name("Ao thun")
            .status(ProductStatus.ACTIVE)
            .build();
        variant = ProductVariant.builder()
            .id(14L)
            .product(product)
            .stockQuantity(5)
            .build();
        shopConfig = ShopConfig.builder()
            .districtId(1442)
            .wardCode("20308")
            .build();
        lenient().when(props.getFallback()).thenReturn(new GhnProperties.Fallback());
    }

    @Test
    void calculateCheckoutFee_success_usesGhnFee() {
        Map<Long, Integer> items = new LinkedHashMap<>();
        items.put(14L, 1);
        when(addressRepository.findByIdAndUser_Id(10L, 1L)).thenReturn(Optional.of(address));
        when(variantRepository.findById(14L)).thenReturn(Optional.of(variant));
        when(productPriceService.getFinalUnitPrice(product, variant)).thenReturn(BigDecimal.valueOf(200000));
        when(packageEstimator.estimate(any())).thenReturn(new ShippingPackageEstimate(700, 30, 20, 8));
        when(shopConfigRepository.getConfig()).thenReturn(shopConfig);
        when(ghnApiClient.getAvailableServiceId(1442, 1444)).thenReturn(53320);
        when(cacheService.get(anyString())).thenReturn(Optional.empty());
        when(ghnApiClient.calculateFee(any(GhnFeeRequest.class))).thenReturn(ghnResponse(32000));

        CheckoutShippingFeeResult result = service.calculateCheckoutFee(1L, 10L, items);

        assertThat(result.provider()).isEqualTo("GHN_SANDBOX");
        assertThat(result.shippingFee()).isEqualTo(32000);
        assertThat(result.estimatedWeight()).isEqualTo(700);
        assertThat(result.packageLength()).isEqualTo(30);
        assertThat(result.packageWidth()).isEqualTo(20);
        assertThat(result.packageHeight()).isEqualTo(8);
        verify(cacheService).put(anyString(), any());
    }

    @Test
    void calculateCheckoutFee_ghnFails_throwsBusinessException() {
        Map<Long, Integer> items = new LinkedHashMap<>();
        items.put(14L, 1);
        when(addressRepository.findByIdAndUser_Id(10L, 1L)).thenReturn(Optional.of(address));
        when(variantRepository.findById(14L)).thenReturn(Optional.of(variant));
        when(productPriceService.getFinalUnitPrice(product, variant)).thenReturn(BigDecimal.valueOf(200000));
        when(packageEstimator.estimate(any())).thenReturn(new ShippingPackageEstimate(700, 30, 20, 8));
        when(shopConfigRepository.getConfig()).thenReturn(shopConfig);
        when(ghnApiClient.getAvailableServiceId(1442, 1444)).thenReturn(53320);
        when(cacheService.get(anyString())).thenReturn(Optional.empty());
        when(ghnApiClient.calculateFee(any(GhnFeeRequest.class))).thenThrow(new IllegalStateException("timeout"));

        assertThatThrownBy(() -> service.calculateCheckoutFee(1L, 10L, items))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Khong the tinh phi van chuyen GHN");
    }

    @Test
    void calculateActualFee_success_usesAdminPackageValues() {
        Order order = order();
        ConfirmPackingRequest request = packingRequest();
        when(shopConfigRepository.getConfig()).thenReturn(shopConfig);
        when(ghnApiClient.getAvailableServiceId(1442, 1444)).thenReturn(53320);
        when(cacheService.get(anyString())).thenReturn(Optional.empty());
        when(ghnApiClient.calculateFee(any(GhnFeeRequest.class))).thenReturn(ghnResponse(34000));

        ActualShippingFeeResult result = service.calculateActualFee(order, request);

        assertThat(result.actualShippingFee()).isEqualByComparingTo("34000");
        assertThat(result.shippingFeeDifference()).isEqualByComparingTo("4000");
        assertThat(result.actualWeight()).isEqualTo(900);
        assertThat(result.packageLength()).isEqualTo(32);

        ArgumentCaptor<GhnFeeRequest> captor = ArgumentCaptor.forClass(GhnFeeRequest.class);
        verify(ghnApiClient).calculateFee(captor.capture());
        GhnFeeRequest feeRequest = captor.getValue();
        assertThat(feeRequest.getWeight()).isEqualTo(900);
        assertThat(feeRequest.getLength()).isEqualTo(32);
        assertThat(feeRequest.getWidth()).isEqualTo(25);
        assertThat(feeRequest.getHeight()).isEqualTo(12);
    }

    @Test
    void calculateActualFee_ghnFails_throwsWithoutInternalFallback() {
        Order order = order();
        ConfirmPackingRequest request = packingRequest();
        when(shopConfigRepository.getConfig()).thenReturn(shopConfig);
        when(ghnApiClient.getAvailableServiceId(1442, 1444)).thenReturn(53320);
        when(cacheService.get(anyString())).thenReturn(Optional.empty());
        when(ghnApiClient.calculateFee(any(GhnFeeRequest.class))).thenThrow(new IllegalStateException("timeout"));

        assertThatThrownBy(() -> service.calculateActualFee(order, request))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("timeout");
    }

    private Order order() {
        return Order.builder()
            .id(99L)
            .shippingFee(BigDecimal.valueOf(30000))
            .subtotal(BigDecimal.valueOf(200000))
            .addressSnapshot(Map.of("districtCode", 1444, "wardCode", "20306"))
            .build();
    }

    private ConfirmPackingRequest packingRequest() {
        ConfirmPackingRequest request = new ConfirmPackingRequest();
        request.setPackageLength(32);
        request.setPackageWidth(25);
        request.setPackageHeight(12);
        request.setActualWeight(900);
        return request;
    }

    private GhnFeeResponse ghnResponse(long fee) {
        GhnFeeResponse response = new GhnFeeResponse();
        response.setCode(200);
        GhnFeeResponse.GhnFeeData data = new GhnFeeResponse.GhnFeeData();
        data.setTotal(fee);
        response.setData(data);
        return response;
    }
}
