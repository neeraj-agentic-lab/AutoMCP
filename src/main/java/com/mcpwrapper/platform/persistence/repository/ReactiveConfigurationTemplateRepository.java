/*
 * MCP REST Adapter - Reactive Configuration Template Repository
 * 
 * @author Neeraj Yadav
 * @version 1.0.0
 * @since 2025-12-16
 */
package com.mcpwrapper.platform.persistence.repository;

import com.mcpwrapper.platform.persistence.entity.ConfigurationTemplateEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface ReactiveConfigurationTemplateRepository extends R2dbcRepository<ConfigurationTemplateEntity, UUID> {
    
    Mono<ConfigurationTemplateEntity> findByName(String name);
    
    Flux<ConfigurationTemplateEntity> findByCategory(String category);
    
    Flux<ConfigurationTemplateEntity> findByIsBuiltinTrue();
    
    Flux<ConfigurationTemplateEntity> findByIsPublicTrue();
    
    Flux<ConfigurationTemplateEntity> findByCreatedBy(String createdBy);
    
    @Query("SELECT * FROM configuration_templates WHERE name = :name ORDER BY version DESC LIMIT 1")
    Mono<ConfigurationTemplateEntity> findLatestVersionByName(@Param("name") String name);
    
    @Query("SELECT DISTINCT category FROM configuration_templates WHERE category IS NOT NULL")
    Flux<String> findDistinctCategories();
}
