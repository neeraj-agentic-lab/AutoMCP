/*
 * MCP REST Adapter - Web Configuration
 * 
 * Configuration for static resource handling and web-related settings.
 * 
 * @author Neeraj Yadav
 * @version 1.0.0
 * @since 2025-12-21
 */
package com.mcpwrapper.platform.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.config.ResourceHandlerRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

/**
 * Web configuration for static resources and routing.
 */
@Configuration
@EnableWebFlux
public class WebConfiguration implements WebFluxConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Serve static resources from classpath
        registry.addResourceHandler("/admin.js")
                .addResourceLocations("classpath:/static/");
        
        registry.addResourceHandler("/admin-new.js")
                .addResourceLocations("classpath:/static/");
        
        registry.addResourceHandler("/static/**")
                .addResourceLocations("classpath:/static/");
        
        // Serve the new React-based admin UI
        registry.addResourceHandler("/admin/**")
                .addResourceLocations("classpath:/static/admin/");
    }
}
