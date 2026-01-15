/*
 * MCP REST Adapter - WebSocket Handler
 * 
 * WebSocket handler for real-time bidirectional MCP communication.
 * Provides full-duplex communication for tool execution, streaming results,
 * and interactive sessions with MCP clients.
 * 
 * @author Neeraj Yadav
 * @version 1.0.0
 * @since 2025-12-16
 * 
 * Copyright (c) 2025 Neeraj Yadav. All rights reserved.
 */
package com.mcpwrapper.transport.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcpwrapper.adapter.rest.RestAdapterService;
import com.mcpwrapper.runtime.service.McpServiceFacade;
import com.mcpwrapper.transport.mcp.McpTool;
import com.mcpwrapper.transport.mcp.McpToolCall;
import com.mcpwrapper.transport.mcp.McpToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket handler for real-time MCP communication.
 * 
 * This handler supports the MCP protocol over WebSocket connections:
 * - Tool discovery and listing
 * - Real-time tool execution
 * - Streaming results and progress
 * - Session management and cleanup
 * - Error handling and recovery
 * 
 * @author Neeraj Yadav
 * @since 1.0.0
 */
@Component
public class McpWebSocketHandler implements WebSocketHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(McpWebSocketHandler.class);
    
    private final McpServiceFacade serviceFacade;
    private final RestAdapterService restAdapter;
    private final ObjectMapper objectMapper;
    
    // Session management
    private final Map<String, WebSocketSession> activeSessions = new ConcurrentHashMap<>();
    private final Map<String, Sinks.Many<WebSocketMessage>> sessionSinks = new ConcurrentHashMap<>();
    
    public McpWebSocketHandler(
            McpServiceFacade serviceFacade,
            RestAdapterService restAdapter,
            ObjectMapper objectMapper) {
        this.serviceFacade = serviceFacade;
        this.restAdapter = restAdapter;
        this.objectMapper = objectMapper;
    }
    
    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String sessionId = session.getId();
        logger.info("WebSocket session established: {}", sessionId);
        
        // Register session
        activeSessions.put(sessionId, session);
        Sinks.Many<WebSocketMessage> sink = Sinks.many().multicast().onBackpressureBuffer();
        sessionSinks.put(sessionId, sink);
        
        // Send welcome message
        sendWelcomeMessage(session);
        
        // Handle incoming messages
        Mono<Void> input = session.receive()
            .doOnNext(message -> logger.debug("Received message from {}: {}", sessionId, message.getPayloadAsText()))
            .flatMap(message -> handleIncomingMessage(session, message))
            .doOnError(error -> logger.error("Error handling message from session {}", sessionId, error))
            .onErrorResume(error -> Mono.empty())
            .then();
        
        // Handle outgoing messages
        Mono<Void> output = session.send(sink.asFlux())
            .doOnError(error -> logger.error("Error sending message to session {}", sessionId, error));
        
        // Cleanup on session close
        return Mono.zip(input, output)
            .doFinally(signal -> {
                logger.info("WebSocket session closed: {} ({})", sessionId, signal);
                activeSessions.remove(sessionId);
                sessionSinks.remove(sessionId);
            })
            .then();
    }
    
    private void sendWelcomeMessage(WebSocketSession session) {
        try {
            WelcomeMessage welcome = new WelcomeMessage(
                "welcome",
                "MCP REST Adapter v1.0.0",
                "Connected to MCP REST Adapter",
                Instant.now()
            );
            
            String messageJson = objectMapper.writeValueAsString(welcome);
            sendMessage(session.getId(), messageJson);
        } catch (Exception e) {
            logger.error("Failed to send welcome message to session {}", session.getId(), e);
        }
    }
    
    private Mono<Void> handleIncomingMessage(WebSocketSession session, WebSocketMessage message) {
        try {
            String payload = message.getPayloadAsText();
            JsonNode messageNode = objectMapper.readTree(payload);
            
            String messageType = messageNode.has("type") ? messageNode.get("type").asText() : "unknown";
            
            return switch (messageType) {
                case "list_tools" -> handleListTools(session);
                case "get_tool" -> handleGetTool(session, messageNode);
                case "invoke_tool" -> handleInvokeTool(session, messageNode);
                case "ping" -> handlePing(session, messageNode);
                case "subscribe" -> handleSubscribe(session, messageNode);
                case "unsubscribe" -> handleUnsubscribe(session, messageNode);
                default -> handleUnknownMessage(session, messageType);
            };
        } catch (Exception e) {
            logger.error("Failed to parse message from session {}", session.getId(), e);
            return sendErrorMessage(session.getId(), "PARSE_ERROR", "Failed to parse message: " + e.getMessage());
        }
    }
    
    private Mono<Void> handleListTools(WebSocketSession session) {
        return serviceFacade.getAllTools()
            .collectList()
            .flatMap(tools -> {
                try {
                    ToolListResponse response = new ToolListResponse(
                        "tool_list",
                        tools,
                        tools.size(),
                        Instant.now()
                    );
                    
                    String responseJson = objectMapper.writeValueAsString(response);
                    return sendMessage(session.getId(), responseJson);
                } catch (Exception e) {
                    logger.error("Failed to serialize tool list for session {}", session.getId(), e);
                    return sendErrorMessage(session.getId(), "SERIALIZATION_ERROR", "Failed to serialize tool list");
                }
            });
    }
    
    private Mono<Void> handleGetTool(WebSocketSession session, JsonNode message) {
        if (!message.has("toolName")) {
            return sendErrorMessage(session.getId(), "MISSING_PARAMETER", "toolName is required");
        }
        
        String toolName = message.get("toolName").asText();
        
        return serviceFacade.getTool(toolName)
            .flatMap(tool -> {
                try {
                    ToolResponse response = new ToolResponse(
                        "tool",
                        tool,
                        Instant.now()
                    );
                    
                    String responseJson = objectMapper.writeValueAsString(response);
                    return sendMessage(session.getId(), responseJson);
                } catch (Exception e) {
                    logger.error("Failed to serialize tool for session {}", session.getId(), e);
                    return sendErrorMessage(session.getId(), "SERIALIZATION_ERROR", "Failed to serialize tool");
                }
            })
            .switchIfEmpty(sendErrorMessage(session.getId(), "TOOL_NOT_FOUND", "Tool not found: " + toolName));
    }
    
    private Mono<Void> handleInvokeTool(WebSocketSession session, JsonNode message) {
        try {
            if (!message.has("toolName") || !message.has("arguments")) {
                return sendErrorMessage(session.getId(), "MISSING_PARAMETERS", "toolName and arguments are required");
            }
            
            String toolName = message.get("toolName").asText();
            JsonNode arguments = message.get("arguments");
            String callId = message.has("callId") ? message.get("callId").asText() : 
                java.util.UUID.randomUUID().toString();
            
            // Send execution started message
            sendExecutionStarted(session.getId(), callId, toolName);
            
            return serviceFacade.getTool(toolName)
                .switchIfEmpty(Mono.error(new RuntimeException("Tool not found: " + toolName)))
                .flatMap(tool -> {
                    McpToolCall toolCall = McpToolCall.builder()
                        .name(toolName)
                        .arguments(arguments)
                        .callId(callId)
                        .build();
                    
                    return restAdapter.executeRestCall(toolCall, tool);
                })
                .flatMap(result -> sendExecutionResult(session.getId(), result))
                .onErrorResume(error -> {
                    logger.error("Tool execution failed for session {}", session.getId(), error);
                    return sendExecutionError(session.getId(), callId, error.getMessage());
                });
                
        } catch (Exception e) {
            logger.error("Failed to handle tool invocation for session {}", session.getId(), e);
            return sendErrorMessage(session.getId(), "INVOCATION_ERROR", "Failed to invoke tool: " + e.getMessage());
        }
    }
    
    private Mono<Void> handlePing(WebSocketSession session, JsonNode message) {
        try {
            PongResponse pong = new PongResponse(
                "pong",
                message.has("timestamp") ? message.get("timestamp").asLong() : System.currentTimeMillis(),
                Instant.now()
            );
            
            String responseJson = objectMapper.writeValueAsString(pong);
            return sendMessage(session.getId(), responseJson);
        } catch (Exception e) {
            logger.error("Failed to handle ping for session {}", session.getId(), e);
            return Mono.empty();
        }
    }
    
    private Mono<Void> handleSubscribe(WebSocketSession session, JsonNode message) {
        // TODO: Implement subscription to events (tool executions, system events, etc.)
        return sendMessage(session.getId(), "{\"type\":\"subscribed\",\"message\":\"Subscription feature not yet implemented\"}");
    }
    
    private Mono<Void> handleUnsubscribe(WebSocketSession session, JsonNode message) {
        // TODO: Implement unsubscription from events
        return sendMessage(session.getId(), "{\"type\":\"unsubscribed\",\"message\":\"Unsubscription feature not yet implemented\"}");
    }
    
    private Mono<Void> handleUnknownMessage(WebSocketSession session, String messageType) {
        return sendErrorMessage(session.getId(), "UNKNOWN_MESSAGE_TYPE", "Unknown message type: " + messageType);
    }
    
    private Mono<Void> sendMessage(String sessionId, String message) {
        Sinks.Many<WebSocketMessage> sink = sessionSinks.get(sessionId);
        WebSocketSession session = activeSessions.get(sessionId);
        
        if (sink != null && session != null) {
            WebSocketMessage wsMessage = session.textMessage(message);
            sink.tryEmitNext(wsMessage);
        }
        
        return Mono.empty();
    }
    
    private Mono<Void> sendErrorMessage(String sessionId, String errorCode, String errorMessage) {
        try {
            ErrorResponse error = new ErrorResponse(
                "error",
                errorCode,
                errorMessage,
                Instant.now()
            );
            
            String errorJson = objectMapper.writeValueAsString(error);
            return sendMessage(sessionId, errorJson);
        } catch (Exception e) {
            logger.error("Failed to send error message to session {}", sessionId, e);
            return Mono.empty();
        }
    }
    
    private Mono<Void> sendExecutionStarted(String sessionId, String callId, String toolName) {
        try {
            ExecutionStartedResponse response = new ExecutionStartedResponse(
                "execution_started",
                callId,
                toolName,
                Instant.now()
            );
            
            String responseJson = objectMapper.writeValueAsString(response);
            return sendMessage(sessionId, responseJson);
        } catch (Exception e) {
            logger.error("Failed to send execution started message to session {}", sessionId, e);
            return Mono.empty();
        }
    }
    
    private Mono<Void> sendExecutionResult(String sessionId, McpToolResult result) {
        try {
            ExecutionResultResponse response = new ExecutionResultResponse(
                "execution_result",
                result,
                Instant.now()
            );
            
            String responseJson = objectMapper.writeValueAsString(response);
            return sendMessage(sessionId, responseJson);
        } catch (Exception e) {
            logger.error("Failed to send execution result to session {}", sessionId, e);
            return Mono.empty();
        }
    }
    
    private Mono<Void> sendExecutionError(String sessionId, String callId, String errorMessage) {
        try {
            ExecutionErrorResponse response = new ExecutionErrorResponse(
                "execution_error",
                callId,
                errorMessage,
                Instant.now()
            );
            
            String responseJson = objectMapper.writeValueAsString(response);
            return sendMessage(sessionId, responseJson);
        } catch (Exception e) {
            logger.error("Failed to send execution error to session {}", sessionId, e);
            return Mono.empty();
        }
    }
    
    // Response DTOs
    
    public record WelcomeMessage(
        String type,
        String version,
        String message,
        Instant timestamp
    ) {}
    
    public record ToolListResponse(
        String type,
        java.util.List<McpTool> tools,
        int count,
        Instant timestamp
    ) {}
    
    public record ToolResponse(
        String type,
        McpTool tool,
        Instant timestamp
    ) {}
    
    public record ErrorResponse(
        String type,
        String errorCode,
        String message,
        Instant timestamp
    ) {}
    
    public record PongResponse(
        String type,
        long clientTimestamp,
        Instant serverTimestamp
    ) {}
    
    public record ExecutionStartedResponse(
        String type,
        String callId,
        String toolName,
        Instant timestamp
    ) {}
    
    public record ExecutionResultResponse(
        String type,
        McpToolResult result,
        Instant timestamp
    ) {}
    
    public record ExecutionErrorResponse(
        String type,
        String callId,
        String error,
        Instant timestamp
    ) {}
}
