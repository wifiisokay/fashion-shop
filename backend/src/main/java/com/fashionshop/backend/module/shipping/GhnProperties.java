package com.fashionshop.backend.module.shipping;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Bind GHN config từ application.properties.
 * Prefix: ghn.*
 */
@Component
@ConfigurationProperties(prefix = "ghn")
@Getter
@Setter
public class GhnProperties {

    /** API key từ GHN dashboard */
    private String token;

    /** Shop ID từ GHN dashboard */
    private String shopId;

    /** Base URL: sandbox hoặc production */
    private String baseUrl;

    private Shop shop = new Shop();
    private Defaults defaults = new Defaults();
    private Fallback fallback = new Fallback();
    private Cache cache = new Cache();

    @Getter @Setter
    public static class Shop {
        /** district_id kho hàng shop (theo GHN master data) */
        private int districtId = 1442;
        /** ward_code kho hàng shop */
        private String wardCode = "20308";
    }

    @Getter @Setter
    public static class Defaults {
        /** Trọng lượng mặc định (gram) */
        private int weight = 500;
        /** Kích thước dài (cm) */
        private int length = 30;
        /** Kích thước rộng (cm) */
        private int width = 20;
        /** Kích thước cao (cm) */
        private int height = 10;
    }

    @Getter @Setter
    public static class Fallback {
        /** Phí ship fallback khi GHN lỗi (VND) */
        private long fee = 30000;
        /** Số ngày giao dự kiến fallback */
        private int days = 3;
    }

    @Getter @Setter
    public static class Cache {
        /** TTL cache phí ship (phút) */
        private int ttlMinutes = 5;
        /** Max entries trong cache */
        private int maxSize = 500;
    }
}
