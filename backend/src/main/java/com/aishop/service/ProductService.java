package com.aishop.service;

import com.aishop.config.CacheConfig;
import com.aishop.dto.ProductDtos.*;
import com.aishop.entity.Product;
import com.aishop.exception.ApiException;
import com.aishop.repository.ProductRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class ProductService {

    private static final Map<String, Sort> SORTS = Map.of(
            "newest", Sort.by(Sort.Direction.DESC, "createdAt"),
            "price_asc", Sort.by(Sort.Direction.ASC, "price"),
            "price_desc", Sort.by(Sort.Direction.DESC, "price"),
            "rating", Sort.by(Sort.Direction.DESC, "rating"));

    private final ProductRepository products;

    public ProductService(ProductRepository products) {
        this.products = products;
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.PRODUCT_PAGES,
            key = "#q + '|' + #category + '|' + #sort + '|' + #page + '|' + #size")
    public ProductPage search(String q, String category, String sort, int page, int size) {
        Sort order = SORTS.getOrDefault(sort, Sort.by("id"));
        var pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 48), order);
        return ProductPage.from(products.search(blankToNull(q), blankToNull(category), pageable));
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.PRODUCT, key = "#id")
    public ProductResponse get(Long id) {
        return ProductResponse.from(find(id));
    }

    @Transactional(readOnly = true)
    @Cacheable(CacheConfig.CATEGORIES)
    public List<String> categories() {
        return products.findCategories();
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheConfig.PRODUCT_PAGES, allEntries = true),
            @CacheEvict(cacheNames = CacheConfig.CATEGORIES, allEntries = true)})
    public ProductResponse create(ProductRequest req) {
        Product p = new Product();
        apply(p, req);
        return ProductResponse.from(products.save(p));
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheConfig.PRODUCT, key = "#id"),
            @CacheEvict(cacheNames = CacheConfig.PRODUCT_PAGES, allEntries = true),
            @CacheEvict(cacheNames = CacheConfig.CATEGORIES, allEntries = true)})
    public ProductResponse update(Long id, ProductRequest req) {
        Product p = find(id);
        apply(p, req);
        return ProductResponse.from(p);
    }

    /** Soft delete: past orders keep pointing at the product. */
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheConfig.PRODUCT, key = "#id"),
            @CacheEvict(cacheNames = CacheConfig.PRODUCT_PAGES, allEntries = true),
            @CacheEvict(cacheNames = CacheConfig.CATEGORIES, allEntries = true)})
    public void deactivate(Long id) {
        find(id).setActive(false);
    }

    /** Called after stock changes so cached product pages do not advertise sold-out items. */
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheConfig.PRODUCT, allEntries = true),
            @CacheEvict(cacheNames = CacheConfig.PRODUCT_PAGES, allEntries = true)})
    public void evictStockCaches() {
    }

    Product find(Long id) {
        return products.findById(id).orElseThrow(() -> ApiException.notFound("Product"));
    }

    private static void apply(Product p, ProductRequest req) {
        p.setName(req.name().trim());
        p.setDescription(req.description());
        p.setPrice(req.price());
        p.setCategory(req.category().trim());
        p.setImageUrl(req.imageUrl());
        p.setTags(req.tags());
        p.setRating(req.rating());
        p.setStock(req.stock());
        if (req.active() != null) {
            p.setActive(req.active());
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
