package com.aishop.controller;

import com.aishop.dto.PaymentDtos.PaymentFailedRequest;
import com.aishop.dto.PaymentDtos.VerifyPaymentRequest;
import com.aishop.security.AuthUser;
import com.aishop.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/verify")
    public Map<String, Object> verify(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody VerifyPaymentRequest req) {
        Long orderId = paymentService.verify(user.id(), req);
        return Map.of("orderId", orderId, "status", "PAID");
    }

    @PostMapping("/failed")
    public ResponseEntity<Void> failed(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody PaymentFailedRequest req) {
        paymentService.markFailed(user.id(), req.orderId());
        return ResponseEntity.noContent().build();
    }

    /** Called by Razorpay, authenticated by the HMAC signature over the raw body instead of a JWT. */
    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(@RequestBody String rawBody,
                                        @RequestHeader(name = "X-Razorpay-Signature", required = false) String signature) {
        paymentService.handleWebhook(rawBody, signature);
        return ResponseEntity.ok().build();
    }
}
