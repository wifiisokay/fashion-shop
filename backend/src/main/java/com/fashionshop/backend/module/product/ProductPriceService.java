package com.fashionshop.backend.module.product;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.fashionshop.backend.domain.Product;
import com.fashionshop.backend.domain.ProductVariant;

@Service
public class ProductPriceService {

    public boolean isOnSale(Product product) {
        if (product == null) return false;
        if (!Boolean.TRUE.equals(product.getIsSale())) return false;
        if (product.getSalePrice() == null) return false;
        if (product.getBasePrice() == null) return false;
        if (product.getSalePrice().compareTo(BigDecimal.ZERO) <= 0) return false;
        if (product.getSalePrice().compareTo(product.getBasePrice()) >= 0) return false;

        LocalDateTime now = LocalDateTime.now();
        if (product.getSaleStartAt() != null && now.isBefore(product.getSaleStartAt())) {
            return false;
        }
        if (product.getSaleEndAt() != null && now.isAfter(product.getSaleEndAt())) {
            return false;
        }
        return true;
    }

    public BigDecimal getEffectivePrice(Product product) {
        if (product == null || product.getBasePrice() == null) {
            return BigDecimal.ZERO;
        }
        return isOnSale(product) ? product.getSalePrice() : product.getBasePrice();
    }

    public BigDecimal getFinalUnitPrice(Product product, ProductVariant variant) {
        BigDecimal price = getEffectivePrice(product);
        BigDecimal adjustment = variant != null && variant.getPriceAdjustment() != null
            ? variant.getPriceAdjustment()
            : BigDecimal.ZERO;
        return price.add(adjustment);
    }

    public int getDiscountPercent(Product product) {
        if (!isOnSale(product)) return 0;
        BigDecimal basePrice = product.getBasePrice();
        BigDecimal salePrice = product.getSalePrice();
        if (basePrice == null || basePrice.compareTo(BigDecimal.ZERO) <= 0) return 0;
        return basePrice.subtract(salePrice)
            .multiply(BigDecimal.valueOf(100))
            .divide(basePrice, 0, RoundingMode.HALF_UP)
            .intValue();
    }

    public long getTotalStock(Product product) {
        if (product == null || product.getVariants() == null) return 0L;
        return product.getVariants().stream()
            .map(ProductVariant::getStockQuantity)
            .filter(stock -> stock != null)
            .mapToLong(Integer::longValue)
            .sum();
    }

    public String getStockStatus(Product product) {
        long totalStock = getTotalStock(product);
        int threshold = product != null && product.getLowStockThreshold() != null
            ? product.getLowStockThreshold()
            : 10;
        if (totalStock <= 0) return "OUT_OF_STOCK";
        if (totalStock <= threshold) return "LOW_STOCK";
        return "IN_STOCK";
    }
}
