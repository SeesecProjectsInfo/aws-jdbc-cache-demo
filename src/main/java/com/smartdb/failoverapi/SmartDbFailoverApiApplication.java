package com.smartdb.failoverapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Smart DB Failover API Application
 * 
 * Main entry point for the Spring Boot application.
 * - DataSourceAutoConfiguration is EXCLUDED because we manage our own
 *   dual-datasource setup via DBFailoverManager.
 * - Caching is enabled for Redis integration.
 * - Scheduling is enabled for periodic primary DB health checks.
 */
@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class })
@EnableCaching
@EnableScheduling
public class SmartDbFailoverApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartDbFailoverApiApplication.class, args);
    }
}
