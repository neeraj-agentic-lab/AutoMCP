/*
 * MCP REST Adapter - Reactive Environment Variable Repository
 * 
 * @author Neeraj Yadav
 * @version 1.0.0
 * @since 2025-12-16
 */
package com.mcpwrapper.platform.persistence.repository;

import com.mcpwrapper.platform.persistence.entity.EnvironmentVariableEntity;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface ReactiveEnvironmentVariableRepository extends R2dbcRepository<EnvironmentVariableEntity, UUID> {
    
    Mono<EnvironmentVariableEntity> findByName(String name);
    
    Mono<Boolean> existsByName(String name);
    
    Flux<EnvironmentVariableEntity> findByIsRequiredTrue();
    
    Flux<EnvironmentVariableEntity> findByIsMaskedTrue();
    
    Mono<Long> countByIsRequiredTrue();
}
