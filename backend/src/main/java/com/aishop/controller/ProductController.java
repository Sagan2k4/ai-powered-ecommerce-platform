package com.aishop.controller;

import com.aishop.dto.ProductDtos.ProductPage;
import com.aishop.dto.ProductDtos.ProductResponse;
import com.aishop.dto.RecommendationDtos.RecommendationResponse;
import com.aishop.service.ProductService;
import com.aishop.service.RecommendationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Public catalog endpoints. */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;
    private final RecommendationService recommendationService;

    public ProductController(ProductService productService, RecommendationService recommendationService) {
        this.productService = productService;
        this.recommendationService = recommendationService;
    }

    @GetMapping
    public ProductPage search(@RequestParam(required = false) String q,
                              @RequestParam(required = false) String category,
                              @RequestParam(defaultValue = "newest") String sort,
                              @RequestParam(defaultValue = "0") int page,
                              @RequestParam(defaultValue = "12") int size) {
        return productService.search(q, category, sort, page, size);
    }

    @GetMapping("/categories")
    public List<String> categories() {
        return productService.categories();
    }

    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable Long id) {
        return productService.get(id);
    }

    @GetMapping("/{id}/similar")
    public RecommendationResponse similar(@PathVariable Long id) {
        return recommendationService.similarTo(id);
    }
}
