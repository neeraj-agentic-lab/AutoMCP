/*
 * MCP REST Adapter - Security Configuration
 * 
 * Security configuration for the MCP REST Adapter.
 * Provides authentication, authorization, CORS, and security headers
 * for protecting the API endpoints and WebSocket connections.
 * 
 * @author Neeraj Yadav
 * @version 1.0.0
 * @since 2025-12-16
 * 
 * Copyright (c) 2025 Neeraj Yadav. All rights reserved.
 */
package com.mcpwrapper.platform.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.config.Customizer;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.header.ReferrerPolicyServerHttpHeadersWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

/**
 * Security configuration for the MCP REST Adapter.
 * 
 * @author Neeraj Yadav
 * @since 1.0.0
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfiguration {
    
    /**
     * Configures the security filter chain.
     */
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .headers(headers -> headers
                .frameOptions(Customizer.withDefaults())
                .contentTypeOptions(Customizer.withDefaults())
                .referrerPolicy(ReferrerPolicyServerHttpHeadersWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
            )
            .authorizeExchange(exchanges -> exchanges
                // Public endpoints
                .pathMatchers(HttpMethod.GET, "/actuator/health", "/actuator/info").permitAll()
                .pathMatchers(HttpMethod.GET, "/").permitAll()
                
                // MCP endpoints (public access - standard MCP server pattern)
                .pathMatchers("/api/v1/mcp/**").permitAll()
                
                // Login page (public access)
                .pathMatchers("/login", "/admin/login").permitAll()
                .pathMatchers("/admin.js", "/admin-new.js").permitAll()
                .pathMatchers("/admin/index.html").authenticated()
                
                // Admin UI endpoints (require authentication)
                .pathMatchers("/admin/**").authenticated()
                
                // WebSocket endpoints (require authentication)
                .pathMatchers("/ws/**", "/websocket/**").authenticated()
                
                // Configuration management (require admin role)
                .pathMatchers("/api/v1/configurations/**").hasRole("ADMIN")
                
                // Monitoring endpoints (require admin role)
                .pathMatchers("/api/v1/monitoring/**").hasRole("ADMIN")
                
                // All other endpoints require authentication
                .anyExchange().authenticated()
            )
            .httpBasic(Customizer.withDefaults())
            .formLogin(formLogin -> formLogin
                .loginPage("/login")
                .authenticationSuccessHandler((exchange, authentication) -> {
                    ServerHttpResponse response = exchange.getExchange().getResponse();
                    response.setStatusCode(HttpStatus.FOUND);
                    response.getHeaders().add("Location", "/admin");
                    return response.setComplete();
                })
                .authenticationFailureHandler((exchange, exception) -> {
                    ServerHttpResponse response = exchange.getExchange().getResponse();
                    response.setStatusCode(HttpStatus.FOUND);
                    response.getHeaders().add("Location", "/login?error=true");
                    return response.setComplete();
                })
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessHandler((exchange, authentication) -> {
                    ServerHttpResponse response = exchange.getExchange().getResponse();
                    response.setStatusCode(HttpStatus.FOUND);
                    response.getHeaders().add("Location", "/login?logout=true");
                    return response.setComplete();
                })
            )
            .csrf(csrf -> csrf
                .requireCsrfProtectionMatcher(apiExcludingCsrfMatcher())
            )
            .build();
    }
    
    /**
     * Creates a CSRF matcher that excludes API endpoints.
     */
    private ServerWebExchangeMatcher apiExcludingCsrfMatcher() {
        return new ServerWebExchangeMatcher() {
            @Override
            public Mono<MatchResult> matches(org.springframework.web.server.ServerWebExchange exchange) {
                try {
                    String path = exchange.getRequest().getPath() != null ? exchange.getRequest().getPath().value() : "";
                    String method = exchange.getRequest().getMethod() != null ? exchange.getRequest().getMethod().name() : "UNKNOWN";
                
                    // Disable CSRF for API endpoints
                    if (path.startsWith("/api/")) {
                        return MatchResult.notMatch();
                    }
                    
                    // Disable CSRF for GET requests (they should be safe)
                    if ("GET".equals(method)) {
                        return MatchResult.notMatch();
                    }
                
                    // Disable CSRF for login page and static resources
                    if (path.equals("/login") || path.equals("/") || 
                        path.startsWith("/admin.js") || path.startsWith("/admin-new.js") || 
                        path.startsWith("/actuator/")) {
                        return MatchResult.notMatch();
                    }
                    
                    // Enable CSRF for POST requests to login (form submission)
                    return MatchResult.match();
                } catch (Exception e) {
                    // If there's any error accessing request path/method, default to no match (disable CSRF)
                    System.out.println("=== SECURITY CONFIG DEBUG: Error in CSRF matcher: " + e.getClass().getSimpleName() + " - " + (e.getMessage() != null ? e.getMessage() : "null"));
                    return MatchResult.notMatch();
                }
            }
        };
    }
    
    /**
     * Configures CORS settings.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
    
    /**
     * Password encoder for user authentication.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    
    /**
     * In-memory user details service for development.
     * In production, this should be replaced with a proper user store.
     */
    @Bean
    public MapReactiveUserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
        UserDetails admin = User.builder()
            .username("admin")
            .password(passwordEncoder.encode("admin123"))
            .roles("ADMIN", "USER")
            .build();
        
        UserDetails user = User.builder()
            .username("user")
            .password(passwordEncoder.encode("user123"))
            .roles("USER")
            .build();
        
        return new MapReactiveUserDetailsService(admin, user);
    }
}
