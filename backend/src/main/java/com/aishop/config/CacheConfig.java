package com.aishop.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.SimpleCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;

import java.time.Duration;

/**
 * Redis caching for hot, read-mostly data. Catalog reads are cached for longer and evicted on admin writes;
 * AI recommendations are cached briefly per user because each one costs an OpenAI call.
 */
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    public static final String PRODUCT = "product";
    public static final String PRODUCT_PAGES = "productPages";
    public static final String CATEGORIES = "categories";
    public static final String RECOMMENDATIONS = "recommendations";
    public static final String SIMILAR = "similarProducts";

    @Bean
    RedisCacheManagerBuilderCustomizer redisCacheCustomizer() {
        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .prefixCacheNameWith("aishop:")
                .disableCachingNullValues();
        return builder -> builder
                .cacheDefaults(base.entryTtl(Duration.ofMinutes(10)))
                .withCacheConfiguration(PRODUCT, base.entryTtl(Duration.ofMinutes(30)))
                .withCacheConfiguration(PRODUCT_PAGES, base.entryTtl(Duration.ofMinutes(5)))
                .withCacheConfiguration(CATEGORIES, base.entryTtl(Duration.ofHours(1)))
                .withCacheConfiguration(RECOMMENDATIONS, base.entryTtl(Duration.ofMinutes(15)))
                .withCacheConfiguration(SIMILAR, base.entryTtl(Duration.ofHours(1)));
    }

    /** The cache is an optimisation: if Redis is down, log it and serve from the database instead of failing. */
    @Override
    public CacheErrorHandler errorHandler() {
        return new SimpleCacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
                log.warn("Cache read failed for {}::{} - {}", cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
                log.warn("Cache write failed for {}::{} - {}", cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
                log.warn("Cache evict failed for {}::{} - {}", cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException e, Cache cache) {
                log.warn("Cache clear failed for {} - {}", cache.getName(), e.getMessage());
            }
        };
    }
}
