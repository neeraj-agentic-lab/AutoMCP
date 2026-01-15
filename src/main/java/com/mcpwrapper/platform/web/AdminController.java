/*
 * MCP REST Adapter - Admin Web Controller
 * 
 * This controller serves the admin web interface for configuring REST APIs
 * as MCP tools. It provides endpoints for the HTML UI and handles static
 * resource serving for the configuration interface.
 * 
 * @author Neeraj Yadav
 * @version 1.0.0
 * @since 2025-12-16
 * 
 * Copyright (c) 2025 Neeraj Yadav. All rights reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mcpwrapper.platform.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import reactor.core.publisher.Mono;
import java.net.URI;

import com.mcpwrapper.runtime.service.ReactiveConfigurationService;
import com.mcpwrapper.runtime.service.ReactiveToolRegistryService;

/**
 * Web controller for the MCP REST Adapter admin interface.
 * 
 * This controller provides endpoints for:
 * - Serving the main admin HTML interface
 * - Providing JavaScript and CSS resources
 * - Exposing configuration data to the UI
 * - Handling UI-specific API endpoints
 * 
 * The admin interface allows users to:
 * - Configure REST APIs as MCP tools
 * - Set up authentication (API keys, OAuth2, etc.)
 * - Test API connections
 * - View generated MCP tool definitions
 * - Monitor API health and usage
 * 
 * @author Neeraj Yadav
 * @since 1.0.0
 */
@Controller
@RequestMapping("/admin")
public class AdminController {
    
    private final ReactiveConfigurationService configurationService;
    private final ReactiveToolRegistryService toolRegistryService;
    
    /**
     * Constructor for dependency injection.
     * 
     * @param configurationService service for managing API configurations
     * @param toolRegistryService service for managing tool registry
     */
    @Autowired
    public AdminController(
            ReactiveConfigurationService configurationService,
            ReactiveToolRegistryService toolRegistryService) {
        this.configurationService = configurationService;
        this.toolRegistryService = toolRegistryService;
    }
    
    /**
     * Serves the main admin interface - redirect to React-based UI.
     * 
     * @return redirect to React admin UI
     */
    @GetMapping({"", "/", "/index"})
    public Mono<ResponseEntity<Void>> adminIndex() {
        return Mono.just(ResponseEntity.status(HttpStatus.FOUND)
            .location(URI.create("/admin/index.html"))
            .build());
    }
    
    /**
     * Test endpoint to verify controller is working
     */
    @GetMapping("/test")
    @ResponseBody
    public Mono<ResponseEntity<String>> testEndpoint() {
        return Mono.just(ResponseEntity.ok()
            .contentType(MediaType.TEXT_HTML)
            .body("<html><head><style>body{background:red;color:white;font-size:24px;padding:2rem;}</style></head><body>TEST - Controller is working! CSS should load now.</body></html>"));
    }
    
    // Static resources are now handled by WebConfiguration
    
    /**
     * Provides server status information for the UI.
     * 
     * @return server status data
     */
    @GetMapping("/status")
    @ResponseBody
    public Mono<ResponseEntity<ServerStatus>> getServerStatus() {
        return toolRegistryService.getToolCount()
            .map(toolCount -> {
                ServerStatus status = new ServerStatus(
                    "RUNNING",
                    "8080",
                    "CONNECTED",
                    toolCount,
                    "READY"
                );
                return ResponseEntity.ok(status);
            })
            .onErrorReturn(ResponseEntity.ok(new ServerStatus(
                "RUNNING", 
                "8080", 
                "ERROR", 
                0L, 
                "ERROR"
            )));
    }
    
    /**
     * Provides configuration statistics for the dashboard.
     * 
     * @return configuration statistics
     */
    @GetMapping("/stats")
    @ResponseBody
    public Mono<ResponseEntity<ConfigStats>> getConfigStats() {
        return Mono.zip(
            configurationService.getAllConfigurations(false).count(),
            configurationService.getAllConfigurations(true).count(),
            toolRegistryService.getToolCount()
        ).map(tuple -> {
            ConfigStats stats = new ConfigStats(
                tuple.getT1(),  // total configurations
                tuple.getT2(),  // enabled configurations
                tuple.getT3()   // total tools
            );
            return ResponseEntity.ok(stats);
        }).onErrorReturn(ResponseEntity.ok(new ConfigStats(0L, 0L, 0L)));
    }
    
    /**
     * Server status data transfer object.
     */
    public record ServerStatus(
        String serverStatus,
        String serverPort,
        String databaseStatus,
        Long toolCount,
        String oauth2Status
    ) {}
    
    /**
     * Configuration statistics data transfer object.
     */
    public record ConfigStats(
        Long totalConfigurations,
        Long enabledConfigurations,
        Long totalTools
    ) {}
}
