/*
 * MCP REST Adapter - Reactive API Health Status Repository
 * 
 * @author Neeraj Yadav
 * @version 1.0.0
 * @since 2025-12-16
 */
package com.mcpwrapper.platform.persistence.repository;

import com.mcpwrapper.platform.persistence.entity.ApiHealthStatusEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface ReactiveApiHealthStatusRepository extends R2dbcRepository<ApiHealthStatusEntity, UUID> {
    
    Flux<ApiHealthStatusEntity> findByHealthState(String healthState);
    
    @Query("SELECT * FROM api_health_status WHERE last_checked_at < :threshold")
    Flux<ApiHealthStatusEntity> findStaleHealthRecords(@Param("threshold") Instant threshold);
    
    @Query("SELECT * FROM api_health_status WHERE health_state = 'UNHEALTHY'")
    Flux<ApiHealthStatusEntity> findUnhealthyApis();
    
    @Query("SELECT * FROM api_health_status WHERE consecutive_failures >= :threshold")
    Flux<ApiHealthStatusEntity> findApisWithConsecutiveFailures(@Param("threshold") int threshold);
}
