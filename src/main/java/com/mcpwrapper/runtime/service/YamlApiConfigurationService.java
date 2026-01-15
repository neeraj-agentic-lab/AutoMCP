package com.mcpwrapper.runtime.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcpwrapper.platform.persistence.entity.ApiConfigurationEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class YamlApiConfigurationService {

    private static final Logger logger = LoggerFactory.getLogger(YamlApiConfigurationService.class);

    private static final Duration DEFAULT_CACHE_TTL = Duration.ofMinutes(5);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    private final Map<UUID, CacheEntry> cache = new ConcurrentHashMap<>();

    public YamlApiConfigurationService(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    public Mono<EffectiveApiConfig> resolveEffectiveConfig(ApiConfigurationEntity dbConfig) {
        if (dbConfig == null) {
            return Mono.error(new IllegalArgumentException("Database configuration cannot be null"));
        }
        
        System.out.println("=== YAML CONFIG DEBUG: Starting resolveEffectiveConfig for config ID: " + 
            (dbConfig.id() != null ? dbConfig.id().toString() : "null"));
        
        // Add comprehensive debugging to catch NPE with full stack trace
        return resolveYamlConfig(dbConfig)
            .doOnNext(yamlConfig -> System.out.println("=== REACTIVE CHAIN DEBUG: resolveYamlConfig completed successfully, yamlConfig: " + (yamlConfig != null ? "present" : "null")))
            .doOnError(error -> {
                String errorMessage = error.getMessage() != null ? error.getMessage() : "null";
                System.out.println("=== REACTIVE CHAIN ERROR: Error in resolveYamlConfig: " + error.getClass().getSimpleName() + " - " + errorMessage);
                System.out.println("=== REACTIVE CHAIN ERROR: Full stack trace:");
                error.printStackTrace();
            })
            .onErrorResume(error -> {
                String errorMessage = error.getMessage() != null ? error.getMessage() : "null";
                System.out.println("=== ERROR RECOVERY: Caught error in resolveEffectiveConfig: " + error.getClass().getSimpleName() + " - " + errorMessage);
                return Mono.just((JsonNode) null);
            })
            .doOnNext(yamlConfig -> System.out.println("=== REACTIVE CHAIN DEBUG: About to enter map operation, yamlConfig: " + (yamlConfig != null ? "present" : "null")))
            .map(yamlConfig -> {
                try {
                    System.out.println("=== MAP OPERATION DEBUG: Starting map operation");
                    return createEffectiveConfig(dbConfig, yamlConfig);
                } catch (Exception e) {
                    System.out.println("=== MAP OPERATION ERROR: Exception in map operation: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                    System.out.println("=== MAP OPERATION ERROR: Full stack trace:");
                    e.printStackTrace();
                    throw e;
                }
            })
            .doOnNext(result -> System.out.println("=== REACTIVE CHAIN DEBUG: EffectiveApiConfig created successfully"))
            .doOnError(error -> {
                String errorMessage = error.getMessage() != null ? error.getMessage() : "null";
                System.out.println("=== FINAL ERROR: Error in resolveEffectiveConfig: " + error.getClass().getSimpleName() + " - " + errorMessage);
                System.out.println("=== FINAL ERROR: Full stack trace:");
                error.printStackTrace();
            });
    }
    
    private EffectiveApiConfig createEffectiveConfig(ApiConfigurationEntity dbConfig, JsonNode yamlConfig) {
        // Extract values safely with null checks and YAML overlay
        String configId = dbConfig.id() != null ? dbConfig.id().toString() : "null-id";
        String baseUrl = yamlConfig != null && yamlConfig.hasNonNull("baseUrl")
            ? yamlConfig.get("baseUrl").asText()
            : (dbConfig.baseUrl() != null ? dbConfig.baseUrl() : "");
        Integer timeoutSeconds = yamlConfig != null && yamlConfig.has("timeoutSeconds") && yamlConfig.get("timeoutSeconds").canConvertToInt()
            ? yamlConfig.get("timeoutSeconds").asInt()
            : (dbConfig.timeoutSeconds() != null ? dbConfig.timeoutSeconds() : 30);
        JsonNode authConfig = yamlConfig != null && yamlConfig.has("auth")
            ? yamlConfig.get("auth")
            : dbConfig.authConfig();
        
        System.out.println("=== CREATE CONFIG DEBUG: Resolved effective config - ID: " + configId + 
            ", baseUrl: " + baseUrl + ", timeout: " + timeoutSeconds + 
            ", authConfig: " + (authConfig != null ? "present" : "null") +
            ", yamlOverride: " + (yamlConfig != null ? "yes" : "no"));
        
        return new EffectiveApiConfig(dbConfig, baseUrl, timeoutSeconds, authConfig);
    }

    public Mono<Void> invalidate(UUID apiConfigId) {
        cache.remove(apiConfigId);
        return Mono.empty();
    }

    private Mono<JsonNode> resolveYamlConfig(ApiConfigurationEntity dbConfig) {
        System.out.println("=== RESOLVE YAML STEP 1: Starting resolveYamlConfig");
        
        try {
            System.out.println("=== RESOLVE YAML STEP 2: About to call extractYamlSource");
            // Extract YAML source synchronously to avoid reactive chain complexity
            YamlSource yamlSource = extractYamlSource(dbConfig);
            System.out.println("=== RESOLVE YAML STEP 3: Extracted YAML source: " + (yamlSource != null ? "present" : "null"));
            
            if (yamlSource == null || yamlSource.url() == null || yamlSource.url().isBlank()) {
                System.out.println("=== RESOLVE YAML STEP 4: No YAML source, returning null");
                return Mono.just((JsonNode) null);
            }
            
            // Check cache first
            UUID configId = dbConfig.id();
            if (configId != null) {
                CacheEntry existing = cache.get(configId);
                if (existing != null && existing.expiresAt().isAfter(Instant.now())) {
                    System.out.println("=== YAML CONFIG DEBUG: Found valid cache entry, returning cached YAML config");
                    // Return the cached Mono directly - don't call .value() on it
                    return existing.value();
                }
                System.out.println("=== YAML CONFIG DEBUG: Cache entry expired or not found");
            }
            
            // Load YAML from URL with proper error handling
            System.out.println("=== YAML CONFIG DEBUG: Loading YAML from URL: " + yamlSource.url());
            Mono<JsonNode> loader = loadYamlFromUrl(yamlSource.url())
                .timeout(Duration.ofSeconds(5)) // Add timeout to prevent hanging
                .onErrorResume(error -> {
                    System.out.println("=== YAML CONFIG DEBUG: Error loading YAML, falling back to null: " + error.getMessage());
                    return Mono.just((JsonNode) null);
                })
                .cache();
            
            // Cache the result
            if (configId != null) {
                CacheEntry entry = new CacheEntry(loader, Instant.now().plus(yamlSource.ttl()));
                cache.put(configId, entry);
            }
            
            return loader;
            
        } catch (Exception e) {
            System.out.println("=== YAML CONFIG DEBUG: Exception in resolveYamlConfig: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            System.out.println("=== YAML CONFIG DEBUG: Full stack trace:");
            e.printStackTrace();
            return Mono.just((JsonNode) null);
        }
    }

    private Mono<JsonNode> loadYamlFromUrl(String url) {
        return webClient
            .get()
            .uri(url)
            .retrieve()
            .bodyToMono(String.class)
            .timeout(Duration.ofSeconds(3)) // Add timeout for HTTP request
            .flatMap(this::parseYamlToJson)
            .doOnSuccess(ignored -> logger.debug("Loaded YAML configuration from URL"))
            .onErrorResume(error -> {
                if (error instanceof WebClientResponseException webError) {
                    HttpStatusCode status = webError.getStatusCode();
                    logger.warn("Failed to load YAML config from URL (status={}): {}", status.value(), webError.getMessage());
                } else {
                    logger.warn("Failed to load YAML config from URL: {}", error.getMessage());
                }
                return Mono.just((JsonNode) null);
            });
    }

    private Mono<JsonNode> parseYamlToJson(String yamlText) {
        return Mono.fromCallable(() -> {
            try {
                if (yamlText == null || yamlText.trim().isEmpty()) {
                    return null;
                }
                org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml();
                Object data = yaml.load(yamlText);
                return data != null ? objectMapper.valueToTree(data) : null;
            } catch (Exception e) {
                System.out.println("=== YAML CONFIG DEBUG: Error parsing YAML: " + e.getMessage());
                return null;
            }
        })
        .subscribeOn(Schedulers.boundedElastic()) // Use bounded elastic scheduler for blocking operations
        .cast(JsonNode.class); // Explicit cast to fix type inference
    }

    private YamlSource extractYamlSource(ApiConfigurationEntity dbConfig) {
        JsonNode advancedConfig = dbConfig.advancedConfig();
        if (advancedConfig == null || !advancedConfig.has("yaml")) {
            return null;
        }

        JsonNode yamlNode = advancedConfig.get("yaml");
        String url = yamlNode.hasNonNull("url") ? yamlNode.get("url").asText() : null;

        Duration ttl = DEFAULT_CACHE_TTL;
        if (yamlNode.has("cacheTtlSeconds") && yamlNode.get("cacheTtlSeconds").canConvertToLong()) {
            long seconds = yamlNode.get("cacheTtlSeconds").asLong();
            if (seconds > 0) {
                ttl = Duration.ofSeconds(seconds);
            }
        }

        return new YamlSource(url, ttl);
    }

    private record CacheEntry(Mono<JsonNode> value, Instant expiresAt) {}

    private record YamlSource(String url, Duration ttl) {}

    public record EffectiveApiConfig(
        ApiConfigurationEntity dbConfig,
        String baseUrl,
        Integer timeoutSeconds,
        JsonNode authConfig
    ) {}
}
