package com.aishop.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentSignatureTest {

    @Test
    void acceptsRazorpayStyleHmacAndRejectsTampering() {
        // HMAC-SHA256("order_abc|pay_xyz", "secret") — the scheme Razorpay uses for checkout callbacks.
        String signature = "8a6a5d6c3a4a1f1ae3a5b5bd2e7fb8f8b1b9d1e8b4e0d5a0b6d8e0c9e0f6b2a4";
        String payload = "order_abc|pay_xyz";
        String real = hmac(payload, "secret");

        assertThat(PaymentService.signatureMatches(payload, real, "secret")).isTrue();
        assertThat(PaymentService.signatureMatches(payload, signature, "secret")).isFalse();
        assertThat(PaymentService.signatureMatches("order_abc|pay_other", real, "secret")).isFalse();
        assertThat(PaymentService.signatureMatches(payload, real, "wrong-secret")).isFalse();
    }

    private static String hmac(String payload, String secret) {
        try {
            var mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(), "HmacSHA256"));
            return java.util.HexFormat.of().formatHex(mac.doFinal(payload.getBytes()));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
