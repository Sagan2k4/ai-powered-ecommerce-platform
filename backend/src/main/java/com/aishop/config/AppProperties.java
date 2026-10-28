package com.aishop.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Cors cors, Razorpay razorpay, OpenAi openai, Admin admin, Store store) {

    /** {@code secret} is a Base64-encoded key of at least 256 bits. */
    public record Jwt(String secret, Duration expiration) {
    }

    public record Cors(List<String> allowedOrigins) {
    }

    /** With {@code mock = true} checkout skips Razorpay entirely, so the app can be demoed without keys. */
    public record Razorpay(String keyId, String keySecret, String webhookSecret, String currency, boolean mock) {
    }

    public record OpenAi(String apiKey, String model, String baseUrl, Duration timeout) {
        public boolean enabled() {
            return apiKey != null && !apiKey.isBlank();
        }
    }

    public record Admin(String name, String email, String password) {
    }

    /** Seller details printed on invoices. */
    public record Store(String name, String address, String gstin, String supportEmail) {
    }
}
