package com.fashionshop.backend.module.shipping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fashionshop.backend.module.shipping.dto.request.ShippingFeeRequest;
import com.fashionshop.backend.module.shipping.dto.response.ShippingFeeResponse;

@ExtendWith(MockitoExtension.class)
class ShippingServiceImplTest {

    @Mock
    private ShippingCalculationService shippingCalculationService;

    @Test
    void calculateShippingFee_delegatesToCalculationService() {
        ShippingServiceImpl service = new ShippingServiceImpl(shippingCalculationService);
        ShippingFeeRequest request = new ShippingFeeRequest();
        request.setAddressId(10L);
        ShippingFeeResponse response = ShippingFeeResponse.builder()
            .provider(ShippingCalculationService.PROVIDER)
            .fee(32000L)
            .shippingFee(32000L)
            .build();
        when(shippingCalculationService.calculatePreviewResponse(1L, 10L, null)).thenReturn(response);

        ShippingFeeResponse result = service.calculateShippingFee(1L, request);

        assertThat(result.getShippingFee()).isEqualTo(32000L);
        verify(shippingCalculationService).calculatePreviewResponse(1L, 10L, null);
    }

    @Test
    void buildEstimatedDateText_formatsVietnameseDate() {
        String text = ShippingServiceImpl.buildEstimatedDateText(1);

        assertThat(text).startsWith("Dự kiến giao");
        assertThat(text).contains("/");
    }
}
