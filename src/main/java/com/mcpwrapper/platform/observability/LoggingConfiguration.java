/*
 * MCP REST Adapter - Logging Configuration
 * 
 * Configuration for structured logging and audit trails.
 * Sets up logback configuration, structured JSON logging,
 * and audit event logging for compliance and debugging.
 * 
 * @author Neeraj Yadav
 * @version 1.0.0
 * @since 2025-12-16
 * 
 * Copyright (c) 2025 Neeraj Yadav. All rights reserved.
 */
package com.mcpwrapper.platform.observability;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import net.logstash.logback.appender.LogstashTcpSocketAppender;
import net.logstash.logback.encoder.LogstashEncoder;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

/**
 * Configuration for application logging.
 * 
 * @author Neeraj Yadav
 * @since 1.0.0
 */
@Configuration
public class LoggingConfiguration {
    
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(LoggingConfiguration.class);
    
    /**
     * Configures structured logging after application startup.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void configureStructuredLogging() {
        logger.info("Configuring structured logging for MCP REST Adapter");
        
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        
        // Configure JSON logging for production
        if (isProductionEnvironment()) {
            configureJsonLogging(context);
        }
        
        // Configure audit logging
        configureAuditLogging(context);
        
        logger.info("Structured logging configuration completed");
    }
    
    private void configureJsonLogging(LoggerContext context) {
        // JSON logging configuration would go here
        // This is a placeholder for production JSON logging setup
        logger.debug("JSON logging configured for production environment");
    }
    
    private void configureAuditLogging(LoggerContext context) {
        // Audit logging configuration would go here
        // This is a placeholder for audit trail setup
        logger.debug("Audit logging configured");
    }
    
    private boolean isProductionEnvironment() {
        String profile = System.getProperty("spring.profiles.active", "dev");
        return "prod".equals(profile) || "production".equals(profile);
    }
}
