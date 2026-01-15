/**
 * MCP REST Adapter - Modern Admin Interface JavaScript
 * 
 * Professional admin dashboard with tabular views, separate pages,
 * and comprehensive testing capabilities.
 * 
 * @author Neeraj Yadav
 * @version 2.0.0
 * @since 2025-12-21
 */

// Global state
let currentAPIs = [];
let currentAPIId = null;
let currentView = 'dashboard';

// Initialize the application when DOM is loaded
document.addEventListener('DOMContentLoaded', function() {
    initializeApp();
});

/**
 * Initialize the application
 */
async function initializeApp() {
    console.log('Initializing MCP REST Adapter Admin Dashboard...');
    
    // Check authentication
    const authenticated = await isAuthenticated();
    if (!authenticated) {
        window.location.href = '/login';
        return;
    }
    
    // Set up navigation handlers
    setupNavigation();
    
    // Load initial data
    await loadDashboard();
    
    // Set up periodic updates
    setInterval(updateStats, 30000); // Update stats every 30 seconds
    
    console.log('Admin dashboard initialized successfully');
}

/**
 * Check if user is authenticated
 */
async function isAuthenticated() {
    try {
        const response = await fetch('/admin/status', {
            credentials: 'include'
        });
        return response.ok;
    } catch (error) {
        return false;
    }
}

/**
 * Set up navigation event handlers
 */
function setupNavigation() {
    document.getElementById('nav-dashboard').addEventListener('click', (e) => {
        e.preventDefault();
        showDashboard();
    });
    
    document.getElementById('nav-add-api').addEventListener('click', (e) => {
        e.preventDefault();
        showAddAPIPage();
    });
    
    document.getElementById('nav-tools').addEventListener('click', (e) => {
        e.preventDefault();
        showToolsPage();
    });
}

/**
 * Show dashboard view
 */
async function showDashboard() {
    hideAllViews();
    showView('dashboard-view');
    currentView = 'dashboard';
    
    // Update navigation active state
    updateNavigation('dashboard');
    
    await loadDashboard();
}

/**
 * Show add API page
 */
function showAddAPIPage() {
    hideAllViews();
    showView('add-api-view');
    currentView = 'add-api';
    
    // Update navigation active state
    updateNavigation('add-api');
    
    setupAddAPIForm();
}

/**
 * Show API details page
 */
async function showAPIDetails(apiId) {
    hideAllViews();
    showView('api-details-view');
    currentView = 'api-details';
    currentAPIId = apiId;
    
    // Update navigation active state
    updateNavigation('api-details');
    
    await loadAPIDetails(apiId);
}

/**
 * Hide all views
 */
function hideAllViews() {
    const views = ['dashboard-view', 'add-api-view', 'api-details-view'];
    views.forEach(viewId => {
        const view = document.getElementById(viewId);
        if (view) {
            view.classList.add('hidden');
            view.style.display = 'none';
        }
    });
}

/**
 * Show specific view
 */
function showView(viewId) {
    const view = document.getElementById(viewId);
    if (view) {
        view.classList.remove('hidden');
        view.style.display = 'block';
    }
}

/**
 * Update navigation active state
 */
function updateNavigation(activeView) {
    // Remove active class from all nav links
    document.querySelectorAll('.nav-link').forEach(link => {
        link.classList.remove('active');
    });
    
    // Add active class to current view
    const navMap = {
        'dashboard': 'nav-dashboard',
        'add-api': 'nav-add-api',
        'api-details': 'nav-dashboard' // Details view is part of dashboard
    };
    
    const activeNavId = navMap[activeView];
    if (activeNavId) {
        const activeNav = document.getElementById(activeNavId);
        if (activeNav) {
            activeNav.classList.add('active');
        }
    }
}

/**
 * Load dashboard data
 */
async function loadDashboard() {
    showLoading(true);
    
    try {
        await Promise.all([
            loadAPIs(),
            updateStats()
        ]);
        
        renderAPIsTable();
    } catch (error) {
        console.error('Error loading dashboard:', error);
        showAlert('Failed to load dashboard data', 'error');
    } finally {
        showLoading(false);
    }
}

/**
 * Load APIs from server
 */
async function loadAPIs() {
    try {
        const response = await fetch('/api/v1/configurations', {
            credentials: 'include'
        });
        
        if (response.ok) {
            const data = await response.json();
            currentAPIs = data.configurations || [];
        } else {
            throw new Error('Failed to load APIs');
        }
    } catch (error) {
        console.error('Error loading APIs:', error);
        currentAPIs = [];
    }
}

/**
 * Update statistics
 */
async function updateStats() {
    const totalAPIs = currentAPIs.length;
    const activeAPIs = currentAPIs.filter(api => api.enabled).length;
    
    document.getElementById('total-apis').textContent = totalAPIs;
    document.getElementById('active-apis').textContent = activeAPIs;
    
    // TODO: Load actual tool count and failed tests from server
    document.getElementById('total-tools').textContent = '0';
    document.getElementById('failed-tests').textContent = '0';
}

/**
 * Render APIs table
 */
function renderAPIsTable() {
    const tbody = document.getElementById('apis-table-body');
    const emptyState = document.getElementById('empty-state');
    
    if (currentAPIs.length === 0) {
        tbody.innerHTML = '';
        emptyState.classList.remove('hidden');
        return;
    }
    
    emptyState.classList.add('hidden');
    
    tbody.innerHTML = currentAPIs.map(api => `
        <tr>
            <td>
                <div class="fw-bold">${escapeHtml(api.name)}</div>
                <div class="text-muted small">${escapeHtml(api.description || 'No description')}</div>
            </td>
            <td>
                <code class="small">${escapeHtml(api.baseUrl)}</code>
            </td>
            <td>
                <span class="status-badge ${api.enabled ? 'status-active' : 'status-inactive'}">
                    <i class="fas fa-${api.enabled ? 'check-circle' : 'times-circle'} me-1"></i>
                    ${api.enabled ? 'Active' : 'Inactive'}
                </span>
            </td>
            <td>
                <span class="badge bg-info">0 tools</span>
            </td>
            <td>
                <span class="text-muted small">Never tested</span>
            </td>
            <td>
                <div class="action-buttons">
                    <button class="action-btn view" onclick="showAPIDetails('${api.id}')" title="View Details">
                        <i class="fas fa-eye"></i>
                    </button>
                    <button class="action-btn edit" onclick="editAPI('${api.id}')" title="Edit">
                        <i class="fas fa-edit"></i>
                    </button>
                    <button class="action-btn test" onclick="testAPI('${api.id}')" title="Test">
                        <i class="fas fa-play"></i>
                    </button>
                    <button class="action-btn delete" onclick="deleteAPI('${api.id}')" title="Delete">
                        <i class="fas fa-trash"></i>
                    </button>
                </div>
            </td>
        </tr>
    `).join('');
}

/**
 * Setup add API form
 */
function setupAddAPIForm() {
    const form = document.getElementById('api-form');
    form.innerHTML = `
        <div class="row">
            <div class="col-md-6">
                <div class="mb-3">
                    <label for="api-name" class="form-label">API Name *</label>
                    <input type="text" class="form-control" id="api-name" required>
                </div>
            </div>
            <div class="col-md-6">
                <div class="mb-3">
                    <label for="api-url" class="form-label">Base URL *</label>
                    <input type="url" class="form-control" id="api-url" required>
                </div>
            </div>
        </div>
        
        <div class="mb-3">
            <label for="description" class="form-label">Description</label>
            <textarea class="form-control" id="description" rows="2"></textarea>
        </div>
        
        <div class="mb-3">
            <label for="openapi-spec" class="form-label">OpenAPI Specification URL *</label>
            <input type="url" class="form-control" id="openapi-spec" required>
        </div>
        
        <div class="row">
            <div class="col-md-4">
                <div class="mb-3">
                    <label for="timeout" class="form-label">Timeout (seconds)</label>
                    <input type="number" class="form-control" id="timeout" value="30" min="1" max="300">
                </div>
            </div>
            <div class="col-md-4">
                <div class="mb-3">
                    <label for="rate-limit" class="form-label">Rate Limit (req/min)</label>
                    <input type="number" class="form-control" id="rate-limit" value="100" min="1" max="10000">
                </div>
            </div>
            <div class="col-md-4">
                <div class="mb-3">
                    <label for="auth-type" class="form-label">Authentication</label>
                    <select class="form-control" id="auth-type">
                        <option value="NONE">None</option>
                        <option value="API_KEY">API Key</option>
                        <option value="BEARER_TOKEN">Bearer Token</option>
                        <option value="BASIC_AUTH">Basic Auth</option>
                        <option value="OAUTH2">OAuth 2.0</option>
                    </select>
                </div>
            </div>
        </div>
        
        <div id="auth-fields">
            <!-- Authentication fields will be populated based on selection -->
        </div>
        
        <div class="row">
            <div class="col-md-6">
                <div class="form-check">
                    <input class="form-check-input" type="checkbox" id="enabled" checked>
                    <label class="form-check-label" for="enabled">
                        Enable API
                    </label>
                </div>
            </div>
            <div class="col-md-6">
                <div class="form-check">
                    <input class="form-check-input" type="checkbox" id="validate-ssl" checked>
                    <label class="form-check-label" for="validate-ssl">
                        Validate SSL Certificates
                    </label>
                </div>
            </div>
        </div>
        
        <div class="mt-4">
            <button type="button" class="btn-custom btn-secondary-custom me-2" onclick="validateOpenAPISpec()">
                <i class="fas fa-check-circle me-1"></i> Validate OpenAPI
            </button>
            <button type="submit" class="btn-custom btn-primary-custom">
                <i class="fas fa-save me-1"></i> Save Configuration
            </button>
        </div>
    `;
    
    // Set up form handlers
    form.addEventListener('submit', handleFormSubmit);
    document.getElementById('auth-type').addEventListener('change', updateAuthFields);
}

/**
 * Update authentication fields based on selected type
 */
function updateAuthFields() {
    const authType = document.getElementById('auth-type').value;
    const authFields = document.getElementById('auth-fields');
    
    let fieldsHTML = '';
    
    switch (authType) {
        case 'API_KEY':
            fieldsHTML = `
                <div class="row">
                    <div class="col-md-6">
                        <div class="mb-3">
                            <label for="api-key" class="form-label">API Key *</label>
                            <input type="password" class="form-control" id="api-key" required>
                        </div>
                    </div>
                    <div class="col-md-6">
                        <div class="mb-3">
                            <label for="api-key-header" class="form-label">Header Name</label>
                            <input type="text" class="form-control" id="api-key-header" value="X-API-Key">
                        </div>
                    </div>
                </div>
            `;
            break;
            
        case 'BEARER_TOKEN':
            fieldsHTML = `
                <div class="mb-3">
                    <label for="bearer-token" class="form-label">Bearer Token *</label>
                    <input type="password" class="form-control" id="bearer-token" required>
                </div>
            `;
            break;
            
        case 'BASIC_AUTH':
            fieldsHTML = `
                <div class="row">
                    <div class="col-md-6">
                        <div class="mb-3">
                            <label for="basic-username" class="form-label">Username *</label>
                            <input type="text" class="form-control" id="basic-username" required>
                        </div>
                    </div>
                    <div class="col-md-6">
                        <div class="mb-3">
                            <label for="basic-password" class="form-label">Password *</label>
                            <input type="password" class="form-control" id="basic-password" required>
                        </div>
                    </div>
                </div>
            `;
            break;
            
        case 'OAUTH2':
            fieldsHTML = `
                <div class="row">
                    <div class="col-md-6">
                        <div class="mb-3">
                            <label for="oauth2-client-id" class="form-label">Client ID *</label>
                            <input type="text" class="form-control" id="oauth2-client-id" required>
                        </div>
                    </div>
                    <div class="col-md-6">
                        <div class="mb-3">
                            <label for="oauth2-client-secret" class="form-label">Client Secret *</label>
                            <input type="password" class="form-control" id="oauth2-client-secret" required>
                        </div>
                    </div>
                </div>
                <div class="mb-3">
                    <label for="oauth2-token-url" class="form-label">Token URL *</label>
                    <input type="url" class="form-control" id="oauth2-token-url" required>
                </div>
            `;
            break;
    }
    
    authFields.innerHTML = fieldsHTML;
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
    
    showLoading(true);
    
    try {
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
            showAlert('API configuration saved successfully!', 'success');
            showDashboard(); // Return to dashboard
        } else {
            const error = await response.text();
            showAlert(`Failed to save configuration: ${error}`, 'error');
        }
    } catch (error) {
        console.error('Error saving configuration:', error);
        showAlert(`Network error: ${error.message}`, 'error');
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
        openapiSourceType: 'URL',
        openapiUrl: document.getElementById('openapi-spec').value,
        openapiContent: null,
        baseUrl: document.getElementById('api-url').value,
        timeoutSeconds: parseInt(document.getElementById('timeout').value) || 30,
        rateLimitPerMinute: parseInt(document.getElementById('rate-limit').value) || 100,
        createdBy: 'admin',
        generateTools: false
    };
    
    // Add authentication configuration
    config.authConfig = collectAuthConfig(authType);
    config.authType = authType;
    
    return config;
}

/**
 * Collect authentication configuration based on auth type
 */
function collectAuthConfig(authType) {
    const authConfig = {};
    
    switch (authType) {
        case 'API_KEY':
            const apiKeyEl = document.getElementById('api-key');
            const headerEl = document.getElementById('api-key-header');
            if (apiKeyEl) authConfig.apiKey = apiKeyEl.value;
            if (headerEl) authConfig.headerName = headerEl.value || 'X-API-Key';
            break;
            
        case 'BEARER_TOKEN':
            const tokenEl = document.getElementById('bearer-token');
            if (tokenEl) authConfig.token = tokenEl.value;
            break;
            
        case 'BASIC_AUTH':
            const usernameEl = document.getElementById('basic-username');
            const passwordEl = document.getElementById('basic-password');
            if (usernameEl) authConfig.username = usernameEl.value;
            if (passwordEl) authConfig.password = passwordEl.value;
            break;
            
        case 'OAUTH2':
            const clientIdEl = document.getElementById('oauth2-client-id');
            const clientSecretEl = document.getElementById('oauth2-client-secret');
            const tokenUrlEl = document.getElementById('oauth2-token-url');
            if (clientIdEl) authConfig.clientId = clientIdEl.value;
            if (clientSecretEl) authConfig.clientSecret = clientSecretEl.value;
            if (tokenUrlEl) authConfig.tokenUrl = tokenUrlEl.value;
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
        showAlert('Validation errors:\n' + errors.join('\n'), 'error');
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
 * Delete an API configuration
 */
async function deleteAPI(apiId) {
    const api = currentAPIs.find(a => a.id === apiId);
    if (!api) {
        showAlert('API not found', 'error');
        return;
    }
    
    if (!confirm(`Are you sure you want to delete the API configuration "${api.name}"?`)) {
        return;
    }
    
    showLoading(true);
    
    try {
        const response = await fetch(`/api/v1/configurations/${apiId}?deletedBy=admin`, {
            method: 'DELETE',
            credentials: 'include'
        });
        
        if (response.ok) {
            showAlert('API configuration deleted successfully', 'success');
            await loadDashboard(); // Refresh the dashboard
        } else {
            const error = await response.text();
            showAlert(`Failed to delete configuration: ${error}`, 'error');
        }
    } catch (error) {
        console.error('Error deleting API:', error);
        showAlert(`Network error: ${error.message}`, 'error');
    } finally {
        showLoading(false);
    }
}

/**
 * Test an API configuration
 */
async function testAPI(apiId) {
    showAlert('Testing API connection...', 'info');
    // TODO: Implement API testing
}

/**
 * Edit an API configuration
 */
function editAPI(apiId) {
    // TODO: Implement edit functionality
    showAlert('Edit functionality coming soon', 'info');
}

/**
 * Refresh APIs list
 */
async function refreshAPIs() {
    await loadDashboard();
    showAlert('APIs refreshed successfully', 'success');
}

/**
 * Test all APIs
 */
async function testAllAPIs() {
    showAlert('Testing all APIs...', 'info');
    // TODO: Implement bulk testing
}

/**
 * Show loading overlay
 */
function showLoading(show) {
    const overlay = document.getElementById('loading-overlay');
    if (show) {
        overlay.classList.remove('hidden');
    } else {
        overlay.classList.add('hidden');
    }
}

/**
 * Show alert message
 */
function showAlert(message, type = 'info') {
    const container = document.getElementById('alert-container');
    const alertId = 'alert-' + Date.now();
    
    const alertHTML = `
        <div id="${alertId}" class="alert-custom alert-${type === 'error' ? 'error' : 'success'} alert-dismissible fade show">
            <i class="fas fa-${type === 'success' ? 'check-circle' : type === 'error' ? 'exclamation-circle' : 'info-circle'} me-2"></i>
            ${escapeHtml(message)}
            <button type="button" class="btn-close" onclick="dismissAlert('${alertId}')"></button>
        </div>
    `;
    
    container.insertAdjacentHTML('beforeend', alertHTML);
    
    // Auto-dismiss after 5 seconds
    setTimeout(() => {
        dismissAlert(alertId);
    }, 5000);
}

/**
 * Dismiss alert
 */
function dismissAlert(alertId) {
    const alert = document.getElementById(alertId);
    if (alert) {
        alert.remove();
    }
}

/**
 * Escape HTML to prevent XSS
 */
function escapeHtml(text) {
    const map = {
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#039;'
    };
    return text.replace(/[&<>"']/g, function(m) { return map[m]; });
}

/**
 * Load API details
 */
async function loadAPIDetails(apiId) {
    // TODO: Implement API details loading
    document.getElementById('api-details-title').textContent = 'API Details - Loading...';
}

/**
 * Validate OpenAPI specification
 */
async function validateOpenAPISpec() {
    const url = document.getElementById('openapi-spec').value;
    if (!url) {
        showAlert('Please enter an OpenAPI specification URL first', 'error');
        return;
    }
    
    showAlert('Validating OpenAPI specification...', 'info');
    // TODO: Implement OpenAPI validation
}
