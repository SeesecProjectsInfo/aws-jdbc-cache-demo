package com.smartdb.failoverapi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis Configuration.
 * 
 * Uses StringRedisTemplate for human-readable keys and values in Redis.
 * Connection settings (host, port, timeout) come from application.yml
 * via Spring Boot auto-configuration.
 */
@Configuration
public class RedisConfig {

    /**
     * StringRedisTemplate — stores keys and values as plain strings.
     * This makes it easier to inspect cache contents via redis-cli
     * and avoids Java serialization compatibility issues.
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
