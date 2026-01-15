/*
 * MCP REST Adapter - Reactive Configuration Audit Repository
 * 
 * @author Neeraj Yadav
 * @version 1.0.0
 * @since 2025-12-16
 */
package com.mcpwrapper.platform.persistence.repository;

import com.mcpwrapper.platform.persistence.entity.ConfigurationAuditEntity;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface ReactiveConfigurationAuditRepository extends R2dbcRepository<ConfigurationAuditEntity, UUID> {
    
    Flux<ConfigurationAuditEntity> findByConfigId(UUID configId);
    
    Flux<ConfigurationAuditEntity> findByUserId(String userId);
    
    Flux<ConfigurationAuditEntity> findByAction(String action);
    
    Flux<ConfigurationAuditEntity> findByTimestampAfter(Instant timestamp);
    
    Flux<ConfigurationAuditEntity> findByConfigIdOrderByTimestampDesc(UUID configId);
}
