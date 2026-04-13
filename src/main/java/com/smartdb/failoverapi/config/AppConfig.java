package com.smartdb.failoverapi.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Application-wide configuration.
 * 
 * DataSource configuration is NOT done here — it's managed entirely
 * by DBFailoverManager, which creates and manages two separate
 * HikariCP connection pools (primary + secondary).
 * 
 * This class exists for any additional cross-cutting configuration
 * that doesn't fit elsewhere.
 */
@Configuration
@EnableScheduling
public class AppConfig {

    // Additional beans can be registered here as the application grows.
    // Examples: custom ObjectMapper, CORS configuration, etc.
}
