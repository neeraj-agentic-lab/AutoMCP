/*
 * MCP REST Adapter - Global Exception Handler
 * 
 * Global exception handler for consistent error responses across all controllers.
 * Provides structured error handling with proper HTTP status codes and
 * detailed error information for debugging and client consumption.
 * 
 * @author Neeraj Yadav
 * @version 1.0.0
 * @since 2025-12-16
 * 
 * Copyright (c) 2025 Neeraj Yadav. All rights reserved.
 */
package com.mcpwrapper.transport.controller;

import com.mcpwrapper.adapter.openapi.OpenApiParserService;
import com.mcpwrapper.runtime.service.ReactiveConfigurationService;
import com.mcpwrapper.runtime.service.ReactiveEnvironmentVariableService;
import com.mcpwrapper.runtime.service.ReactiveToolRegistryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Global exception handler for all REST controllers.
 * 
 * This handler provides consistent error responses across the application
 * with proper HTTP status codes and structured error information.
 * 
 * @author Neeraj Yadav
 * @since 1.0.0
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    /**
     * Handles configuration not found exceptions.
     */
    @ExceptionHandler(ReactiveConfigurationService.ConfigurationNotFoundException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleConfigurationNotFound(
            ReactiveConfigurationService.ConfigurationNotFoundException ex) {
        
        logger.warn("Configuration not found: {}", ex.getMessage());
        
        ErrorResponse error = new ErrorResponse(
            "CONFIGURATION_NOT_FOUND",
            ex.getMessage(),
            HttpStatus.NOT_FOUND.value(),
            Instant.now(),
            null
        );
        
        return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body(error));
    }
    
    /**
     * Handles configuration already exists exceptions.
     */
    @ExceptionHandler(ReactiveConfigurationService.ConfigurationAlreadyExistsException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleConfigurationAlreadyExists(
            ReactiveConfigurationService.ConfigurationAlreadyExistsException ex) {
        
        logger.warn("Configuration already exists: {}", ex.getMessage());
        
        ErrorResponse error = new ErrorResponse(
            "CONFIGURATION_ALREADY_EXISTS",
            ex.getMessage(),
            HttpStatus.CONFLICT.value(),
            Instant.now(),
            null
        );
        
        return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).body(error));
    }
    
    /**
     * Handles tool not found exceptions.
     */
    @ExceptionHandler({
        ReactiveToolRegistryService.ToolNotFoundException.class,
        McpController.ToolNotFoundException.class
    })
    public Mono<ResponseEntity<ErrorResponse>> handleToolNotFound(RuntimeException ex) {
        logger.warn("Tool not found: {}", ex.getMessage());
        
        ErrorResponse error = new ErrorResponse(
            "TOOL_NOT_FOUND",
            ex.getMessage(),
            HttpStatus.NOT_FOUND.value(),
            Instant.now(),
            null
        );
        
        return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body(error));
    }
    
    /**
     * Handles tool already exists exceptions.
     */
    @ExceptionHandler(ReactiveToolRegistryService.ToolAlreadyExistsException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleToolAlreadyExists(
            ReactiveToolRegistryService.ToolAlreadyExistsException ex) {
        
        logger.warn("Tool already exists: {}", ex.getMessage());
        
        ErrorResponse error = new ErrorResponse(
            "TOOL_ALREADY_EXISTS",
            ex.getMessage(),
            HttpStatus.CONFLICT.value(),
            Instant.now(),
            null
        );
        
        return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).body(error));
    }
    
    /**
     * Handles environment variable exceptions.
     */
    @ExceptionHandler({
        ReactiveEnvironmentVariableService.VariableNotFoundException.class,
        ReactiveEnvironmentVariableService.VariableAlreadyExistsException.class
    })
    public Mono<ResponseEntity<ErrorResponse>> handleEnvironmentVariableException(RuntimeException ex) {
        HttpStatus status = ex instanceof ReactiveEnvironmentVariableService.VariableNotFoundException 
            ? HttpStatus.NOT_FOUND 
            : HttpStatus.CONFLICT;
        
        String errorCode = ex instanceof ReactiveEnvironmentVariableService.VariableNotFoundException 
            ? "VARIABLE_NOT_FOUND" 
            : "VARIABLE_ALREADY_EXISTS";
        
        logger.warn("Environment variable error: {}", ex.getMessage());
        
        ErrorResponse error = new ErrorResponse(
            errorCode,
            ex.getMessage(),
            status.value(),
            Instant.now(),
            null
        );
        
        return Mono.just(ResponseEntity.status(status).body(error));
    }
    
    /**
     * Handles OpenAPI parsing exceptions.
     */
    @ExceptionHandler(OpenApiParserService.OpenApiParseException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleOpenApiParseException(
            OpenApiParserService.OpenApiParseException ex) {
        
        logger.error("OpenAPI parsing failed: {}", ex.getMessage());
        
        ErrorResponse error = new ErrorResponse(
            "OPENAPI_PARSE_ERROR",
            ex.getMessage(),
            HttpStatus.BAD_REQUEST.value(),
            Instant.now(),
            null
        );
        
        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error));
    }
    
    /**
     * Handles validation exceptions.
     */
    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleValidationException(WebExchangeBindException ex) {
        logger.warn("Validation failed: {}", ex.getMessage());
        
        List<String> validationErrors = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .collect(Collectors.toList());
        
        ErrorResponse error = new ErrorResponse(
            "VALIDATION_ERROR",
            "Request validation failed",
            HttpStatus.BAD_REQUEST.value(),
            Instant.now(),
            validationErrors
        );
        
        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error));
    }
    
    /**
     * Handles invalid input exceptions.
     */
    @ExceptionHandler({IllegalArgumentException.class, ServerWebInputException.class})
    public Mono<ResponseEntity<ErrorResponse>> handleInvalidInput(Exception ex) {
        logger.warn("Invalid input: {}", ex.getMessage());
        
        ErrorResponse error = new ErrorResponse(
            "INVALID_INPUT",
            ex.getMessage(),
            HttpStatus.BAD_REQUEST.value(),
            Instant.now(),
            null
        );
        
        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error));
    }
    
    /**
     * Handles security exceptions.
     */
    @ExceptionHandler(SecurityException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleSecurityException(SecurityException ex) {
        logger.warn("Security violation: {}", ex.getMessage());
        
        ErrorResponse error = new ErrorResponse(
            "SECURITY_ERROR",
            "Access denied",
            HttpStatus.FORBIDDEN.value(),
            Instant.now(),
            null
        );
        
        return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).body(error));
    }
    
    /**
     * Handles timeout exceptions.
     */
    @ExceptionHandler(java.util.concurrent.TimeoutException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleTimeoutException(java.util.concurrent.TimeoutException ex) {
        logger.error("Operation timed out: {}", ex.getMessage());
        
        ErrorResponse error = new ErrorResponse(
            "TIMEOUT_ERROR",
            "Operation timed out",
            HttpStatus.REQUEST_TIMEOUT.value(),
            Instant.now(),
            null
        );
        
        return Mono.just(ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).body(error));
    }
    
    /**
     * Handles all other runtime exceptions.
     */
    @ExceptionHandler(RuntimeException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleRuntimeException(RuntimeException ex) {
        logger.error("Unexpected runtime error", ex);
        
        // Temporarily expose the real error message for debugging
        String debugMessage = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
        
        ErrorResponse error = new ErrorResponse(
            "RUNTIME_ERROR",
            "Debug: " + debugMessage + " | Cause: " + (ex.getCause() != null ? ex.getCause().getMessage() : "None"),
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            Instant.now(),
            null
        );
        
        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error));
    }
    
    /**
     * Handles all other exceptions.
     */
    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<ErrorResponse>> handleGenericException(Exception ex) {
        logger.error("Unexpected error", ex);
        
        // Temporarily expose the real error message for debugging
        String debugMessage = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
        
        ErrorResponse error = new ErrorResponse(
            "INTERNAL_ERROR",
            "Debug: " + debugMessage + " | Cause: " + (ex.getCause() != null ? ex.getCause().getMessage() : "None"),
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            Instant.now(),
            null
        );
        
        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error));
    }
    
    /**
     * Standardized error response structure.
     */
    public record ErrorResponse(
        String errorCode,
        String message,
        int statusCode,
        Instant timestamp,
        List<String> details
    ) {}
}
