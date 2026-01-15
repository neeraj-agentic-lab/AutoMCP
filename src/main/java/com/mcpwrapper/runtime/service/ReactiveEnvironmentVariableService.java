/*
 * MCP REST Adapter - Reactive Environment Variable Service
 * 
 * Service for managing encrypted environment variables with reactive streams.
 * Provides secure storage and retrieval of sensitive configuration values
 * like API keys, tokens, and passwords.
 * 
 * @author Neeraj Yadav
 * @version 1.0.0
 * @since 2025-12-16
 * 
 * Copyright (c) 2025 Neeraj Yadav. All rights reserved.
 */
package com.mcpwrapper.runtime.service;

import com.mcpwrapper.platform.persistence.entity.EnvironmentVariableEntity;
import com.mcpwrapper.platform.persistence.repository.ReactiveEnvironmentVariableRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

/**
 * Reactive service for managing encrypted environment variables.
 * 
 * @author Neeraj Yadav
 * @since 1.0.0
 */
@Service
@Transactional
public class ReactiveEnvironmentVariableService {
    
    private static final Logger logger = LoggerFactory.getLogger(ReactiveEnvironmentVariableService.class);
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/ECB/PKCS5Padding";
    
    private final ReactiveEnvironmentVariableRepository repository;
    private final SecretKey encryptionKey;
    
    public ReactiveEnvironmentVariableService(ReactiveEnvironmentVariableRepository repository) {
        this.repository = repository;
        this.encryptionKey = generateEncryptionKey();
    }
    
    /**
     * Creates a new environment variable.
     */
    public Mono<EnvironmentVariableEntity> createVariable(CreateVariableRequest request) {
        logger.debug("Creating environment variable: {}", request.name());
        
        return validateVariableName(request.name())
            .then(encryptValue(request.value()))
            .map(encryptedValue -> EnvironmentVariableEntity.create(
                request.name(),
                request.description(),
                encryptedValue,
                request.isRequired(),
                request.isMasked()
            ))
            .flatMap(repository::save)
            .doOnSuccess(var -> logger.info("Created environment variable: {}", var.name()))
            .doOnError(error -> logger.error("Failed to create variable: {}", request.name(), error));
    }
    
    /**
     * Updates an existing environment variable.
     */
    public Mono<EnvironmentVariableEntity> updateVariable(UUID id, UpdateVariableRequest request) {
        logger.debug("Updating environment variable: {}", id);
        
        return repository.findById(id)
            .switchIfEmpty(Mono.error(new VariableNotFoundException("Variable not found: " + id)))
            .flatMap(existing -> {
                Mono<String> encryptedValueMono = request.value() != null 
                    ? encryptValue(request.value())
                    : Mono.just(existing.encryptedValue());
                
                return encryptedValueMono.map(encryptedValue -> existing.withUpdates(
                    request.description(),
                    encryptedValue,
                    request.isRequired(),
                    request.isMasked()
                ));
            })
            .flatMap(repository::save)
            .doOnSuccess(var -> logger.info("Updated environment variable: {}", var.name()));
    }
    
    /**
     * Retrieves a variable by ID.
     */
    public Mono<EnvironmentVariableEntity> getVariable(UUID id) {
        return repository.findById(id)
            .switchIfEmpty(Mono.error(new VariableNotFoundException("Variable not found: " + id)));
    }
    
    /**
     * Retrieves a variable by name.
     */
    public Mono<EnvironmentVariableEntity> getVariableByName(String name) {
        return repository.findByName(name)
            .switchIfEmpty(Mono.error(new VariableNotFoundException("Variable not found: " + name)));
    }
    
    /**
     * Gets the decrypted value of a variable.
     */
    public Mono<String> getDecryptedValue(String name) {
        return getVariableByName(name)
            .flatMap(var -> decryptValue(var.encryptedValue()));
    }
    
    /**
     * Lists all variables (without decrypted values).
     */
    public Flux<EnvironmentVariableEntity> getAllVariables() {
        return repository.findAll();
    }
    
    /**
     * Lists all required variables.
     */
    public Flux<EnvironmentVariableEntity> getRequiredVariables() {
        return repository.findByIsRequiredTrue();
    }
    
    /**
     * Deletes a variable.
     */
    public Mono<Void> deleteVariable(UUID id) {
        logger.debug("Deleting environment variable: {}", id);
        
        return repository.findById(id)
            .switchIfEmpty(Mono.error(new VariableNotFoundException("Variable not found: " + id)))
            .flatMap(var -> repository.deleteById(id)
                .doOnSuccess(v -> logger.info("Deleted environment variable: {}", var.name())));
    }
    
    /**
     * Validates that all required variables have values.
     */
    public Mono<ValidationResult> validateRequiredVariables() {
        return getRequiredVariables()
            .filter(var -> !var.hasValue())
            .collectList()
            .map(missingVars -> {
                if (missingVars.isEmpty()) {
                    return new ValidationResult(true, "All required variables are set");
                } else {
                    String missing = missingVars.stream()
                        .map(EnvironmentVariableEntity::name)
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("");
                    return new ValidationResult(false, "Missing required variables: " + missing);
                }
            });
    }
    
    // Private helper methods
    
    private Mono<Void> validateVariableName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return Mono.error(new IllegalArgumentException("Variable name cannot be empty"));
        }
        
        return repository.existsByName(name)
            .flatMap(exists -> exists 
                ? Mono.error(new VariableAlreadyExistsException("Variable already exists: " + name))
                : Mono.empty());
    }
    
    private Mono<String> encryptValue(String plainValue) {
        return Mono.fromCallable(() -> {
            try {
                Cipher cipher = Cipher.getInstance(TRANSFORMATION);
                cipher.init(Cipher.ENCRYPT_MODE, encryptionKey);
                byte[] encryptedBytes = cipher.doFinal(plainValue.getBytes(StandardCharsets.UTF_8));
                return Base64.getEncoder().encodeToString(encryptedBytes);
            } catch (Exception e) {
                throw new RuntimeException("Failed to encrypt value", e);
            }
        });
    }
    
    private Mono<String> decryptValue(String encryptedValue) {
        return Mono.fromCallable(() -> {
            try {
                Cipher cipher = Cipher.getInstance(TRANSFORMATION);
                cipher.init(Cipher.DECRYPT_MODE, encryptionKey);
                byte[] decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(encryptedValue));
                return new String(decryptedBytes, StandardCharsets.UTF_8);
            } catch (Exception e) {
                throw new RuntimeException("Failed to decrypt value", e);
            }
        });
    }
    
    private SecretKey generateEncryptionKey() {
        try {
            // In production, this should be loaded from secure storage
            KeyGenerator keyGen = KeyGenerator.getInstance(ALGORITHM);
            keyGen.init(256);
            return keyGen.generateKey();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate encryption key", e);
        }
    }
    
    // Request/Response records
    
    public record CreateVariableRequest(
        String name,
        String description,
        String value,
        Boolean isRequired,
        Boolean isMasked
    ) {}
    
    public record UpdateVariableRequest(
        String description,
        String value,
        Boolean isRequired,
        Boolean isMasked
    ) {}
    
    public record ValidationResult(
        boolean isValid,
        String message
    ) {}
    
    // Exception classes
    
    public static class VariableNotFoundException extends RuntimeException {
        public VariableNotFoundException(String message) {
            super(message);
        }
    }
    
    public static class VariableAlreadyExistsException extends RuntimeException {
        public VariableAlreadyExistsException(String message) {
            super(message);
        }
    }
}
