package com.aishop.dto;

import com.aishop.entity.Product;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.Page;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

public final class ProductDtos {

    private ProductDtos() {
    }

    /** Serializable so it can be stored in the Redis cache. */
    public record ProductResponse(Long id, String name, String description, BigDecimal price, String category,
                                  String imageUrl, String tags, double rating, int stock, boolean active)
            implements Serializable {
        public static ProductResponse from(Product p) {
            return new ProductResponse(p.getId(), p.getName(), p.getDescription(), p.getPrice(), p.getCategory(),
                    p.getImageUrl(), p.getTags(), p.getRating(), p.getStock(), p.isActive());
        }
    }

    public record ProductPage(List<ProductResponse> content, int page, int size, long totalElements, int totalPages)
            implements Serializable {
        public static ProductPage from(Page<Product> page) {
            return new ProductPage(page.map(ProductResponse::from).getContent(), page.getNumber(), page.getSize(),
                    page.getTotalElements(), page.getTotalPages());
        }
    }

    public record ProductRequest(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 2000) String description,
            @NotNull @DecimalMin(value = "1.00") BigDecimal price,
            @NotBlank @Size(max = 60) String category,
            @Size(max = 500) String imageUrl,
            @Size(max = 300) String tags,
            @DecimalMin("0") @DecimalMax("5") double rating,
            @Min(0) int stock,
            Boolean active) {
    }
}
