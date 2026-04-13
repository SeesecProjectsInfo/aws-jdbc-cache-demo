package com.smartdb.failoverapi.failover;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Simple Circuit Breaker implementation for database failover.
 * 
 * States:
 *   CLOSED  → Normal operation, all requests go to primary DB.
 *   OPEN    → Primary DB is considered down; all requests route to secondary.
 *   HALF_OPEN → Cooldown expired; the next request will test the primary DB
 *               to see if it has recovered.
 * 
 * Transition flow:
 *   CLOSED --[failures >= threshold]--> OPEN
 *   OPEN   --[cooldown expires]------> HALF_OPEN
 *   HALF_OPEN --[test succeeds]-------> CLOSED  (recovery)
 *   HALF_OPEN --[test fails]----------> OPEN    (still down)
 */
@Component
public class CircuitBreaker {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreaker.class);

    public enum State {
        CLOSED,    // Primary DB is healthy
        OPEN,      // Primary DB is down, using secondary
        HALF_OPEN  // Testing if primary has recovered
    }

    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private volatile Instant lastFailureTime = Instant.now();

    private int failureThreshold = 3;       // How many failures before opening
    private long cooldownSeconds = 30;       // How long to wait before retrying primary

    // --- Configuration Setters ---

    public void setFailureThreshold(int threshold) {
        this.failureThreshold = threshold;
    }

    public void setCooldownSeconds(long seconds) {
        this.cooldownSeconds = seconds;
    }

    // --- State Queries ---

    public State getState() {
        return state.get();
    }

    /**
     * Determines whether we should attempt to use the primary DB.
     * 
     * Returns true if:
     *   - Circuit is CLOSED (normal operation)
     *   - Circuit is HALF_OPEN (time to test primary again)
     *   - Circuit is OPEN but cooldown has expired (transitions to HALF_OPEN)
     */
    public boolean shouldAttemptPrimary() {
        State current = state.get();

        if (current == State.CLOSED) {
            return true;
        }

        if (current == State.HALF_OPEN) {
            return true;
        }

        // OPEN state — check if cooldown has expired
        if (current == State.OPEN) {
            Instant now = Instant.now();
            if (now.isAfter(lastFailureTime.plusSeconds(cooldownSeconds))) {
                // Cooldown expired → transition to HALF_OPEN for a test attempt
                if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                    log.info("⏱️ Circuit breaker cooldown expired. Transitioning to HALF_OPEN — will test primary DB.");
                }
                return true;
            }
        }

        return false;
    }

    /**
     * Called when a primary DB operation succeeds.
     * Resets the circuit breaker to CLOSED state.
     */
    public void recordSuccess() {
        State previous = state.get();
        failureCount.set(0);
        state.set(State.CLOSED);

        if (previous != State.CLOSED) {
            log.info("✅ Circuit breaker CLOSED — primary DB recovered successfully.");
        }
    }

    /**
     * Called when a primary DB operation fails.
     * Increments failure count and opens the circuit if threshold is breached.
     */
    public void recordFailure() {
        lastFailureTime = Instant.now();
        int failures = failureCount.incrementAndGet();

        if (failures >= failureThreshold) {
            State previous = state.getAndSet(State.OPEN);
            if (previous != State.OPEN) {
                log.warn("🔴 Circuit breaker OPENED — {} consecutive failures. Routing to secondary DB. "
                       + "Will retry primary after {} seconds.", failures, cooldownSeconds);
            }
        } else {
            log.warn("⚠️ Primary DB failure #{}/{}. Circuit still CLOSED.",
                     failures, failureThreshold);
        }
    }

    /**
     * Returns a human-readable status summary for the health endpoint.
     */
    public String getStatusSummary() {
        return String.format("state=%s, failures=%d/%d, cooldown=%ds",
                state.get(), failureCount.get(), failureThreshold, cooldownSeconds);
    }
}
