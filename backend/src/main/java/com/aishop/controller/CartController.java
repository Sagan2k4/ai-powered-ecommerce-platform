package com.aishop.controller;

import com.aishop.dto.CartDtos.*;
import com.aishop.security.AuthUser;
import com.aishop.service.CartService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public CartResponse get(@AuthenticationPrincipal AuthUser user) {
        return cartService.get(user.id());
    }

    @PostMapping("/items")
    public CartResponse add(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody AddToCartRequest req) {
        return cartService.add(user.id(), req);
    }

    @PatchMapping("/items/{itemId}")
    public CartResponse update(@AuthenticationPrincipal AuthUser user, @PathVariable Long itemId,
                               @Valid @RequestBody UpdateQuantityRequest req) {
        return cartService.updateQuantity(user.id(), itemId, req.quantity());
    }

    @DeleteMapping("/items/{itemId}")
    public CartResponse remove(@AuthenticationPrincipal AuthUser user, @PathVariable Long itemId) {
        return cartService.remove(user.id(), itemId);
    }
}
