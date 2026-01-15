/*
 * MCP REST Adapter - Login Controller
 * 
 * This controller handles the login page for the admin interface.
 * 
 * @author Neeraj Yadav
 * @version 1.0.0
 * @since 2025-12-21
 */
package com.mcpwrapper.platform.web;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import reactor.core.publisher.Mono;

/**
 * Controller for handling login-related endpoints.
 */
@Controller
public class LoginController {
    
    /**
     * Serves the login page.
     * 
     * @return login HTML content as a resource
     */
    @GetMapping("/login")
    @ResponseBody
    public Mono<ResponseEntity<Resource>> loginPage() {
        return Mono.fromCallable(() -> {
            Resource resource = new ClassPathResource("templates/login.html");
            if (resource.exists()) {
                return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        });
    }
}
