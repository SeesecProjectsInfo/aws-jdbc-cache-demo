package com.smartdb.failoverapi.metrics;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks application-level metrics for failover events and cache performance.
 * 
 * Uses AtomicLong for thread-safe counters — safe for concurrent access
 * from multiple request threads without synchronization overhead.
 */
@Component
public class FailoverMetrics {

    private final AtomicLong totalFailovers = new AtomicLong(0);
    private final AtomicLong totalRecoveries = new AtomicLong(0);
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final AtomicLong primaryQueryCount = new AtomicLong(0);
    private final AtomicLong secondaryQueryCount = new AtomicLong(0);

    // --- Failover Metrics ---

    public void recordFailover() {
        totalFailovers.incrementAndGet();
    }

    public void recordRecovery() {
        totalRecoveries.incrementAndGet();
    }

    public long getTotalFailovers() {
        return totalFailovers.get();
    }

    public long getTotalRecoveries() {
        return totalRecoveries.get();
    }

    // --- Cache Metrics ---

    public void recordCacheHit() {
        cacheHits.incrementAndGet();
    }

    public void recordCacheMiss() {
        cacheMisses.incrementAndGet();
    }

    public long getCacheHits() {
        return cacheHits.get();
    }

    public long getCacheMisses() {
        return cacheMisses.get();
    }

    // --- Query Metrics ---

    public void recordPrimaryQuery() {
        primaryQueryCount.incrementAndGet();
    }

    public void recordSecondaryQuery() {
        secondaryQueryCount.incrementAndGet();
    }

    public long getPrimaryQueryCount() {
        return primaryQueryCount.get();
    }

    public long getSecondaryQueryCount() {
        return secondaryQueryCount.get();
    }

    /**
     * Returns a snapshot of all metrics as a formatted string.
     */
    public String getSummary() {
        return String.format(
            "Metrics [failovers=%d, recoveries=%d, cacheHits=%d, cacheMisses=%d, primaryQueries=%d, secondaryQueries=%d]",
            totalFailovers.get(), totalRecoveries.get(),
            cacheHits.get(), cacheMisses.get(),
            primaryQueryCount.get(), secondaryQueryCount.get()
        );
    }
}
