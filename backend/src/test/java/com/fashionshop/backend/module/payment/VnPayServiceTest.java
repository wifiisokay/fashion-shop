package com.fashionshop.backend.module.payment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VnPayServiceTest {

    private VnPayService sut;

    @BeforeEach
    void setUp() {
        VnPayProperties props = new VnPayProperties();
        props.setTmnCode("TEST_TMN_CODE");
        props.setHashSecret("TEST_HASH_SECRET_KEY_FOR_UNIT_TESTING");
        props.setUrl("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        props.setReturnUrl("http://localhost:8080/api/payment/vnpay-return");
        props.setVersion("2.1.0");
        props.setCommand("pay");
        props.setLocale("vn");

        sut = new VnPayService(props);
    }

    // ==================== buildPaymentUrl ====================

    @Nested
    @DisplayName("buildPaymentUrl")
    class BuildPaymentUrl {

        @Test
        @DisplayName("URL chứa sandbox domain")
        void containsSandboxDomain() {
            String url = sut.buildPaymentUrl(1L, BigDecimal.valueOf(350000), "127.0.0.1", "12320260429143022");

            assertTrue(url.startsWith("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?"));
        }

        @Test
        @DisplayName("URL chứa vnp_Amount = amount * 100 (đơn vị xu)")
        void amountMultipliedBy100() {
            String url = sut.buildPaymentUrl(1L, BigDecimal.valueOf(350000), "127.0.0.1", "txn123");

            // 350000 * 100 = 35000000
            assertTrue(url.contains("vnp_Amount=35000000"));
        }

        @Test
        @DisplayName("URL chứa vnp_TxnRef")
        void containsTxnRef() {
            String url = sut.buildPaymentUrl(1L, BigDecimal.valueOf(100000), "127.0.0.1", "my_txn_ref_001");

            assertTrue(url.contains("vnp_TxnRef=my_txn_ref_001"));
        }

        @Test
        @DisplayName("URL chứa vnp_SecureHash (HMAC SHA512)")
        void containsSecureHash() {
            String url = sut.buildPaymentUrl(1L, BigDecimal.valueOf(100000), "127.0.0.1", "txn123");

            assertTrue(url.contains("vnp_SecureHash="));
            // HMAC SHA512 = 128 hex characters
            String hash = url.substring(url.indexOf("vnp_SecureHash=") + 15);
            assertEquals(128, hash.length());
        }

        @Test
        @DisplayName("URL chứa vnp_TmnCode từ config")
        void containsTmnCode() {
            String url = sut.buildPaymentUrl(1L, BigDecimal.valueOf(100000), "127.0.0.1", "txn123");

            assertTrue(url.contains("vnp_TmnCode=TEST_TMN_CODE"));
        }

        @Test
        @DisplayName("URL chứa vnp_ReturnUrl")
        void containsReturnUrl() {
            String url = sut.buildPaymentUrl(1L, BigDecimal.valueOf(100000), "127.0.0.1", "txn123");

            // URL encoded
            assertTrue(url.contains("vnp_ReturnUrl="));
        }
    }

    // ==================== verifySignature ====================

    @Nested
    @DisplayName("verifySignature")
    class VerifySignature {

        @Test
        @DisplayName("Chữ ký hợp lệ — return true")
        void validSignature_returnsTrue() {
            // Tạo URL → lấy hash → verify lại
            String url = sut.buildPaymentUrl(1L, BigDecimal.valueOf(100000), "127.0.0.1", "txn123");

            // Extract params từ URL
            Map<String, String> params = new HashMap<>();
            String queryString = url.substring(url.indexOf("?") + 1);
            for (String param : queryString.split("&")) {
                String[] pair = param.split("=", 2);
                params.put(
                    java.net.URLDecoder.decode(pair[0], java.nio.charset.StandardCharsets.US_ASCII),
                    pair.length > 1 ? java.net.URLDecoder.decode(pair[1], java.nio.charset.StandardCharsets.US_ASCII) : ""
                );
            }

            String secureHash = params.get("vnp_SecureHash");
            assertTrue(sut.verifySignature(params, secureHash));
        }

        @Test
        @DisplayName("Chữ ký bị sửa — return false")
        void tamperedSignature_returnsFalse() {
            Map<String, String> params = new HashMap<>();
            params.put("vnp_TxnRef", "txn123");
            params.put("vnp_Amount", "10000000");
            params.put("vnp_ResponseCode", "00");

            assertFalse(sut.verifySignature(params, "fake_hash_tampered_by_attacker"));
        }

        @Test
        @DisplayName("Params bị tamper (sửa amount) — return false")
        void tamperedParams_returnsFalse() {
            // Tạo URL hợp lệ
            String url = sut.buildPaymentUrl(1L, BigDecimal.valueOf(100000), "127.0.0.1", "txn123");

            Map<String, String> params = new HashMap<>();
            String queryString = url.substring(url.indexOf("?") + 1);
            for (String param : queryString.split("&")) {
                String[] pair = param.split("=", 2);
                params.put(
                    java.net.URLDecoder.decode(pair[0], java.nio.charset.StandardCharsets.US_ASCII),
                    pair.length > 1 ? java.net.URLDecoder.decode(pair[1], java.nio.charset.StandardCharsets.US_ASCII) : ""
                );
            }

            String secureHash = params.get("vnp_SecureHash");

            // Tamper amount
            params.put("vnp_Amount", "99999999");

            assertFalse(sut.verifySignature(params, secureHash));
        }
    }

    // ==================== generateTxnRef ====================

    @Nested
    @DisplayName("generateTxnRef")
    class GenerateTxnRef {

        @Test
        @DisplayName("Bắt đầu bằng orderId")
        void startsWithOrderId() {
            String txnRef = sut.generateTxnRef(123L);

            assertTrue(txnRef.startsWith("123"));
        }

        @Test
        @DisplayName("Có 14 chữ số timestamp (yyyyMMddHHmmss)")
        void contains14DigitTimestamp() {
            String txnRef = sut.generateTxnRef(1L);

            // "1" + 14 digit timestamp = length >= 15
            assertTrue(txnRef.length() >= 15);
            // Phần sau orderId phải là số
            String timestampPart = txnRef.substring(1);
            assertTrue(timestampPart.matches("\\d{14}"));
        }

        @Test
        @DisplayName("2 lần gọi liên tiếp — txnRef khác nhau (timestamp khác)")
        void uniquePerCall() throws InterruptedException {
            String ref1 = sut.generateTxnRef(1L);
            Thread.sleep(1100); // đợi 1.1 giây để timestamp đổi
            String ref2 = sut.generateTxnRef(1L);

            assertNotEquals(ref1, ref2);
        }
    }
}
