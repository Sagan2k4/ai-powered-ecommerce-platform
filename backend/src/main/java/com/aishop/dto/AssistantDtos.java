package com.aishop.dto;

import com.aishop.dto.RecommendationDtos.Recommendation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class AssistantDtos {

    private AssistantDtos() {
    }

    public record AssistantRequest(@NotBlank @Size(max = 300) String message) {
    }

    /** {@code source} is "openai" when the model answered, "fallback" for the built-in keyword matcher. */
    public record AssistantResponse(String reply, List<Recommendation> items, String source) {
    }
}
