package com.aishop.service;

import com.aishop.config.AppProperties;
import com.aishop.dto.OrderDtos.*;
import com.aishop.entity.*;
import com.aishop.exception.ApiException;
import com.aishop.repository.*;
import com.aishop.security.AuthUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
public class OrderService {

    static final Set<OrderStatus> PAID_STATUSES = EnumSet.of(OrderStatus.PAID, OrderStatus.SHIPPED, OrderStatus.DELIVERED);

    private final OrderRepository orders;
    private final CartItemRepository cartItems;
    private final UserRepository users;
    private final ProductRepository products;
    private final InvoiceRepository invoices;
    private final PaymentService paymentService;
    private final AppProperties props;

    public OrderService(OrderRepository orders, CartItemRepository cartItems, UserRepository users,
                        ProductRepository products, InvoiceRepository invoices, PaymentService paymentService,
                        AppProperties props) {
        this.orders = orders;
        this.cartItems = cartItems;
        this.users = users;
        this.products = products;
        this.invoices = invoices;
        this.paymentService = paymentService;
        this.props = props;
    }

    /**
     * Turns the cart into a PENDING_PAYMENT order and opens a matching Razorpay order. The cart is only
     * cleared once payment is verified, so an abandoned checkout loses nothing.
     */
    @Transactional
    public CheckoutResponse checkout(Long userId, CheckoutRequest req) {
        List<CartItem> cart = cartItems.findByUserIdOrderByIdAsc(userId);
        if (cart.isEmpty()) {
            throw ApiException.badRequest("Your cart is empty");
        }
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        Order order = new Order(user, req.shippingName().trim(), req.shippingPhone().trim(), req.shippingAddress().trim());
        for (CartItem ci : cart) {
            Product p = ci.getProduct();
            if (!p.isActive()) {
                throw ApiException.badRequest("\"" + p.getName() + "\" is no longer available");
            }
            if (p.getStock() < ci.getQuantity()) {
                throw ApiException.badRequest("Only " + p.getStock() + " of \"" + p.getName() + "\" left in stock");
            }
            order.addItem(new OrderItem(p, ci.getQuantity()));
        }
        orders.save(order);

        long amountInPaise = order.getTotalAmount().multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP).longValueExact();
        String razorpayOrderId = paymentService.createGatewayOrder(order.getId(), amountInPaise);
        order.setRazorpayOrderId(razorpayOrderId);

        var rzp = props.razorpay();
        return new CheckoutResponse(order.getId(), razorpayOrderId, amountInPaise, rzp.currency(), rzp.keyId(),
                rzp.mock(), user.getName(), user.getEmail(), order.getShippingPhone());
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> myOrders(AuthUser user) {
        return orders.findByUserIdOrderByCreatedAtDesc(user.id()).stream()
                .map(o -> toResponse(o, user.email()))
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse get(AuthUser requester, Long orderId) {
        Order order = findVisible(requester, orderId);
        return toResponse(order, order.getUser().getEmail());
    }

    @Transactional(readOnly = true)
    public Invoice invoice(AuthUser requester, Long orderId) {
        findVisible(requester, orderId);
        return invoices.findByOrderId(orderId)
                .orElseThrow(() -> ApiException.notFound("Invoice for this order"));
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> all(int page, int size) {
        return orders.findAllByOrderByCreatedAtDesc(PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100)))
                .map(o -> toResponse(o, o.getUser().getEmail()));
    }

    @Transactional
    public OrderResponse updateStatus(Long orderId, OrderStatus status) {
        Order order = orders.findWithItemsById(orderId).orElseThrow(() -> ApiException.notFound("Order"));
        if (order.getStatus() == OrderStatus.PENDING_PAYMENT && PAID_STATUSES.contains(status)) {
            throw ApiException.badRequest("An unpaid order cannot be marked " + status);
        }
        order.setStatus(status);
        return toResponse(order, order.getUser().getEmail());
    }

    @Transactional(readOnly = true)
    public DashboardStats stats() {
        return new DashboardStats(products.count(), users.count(), orders.countByStatusIn(PAID_STATUSES),
                orders.countByStatusIn(EnumSet.of(OrderStatus.PENDING_PAYMENT)), orders.sumRevenue(PAID_STATUSES));
    }

    /** Customers see only their own orders; admins see all. Others get 404 so ids are not enumerable. */
    private Order findVisible(AuthUser requester, Long orderId) {
        return orders.findWithItemsById(orderId)
                .filter(o -> requester.role() == Role.ADMIN || o.getUser().getId().equals(requester.id()))
                .orElseThrow(() -> ApiException.notFound("Order"));
    }

    private OrderResponse toResponse(Order o, String email) {
        return OrderResponse.from(o, invoices.existsByOrderId(o.getId()), email);
    }
}
