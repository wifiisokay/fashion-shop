package com.fashionshop.backend.module.order.shipping;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.fashionshop.backend.domain.Order;
import com.fashionshop.backend.module.order.dto.request.ConfirmPackingRequest;

@Service
public class ShippingFeeEstimator {

    private static final String SHOP_PROVINCE = "Da Nang";

    public PackingShippingEstimate estimate(Order order, ConfirmPackingRequest request) {
        int volumetricWeight = calculateVolumetricWeight(
            request.getLength(), request.getWidth(), request.getHeight());
        int chargeableWeight = calculateChargeableWeight(request.getActualWeight(), volumetricWeight);
        BigDecimal estimatedShippingFee = estimateShippingFee(order, chargeableWeight);
        BigDecimal shippingFee = safeFee(order.getShippingFee());
        BigDecimal shippingFeeDifference = estimatedShippingFee.subtract(shippingFee);
        List<String> warnings = buildPackingWarnings(
            request.getLength(), request.getWidth(), request.getHeight(),
            request.getActualWeight(), volumetricWeight, shippingFeeDifference);

        return new PackingShippingEstimate(
            volumetricWeight,
            chargeableWeight,
            estimatedShippingFee,
            shippingFeeDifference,
            warnings
        );
    }

    public PackingShippingEstimate estimateFromOrder(Order order) {
        if (order.getPackageLength() == null || order.getPackageWidth() == null
            || order.getPackageHeight() == null || order.getActualWeight() == null) {
            return null;
        }

        int volumetricWeight = calculateVolumetricWeight(
            order.getPackageLength(), order.getPackageWidth(), order.getPackageHeight());
        int chargeableWeight = calculateChargeableWeight(order.getActualWeight(), volumetricWeight);
        BigDecimal estimatedShippingFee = estimateShippingFee(order, chargeableWeight);
        BigDecimal shippingFee = safeFee(order.getShippingFee());
        BigDecimal shippingFeeDifference = estimatedShippingFee.subtract(shippingFee);
        List<String> warnings = buildPackingWarnings(
            order.getPackageLength(), order.getPackageWidth(), order.getPackageHeight(),
            order.getActualWeight(), volumetricWeight, shippingFeeDifference);

        return new PackingShippingEstimate(
            volumetricWeight,
            chargeableWeight,
            estimatedShippingFee,
            shippingFeeDifference,
            warnings
        );
    }

    public int calculateVolumetricWeight(int length, int width, int height) {
        return (int) Math.ceil(length * width * height / 5000.0 * 1000.0);
    }

    public int calculateChargeableWeight(int actualWeight, int volumetricWeight) {
        return Math.max(actualWeight, volumetricWeight);
    }

    public BigDecimal estimateShippingFee(Order order, int chargeableWeight) {
        boolean sameProvince = isSameProvinceWithShop(order.getAddressSnapshot());
        int baseFee = sameProvince ? 20000 : 30000;
        int extraFee = sameProvince ? 5000 : 8000;

        int extraWeight = Math.max(0, chargeableWeight - 500);
        int steps = (int) Math.ceil(extraWeight / 500.0);

        return BigDecimal.valueOf(baseFee + steps * extraFee);
    }

    private List<String> buildPackingWarnings(int length, int width, int height,
                                              int actualWeight, int volumetricWeight,
                                              BigDecimal shippingFeeDifference) {
        List<String> warnings = new ArrayList<>();

        if (length > 30 || width > 30 || height > 30) {
            warnings.add("Kích thước kiện hàng lớn, đơn vị vận chuyển có thể cân đo lại.");
        }

        if (actualWeight > 0 && volumetricWeight > actualWeight * 1.5) {
            warnings.add("Khối lượng quy đổi cao hơn nhiều so với cân nặng thực tế, có nguy cơ lệch cước.");
        }

        if (shippingFeeDifference.compareTo(BigDecimal.ZERO) > 0) {
            warnings.add("Phí ship ước tính cao hơn phí khách đã trả. Staff/Admin cần kiểm tra trước khi giao.");
        } else if (shippingFeeDifference.compareTo(BigDecimal.ZERO) < 0) {
            warnings.add("Phí ship ước tính thấp hơn phí khách đã trả.");
        }

        return warnings;
    }

    private boolean isSameProvinceWithShop(Map<String, Object> addressSnapshot) {
        if (addressSnapshot == null) {
            return false;
        }

        Object province = addressSnapshot.get("province");
        if (province == null) {
            return false;
        }

        String normalizedProvince = normalizeText(province.toString());
        String normalizedShop = normalizeText(SHOP_PROVINCE);
        return normalizedProvince.equals(normalizedShop);
    }

    private String normalizeText(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "");
        return normalized.toLowerCase().trim();
    }

    private BigDecimal safeFee(BigDecimal fee) {
        return fee != null ? fee : BigDecimal.ZERO;
    }
}
