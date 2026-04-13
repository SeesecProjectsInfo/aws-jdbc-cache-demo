package com.smartdb.failoverapi.controller;

import com.smartdb.failoverapi.cache.CacheService;
import com.smartdb.failoverapi.failover.DBFailoverManager;
import com.smartdb.failoverapi.metrics.FailoverMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HealthController — Provides system health and metrics endpoints.
 * 
 * Endpoints:
 *   GET /health   → Full system health including active DB, circuit breaker state,
 *                    cache availability, and performance metrics
 *   GET /metrics  → Detailed failover and cache metrics
 */
@RestController
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    @Autowired
    private DBFailoverManager dbFailoverManager;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private FailoverMetrics metrics;

    /**
     * GET /health — Returns comprehensive system health status.
     * 
     * Response includes:
     *   - Which database is currently active (PRIMARY / SECONDARY)
     *   - Circuit breaker state (CLOSED / OPEN / HALF_OPEN)
     *   - Redis cache availability
     *   - Key performance metrics
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        log.info("🌐 GET /health");

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "UP");
        response.put("timestamp", Instant.now().toString());

        // --- Database Status ---
        Map<String, Object> dbStatus = new LinkedHashMap<>();
        dbStatus.put("activeDatabase", dbFailoverManager.getActiveDatabase());
        dbStatus.put("circuitBreakerState", dbFailoverManager.getCircuitBreakerState().toString());
        dbStatus.put("circuitBreakerDetails", dbFailoverManager.getCircuitBreakerStatus());
        response.put("database", dbStatus);

        // --- Cache Status ---
        Map<String, Object> cacheStatus = new LinkedHashMap<>();
        boolean redisAvailable = cacheService.isRedisAvailable();
        cacheStatus.put("redis", redisAvailable ? "CONNECTED" : "DISCONNECTED");
        cacheStatus.put("cacheHits", metrics.getCacheHits());
        cacheStatus.put("cacheMisses", metrics.getCacheMisses());
        long totalCacheOps = metrics.getCacheHits() + metrics.getCacheMisses();
        if (totalCacheOps > 0) {
            double hitRate = (double) metrics.getCacheHits() / totalCacheOps * 100;
            cacheStatus.put("cacheHitRate", String.format("%.1f%%", hitRate));
        } else {
            cacheStatus.put("cacheHitRate", "N/A");
        }
        response.put("cache", cacheStatus);

        // --- Failover Metrics ---
        Map<String, Object> failoverStats = new LinkedHashMap<>();
        failoverStats.put("totalFailovers", metrics.getTotalFailovers());
        failoverStats.put("totalRecoveries", metrics.getTotalRecoveries());
        failoverStats.put("primaryQueryCount", metrics.getPrimaryQueryCount());
        failoverStats.put("secondaryQueryCount", metrics.getSecondaryQueryCount());
        response.put("failover", failoverStats);

        return ResponseEntity.ok(response);
    }

    /**
     * GET /metrics — Returns detailed metrics summary.
     */
    @GetMapping("/metrics/custom")
    public ResponseEntity<Map<String, Object>> customMetrics() {
        log.info("🌐 GET /metrics/custom");

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("timestamp", Instant.now().toString());

        // Database metrics
        response.put("activeDatabase", dbFailoverManager.getActiveDatabase());
        response.put("totalFailovers", metrics.getTotalFailovers());
        response.put("totalRecoveries", metrics.getTotalRecoveries());
        response.put("primaryQueries", metrics.getPrimaryQueryCount());
        response.put("secondaryQueries", metrics.getSecondaryQueryCount());

        // Cache metrics
        response.put("cacheHits", metrics.getCacheHits());
        response.put("cacheMisses", metrics.getCacheMisses());

        long totalCacheOps = metrics.getCacheHits() + metrics.getCacheMisses();
        if (totalCacheOps > 0) {
            response.put("cacheHitRate", String.format("%.1f%%",
                    (double) metrics.getCacheHits() / totalCacheOps * 100));
        }

        response.put("summary", metrics.getSummary());

        return ResponseEntity.ok(response);
    }
}
