package com.aishop.dto;

import com.aishop.dto.ProductDtos.ProductResponse;

import java.io.Serializable;
import java.util.List;

public final class RecommendationDtos {

    private RecommendationDtos() {
    }

    public record Recommendation(ProductResponse product, String reason) implements Serializable {
    }

    /** {@code source} is "openai" when the model ranked the items, "fallback" for the built-in scorer. */
    public record RecommendationResponse(List<Recommendation> items, String source) implements Serializable {
    }
}
