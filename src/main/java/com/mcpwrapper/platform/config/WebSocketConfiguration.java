/*
 * MCP REST Adapter - WebSocket Configuration
 * 
 * Configuration for WebSocket endpoints and handlers.
 * Sets up reactive WebSocket support for real-time MCP communication.
 * 
 * @author Neeraj Yadav
 * @version 1.0.0
 * @since 2025-12-16
 * 
 * Copyright (c) 2025 Neeraj Yadav. All rights reserved.
 */
package com.mcpwrapper.platform.config;

import com.mcpwrapper.transport.websocket.McpWebSocketHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for WebSocket support.
 * 
 * @author Neeraj Yadav
 * @since 1.0.0
 */
@Configuration
public class WebSocketConfiguration {
    
    /**
     * Configures WebSocket handler adapter.
     */
    @Bean
    public WebSocketHandlerAdapter handlerAdapter() {
        return new WebSocketHandlerAdapter();
    }
    
    /**
     * Maps WebSocket endpoints to handlers.
     */
    @Bean
    public HandlerMapping webSocketMapping(McpWebSocketHandler mcpHandler) {
        Map<String, WebSocketHandler> map = new HashMap<>();
        map.put("/ws/mcp", mcpHandler);
        map.put("/websocket/mcp", mcpHandler); // Alternative endpoint
        
        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setUrlMap(map);
        mapping.setOrder(-1); // High priority
        return mapping;
    }
}
