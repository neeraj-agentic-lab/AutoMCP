/*
 * MCP REST Adapter - Audit Service
 * 
 * Service for tracking and logging audit events throughout the system.
 * Provides comprehensive audit trails for compliance, security monitoring,
 * and operational debugging with structured event logging.
 * 
 * @author Neeraj Yadav
 * @version 1.0.0
 * @since 2025-12-16
 * 
 * Copyright (c) 2025 Neeraj Yadav. All rights reserved.
 */
package com.mcpwrapper.platform.observability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcpwrapper.platform.persistence.entity.ConfigurationAuditEntity;
import com.mcpwrapper.platform.persistence.repository.ReactiveConfigurationAuditRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Service for comprehensive audit logging and event tracking.
 * 
 * @author Neeraj Yadav
 * @since 1.0.0
 */
@Service
public class AuditService {
    
    private static final Logger auditLogger = LoggerFactory.getLogger("AUDIT");
    private static final Logger logger = LoggerFactory.getLogger(AuditService.class);
    
    private final ReactiveConfigurationAuditRepository auditRepository;
    private final ObjectMapper objectMapper;
    
    public AuditService(
            ReactiveConfigurationAuditRepository auditRepository,
            ObjectMapper objectMapper) {
        this.auditRepository = auditRepository;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Logs a tool invocation event.
     */
    public Mono<Void> logToolInvocation(String toolName, String callId, String userId, 
                                       Map<String, Object> parameters, String result, 
                                       boolean success, long executionTimeMs) {
        
        return Mono.fromRunnable(() -> {
            try {
                MDC.put("eventType", "TOOL_INVOCATION");
                MDC.put("toolName", toolName);
                MDC.put("callId", callId);
                MDC.put("userId", userId);
                MDC.put("success", String.valueOf(success));
                MDC.put("executionTime", String.valueOf(executionTimeMs));
                
                AuditEvent event = new AuditEvent(
                    "TOOL_INVOCATION",
                    toolName,
                    userId,
                    success ? "SUCCESS" : "FAILURE",
                    Map.of(
                        "callId", callId,
                        "parameters", parameters,
                        "result", result,
                        "executionTimeMs", executionTimeMs
                    ),
                    Instant.now()
                );
                
                auditLogger.info("Tool invocation: {}", objectMapper.writeValueAsString(event));
                
            } catch (Exception e) {
                logger.error("Failed to log tool invocation audit event", e);
            } finally {
                MDC.clear();
            }
        });
    }
    
    /**
     * Logs a configuration change event.
     */
    public Mono<Void> logConfigurationChange(UUID configId, String action, String userId, 
                                           JsonNode beforeData, JsonNode afterData) {
        
        ConfigurationAuditEntity auditEntity = switch (action.toUpperCase()) {
            case "CREATE" -> ConfigurationAuditEntity.forCreate(configId, afterData, userId, "127.0.0.1");
            case "UPDATE" -> ConfigurationAuditEntity.forUpdate(configId, beforeData, afterData, userId, "127.0.0.1");
            case "DELETE" -> ConfigurationAuditEntity.forDelete(configId, beforeData, userId, "127.0.0.1");
            default -> throw new IllegalArgumentException("Unknown action: " + action);
        };
        
        return auditRepository.save(auditEntity)
            .doOnSuccess(saved -> {
                try {
                    MDC.put("eventType", "CONFIGURATION_CHANGE");
                    MDC.put("configId", configId.toString());
                    MDC.put("action", action);
                    MDC.put("userId", userId);
                    
                    AuditEvent event = new AuditEvent(
                        "CONFIGURATION_CHANGE",
                        "configuration:" + configId,
                        userId,
                        "SUCCESS",
                        Map.of(
                            "action", action,
                            "configId", configId.toString(),
                            "hasBeforeData", beforeData != null,
                            "hasAfterData", afterData != null
                        ),
                        Instant.now()
                    );
                    
                    auditLogger.info("Configuration change: {}", objectMapper.writeValueAsString(event));
                    
                } catch (Exception e) {
                    logger.error("Failed to log configuration change audit event", e);
                } finally {
                    MDC.clear();
                }
            })
            .then();
    }
    
    /**
     * Logs a security event.
     */
    public Mono<Void> logSecurityEvent(String eventType, String userId, String resource, 
                                      String action, boolean allowed, String reason) {
        
        return Mono.fromRunnable(() -> {
            try {
                MDC.put("eventType", "SECURITY_EVENT");
                MDC.put("securityEventType", eventType);
                MDC.put("userId", userId);
                MDC.put("resource", resource);
                MDC.put("action", action);
                MDC.put("allowed", String.valueOf(allowed));
                
                AuditEvent event = new AuditEvent(
                    "SECURITY_EVENT",
                    resource,
                    userId,
                    allowed ? "ALLOWED" : "DENIED",
                    Map.of(
                        "eventType", eventType,
                        "action", action,
                        "reason", reason
                    ),
                    Instant.now()
                );
                
                if (allowed) {
                    auditLogger.info("Security event: {}", objectMapper.writeValueAsString(event));
                } else {
                    auditLogger.warn("Security violation: {}", objectMapper.writeValueAsString(event));
                }
                
            } catch (Exception e) {
                logger.error("Failed to log security audit event", e);
            } finally {
                MDC.clear();
            }
        });
    }
    
    /**
     * Logs an API access event.
     */
    public Mono<Void> logApiAccess(String method, String path, String userId, 
                                  int statusCode, long responseTimeMs, String userAgent) {
        
        return Mono.fromRunnable(() -> {
            try {
                MDC.put("eventType", "API_ACCESS");
                MDC.put("method", method);
                MDC.put("path", path);
                MDC.put("userId", userId);
                MDC.put("statusCode", String.valueOf(statusCode));
                MDC.put("responseTime", String.valueOf(responseTimeMs));
                
                AuditEvent event = new AuditEvent(
                    "API_ACCESS",
                    method + " " + path,
                    userId,
                    statusCode < 400 ? "SUCCESS" : "ERROR",
                    Map.of(
                        "method", method,
                        "path", path,
                        "statusCode", statusCode,
                        "responseTimeMs", responseTimeMs,
                        "userAgent", userAgent
                    ),
                    Instant.now()
                );
                
                auditLogger.info("API access: {}", objectMapper.writeValueAsString(event));
                
            } catch (Exception e) {
                logger.error("Failed to log API access audit event", e);
            } finally {
                MDC.clear();
            }
        });
    }
    
    /**
     * Logs a system event.
     */
    public Mono<Void> logSystemEvent(String eventType, String description, 
                                    Map<String, Object> details) {
        
        return Mono.fromRunnable(() -> {
            try {
                MDC.put("eventType", "SYSTEM_EVENT");
                MDC.put("systemEventType", eventType);
                
                AuditEvent event = new AuditEvent(
                    "SYSTEM_EVENT",
                    eventType,
                    "system",
                    "INFO",
                    Map.of(
                        "description", description,
                        "details", details
                    ),
                    Instant.now()
                );
                
                auditLogger.info("System event: {}", objectMapper.writeValueAsString(event));
                
            } catch (Exception e) {
                logger.error("Failed to log system audit event", e);
            } finally {
                MDC.clear();
            }
        });
    }
    
    /**
     * Retrieves audit events for a specific configuration.
     */
    public Flux<ConfigurationAuditEntity> getConfigurationAuditTrail(UUID configId) {
        return auditRepository.findByConfigIdOrderByTimestampDesc(configId)
            .doOnSubscribe(subscription -> logger.debug("Retrieving audit trail for configuration: {}", configId));
    }
    
    /**
     * Retrieves audit events for a specific user.
     */
    public Flux<ConfigurationAuditEntity> getUserAuditTrail(String userId) {
        return auditRepository.findByUserId(userId)
            .doOnSubscribe(subscription -> logger.debug("Retrieving audit trail for user: {}", userId));
    }
    
    /**
     * Retrieves recent audit events.
     */
    public Flux<ConfigurationAuditEntity> getRecentAuditEvents(Instant since) {
        return auditRepository.findByTimestampAfter(since)
            .doOnSubscribe(subscription -> logger.debug("Retrieving audit events since: {}", since));
    }
    
    // Audit event record
    
    public record AuditEvent(
        String eventType,
        String resource,
        String userId,
        String outcome,
        Map<String, Object> details,
        Instant timestamp
    ) {}
}
