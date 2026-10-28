package com.aishop.dto;

import com.aishop.dto.ProductDtos.ProductResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public final class CartDtos {

    private CartDtos() {
    }

    public record AddToCartRequest(@NotNull Long productId, @Min(1) @Max(20) int quantity) {
    }

    public record UpdateQuantityRequest(@Min(1) @Max(20) int quantity) {
    }

    public record CartItemResponse(Long id, ProductResponse product, int quantity, BigDecimal lineTotal) {
    }

    public record CartResponse(List<CartItemResponse> items, int itemCount, BigDecimal total) {
    }
}
