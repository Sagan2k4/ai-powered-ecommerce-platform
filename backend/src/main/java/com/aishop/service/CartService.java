package com.aishop.service;

import com.aishop.dto.CartDtos.*;
import com.aishop.dto.ProductDtos.ProductResponse;
import com.aishop.entity.CartItem;
import com.aishop.entity.Product;
import com.aishop.exception.ApiException;
import com.aishop.repository.CartItemRepository;
import com.aishop.repository.ProductRepository;
import com.aishop.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class CartService {

    private static final int MAX_PER_ITEM = 20;

    private final CartItemRepository cartItems;
    private final ProductRepository products;
    private final UserRepository users;

    public CartService(CartItemRepository cartItems, ProductRepository products, UserRepository users) {
        this.cartItems = cartItems;
        this.products = products;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public CartResponse get(Long userId) {
        List<CartItemResponse> items = cartItems.findByUserIdOrderByIdAsc(userId).stream()
                .map(ci -> new CartItemResponse(ci.getId(), ProductResponse.from(ci.getProduct()), ci.getQuantity(),
                        ci.getProduct().getPrice().multiply(BigDecimal.valueOf(ci.getQuantity()))))
                .toList();
        BigDecimal total = items.stream().map(CartItemResponse::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        int count = items.stream().mapToInt(CartItemResponse::quantity).sum();
        return new CartResponse(items, count, total);
    }

    @Transactional
    public CartResponse add(Long userId, AddToCartRequest req) {
        Product product = products.findById(req.productId())
                .filter(Product::isActive)
                .orElseThrow(() -> ApiException.notFound("Product"));
        CartItem item = cartItems.findByUserIdAndProductId(userId, product.getId())
                .orElseGet(() -> new CartItem(users.getReferenceById(userId), product, 0));
        int quantity = Math.min(item.getQuantity() + req.quantity(), MAX_PER_ITEM);
        ensureStock(product, quantity);
        item.setQuantity(quantity);
        cartItems.save(item);
        return get(userId);
    }

    @Transactional
    public CartResponse updateQuantity(Long userId, Long itemId, int quantity) {
        CartItem item = findOwned(userId, itemId);
        ensureStock(item.getProduct(), quantity);
        item.setQuantity(quantity);
        return get(userId);
    }

    @Transactional
    public CartResponse remove(Long userId, Long itemId) {
        cartItems.delete(findOwned(userId, itemId));
        return get(userId);
    }

    private CartItem findOwned(Long userId, Long itemId) {
        return cartItems.findById(itemId)
                .filter(ci -> ci.getUser().getId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("Cart item"));
    }

    private static void ensureStock(Product product, int quantity) {
        if (product.getStock() < quantity) {
            throw ApiException.badRequest("Only " + product.getStock() + " of \"" + product.getName() + "\" left in stock");
        }
    }
}
