package com.fashionshop.backend.module.payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * VNPay utility service — build payment URL, verify HMAC SHA512 signature.
 * Không có state, chỉ chứa logic crypto + URL building.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VnPayService {

    private static final DateTimeFormatter VN_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final VnPayProperties vnPayProperties;

    // ================================================================
    // Build Payment URL
    // ================================================================

    /**
     * Tạo VNPay payment URL cho customer redirect.
     *
     * @param orderId   ID đơn hàng
     * @param amount    Số tiền thanh toán (VND)
     * @param ipAddress IP của customer
     * @param txnRef    Mã giao dịch duy nhất (orderId + timestamp)
     * @return Full VNPay URL kèm HMAC signature
     */
    public String buildPaymentUrl(Long orderId, BigDecimal amount, String ipAddress, String txnRef) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expireDate = now.plusMinutes(15);

        Map<String, String> params = new TreeMap<>(); // TreeMap tự sort alphabetically
        params.put("vnp_Version", vnPayProperties.getVersion());
        params.put("vnp_Command", vnPayProperties.getCommand());
        params.put("vnp_TmnCode", vnPayProperties.getTmnCode());
        params.put("vnp_Amount", amount.multiply(BigDecimal.valueOf(100)).toBigInteger().toString());
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", txnRef);
        params.put("vnp_OrderInfo", "Ma don hang: " + orderId);
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", vnPayProperties.getLocale());
        params.put("vnp_ReturnUrl", vnPayProperties.getReturnUrl());
        params.put("vnp_IpAddr", ipAddress);
        params.put("vnp_CreateDate", now.format(VN_DATE_FMT));
        params.put("vnp_ExpireDate", expireDate.format(VN_DATE_FMT));

        // Build query string (đã sorted vì TreeMap)
        String queryString = buildQueryString(params);

        // HMAC SHA512
        String secureHash = hmacSHA512(vnPayProperties.getHashSecret(), queryString);

        return vnPayProperties.getUrl() + "?" + queryString + "&vnp_SecureHash=" + secureHash;
    }

    // ================================================================
    // Verify Signature
    // ================================================================

    /**
     * Xác thực chữ ký HMAC SHA512 từ VNPay callback (IPN / Return URL).
     * Remove vnp_SecureHash + vnp_SecureHashType khỏi params trước khi tính lại.
     *
     * @param params     Tất cả params từ VNPay callback
     * @param secureHash Chữ ký VNPay gửi về
     * @return true nếu chữ ký hợp lệ
     */
    public boolean verifySignature(Map<String, String> params, String secureHash) {
        // Copy params, loại bỏ hash fields
        Map<String, String> filtered = new TreeMap<>(params);
        filtered.remove("vnp_SecureHash");
        filtered.remove("vnp_SecureHashType");

        String queryString = buildQueryString(filtered);
        String calculatedHash = hmacSHA512(vnPayProperties.getHashSecret(), queryString);

        return calculatedHash.equalsIgnoreCase(secureHash);
    }

    // ================================================================
    // Generate Transaction Reference
    // ================================================================

    /**
     * Sinh mã giao dịch duy nhất: orderId + timestamp.
     * VD: orderId=123 → "12320260429143022"
     */
    public String generateTxnRef(Long orderId) {
        return orderId + LocalDateTime.now().format(VN_DATE_FMT);
    }

    // ================================================================
    // Private helpers
    // ================================================================

    private String buildQueryString(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (sb.length() > 0) sb.append("&");
            sb.append(URLEncoder.encode(entry.getKey(), StandardCharsets.US_ASCII));
            sb.append("=");
            sb.append(URLEncoder.encode(entry.getValue(), StandardCharsets.US_ASCII));
        }
        return sb.toString();
    }

    private String hmacSHA512(String key, String data) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA512");
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            hmac.init(secretKey);
            byte[] hash = hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            log.error("HMAC SHA512 error", e);
            throw new RuntimeException("Lỗi tạo chữ ký HMAC SHA512", e);
        }
    }
}
