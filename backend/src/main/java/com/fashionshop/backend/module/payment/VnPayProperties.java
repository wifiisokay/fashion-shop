package com.fashionshop.backend.module.payment;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * VNPay configuration — bind từ application.properties.
 * Prefix: vnpay.*
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "vnpay")
public class VnPayProperties {

    private String tmnCode;
    private String hashSecret;
    private String url;
    private String returnUrl;
    private String ipnUrl;

    /** Version VNPay API (mặc định 2.1.0). */
    private String version = "2.1.0";

    /** Command type (mặc định "pay"). */
    private String command = "pay";

    /** Locale hiển thị trên trang VNPay (mặc định "vn"). */
    private String locale = "vn";
}
