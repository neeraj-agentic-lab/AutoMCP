/*
 * MCP REST Adapter - Reactive Tool Usage Stats Repository
 * 
 * @author Neeraj Yadav
 * @version 1.0.0
 * @since 2025-12-16
 */
package com.mcpwrapper.platform.persistence.repository;

import com.mcpwrapper.platform.persistence.entity.ToolUsageStatsEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface ReactiveToolUsageStatsRepository extends R2dbcRepository<ToolUsageStatsEntity, String> {
    
    Flux<ToolUsageStatsEntity> findByApiConfigId(UUID apiConfigId);
    
    @Query("SELECT * FROM tool_usage_stats ORDER BY invocation_count DESC LIMIT :limit")
    Flux<ToolUsageStatsEntity> findTopByUsage(@Param("limit") int limit);
    
    @Query("SELECT * FROM tool_usage_stats WHERE last_used > :since")
    Flux<ToolUsageStatsEntity> findRecentlyUsed(@Param("since") Instant since);
    
    @Query("SELECT * FROM tool_usage_stats WHERE last_error > :since")
    Flux<ToolUsageStatsEntity> findWithRecentErrors(@Param("since") Instant since);
    
    Mono<Long> deleteByApiConfigId(UUID apiConfigId);
}
