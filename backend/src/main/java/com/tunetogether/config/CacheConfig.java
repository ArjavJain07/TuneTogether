package com.tunetogether.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Caffeine-backed cache for YouTube search results. Identical searches are
 * common (multiple users searching the same popular song) and YouTube's quota
 * is limited, so a short-TTL cache meaningfully reduces upstream calls.
 */
@Configuration
public class CacheConfig {

    public static final String YOUTUBE_SEARCH_CACHE = "youtubeSearch";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(YOUTUBE_SEARCH_CACHE);
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .maximumSize(1000));
        return manager;
    }
}
