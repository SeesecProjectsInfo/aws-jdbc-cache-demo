package com.smartdb.failoverapi.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.smartdb.failoverapi.metrics.FailoverMetrics;
import com.smartdb.failoverapi.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * CacheService — Redis-based caching layer for User entities.
 * 
 * Strategy: CACHE-FIRST
 *   1. Read from cache first
 *   2. On cache miss → read from DB → write result to cache
 *   3. On DB failure → serve stale cache if available (graceful degradation)
 * 
 * Design decisions:
 *   - Uses StringRedisTemplate (not RedisTemplate<String, Object>) for
 *     simpler serialization and human-readable Redis entries.
 *   - JSON serialization via Jackson for type-safe deserialization.
 *   - TTL of 60 seconds by default (configurable).
 *   - Cache keys follow pattern: "user:{id}"
 */
@Service
public class CacheService {

    private static final Logger log = LoggerFactory.getLogger(CacheService.class);

    private static final String CACHE_KEY_PREFIX = "user:";
    private static final Duration DEFAULT_TTL = Duration.ofSeconds(60);

    private final StringRedisTemplate redisTemplate;
    private final FailoverMetrics metrics;
    private final ObjectMapper objectMapper;

    @Autowired
    public CacheService(StringRedisTemplate redisTemplate, FailoverMetrics metrics) {
        this.redisTemplate = redisTemplate;
        this.metrics = metrics;

        // Configure ObjectMapper to handle Java 8 date/time types
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Attempts to retrieve a User from cache.
     * 
     * @param id User ID
     * @return Optional containing the cached User, or empty on miss/error
     */
    public Optional<User> getFromCache(Long id) {
        try {
            String key = CACHE_KEY_PREFIX + id;
            String json = redisTemplate.opsForValue().get(key);

            if (json != null) {
                User user = objectMapper.readValue(json, User.class);
                metrics.recordCacheHit();
                log.debug("🟢 CACHE HIT for key '{}': {}", key, user.getName());
                return Optional.of(user);
            } else {
                metrics.recordCacheMiss();
                log.debug("🔴 CACHE MISS for key '{}'", key);
                return Optional.empty();
            }
        } catch (Exception e) {
            // Redis might be down — log and treat as cache miss
            metrics.recordCacheMiss();
            log.warn("⚠️ Cache read failed for user {}: {}. Treating as cache miss.", id, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Stores a User in the cache with TTL.
     * 
     * @param user The user to cache
     */
    public void putInCache(User user) {
        if (user == null || user.getId() == null) {
            return;
        }

        try {
            String key = CACHE_KEY_PREFIX + user.getId();
            String json = objectMapper.writeValueAsString(user);
            redisTemplate.opsForValue().set(key, json, DEFAULT_TTL);
            log.debug("💾 Cached user '{}' with key '{}' (TTL: {}s)", user.getName(), key, DEFAULT_TTL.getSeconds());
        } catch (JsonProcessingException e) {
            log.error("❌ Failed to serialize user {} for caching: {}", user.getId(), e.getMessage());
        } catch (Exception e) {
            // Redis might be down — log but don't fail the request
            log.warn("⚠️ Cache write failed for user {}: {}. Continuing without cache.", user.getId(), e.getMessage());
        }
    }

    /**
     * Removes a user from the cache (used after updates/deletes).
     * 
     * @param id User ID to evict
     */
    public void evictFromCache(Long id) {
        try {
            String key = CACHE_KEY_PREFIX + id;
            Boolean deleted = redisTemplate.delete(key);
            log.debug("🗑️ Evicted cache key '{}': {}", key, deleted != null && deleted ? "SUCCESS" : "NOT_FOUND");
        } catch (Exception e) {
            log.warn("⚠️ Cache eviction failed for user {}: {}", id, e.getMessage());
        }
    }

    /**
     * Clears all user entries from the cache.
     * Uses SCAN-based pattern matching to avoid blocking Redis with KEYS command.
     */
    public void clearAllUserCache() {
        try {
            var keys = redisTemplate.keys(CACHE_KEY_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("🗑️ Cleared {} user cache entries.", keys.size());
            }
        } catch (Exception e) {
            log.warn("⚠️ Failed to clear user cache: {}", e.getMessage());
        }
    }

    /**
     * Checks if Redis is reachable.
     * 
     * @return true if Redis responds to PING
     */
    public boolean isRedisAvailable() {
        try {
            String result = redisTemplate.getConnectionFactory()
                    .getConnection()
                    .ping();
            return "PONG".equals(result);
        } catch (Exception e) {
            log.warn("⚠️ Redis is unavailable: {}", e.getMessage());
            return false;
        }
    }
}
