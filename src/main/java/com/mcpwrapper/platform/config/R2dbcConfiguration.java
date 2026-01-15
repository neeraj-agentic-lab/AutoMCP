/*
 * MCP REST Adapter - R2DBC Configuration
 * 
 * Configuration class for R2DBC reactive database access.
 * Sets up connection factory, transaction manager, and custom converters
 * for optimal reactive database performance.
 * 
 * @author Neeraj Yadav
 * @version 1.0.0
 * @since 2025-12-16
 * 
 * Copyright (c) 2025 Neeraj Yadav. All rights reserved.
 */
package com.mcpwrapper.platform.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.r2dbc.postgresql.codec.Json;
import io.r2dbc.spi.ConnectionFactory;
import java.net.InetAddress;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.r2dbc.config.AbstractR2dbcConfiguration;
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.transaction.ReactiveTransactionManager;

import java.util.List;

/**
 * R2DBC configuration for reactive database access.
 * 
 * This configuration class sets up:
 * - Custom JSON converters for PostgreSQL JSONB columns
 * - Transaction manager for reactive transactions
 * - Repository scanning for reactive repositories
 * - Connection factory optimization
 * 
 * @author Neeraj Yadav
 * @since 1.0.0
 */
@Configuration
@EnableR2dbcRepositories(basePackages = "com.mcpwrapper.platform.persistence.repository")
public class R2dbcConfiguration extends AbstractR2dbcConfiguration {
    
    private final ConnectionFactory connectionFactory;
    private final ObjectMapper objectMapper;
    
    public R2dbcConfiguration(ConnectionFactory connectionFactory, ObjectMapper objectMapper) {
        this.connectionFactory = connectionFactory;
        this.objectMapper = objectMapper;
    }
    
    @Override
    public ConnectionFactory connectionFactory() {
        return connectionFactory;
    }
    
    /**
     * Configures reactive transaction manager.
     * 
     * @return reactive transaction manager
     */
    @Bean
    public ReactiveTransactionManager transactionManager() {
        return new R2dbcTransactionManager(connectionFactory);
    }
    
    /**
     * Configures custom converters for R2DBC.
     * 
     * @return R2DBC custom conversions
     */
    @Override
    public R2dbcCustomConversions r2dbcCustomConversions() {
        return new R2dbcCustomConversions(
            R2dbcCustomConversions.STORE_CONVERSIONS,
            List.of(
                new JsonNodeToJsonConverter(objectMapper),
                new JsonToJsonNodeConverter(objectMapper)
            )
        );
    }
    
    /**
     * Converter for writing JsonNode to PostgreSQL JSONB.
     */
    @WritingConverter
    static class JsonNodeToJsonConverter implements Converter<JsonNode, Json> {
        
        private final ObjectMapper objectMapper;
        
        JsonNodeToJsonConverter(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }
        
        @Override
        public Json convert(JsonNode source) {
            try {
                if (source == null) {
                    return null;
                }
                String jsonString = objectMapper.writeValueAsString(source);
                return Json.of(jsonString);
            } catch (Exception e) {
                throw new RuntimeException("Failed to convert JsonNode to Json", e);
            }
        }
    }
    
    /**
     * Converter for reading PostgreSQL JSONB to JsonNode.
     */
    @ReadingConverter
    static class JsonToJsonNodeConverter implements Converter<Json, JsonNode> {
        
        private final ObjectMapper objectMapper;
        
        JsonToJsonNodeConverter(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }
        
        @Override
        public JsonNode convert(Json source) {
            try {
                if (source == null) {
                    return null;
                }
                return objectMapper.readTree(source.asString());
            } catch (Exception e) {
                throw new RuntimeException("Failed to convert Json to JsonNode", e);
            }
        }
    }
}
