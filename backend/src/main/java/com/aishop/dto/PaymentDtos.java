package com.aishop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class PaymentDtos {

    private PaymentDtos() {
    }

    /** The three values Razorpay Checkout hands back to the browser after a successful payment. */
    public record VerifyPaymentRequest(
            @NotNull Long orderId,
            @NotBlank String razorpayOrderId,
            @NotBlank String razorpayPaymentId,
            @NotBlank String razorpaySignature) {
    }

    public record PaymentFailedRequest(@NotNull Long orderId, String reason) {
    }
}
