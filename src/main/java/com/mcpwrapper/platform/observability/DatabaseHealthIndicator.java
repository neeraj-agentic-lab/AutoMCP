/*
 * MCP REST Adapter - Database Health Indicator
 * 
 * Health indicator for database connectivity and performance.
 * Monitors R2DBC connection pool status and database responsiveness.
 * 
 * @author Neeraj Yadav
 * @version 1.0.0
 * @since 2025-12-16
 * 
 * Copyright (c) 2025 Neeraj Yadav. All rights reserved.
 */
package com.mcpwrapper.platform.observability;

import io.r2dbc.spi.ConnectionFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

/**
 * Health indicator for database connectivity.
 * 
 * @author Neeraj Yadav
 * @since 1.0.0
 */
@Component
public class DatabaseHealthIndicator implements ReactiveHealthIndicator {
    
    private final DatabaseClient databaseClient;
    
    public DatabaseHealthIndicator(ConnectionFactory connectionFactory) {
        this.databaseClient = DatabaseClient.create(connectionFactory);
    }
    
    @Override
    public Mono<Health> health() {
        Instant start = Instant.now();
        
        return databaseClient.sql("SELECT 1")
            .fetch()
            .first()
            .timeout(Duration.ofSeconds(3))
            .map(result -> {
                Duration responseTime = Duration.between(start, Instant.now());
                
                Health.Builder builder = Health.up();
                builder.withDetail("database", "postgresql")
                    .withDetail("responseTime", responseTime.toMillis() + "ms")
                    .withDetail("status", "UP");
                
                if (responseTime.toMillis() > 1000) {
                    builder.withDetail("warning", "Slow database response");
                }
                
                return builder.build();
            })
            .onErrorReturn(Health.down()
                .withDetail("database", "postgresql")
                .withDetail("error", "Database connection failed")
                .withDetail("status", "DOWN")
                .build());
    }
}
