package com.aishop.controller;

import com.aishop.dto.OrderDtos.DashboardStats;
import com.aishop.dto.OrderDtos.OrderResponse;
import com.aishop.dto.OrderDtos.UpdateStatusRequest;
import com.aishop.dto.ProductDtos.ProductRequest;
import com.aishop.dto.ProductDtos.ProductResponse;
import com.aishop.service.OrderService;
import com.aishop.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Admin-only operations. Guarded both by the URL rule in SecurityConfig and by method security here. */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final ProductService productService;
    private final OrderService orderService;

    public AdminController(ProductService productService, OrderService orderService) {
        this.productService = productService;
        this.orderService = orderService;
    }

    @GetMapping("/stats")
    public DashboardStats stats() {
        return orderService.stats();
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@Valid @RequestBody ProductRequest req) {
        return productService.create(req);
    }

    @PutMapping("/products/{id}")
    public ProductResponse update(@PathVariable Long id, @Valid @RequestBody ProductRequest req) {
        return productService.update(id, req);
    }

    @DeleteMapping("/products/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        productService.deactivate(id);
    }

    @GetMapping("/orders")
    public PagedModel<OrderResponse> orders(@RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return new PagedModel<>(orderService.all(page, size));
    }

    @PatchMapping("/orders/{id}/status")
    public OrderResponse updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateStatusRequest req) {
        return orderService.updateStatus(id, req.status());
    }
}
