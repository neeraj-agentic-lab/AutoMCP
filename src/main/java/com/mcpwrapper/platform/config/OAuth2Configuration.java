/*
 * MCP REST Adapter - OAuth2 Configuration
 * 
 * Conditional OAuth2 configuration that enables OAuth2 client support
 * only when proper credentials are provided via environment variables.
 * This allows the MCP adapter to authenticate with external REST APIs
 * that require OAuth2 and expose them as MCP tools.
 * 
 * @author Neeraj Yadav
 * @version 1.0.0
 * @since 2025-12-16
 * 
 * Copyright (c) 2025 Neeraj Yadav. All rights reserved.
 */
package com.mcpwrapper.platform.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.core.Authentication;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * OAuth2 configuration for external API authentication.
 * 
 * This configuration is conditionally enabled when OAuth2 credentials
 * are properly configured. It provides OAuth2 client capabilities for
 * authenticating with external REST APIs that the MCP adapter needs
 * to access and expose as MCP tools.
 * 
 * @author Neeraj Yadav
 * @since 1.0.0
 */
@Configuration
@ConditionalOnProperty(
    name = "mcp.oauth2.enabled", 
    havingValue = "true", 
    matchIfMissing = false
)
// Note: OAuth2 configuration is disabled by default
// To enable: set MCP_OAUTH2_ENABLED=true and provide OAuth2 credentials
public class OAuth2Configuration {
    
    /**
     * Creates a simple in-memory OAuth2 authorized client repository.
     * For production use, consider implementing a persistent repository.
     */
    @Bean
    public ServerOAuth2AuthorizedClientRepository authorizedClientRepository() {
        return new ServerOAuth2AuthorizedClientRepository() {
            @Override
            public Mono<OAuth2AuthorizedClient> loadAuthorizedClient(
                    String clientRegistrationId, 
                    Authentication principal, 
                    ServerWebExchange exchange) {
                // For client_credentials flow, we don't need to store/load clients
                // The client manager will handle token acquisition automatically
                return Mono.empty();
            }
            
            @Override
            public Mono<Void> saveAuthorizedClient(
                    OAuth2AuthorizedClient authorizedClient,
                    Authentication principal,
                    ServerWebExchange exchange) {
                // For client_credentials flow, we typically don't need to persist
                // tokens as they are short-lived and obtained on-demand
                return Mono.empty();
            }
            
            @Override
            public Mono<Void> removeAuthorizedClient(
                    String clientRegistrationId,
                    Authentication principal,
                    ServerWebExchange exchange) {
                return Mono.empty();
            }
        };
    }

    /**
     * Creates an OAuth2 authorized client manager for reactive applications.
     * 
     * @param clientRegistrationRepository the client registration repository
     * @param authorizedClientRepository the authorized client repository
     * @return configured OAuth2 authorized client manager
     */
    @Bean
    public ReactiveOAuth2AuthorizedClientManager authorizedClientManager(
            ReactiveClientRegistrationRepository clientRegistrationRepository,
            ServerOAuth2AuthorizedClientRepository authorizedClientRepository) {
        
        ReactiveOAuth2AuthorizedClientProvider authorizedClientProvider =
            ReactiveOAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials()
                .refreshToken()
                .build();
        
        DefaultReactiveOAuth2AuthorizedClientManager authorizedClientManager =
            new DefaultReactiveOAuth2AuthorizedClientManager(
                clientRegistrationRepository, 
                authorizedClientRepository
            );
        
        authorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider);
        
        return authorizedClientManager;
    }
    
    /**
     * Creates an OAuth2-enabled WebClient for making authenticated requests
     * to external APIs.
     * 
     * @param authorizedClientManager the OAuth2 authorized client manager
     * @return OAuth2-enabled WebClient
     */
    @Bean("oauth2WebClient")
    public WebClient oauth2WebClient(ReactiveOAuth2AuthorizedClientManager authorizedClientManager) {
        ServerOAuth2AuthorizedClientExchangeFilterFunction oauth2Filter =
            new ServerOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager);
        
        oauth2Filter.setDefaultClientRegistrationId("default");
        
        return WebClient.builder()
            .filter(oauth2Filter)
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
            .defaultHeader("User-Agent", "MCP-REST-Adapter/1.0.0")
            .build();
    }
}
