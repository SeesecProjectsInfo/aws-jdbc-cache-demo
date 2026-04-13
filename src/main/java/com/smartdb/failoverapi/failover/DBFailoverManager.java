package com.smartdb.failoverapi.failover;

import com.smartdb.failoverapi.metrics.FailoverMetrics;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * DBFailoverManager — The heart of the failover system.
 * 
 * Responsibilities:
 *   1. Maintains connection pools for BOTH primary and secondary databases.
 *   2. Exposes a single getActiveJdbcTemplate() method that returns the
 *      JdbcTemplate connected to whichever DB is currently healthy.
 *   3. Uses CircuitBreaker to decide when to failover and when to recover.
 *   4. Runs a scheduled health check to auto-recover to primary when it comes back.
 * 
 * Connection flow:
 *   Request → getActiveJdbcTemplate()
 *           → CircuitBreaker says "try primary"?
 *             YES → try primary connection
 *                   SUCCESS → use primary, record success
 *                   FAILURE → record failure, fall back to secondary
 *             NO  → use secondary directly
 */
@Component
public class DBFailoverManager {

    private static final Logger log = LoggerFactory.getLogger(DBFailoverManager.class);

    // --- Primary DB Config ---
    @Value("${datasource.primary.url}")
    private String primaryUrl;

    @Value("${datasource.primary.username}")
    private String primaryUsername;

    @Value("${datasource.primary.password}")
    private String primaryPassword;

    @Value("${datasource.primary.driver-class-name}")
    private String primaryDriverClass;

    // --- Secondary DB Config ---
    @Value("${datasource.secondary.url}")
    private String secondaryUrl;

    @Value("${datasource.secondary.username}")
    private String secondaryUsername;

    @Value("${datasource.secondary.password}")
    private String secondaryPassword;

    @Value("${datasource.secondary.driver-class-name}")
    private String secondaryDriverClass;

    // --- Failover Config ---
    @Value("${failover.cooldown-seconds:30}")
    private long cooldownSeconds;

    @Value("${failover.failure-threshold:3}")
    private int failureThreshold;

    @Value("${failover.validation-timeout-seconds:5}")
    private int validationTimeout;

    @Autowired
    private CircuitBreaker circuitBreaker;

    @Autowired
    private FailoverMetrics metrics;

    private HikariDataSource primaryDataSource;
    private HikariDataSource secondaryDataSource;
    private JdbcTemplate primaryJdbcTemplate;
    private JdbcTemplate secondaryJdbcTemplate;

    // Tracks which DB is currently serving requests
    private volatile String activeDatabase = "PRIMARY";

    /**
     * Initializes both connection pools and configures the circuit breaker.
     * Called once after dependency injection is complete.
     */
    @PostConstruct
    public void init() {
        log.info("🔧 Initializing DB Failover Manager...");

        // Configure circuit breaker thresholds from application.yml
        circuitBreaker.setFailureThreshold(failureThreshold);
        circuitBreaker.setCooldownSeconds(cooldownSeconds);

        // Build connection pools
        primaryDataSource = createDataSource("PRIMARY", primaryUrl, primaryUsername, primaryPassword, primaryDriverClass);
        secondaryDataSource = createDataSource("SECONDARY", secondaryUrl, secondaryUsername, secondaryPassword, secondaryDriverClass);

        primaryJdbcTemplate = new JdbcTemplate(primaryDataSource);
        secondaryJdbcTemplate = new JdbcTemplate(secondaryDataSource);

        // Test initial connectivity
        if (isDataSourceHealthy(primaryDataSource, "PRIMARY")) {
            activeDatabase = "PRIMARY";
            log.info("✅ Primary DB connection established successfully.");
        } else {
            log.warn("⚠️ Primary DB unavailable at startup. Failing over to secondary.");
            if (isDataSourceHealthy(secondaryDataSource, "SECONDARY")) {
                activeDatabase = "SECONDARY";
                circuitBreaker.recordFailure();
                circuitBreaker.recordFailure();
                circuitBreaker.recordFailure(); // Force circuit open
                metrics.recordFailover();
                log.info("✅ Secondary DB connection established. Running in failover mode.");
            } else {
                log.error("🔴 BOTH databases are unavailable! Application will retry on each request.");
                activeDatabase = "NONE";
            }
        }

        log.info("🔧 DB Failover Manager initialized. Active DB: {}", activeDatabase);
    }

    /**
     * Creates a HikariCP connection pool with sensible defaults.
     */
    private HikariDataSource createDataSource(String name, String url, String username,
                                               String password, String driverClass) {
        HikariConfig config = new HikariConfig();
        config.setPoolName(name + "-Pool");
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName(driverClass);

        // Pool sizing — keep it small for failover scenarios
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(5000);       // 5 seconds to get a connection
        config.setValidationTimeout(validationTimeout * 1000L);
        config.setIdleTimeout(300000);            // 5 minutes idle before eviction
        config.setMaxLifetime(600000);            // 10 minutes max connection lifetime

        // Health check query
        config.setConnectionTestQuery("SELECT 1");

        try {
            HikariDataSource ds = new HikariDataSource(config);
            log.info("📦 Connection pool '{}' created for: {}", name, url);
            return ds;
        } catch (Exception e) {
            log.error("❌ Failed to create connection pool '{}': {}", name, e.getMessage());
            // Return a pool anyway — it will fail on use, triggering failover
            return new HikariDataSource(config);
        }
    }

    /**
     * Tests whether a datasource can provide a valid connection.
     */
    private boolean isDataSourceHealthy(DataSource dataSource, String name) {
        try (Connection conn = dataSource.getConnection()) {
            boolean valid = conn.isValid(validationTimeout);
            log.debug("🏥 Health check for {}: {}", name, valid ? "HEALTHY" : "UNHEALTHY");
            return valid;
        } catch (SQLException e) {
            log.debug("🏥 Health check for {}: UNHEALTHY ({})", name, e.getMessage());
            return false;
        }
    }

    /**
     * Returns the JdbcTemplate connected to the currently active (healthy) database.
     * 
     * This is the PRIMARY method that all repository classes should call.
     * It implements the failover decision logic:
     *   1. Ask CircuitBreaker if we should try primary
     *   2. If yes, test primary connection
     *   3. On failure, fall back to secondary
     *   4. Record metrics throughout
     */
    public JdbcTemplate getActiveJdbcTemplate() {
        if (circuitBreaker.shouldAttemptPrimary()) {
            // Try the primary database
            if (isDataSourceHealthy(primaryDataSource, "PRIMARY")) {
                circuitBreaker.recordSuccess();

                if (!"PRIMARY".equals(activeDatabase)) {
                    log.info("🔄 RECOVERED: Switching back to PRIMARY database.");
                    metrics.recordRecovery();
                    activeDatabase = "PRIMARY";
                }

                metrics.recordPrimaryQuery();
                return primaryJdbcTemplate;
            } else {
                // Primary failed — record failure in circuit breaker
                circuitBreaker.recordFailure();

                if ("PRIMARY".equals(activeDatabase)) {
                    log.warn("🔴 FAILOVER: Primary DB is down. Switching to SECONDARY database.");
                    metrics.recordFailover();
                    activeDatabase = "SECONDARY";
                }
            }
        }

        // Use secondary database
        metrics.recordSecondaryQuery();
        return secondaryJdbcTemplate;
    }

    /**
     * Returns the name of the currently active database.
     */
    public String getActiveDatabase() {
        return activeDatabase;
    }

    /**
     * Returns the current circuit breaker state.
     */
    public CircuitBreaker.State getCircuitBreakerState() {
        return circuitBreaker.getState();
    }

    /**
     * Returns the circuit breaker status summary.
     */
    public String getCircuitBreakerStatus() {
        return circuitBreaker.getStatusSummary();
    }

    /**
     * Scheduled health check — runs every 15 seconds.
     * 
     * Purpose: When we're in failover mode (using secondary), this periodically
     * checks if the primary has come back online. If it has, we automatically
     * recover without waiting for a user request to trigger the check.
     */
    @Scheduled(fixedDelayString = "15000")
    public void scheduledHealthCheck() {
        if (circuitBreaker.getState() != CircuitBreaker.State.CLOSED) {
            log.debug("🔍 Scheduled health check — testing primary DB availability...");

            if (isDataSourceHealthy(primaryDataSource, "PRIMARY")) {
                circuitBreaker.recordSuccess();
                if (!"PRIMARY".equals(activeDatabase)) {
                    log.info("🔄 RECOVERY (scheduled): Primary DB is back online! Switching back.");
                    metrics.recordRecovery();
                    activeDatabase = "PRIMARY";
                }
            } else {
                log.debug("🔍 Primary DB still unavailable. Continuing with secondary.");
            }
        }
    }

    /**
     * Clean up connection pools on application shutdown.
     */
    @PreDestroy
    public void shutdown() {
        log.info("🛑 Shutting down DB Failover Manager...");
        if (primaryDataSource != null && !primaryDataSource.isClosed()) {
            primaryDataSource.close();
        }
        if (secondaryDataSource != null && !secondaryDataSource.isClosed()) {
            secondaryDataSource.close();
        }
    }
}
