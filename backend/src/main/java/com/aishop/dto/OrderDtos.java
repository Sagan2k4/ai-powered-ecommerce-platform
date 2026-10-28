package com.aishop.dto;

import com.aishop.entity.Order;
import com.aishop.entity.OrderItem;
import com.aishop.entity.OrderStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class OrderDtos {

    private OrderDtos() {
    }

    public record CheckoutRequest(
            @NotBlank @Size(max = 100) String shippingName,
            @NotBlank @Pattern(regexp = "^[0-9+\\- ]{7,20}$", message = "is not a valid phone number") String shippingPhone,
            @NotBlank @Size(max = 500) String shippingAddress) {
    }

    /** Everything the browser needs to open Razorpay Checkout for this order. */
    public record CheckoutResponse(Long orderId, String razorpayOrderId, long amountInPaise, String currency,
                                   String razorpayKeyId, boolean mockPayment, String customerName,
                                   String customerEmail, String customerPhone) {
    }

    public record OrderItemResponse(Long productId, String productName, BigDecimal unitPrice, int quantity,
                                    BigDecimal lineTotal) {
        static OrderItemResponse from(OrderItem i) {
            return new OrderItemResponse(i.getProduct() == null ? null : i.getProduct().getId(), i.getProductName(),
                    i.getUnitPrice(), i.getQuantity(), i.getLineTotal());
        }
    }

    public record OrderResponse(Long id, OrderStatus status, BigDecimal totalAmount, String shippingName,
                                String shippingPhone, String shippingAddress, String razorpayOrderId,
                                String razorpayPaymentId, Instant createdAt, Instant paidAt,
                                boolean invoiceAvailable, String customerEmail, List<OrderItemResponse> items) {
        public static OrderResponse from(Order o, boolean invoiceAvailable, String customerEmail) {
            return new OrderResponse(o.getId(), o.getStatus(), o.getTotalAmount(), o.getShippingName(),
                    o.getShippingPhone(), o.getShippingAddress(), o.getRazorpayOrderId(), o.getRazorpayPaymentId(),
                    o.getCreatedAt(), o.getPaidAt(), invoiceAvailable, customerEmail,
                    o.getItems().stream().map(OrderItemResponse::from).toList());
        }
    }

    public record UpdateStatusRequest(@NotNull OrderStatus status) {
    }

    public record DashboardStats(long products, long users, long paidOrders, long pendingOrders, BigDecimal revenue) {
    }
}
