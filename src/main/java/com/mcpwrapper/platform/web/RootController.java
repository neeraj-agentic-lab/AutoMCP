/*
 * MCP REST Adapter - Root Web Controller
 * 
 * This controller handles root-level web requests and provides redirects
 * to the main admin interface.
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

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Root controller for handling base URL requests.
 * 
 * This controller provides a simple redirect from the root URL
 * to the admin interface, making it easy for users to access
 * the configuration UI.
 * 
 * @author Neeraj Yadav
 * @since 1.0.0
 */
@Controller
public class RootController {
    
    /**
     * Redirect root requests to the admin interface.
     * 
     * @return redirect to admin interface
     */
    @GetMapping("/")
    public String redirectToAdmin() {
        return "redirect:/admin";
    }
}
