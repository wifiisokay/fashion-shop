package com.fashionshop.backend.module.shipping;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fashionshop.backend.common.enums.ProductStatus;
import com.fashionshop.backend.domain.Address;
import com.fashionshop.backend.domain.CartItem;
import com.fashionshop.backend.domain.Order;
import com.fashionshop.backend.domain.Product;
import com.fashionshop.backend.domain.ProductVariant;
import com.fashionshop.backend.domain.ShopConfig;
import com.fashionshop.backend.domain.repository.AddressRepository;
import com.fashionshop.backend.domain.repository.CartItemRepository;
import com.fashionshop.backend.domain.repository.ProductVariantRepository;
import com.fashionshop.backend.domain.repository.ShopConfigRepository;
import com.fashionshop.backend.exception.BusinessException;
import com.fashionshop.backend.exception.ErrorCode;
import com.fashionshop.backend.module.order.dto.request.ConfirmPackingRequest;
import com.fashionshop.backend.module.order.dto.request.OrderItemRequest;
import com.fashionshop.backend.module.product.ProductPriceService;
import com.fashionshop.backend.module.shipping.cache.ShippingCacheService;
import com.fashionshop.backend.module.shipping.dto.ghn.GhnFeeRequest;
import com.fashionshop.backend.module.shipping.dto.ghn.GhnFeeResponse;
import com.fashionshop.backend.module.shipping.dto.response.ShippingFeeResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShippingCalculationService {

    public static final String PROVIDER = "GHN_SANDBOX";
    private static final String CUSTOMER_GHN_ERROR = "Khong the tinh phi van chuyen GHN, vui long thu lai.";

    private final AddressRepository addressRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductVariantRepository variantRepository;
    private final ShopConfigRepository shopConfigRepository;
    private final GhnApiClient ghnApiClient;
    private final GhnProperties props;
    private final ShippingPackageEstimator packageEstimator;
    private final ShippingCacheService cacheService;
    private final ProductPriceService productPriceService;

    @Transactional(readOnly = true)
    public CheckoutShippingFeeResult calculateCheckoutFee(Long userId, Long addressId, List<OrderItemRequest> items) {
        return calculateCheckoutFee(userId, addressId, normalizeRequestItemsOrCart(userId, items));
    }

    @Transactional(readOnly = true)
    public CheckoutShippingFeeResult calculateCheckoutFee(Long userId, Long addressId, Map<Long, Integer> items) {
        Address address = loadUserAddress(userId, addressId);
        List<ProductVariant> variants = loadAndValidateVariants(items);
        List<ShippingPackageItem> packageItems = new ArrayList<>();
        long insuranceValue = 0;

        for (ProductVariant variant : variants) {
            int quantity = items.get(variant.getId());
            Product product = variant.getProduct();
            packageItems.add(new ShippingPackageItem(product, quantity));
            BigDecimal unitPrice = productPriceService.getFinalUnitPrice(product, variant);
            insuranceValue += unitPrice.multiply(BigDecimal.valueOf(quantity)).longValue();
        }

        ShippingPackageEstimate estimate = packageEstimator.estimate(packageItems);
        ShippingFeeResponse response;
        try {
            response = calculateGhnFeeOrThrow(address.getDistrictCode(), address.getWardCode(), estimate,
                    insuranceValue);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SHIPPING_SERVICE_UNAVAILABLE, HttpStatus.BAD_GATEWAY,
                    CUSTOMER_GHN_ERROR);
        }

        LocalDate expectedDate = response.getEstimatedDays() > 0
                ? LocalDate.now().plusDays(response.getEstimatedDays())
                : null;

        return new CheckoutShippingFeeResult(
                PROVIDER,
                response.getShippingFee(),
                estimate.weight(),
                estimate.length(),
                estimate.width(),
                estimate.height(),
                response.getServiceId(),
                response.getEstimatedDays(),
                response.getEstimatedDateText(),
                expectedDate,
                response.isCached());
    }

    @Transactional(readOnly = true)
    public ShippingFeeResponse calculatePreviewResponse(Long userId, Long addressId, List<OrderItemRequest> items) {
        CheckoutShippingFeeResult result = calculateCheckoutFee(userId, addressId, items);
        return ShippingFeeResponse.builder()
                .provider(result.provider())
                .fee(result.shippingFee())
                .shippingFee(result.shippingFee())
                .estimatedWeight(result.estimatedWeight())
                .packageLength(result.packageLength())
                .packageWidth(result.packageWidth())
                .packageHeight(result.packageHeight())
                .serviceId(result.serviceId())
                .estimatedDays(result.estimatedDays())
                .estimatedDateText(result.estimatedDateText())
                .serviceName("Giao hang chuan")
                .cached(result.cached())
                .fallback(false)
                .build();
    }

    public ActualShippingFeeResult calculateActualFee(Order order, ConfirmPackingRequest request) {
        ShippingPackageEstimate actualPackage = new ShippingPackageEstimate(
                request.getActualWeight(),
                request.getPackageLength(),
                request.getPackageWidth(),
                request.getPackageHeight());
        Destination destination = resolveDestination(order);
        ShippingFeeResponse response = calculateGhnFeeOrThrow(destination.districtCode(), destination.wardCode(),
                actualPackage, order.getSubtotal() != null ? order.getSubtotal().longValue() : 0);
        BigDecimal actualFee = BigDecimal.valueOf(response.getShippingFee());
        return new ActualShippingFeeResult(
                actualFee,
                actualFee.subtract(safeFee(order.getShippingFee())),
                request.getActualWeight(),
                request.getPackageLength(),
                request.getPackageWidth(),
                request.getPackageHeight());
    }

    private ShippingFeeResponse calculateGhnFeeOrThrow(int toDistrictId, String toWardCode,
            ShippingPackageEstimate estimate, long insuranceValue) {
        if (toDistrictId <= 0 || toWardCode == null || toWardCode.isBlank()) {
            throw new BusinessException(ErrorCode.SHIPPING_SERVICE_UNAVAILABLE, HttpStatus.BAD_REQUEST,
                    "Dia chi giao hang thieu ma GHN district/ward.");
        }

        ShopConfig shopConfig = shopConfigRepository.getConfig();
        int serviceId = resolveServiceId(shopConfig.getDistrictId(), toDistrictId);
        String cacheKey = ShippingCacheService.buildKey(toDistrictId, toWardCode, estimate.weight(), estimate.length(),
                estimate.width(), estimate.height(), serviceId);

        Optional<ShippingFeeResponse> cached = cacheService.get(cacheKey);
        if (cached.isPresent()) {
            ShippingFeeResponse value = cached.get();
            return ShippingFeeResponse.builder()
                    .provider(PROVIDER)
                    .fee(value.getFee())
                    .shippingFee(value.getShippingFee())
                    .estimatedWeight(estimate.weight())
                    .packageLength(estimate.length())
                    .packageWidth(estimate.width())
                    .packageHeight(estimate.height())
                    .serviceId(serviceId)
                    .estimatedDays(value.getEstimatedDays())
                    .estimatedDateText(value.getEstimatedDateText())
                    .serviceName(value.getServiceName())
                    .cached(true)
                    .fallback(false)
                    .build();
        }

        GhnFeeRequest feeRequest = GhnFeeRequest.builder()
                .serviceId(serviceId)
                .insuranceValue(Math.max(insuranceValue, 0))
                .fromDistrictId(shopConfig.getDistrictId())
                .fromWardCode(shopConfig.getWardCode())
                .toDistrictId(toDistrictId)
                .toWardCode(toWardCode)
                .weight(estimate.weight())
                .length(estimate.length())
                .width(estimate.width())
                .height(estimate.height())
                .build();

        GhnFeeResponse ghnResponse = ghnApiClient.calculateFee(feeRequest);
        if (ghnResponse == null || ghnResponse.getData() == null || ghnResponse.getCode() != 200) {
            throw new BusinessException(ErrorCode.SHIPPING_SERVICE_UNAVAILABLE, HttpStatus.BAD_GATEWAY,
                    CUSTOMER_GHN_ERROR);
        }

        long fee = ghnResponse.getData().getTotal();
        int estimatedDays = estimateDays(ghnResponse.getData().getExpectedDeliveryTime());
        ShippingFeeResponse response = ShippingFeeResponse.builder()
                .provider(PROVIDER)
                .fee(fee)
                .shippingFee(fee)
                .estimatedWeight(estimate.weight())
                .packageLength(estimate.length())
                .packageWidth(estimate.width())
                .packageHeight(estimate.height())
                .serviceId(serviceId)
                .estimatedDays(estimatedDays)
                .estimatedDateText(buildEstimatedDateText(estimatedDays))
                .serviceName("Giao hang chuan")
                .cached(false)
                .fallback(false)
                .build();
        cacheService.put(cacheKey, response);
        return response;
    }

    private Map<Long, Integer> normalizeRequestItemsOrCart(Long userId, List<OrderItemRequest> items) {
        if (items == null || items.isEmpty()) {
            List<CartItem> cartItems = cartItemRepository.findByUserIdWithDetails(userId);
            if (cartItems.isEmpty()) {
                throw new BusinessException(ErrorCode.CART_EMPTY, HttpStatus.BAD_REQUEST);
            }
            Map<Long, Integer> fromCart = new LinkedHashMap<>();
            for (CartItem item : cartItems) {
                fromCart.put(item.getVariant().getId(), item.getQuantity());
            }
            return fromCart;
        }

        Map<Long, Integer> normalized = new LinkedHashMap<>();
        for (OrderItemRequest item : items) {
            if (item == null || item.getVariantId() == null || item.getQuantity() < 1) {
                throw new BusinessException(ErrorCode.ORDER_ITEMS_EMPTY, HttpStatus.BAD_REQUEST);
            }
            normalized.merge(item.getVariantId(), item.getQuantity(), Integer::sum);
        }
        return normalized;
    }

    private Address loadUserAddress(Long userId, Long addressId) {
        return addressRepository.findByIdAndUser_Id(addressId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ADDRESS_NOT_BELONG_TO_USER, HttpStatus.FORBIDDEN));
    }

    private List<ProductVariant> loadAndValidateVariants(Map<Long, Integer> items) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException(ErrorCode.ORDER_ITEMS_EMPTY, HttpStatus.BAD_REQUEST);
        }
        List<ProductVariant> variants = new ArrayList<>();
        for (Long variantId : items.keySet()) {
            ProductVariant variant = variantRepository.findById(variantId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.VARIANT_NOT_FOUND, HttpStatus.NOT_FOUND));
            Product product = variant.getProduct();
            if (product.getStatus() != ProductStatus.ACTIVE) {
                throw new BusinessException(ErrorCode.PRODUCT_OUT_OF_STOCK, HttpStatus.BAD_REQUEST,
                        "San pham " + product.getName() + " khong con ban");
            }
            int quantity = items.get(variantId);
            if (variant.getStockQuantity() == null || variant.getStockQuantity() < quantity) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK, HttpStatus.BAD_REQUEST,
                        "San pham " + product.getName() + " khong du ton kho");
            }
            variants.add(variant);
        }
        return variants;
    }

    private int resolveServiceId(int fromDistrictId, int toDistrictId) {
        int serviceId = ghnApiClient.getAvailableServiceId(fromDistrictId, toDistrictId);
        if (serviceId <= 0) {
            throw new BusinessException(ErrorCode.SHIPPING_SERVICE_UNAVAILABLE, HttpStatus.BAD_GATEWAY,
                    "Khong tim thay dich vu GHN phu hop cho tuyen giao hang.");
        }
        return serviceId;
    }

    private Destination resolveDestination(Order order) {
        Map<String, Object> snapshot = order.getAddressSnapshot();
        if (snapshot == null) {
            throw new IllegalStateException("Order address snapshot is missing");
        }
        Object district = snapshot.get("districtCode");
        Object ward = snapshot.get("wardCode");
        if (!(district instanceof Number) || ward == null || ward.toString().isBlank()) {
            throw new IllegalStateException("Order address snapshot lacks GHN district/ward code");
        }
        return new Destination(((Number) district).intValue(), ward.toString());
    }

    private int estimateDays(String expectedDeliveryTime) {
        if (expectedDeliveryTime == null || expectedDeliveryTime.isBlank()) {
            return props.getFallback().getDays();
        }
        try {
            long epoch = Long.parseLong(expectedDeliveryTime);
            LocalDate deliveryDate = Instant.ofEpochSecond(epoch).atZone(ZoneId.systemDefault()).toLocalDate();
            return Math.max((int) (deliveryDate.toEpochDay() - LocalDate.now().toEpochDay()), 1);
        } catch (NumberFormatException ignored) {
            try {
                LocalDate deliveryDate = Instant.parse(expectedDeliveryTime).atZone(ZoneId.systemDefault())
                        .toLocalDate();
                return Math.max((int) (deliveryDate.toEpochDay() - LocalDate.now().toEpochDay()), 1);
            } catch (Exception e) {
                return props.getFallback().getDays();
            }
        }
    }

    private static String buildEstimatedDateText(int estimatedDays) {
        LocalDate target = LocalDate.now().plusDays(estimatedDays);
        return "Du kien giao " + vietnameseDayOfWeek(target.getDayOfWeek()) + ", "
                + target.format(DateTimeFormatter.ofPattern("dd/MM"));
    }

    private static String vietnameseDayOfWeek(DayOfWeek dow) {
        return switch (dow) {
            case MONDAY -> "Thu 2";
            case TUESDAY -> "Thu 3";
            case WEDNESDAY -> "Thu 4";
            case THURSDAY -> "Thu 5";
            case FRIDAY -> "Thu 6";
            case SATURDAY -> "Thu 7";
            case SUNDAY -> "Chu Nhat";
        };
    }

    private BigDecimal safeFee(BigDecimal fee) {
        return fee != null ? fee : BigDecimal.ZERO;
    }

    private record Destination(int districtCode, String wardCode) {}
}
