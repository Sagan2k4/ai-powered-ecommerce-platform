package com.aishop.controller;

import com.aishop.dto.RecommendationDtos.RecommendationResponse;
import com.aishop.security.AuthUser;
import com.aishop.service.RecommendationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @GetMapping
    public RecommendationResponse forMe(@AuthenticationPrincipal AuthUser user) {
        return recommendationService.forUser(user.id());
    }
}
