package com.aishop.service;

import com.aishop.config.AppProperties;
import com.aishop.dto.PaymentDtos.VerifyPaymentRequest;
import com.aishop.entity.Order;
import com.aishop.entity.OrderItem;
import com.aishop.entity.OrderStatus;
import com.aishop.exception.ApiException;
import com.aishop.repository.CartItemRepository;
import com.aishop.repository.OrderRepository;
import com.aishop.repository.ProductRepository;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Razorpay integration: creates gateway orders at checkout and confirms payments, either from the browser
 * callback (signature over {@code order_id|payment_id}) or from the server-to-server webhook (signature over
 * the raw body). Both paths are idempotent and converge on {@link #confirmPayment}.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final String MOCK_SIGNATURE = "mock_signature";

    private final AppProperties.Razorpay config;
    private final OrderRepository orders;
    private final ProductRepository products;
    private final CartItemRepository cartItems;
    private final ProductService productService;
    private final ApplicationEventPublisher events;

    public PaymentService(AppProperties props, OrderRepository orders, ProductRepository products,
                          CartItemRepository cartItems, ProductService productService,
                          ApplicationEventPublisher events) {
        this.config = props.razorpay();
        this.orders = orders;
        this.products = products;
        this.cartItems = cartItems;
        this.productService = productService;
        this.events = events;
    }

    /** Creates the Razorpay order the checkout widget is opened against and returns its id. */
    public String createGatewayOrder(Long orderId, long amountInPaise) {
        if (config.mock()) {
            return "order_mock_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        }
        try {
            RazorpayClient client = new RazorpayClient(config.keyId(), config.keySecret());
            JSONObject request = new JSONObject()
                    .put("amount", amountInPaise)
                    .put("currency", config.currency())
                    .put("receipt", "order_" + orderId)
                    .put("notes", new JSONObject().put("orderId", orderId));
            com.razorpay.Order rzpOrder = client.orders.create(request);
            return rzpOrder.get("id");
        } catch (RazorpayException e) {
            log.error("Razorpay order creation failed for order {}", orderId, e);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Payment gateway is unavailable, please try again");
        }
    }

    @Transactional
    public Long verify(Long userId, VerifyPaymentRequest req) {
        Order order = orders.findWithItemsById(req.orderId())
                .filter(o -> o.getUser().getId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("Order"));
        if (!req.razorpayOrderId().equals(order.getRazorpayOrderId())) {
            throw ApiException.badRequest("Payment does not belong to this order");
        }
        boolean valid = config.mock()
                ? MOCK_SIGNATURE.equals(req.razorpaySignature())
                : signatureMatches(req.razorpayOrderId() + "|" + req.razorpayPaymentId(), req.razorpaySignature(),
                config.keySecret());
        if (!valid) {
            log.warn("Rejected payment with invalid signature for order {}", order.getId());
            throw ApiException.badRequest("Payment verification failed");
        }
        confirmPayment(order, req.razorpayPaymentId());
        return order.getId();
    }

    @Transactional
    public void markFailed(Long userId, Long orderId) {
        orders.findById(orderId)
                .filter(o -> o.getUser().getId().equals(userId) && o.getStatus() == OrderStatus.PENDING_PAYMENT)
                .ifPresent(o -> o.setStatus(OrderStatus.PAYMENT_FAILED));
    }

    /** Handles Razorpay webhooks so orders are confirmed even if the customer closes the tab mid-payment. */
    @Transactional
    public void handleWebhook(String rawBody, String signature) {
        if (config.webhookSecret() == null || config.webhookSecret().isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Webhook secret not configured");
        }
        if (signature == null || !signatureMatches(rawBody, signature, config.webhookSecret())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid webhook signature");
        }
        JSONObject event = new JSONObject(rawBody);
        String type = event.optString("event");
        if (!"payment.captured".equals(type) && !"order.paid".equals(type)) {
            return;
        }
        JSONObject payment = event.getJSONObject("payload").getJSONObject("payment").getJSONObject("entity");
        String rzpOrderId = payment.optString("order_id");
        orders.findByRazorpayOrderId(rzpOrderId)
                .ifPresentOrElse(o -> confirmPayment(o, payment.getString("id")),
                        () -> log.warn("Webhook {} for unknown Razorpay order {}", type, rzpOrderId));
    }

    private void confirmPayment(Order order, String paymentId) {
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT && order.getStatus() != OrderStatus.PAYMENT_FAILED) {
            return; // already confirmed by the other path
        }
        for (OrderItem item : order.getItems()) {
            if (item.getProduct() != null && products.decrementStock(item.getProduct().getId(), item.getQuantity()) == 0) {
                // Money is already captured; flag for manual fulfilment/refund rather than failing the order.
                log.warn("Insufficient stock for product {} on paid order {}", item.getProduct().getId(), order.getId());
            }
        }
        order.markPaid(paymentId);
        cartItems.deleteByUserId(order.getUser().getId());
        productService.evictStockCaches();
        events.publishEvent(new OrderPaidEvent(order.getId(), order.getUser().getId()));
        log.info("Order {} paid via {}", order.getId(), paymentId);
    }

    static boolean signatureMatches(String payload, String signature, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
            // Constant-time comparison to avoid leaking the signature through timing.
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }
}
