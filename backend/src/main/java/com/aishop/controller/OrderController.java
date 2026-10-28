package com.aishop.controller;

import com.aishop.dto.OrderDtos.*;
import com.aishop.entity.Invoice;
import com.aishop.security.AuthUser;
import com.aishop.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/checkout")
    public CheckoutResponse checkout(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody CheckoutRequest req) {
        return orderService.checkout(user.id(), req);
    }

    @GetMapping
    public List<OrderResponse> mine(@AuthenticationPrincipal AuthUser user) {
        return orderService.myOrders(user);
    }

    @GetMapping("/{id}")
    public OrderResponse get(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return orderService.get(user, id);
    }

    @GetMapping("/{id}/invoice")
    public ResponseEntity<byte[]> invoice(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        Invoice invoice = orderService.invoice(user, id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(invoice.getInvoiceNumber() + ".pdf").build().toString())
                .body(invoice.getPdf());
    }
}
