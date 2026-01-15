/**
 * MCP REST Adapter - Admin Interface JavaScript
 * 
 * This file provides the interactive functionality for the admin web interface,
 * including form handling, API communication, and dynamic UI updates.
 * 
 * @author Neeraj Yadav
 * @version 1.0.0
 * @since 2025-12-16
 */

// Global state
let currentAPIs = [];
let validationResults = null;

// Initialize the application when DOM is loaded
document.addEventListener('DOMContentLoaded', function() {
    initializeApp();
});

/**
 * Initialize the application
 */
function initializeApp() {
    console.log('Initializing MCP REST Adapter Admin Interface...');
    
    // Load initial data
    loadServerStatus();
    loadAPIs();
    
    // Set up form handlers
    setupFormHandlers();
    
    // Set up periodic updates
    setInterval(loadServerStatus, 30000); // Update status every 30 seconds
    setInterval(loadAPIs, 60000); // Refresh API list every minute
    
    console.log('Admin interface initialized successfully');
}

/**
 * Set up form event handlers
 */
function setupFormHandlers() {
    const form = document.getElementById('api-form');
    if (form) {
        form.addEventListener('submit', handleFormSubmit);
    }
    
    // Set up auth type change handler
    const authTypeSelect = document.getElementById('auth-type');
    if (authTypeSelect) {
        authTypeSelect.addEventListener('change', toggleAuthFields);
    }
}

/**
 * Handle form submission
 */
async function handleFormSubmit(event) {
    event.preventDefault();
    
    const formData = collectFormData();
    if (!validateFormData(formData)) {
        return;
    }
    
    try {
        showLoading(true);
        const response = await fetch('/api/v1/configurations', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            credentials: 'include',
            body: JSON.stringify(formData)
        });
        
        if (response.ok) {
            const result = await response.json();
            showSuccess('API configuration saved successfully!');
            resetForm();
            loadAPIs(); // Refresh the API list
        } else {
            const error = await response.text();
            showError(`Failed to save configuration: ${error}`);
        }
    } catch (error) {
        console.error('Error saving configuration:', error);
        showError(`Network error: ${error.message}`);
    } finally {
        showLoading(false);
    }
}

/**
 * Collect form data into a configuration object
 */
function collectFormData() {
    const authType = document.getElementById('auth-type').value;
    
    const config = {
        name: document.getElementById('api-name').value,
        description: document.getElementById('description').value || '',
        enabled: document.getElementById('enabled').checked,
        openapiSourceType: 'URL', // Default to URL source type
        openapiUrl: document.getElementById('openapi-spec').value,
        openapiContent: null,
        baseUrl: document.getElementById('api-url').value,
        timeoutSeconds: parseInt(document.getElementById('timeout').value) || 30,
        rateLimitPerMinute: parseInt(document.getElementById('rate-limit').value) || 100,
        createdBy: 'admin', // TODO: Get from current user session
        generateTools: false
    };
    
    // Add authentication configuration based on type
    config.authConfig = collectAuthConfig(authType);
    config.authType = authType; // Keep for validation
    
    return config;
}

/**
 * Collect authentication configuration based on auth type
 */
function collectAuthConfig(authType) {
    const authConfig = {};
    
    switch (authType) {
        case 'API_KEY':
            authConfig.apiKey = document.getElementById('api-key').value;
            authConfig.headerName = document.getElementById('api-key-header').value || 'X-API-Key';
            break;
            
        case 'BEARER_TOKEN':
            authConfig.token = document.getElementById('bearer-token').value;
            break;
            
        case 'OAUTH2':
            authConfig.clientId = document.getElementById('oauth2-client-id').value;
            authConfig.clientSecret = document.getElementById('oauth2-client-secret').value;
            authConfig.tokenUrl = document.getElementById('oauth2-token-url').value;
            authConfig.scopes = document.getElementById('oauth2-scopes').value.split(' ').filter(s => s.trim());
            break;
            
        case 'BASIC_AUTH':
            authConfig.username = document.getElementById('basic-username').value;
            authConfig.password = document.getElementById('basic-password').value;
            break;
            
        default:
            // No auth config needed for NONE
            break;
    }
    
    return authConfig;
}

/**
 * Validate form data before submission
 */
function validateFormData(formData) {
    const errors = [];
    
    if (!formData.name || formData.name.trim().length === 0) {
        errors.push('API name is required');
    }
    
    if (!formData.baseUrl || !isValidUrl(formData.baseUrl)) {
        errors.push('Valid base URL is required');
    }
    
    if (!formData.openapiUrl || !isValidUrl(formData.openapiUrl)) {
        errors.push('Valid OpenAPI specification URL is required');
    }
    
    // Validate auth-specific fields
    if (formData.authType === 'API_KEY' && !formData.authConfig.apiKey) {
        errors.push('API key is required for API key authentication');
    }
    
    if (formData.authType === 'BEARER_TOKEN' && !formData.authConfig.token) {
        errors.push('Bearer token is required for bearer token authentication');
    }
    
    if (formData.authType === 'OAUTH2') {
        if (!formData.authConfig.clientId) errors.push('OAuth2 client ID is required');
        if (!formData.authConfig.clientSecret) errors.push('OAuth2 client secret is required');
        if (!formData.authConfig.tokenUrl || !isValidUrl(formData.authConfig.tokenUrl)) {
            errors.push('Valid OAuth2 token URL is required');
        }
    }
    
    if (formData.authType === 'BASIC_AUTH') {
        if (!formData.authConfig.username) errors.push('Username is required for basic authentication');
        if (!formData.authConfig.password) errors.push('Password is required for basic authentication');
    }
    
    if (errors.length > 0) {
        showError('Validation errors:\n' + errors.join('\n'));
        return false;
    }
    
    return true;
}

/**
 * Check if a string is a valid URL
 */
function isValidUrl(string) {
    try {
        new URL(string);
        return true;
    } catch (_) {
        return false;
    }
}

/**
 * Load server status and update UI
 */
async function loadServerStatus() {
    try {
        const response = await fetch('/admin/status');
        if (response.ok) {
            const status = await response.json();
            updateStatusBar(status);
        }
    } catch (error) {
        console.error('Error loading server status:', error);
    }
}

/**
 * Update the status bar with server information
 */
function updateStatusBar(status) {
    const dbStatusElement = document.getElementById('db-status');
    const toolCountElement = document.getElementById('tool-count');
    
    if (dbStatusElement) {
        dbStatusElement.textContent = `Database: ${status.databaseStatus}`;
    }
    
    if (toolCountElement) {
        toolCountElement.textContent = `Tools: ${status.toolCount}`;
    }
}

/**
 * Load and display the list of configured APIs
 */
async function loadAPIs() {
    const loadingElement = document.getElementById('loading');
    const apiListElement = document.getElementById('api-list');
    
    try {
        if (loadingElement) loadingElement.style.display = 'block';
        
        const response = await fetch('/api/v1/configurations', {
            credentials: 'include'
        });
        
        if (response.ok) {
            const apis = await response.json();
            currentAPIs = Array.isArray(apis) ? apis : [];
            displayAPIs(currentAPIs);
        } else {
            showError('Failed to load API configurations');
            displayAPIs([]);
        }
    } catch (error) {
        console.error('Error loading APIs:', error);
        showError(`Network error: ${error.message}`);
        displayAPIs([]);
    } finally {
        if (loadingElement) loadingElement.style.display = 'none';
    }
}

/**
 * Display the list of APIs in the UI
 */
function displayAPIs(apis) {
    const apiListElement = document.getElementById('api-list');
    if (!apiListElement) return;
    
    if (apis.length === 0) {
        apiListElement.innerHTML = `
            <div style="text-align: center; padding: 40px; color: #718096;">
                <i class="fas fa-inbox" style="font-size: 48px; margin-bottom: 16px;"></i>
                <p>No APIs configured yet</p>
                <p style="font-size: 0.9rem;">Add your first API using the form on the left</p>
            </div>
        `;
        return;
    }
    
    const apiHTML = apis.map(api => `
        <div class="api-item">
            <div>
                <h4>${escapeHtml(api.name)}</h4>
                <p>${escapeHtml(api.baseUrl)}</p>
                <small style="color: ${api.enabled ? '#48bb78' : '#f56565'};">
                    <i class="fas fa-circle" style="font-size: 8px;"></i>
                    ${api.enabled ? 'Enabled' : 'Disabled'}
                </small>
            </div>
            <div class="api-actions">
                <button class="btn btn-secondary" onclick="testAPI('${api.id}')" title="Test API">
                    <i class="fas fa-play"></i>
                </button>
                <button class="btn btn-primary" onclick="viewTools('${api.id}')" title="View Tools">
                    <i class="fas fa-tools"></i>
                </button>
                <button class="btn btn-secondary" onclick="editAPI('${api.id}')" title="Edit">
                    <i class="fas fa-edit"></i>
                </button>
                <button class="btn btn-danger" onclick="deleteAPI('${api.id}')" title="Delete">
                    <i class="fas fa-trash"></i>
                </button>
            </div>
        </div>
    `).join('');
    
    apiListElement.innerHTML = apiHTML;
}

/**
 * Validate OpenAPI specification
 */
async function validateOpenAPI() {
    const openApiUrl = document.getElementById('openapi-spec').value;
    if (!openApiUrl) {
        showError('Please enter an OpenAPI specification URL first');
        return;
    }
    
    const resultsElement = document.getElementById('validation-results');
    if (!resultsElement) return;
    
    try {
        resultsElement.innerHTML = '<div class="loading"><div class="spinner"></div><p>Validating OpenAPI specification...</p></div>';
        
        const response = await fetch('/api/v1/openapi/validate', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            credentials: 'include',
            body: JSON.stringify({ url: openApiUrl })
        });
        
        if (response.ok) {
            const result = await response.json();
            displayValidationResults(result);
        } else {
            const error = await response.text();
            resultsElement.innerHTML = `
                <div class="alert alert-error">
                    <i class="fas fa-exclamation-circle"></i>
                    Validation failed: ${escapeHtml(error)}
                </div>
            `;
        }
    } catch (error) {
        console.error('Error validating OpenAPI:', error);
        resultsElement.innerHTML = `
            <div class="alert alert-error">
                <i class="fas fa-exclamation-circle"></i>
                Network error: ${escapeHtml(error.message)}
            </div>
        `;
    }
}

/**
 * Display OpenAPI validation results
 */
function displayValidationResults(result) {
    const resultsElement = document.getElementById('validation-results');
    if (!resultsElement) return;
    
    const isValid = result.valid;
    const toolCount = result.toolCount || 0;
    
    let html = `
        <div class="alert ${isValid ? 'alert-success' : 'alert-error'}">
            <i class="fas fa-${isValid ? 'check-circle' : 'exclamation-circle'}"></i>
            ${isValid ? 
                `OpenAPI specification is valid! Found ${toolCount} potential tools.` :
                `OpenAPI specification has errors: ${escapeHtml(result.error || 'Unknown error')}`
            }
        </div>
    `;
    
    if (isValid && result.tools && result.tools.length > 0) {
        html += `
            <div style="margin-top: 15px;">
                <h4>Generated Tools Preview:</h4>
                <div class="code-preview">
${result.tools.map(tool => `• ${escapeHtml(tool.name)}: ${escapeHtml(tool.description)}`).join('\n')}
                </div>
            </div>
        `;
    }
    
    resultsElement.innerHTML = html;
    validationResults = result;
}

/**
 * Test an API configuration
 */
async function testAPI(apiId) {
    try {
        const response = await fetch(`/api/v1/configurations/${apiId}/test`, {
            method: 'POST',
            credentials: 'include'
        });
        
        if (response.ok) {
            const result = await response.json();
            showSuccess(`API test successful! Response time: ${result.responseTime}ms`);
        } else {
            const error = await response.text();
            showError(`API test failed: ${error}`);
        }
    } catch (error) {
        console.error('Error testing API:', error);
        showError(`Network error: ${error.message}`);
    }
}

/**
 * View tools for an API
 */
async function viewTools(apiId) {
    try {
        const response = await fetch(`/api/v1/configurations/${apiId}/tools`, {
            credentials: 'include'
        });
        
        if (response.ok) {
            const tools = await response.json();
            showToolsModal(tools);
        } else {
            const error = await response.text();
            showError(`Failed to load tools: ${error}`);
        }
    } catch (error) {
        console.error('Error loading tools:', error);
        showError(`Network error: ${error.message}`);
    }
}

/**
 * Show tools in a modal
 */
function showToolsModal(tools) {
    const modal = document.getElementById('tool-modal');
    const content = document.getElementById('tool-preview-content');
    
    if (!modal || !content) return;
    
    let html = '';
    if (tools.length === 0) {
        html = '<p>No tools generated for this API.</p>';
    } else {
        html = `
            <p><strong>${tools.length}</strong> tools generated from this API:</p>
            <div class="code-preview">
${tools.map(tool => `
{
  "name": "${escapeHtml(tool.name)}",
  "description": "${escapeHtml(tool.description)}",
  "inputSchema": ${JSON.stringify(tool.inputSchema, null, 2)}
}`).join('\n\n')}
            </div>
        `;
    }
    
    content.innerHTML = html;
    modal.style.display = 'block';
}

/**
 * Edit an API configuration
 */
function editAPI(apiId) {
    const api = currentAPIs.find(a => a.id === apiId);
    if (!api) {
        showError('API not found');
        return;
    }
    
    // Populate form with API data
    document.getElementById('api-name').value = api.name || '';
    document.getElementById('api-url').value = api.baseUrl || '';
    document.getElementById('openapi-spec').value = api.openApiSpec || '';
    document.getElementById('description').value = api.description || '';
    document.getElementById('auth-type').value = api.authType || 'NONE';
    document.getElementById('timeout').value = api.timeout || 30;
    document.getElementById('rate-limit').value = api.rateLimit || 100;
    document.getElementById('enabled').checked = api.enabled !== false;
    document.getElementById('validate-ssl').checked = api.validateSsl !== false;
    
    // Populate auth fields
    toggleAuthFields();
    if (api.authConfig) {
        populateAuthFields(api.authType, api.authConfig);
    }
    
    // Scroll to form
    document.querySelector('.card').scrollIntoView({ behavior: 'smooth' });
}

/**
 * Populate authentication fields based on type and config
 */
function populateAuthFields(authType, authConfig) {
    switch (authType) {
        case 'API_KEY':
            if (document.getElementById('api-key')) document.getElementById('api-key').value = authConfig.apiKey || '';
            if (document.getElementById('api-key-header')) document.getElementById('api-key-header').value = authConfig.headerName || 'X-API-Key';
            break;
            
        case 'BEARER_TOKEN':
            if (document.getElementById('bearer-token')) document.getElementById('bearer-token').value = authConfig.token || '';
            break;
            
        case 'OAUTH2':
            if (document.getElementById('oauth2-client-id')) document.getElementById('oauth2-client-id').value = authConfig.clientId || '';
            if (document.getElementById('oauth2-client-secret')) document.getElementById('oauth2-client-secret').value = authConfig.clientSecret || '';
            if (document.getElementById('oauth2-token-url')) document.getElementById('oauth2-token-url').value = authConfig.tokenUrl || '';
            if (document.getElementById('oauth2-scopes')) document.getElementById('oauth2-scopes').value = (authConfig.scopes || []).join(' ');
            break;
            
        case 'BASIC_AUTH':
            if (document.getElementById('basic-username')) document.getElementById('basic-username').value = authConfig.username || '';
            if (document.getElementById('basic-password')) document.getElementById('basic-password').value = authConfig.password || '';
            break;
    }
}

/**
 * Delete an API configuration
 */
async function deleteAPI(apiId) {
    const api = currentAPIs.find(a => a.id === apiId);
    if (!api) {
        showError('API not found');
        return;
    }
    
    if (!confirm(`Are you sure you want to delete the API configuration "${api.name}"?`)) {
        return;
    }
    
    try {
        const response = await fetch(`/api/v1/configurations/${apiId}?deletedBy=admin`, {
            method: 'DELETE',
            credentials: 'include'
        });
        
        if (response.ok) {
            showSuccess('API configuration deleted successfully');
            loadAPIs(); // Refresh the list
        } else {
            const error = await response.text();
            showError(`Failed to delete configuration: ${error}`);
        }
    } catch (error) {
        console.error('Error deleting API:', error);
        showError(`Network error: ${error.message}`);
    }
}

/**
 * Test all configured APIs
 */
async function testAllAPIs() {
    if (currentAPIs.length === 0) {
        showError('No APIs configured to test');
        return;
    }
    
    showSuccess('Testing all APIs...');
    
    let successCount = 0;
    let failureCount = 0;
    
    for (const api of currentAPIs) {
        try {
            const response = await fetch(`/api/v1/configurations/${api.id}/test`, {
                method: 'POST',
                credentials: 'include'
            });
            
            if (response.ok) {
                successCount++;
            } else {
                failureCount++;
            }
        } catch (error) {
            failureCount++;
        }
    }
    
    const message = `Test completed: ${successCount} successful, ${failureCount} failed`;
    if (failureCount === 0) {
        showSuccess(message);
    } else {
        showError(message);
    }
}

/**
 * Refresh the API list
 */
function refreshAPIs() {
    loadAPIs();
}

/**
 * Switch between tabs
 */
function switchTab(tabName, event) {
    // Hide all tab contents
    document.querySelectorAll('.tab-content').forEach(tab => {
        tab.classList.remove('active');
    });
    
    // Remove active class from all tabs
    document.querySelectorAll('.tab').forEach(tab => {
        tab.classList.remove('active');
    });
    
    // Show selected tab content
    const selectedTab = document.getElementById(`${tabName}-tab`);
    if (selectedTab) {
        selectedTab.classList.add('active');
    }
    
    // Add active class to clicked tab
    if (event && event.target) {
        event.target.classList.add('active');
    }
}

/**
 * Toggle authentication fields based on selected type
 */
function toggleAuthFields() {
    const authType = document.getElementById('auth-type').value;
    
    // Hide all auth field groups
    const authFieldGroups = [
        'api-key-fields',
        'bearer-token-fields', 
        'oauth2-fields',
        'basic-auth-fields'
    ];
    
    authFieldGroups.forEach(groupId => {
        const group = document.getElementById(groupId);
        if (group) {
            group.style.display = 'none';
        }
    });
    
    // Show relevant auth fields
    let targetGroupId = null;
    switch (authType) {
        case 'API_KEY':
            targetGroupId = 'api-key-fields';
            break;
        case 'BEARER_TOKEN':
            targetGroupId = 'bearer-token-fields';
            break;
        case 'OAUTH2':
            targetGroupId = 'oauth2-fields';
            break;
        case 'BASIC_AUTH':
            targetGroupId = 'basic-auth-fields';
            break;
    }
    
    if (targetGroupId) {
        const targetGroup = document.getElementById(targetGroupId);
        if (targetGroup) {
            targetGroup.style.display = 'block';
        }
    }
}

/**
 * Reset the form to initial state
 */
function resetForm() {
    const form = document.getElementById('api-form');
    if (form) {
        form.reset();
        
        // Reset to default values
        document.getElementById('timeout').value = '30';
        document.getElementById('rate-limit').value = '100';
        document.getElementById('enabled').checked = true;
        document.getElementById('validate-ssl').checked = true;
        document.getElementById('auth-type').value = 'NONE';
        
        // Hide auth fields
        toggleAuthFields();
        
        // Clear validation results
        const resultsElement = document.getElementById('validation-results');
        if (resultsElement) {
            resultsElement.innerHTML = '';
        }
        
        // Switch back to basic tab
        switchTab('basic');
    }
}

/**
 * Close modal
 */
function closeModal() {
    const modal = document.getElementById('tool-modal');
    if (modal) {
        modal.style.display = 'none';
    }
}

/**
 * Show loading state
 */
function showLoading(show) {
    // This could be enhanced to show loading indicators
    console.log(show ? 'Loading...' : 'Loading complete');
}

/**
 * Show success message
 */
function showSuccess(message) {
    const alert = document.getElementById('success-alert');
    const messageElement = document.getElementById('success-message');
    
    if (alert && messageElement) {
        messageElement.textContent = message;
        alert.style.display = 'block';
        
        setTimeout(() => {
            alert.style.display = 'none';
        }, 5000);
    }
    
    console.log('Success:', message);
}

/**
 * Show error message
 */
function showError(message) {
    const alert = document.getElementById('error-alert');
    const messageElement = document.getElementById('error-message');
    
    if (alert && messageElement) {
        messageElement.textContent = message;
        alert.style.display = 'block';
        
        setTimeout(() => {
            alert.style.display = 'none';
        }, 8000);
    }
    
    console.error('Error:', message);
}

/**
 * Get authorization header for API requests
 * Since we're using session-based authentication, we don't need to send auth headers
 * Spring Security will handle authentication via session cookies
 */
function getAuthHeader() {
    // Return empty string since we're using session-based auth
    // The session cookie will be automatically included in requests
    return '';
}

/**
 * Check if user is authenticated by testing a protected endpoint
 */
async function isAuthenticated() {
    try {
        const response = await fetch('/admin/status', {
            method: 'GET',
            credentials: 'include' // Include session cookies
        });
        return response.ok;
    } catch (error) {
        return false;
    }
}

/**
 * Logout user using Spring Security logout endpoint
 */
function logout() {
    // Use Spring Security logout endpoint
    window.location.href = '/logout';
}

/**
 * Get current username from server
 */
async function getCurrentUsername() {
    try {
        // In a real implementation, you might have an endpoint that returns current user info
        // For now, return a default value
        return 'admin';
    } catch (error) {
        return 'admin';
    }
}

/**
 * Escape HTML to prevent XSS
 */
function escapeHtml(text) {
    if (typeof text !== 'string') return text;
    
    const map = {
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#039;'
    };
    
    return text.replace(/[&<>"']/g, function(m) { return map[m]; });
}

// Close modal when clicking outside
window.onclick = function(event) {
    const modal = document.getElementById('tool-modal');
    if (event.target === modal) {
        closeModal();
    }
}
