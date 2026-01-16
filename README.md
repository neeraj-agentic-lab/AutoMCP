# AutoMCP - MCP REST Adapter

[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.java.net/projects/jdk/21/)
[![Spring Boot 3.x](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-13+-blue.svg)](https://www.postgresql.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Build Status](https://img.shields.io/badge/Build-Passing-brightgreen.svg)](https://github.com/nyadav/mcp-rest-adapter)

> **🚀 Turn Any REST API into AI Agent Tools in Minutes**

**The Problem**: AI agents and LLMs can't directly interact with your existing REST APIs. You need to manually create tool definitions, handle authentication, manage connections, and write custom integration code for every API.

**The Solution**: AutoMCP automatically converts any REST API (with OpenAPI specification) into ready-to-use tools that AI agents can discover and execute. No coding required - just point it to your API documentation and you're done.

**Business Value**:
- ⚡ **Instant AI Integration** - Connect existing APIs to AI workflows in minutes, not weeks
- 💰 **Reduce Development Costs** - Eliminate custom integration code for every API
- 🔒 **Enterprise Security** - Built-in authentication, rate limiting, and validation
- 📈 **Scale AI Adoption** - Enable teams to quickly add AI capabilities to existing systems
- 🛠️ **Zero Maintenance** - Automatically stays in sync with API changes via OpenAPI specs

Built with Java 21, Spring Boot 3.x, and reactive architecture for enterprise-grade performance and reliability.

## 🌟 What Makes This Special

Transform any REST API into MCP tools that LLM agents can discover and use automatically. No manual tool definitions required - just provide an OpenAPI specification and the adapter handles the rest.

**Perfect for:**
- 🤖 **AI Agent Developers** - Instantly connect agents to existing APIs
- 🏢 **Enterprise Teams** - Expose internal APIs to LLM workflows securely  
- 🔧 **API Providers** - Make your APIs LLM-agent ready
- 📊 **Data Scientists** - Connect ML models to real-world data sources

## 🎯 Overview

The MCP REST Adapter bridges the gap between LLM agents and existing REST APIs by:

- **Dynamic Tool Generation**: Automatically parses OpenAPI specifications to create MCP tools
- **Secure Execution**: Implements comprehensive authentication, validation, and safety measures  
- **Production Ready**: Includes observability, resilience patterns, and enterprise features
- **High Performance**: Built on reactive Spring WebFlux for non-blocking, scalable operations

### Key Features

- ✅ **Web-Based Admin Interface** - Complete UI for API management and monitoring
- ✅ **OpenAPI 3.0 Support** - Automatic tool generation from specifications
- ✅ **Multiple Auth Methods** - API keys, OAuth2, Bearer tokens
- ✅ **Reactive Architecture** - Non-blocking I/O with Spring WebFlux
- ✅ **Real-time Streaming** - Server-Sent Events for live monitoring
- ✅ **PostgreSQL Database** - Persistent storage for configurations and tools
- ✅ **Comprehensive API Documentation** - Built-in docs with Postman collection
- ✅ **Circuit Breakers** - Resilience4j integration for fault tolerance
- ✅ **Comprehensive Metrics** - Micrometer + Prometheus integration
- ✅ **Distributed Tracing** - OpenTelemetry support
- ✅ **Rate Limiting** - Configurable request throttling
- ✅ **Input Validation** - JSON Schema validation for all tool inputs
- ✅ **Production Ready** - Health checks, graceful shutdown, observability

## 🚀 Quick Start

### Prerequisites

- **Java 21** or later
- **Maven 3.8+** for building
- **PostgreSQL 13+** for data persistence
- **Docker** (optional, for containerized deployment)
- **Modern Web Browser** for admin interface

### Installation

1. **Clone the repository:**
   ```bash
   git clone https://github.com/nyadav/mcp-rest-adapter.git
   cd mcp-rest-adapter
   ```

2. **Build the application:**
   ```bash
   mvn clean package
   ```

3. **Set up PostgreSQL database:**
   ```bash
   # Using Docker (recommended)
   docker run --name mcp-postgres \
     -e POSTGRES_DB=mcp_adapter \
     -e POSTGRES_USER=mcp_user \
     -e POSTGRES_PASSWORD=mcp_password \
     -p 5432:5432 -d postgres:15
   
   # Or install PostgreSQL locally and create database
   createdb mcp_adapter
   ```

4. **Configure database connection:**
   ```bash
   export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/mcp_adapter
   export SPRING_DATASOURCE_USERNAME=mcp_user
   export SPRING_DATASOURCE_PASSWORD=mcp_password
   ```

5. **Run the application:**
   ```bash
   java -jar target/mcp-rest-adapter-1.0.0.jar
   ```

6. **Access the admin interface:**
   ```bash
   # Open in browser
   open http://localhost:8080/admin
   
   # Or verify server health
   curl http://localhost:8080/actuator/health
   ```

## 🎛️ Admin Interface

The MCP REST Adapter includes a comprehensive web-based admin interface for easy API management.

### Accessing the Admin Interface

1. **Start the server** and navigate to: `http://localhost:8080/admin`
2. **Default credentials**: `admin` / `admin123`
3. **Features available**:
   - 📊 **Dashboard** - Real-time statistics and API overview
   - ➕ **API Management** - Add, edit, delete API configurations
   - 🔧 **Tool Generation** - Automatic MCP tool creation from OpenAPI specs
   - 📖 **API Documentation** - Complete endpoint reference with Postman collection
   - 📈 **Monitoring** - Health checks, metrics, and performance data

### Adding APIs via Admin Interface

1. **Click "Add New API"** in the dashboard
2. **Fill in the configuration**:
   - **Name**: Descriptive name for your API
   - **Base URL**: Root URL of the API
   - **OpenAPI URL**: Link to OpenAPI 3.0 specification
   - **Authentication**: Choose from API Key, OAuth2, or Bearer Token
   - **Timeout & Rate Limits**: Configure performance settings
3. **Test the connection** to verify configuration
4. **Generate tools** automatically from the OpenAPI spec

### Configuration via Files (Alternative)

You can also configure APIs using YAML files:

```yaml
# application-custom.yml
mcp:
  apis:
    petstore:
      enabled: true
      openapi: https://petstore.swagger.io/v2/swagger.json
      base-url: https://petstore.swagger.io/v2
      auth:
        type: api_key
        header: api_key
        api-key: ${PETSTORE_API_KEY}
      timeout: 30s
      rate-limit: 100

    github:
      enabled: true
      openapi: https://api.github.com/openapi.json
      base-url: https://api.github.com
      auth:
        type: bearer_token
        bearer-token: ${GITHUB_TOKEN}
```

## 📖 Usage

### MCP Protocol Endpoints

The adapter exposes comprehensive MCP endpoints:

#### Core MCP Protocol
- **List Tools**: `GET /api/v1/mcp/tools`
- **Get Tool Details**: `GET /api/v1/mcp/tools/{toolName}`
- **Execute Tool**: `POST /api/v1/mcp/tools/{toolName}/invoke`
- **Validate Tool**: `POST /api/v1/mcp/tools/{toolName}/validate`
- **Tool Statistics**: `GET /api/v1/mcp/tools/{toolName}/stats`
- **System Stats**: `GET /api/v1/mcp/stats`

#### Real-time Streaming (SSE)
- **System Events**: `GET /api/v1/mcp/stream/system`
- **Execution Events**: `GET /api/v1/mcp/stream/executions`
- **Tool Availability**: `GET /api/v1/mcp/stream/tools/availability`
- **Health Status**: `GET /api/v1/mcp/stream/health`

#### Configuration Management
- **Manage APIs**: `GET|POST|PUT|DELETE /api/v1/configurations`
- **Generate Tools**: `POST /api/v1/configurations/{id}/generate-tools`
- **Test APIs**: `POST /api/v1/configurations/{id}/test`

#### Monitoring & Observability
- **System Metrics**: `GET /api/v1/monitoring/metrics`
- **Health Details**: `GET /api/v1/monitoring/health/detailed`
- **Performance Stats**: `GET /api/v1/monitoring/performance`
- **Audit Trail**: `GET /api/v1/monitoring/audit`

### Example: Tool Discovery

```bash
curl http://localhost:8080/api/v1/mcp/tools
```

Response:
```json
{
  "tools": [
    {
      "name": "petstore_find_pets_by_status",
      "description": "Finds pets by status in the pet store",
      "inputSchema": {
        "type": "object",
        "properties": {
          "status": {
            "type": "array",
            "items": {
              "type": "string",
              "enum": ["available", "pending", "sold"]
            }
          },
          "limit": {
            "type": "integer",
            "minimum": 1,
            "maximum": 100,
            "default": 20
          }
        },
        "required": ["status"]
      }
    }
  ]
}
```

### Example: Tool Invocation

```bash
curl -X POST http://localhost:8080/api/v1/mcp/tools/petstore_find_pets_by_status/invoke \
  -H "Content-Type: application/json" \
  -d '{
    "callId": "unique-call-id-123",
    "arguments": {
      "status": ["available"],
      "limit": 5
    },
    "context": {}
  }'
```

Response:
```json
{
  "content": [
    {
      "type": "text",
      "text": "Found 3 available pets"
    },
    {
      "type": "json",
      "data": [
        {
          "id": 1,
          "name": "Fluffy",
          "status": "available"
        }
      ]
    }
  ],
  "isError": false,
  "callId": "550e8400-e29b-41d4-a716-446655440000",
  "executionTime": "PT0.245S"
}
```

## 🏗️ Architecture

The application follows a strict layered architecture with clear separation of concerns:

```
src/main/java/com/mcpwrapper/
├── transport/controller/    # REST API Controllers
│   ├── McpController       # Core MCP protocol endpoints
│   ├── McpSseController    # Server-Sent Events streaming
│   ├── ConfigurationController # API configuration management
│   └── MonitoringController    # Observability endpoints
├── runtime/service/        # Core Business Logic  
│   ├── McpServiceFacade    # Main service orchestration
│   ├── ReactiveToolRegistryService # Tool management
│   └── ToolInvoker         # Tool execution engine
├── adapter/               # External Integration
│   ├── openapi/           # OpenAPI spec parsing & tool generation
│   └── rest/              # REST API invocation & authentication
├── platform/              # Cross-cutting Concerns
│   ├── web/               # Admin web interface controllers
│   ├── config/            # Configuration management
│   └── observability/     # Metrics, logging, tracing
└── domain/                # Domain Models
    ├── McpTool            # Tool definitions
    ├── ApiConfiguration   # API config entities
    └── ToolExecution      # Execution tracking
```

### Key Components

- **Tool Registry**: Manages tool definitions and metadata
- **Tool Invoker**: Orchestrates tool execution with validation and resilience
- **OpenAPI Parser**: Converts API specs to MCP tool definitions
- **REST Adapter**: Handles HTTP client operations with authentication
- **Security Layer**: Implements authentication and authorization policies

## ⚙️ Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `MCP_SERVER_PORT` | Server port | `8080` |
| `MCP_LOG_LEVEL` | Logging level | `INFO` |
| `MCP_METRICS_ENABLED` | Enable metrics | `true` |
| `MCP_AUTH_ENABLED` | Enable authentication | `true` |

### Configuration Profiles

- **dev**: Development with debug logging and disabled security
- **prod**: Production with optimized settings and security enabled
- **test**: Testing with minimal resources and fast startup

### API Authentication

The adapter supports multiple authentication methods:

#### API Key Authentication
```yaml
auth:
  type: api_key
  header: X-API-Key
  api-key: ${API_KEY}
```

#### OAuth2 Client Credentials
```yaml
auth:
  type: oauth2_client_credentials
  token-url: https://auth.example.com/token
  client-id: ${CLIENT_ID}
  client-secret: ${CLIENT_SECRET}
  scopes: ["api:read", "api:write"]
```

#### Bearer Token
```yaml
auth:
  type: bearer_token
  bearer-token: ${BEARER_TOKEN}
```

## 🔧 Development

### Building from Source

```bash
# Clone repository
git clone https://github.com/nyadav/mcp-rest-adapter.git
cd mcp-rest-adapter

# Run tests
mvn test

# Run integration tests
mvn verify

# Build JAR
mvn package

# Run with development profile
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### Code Quality

The project maintains high code quality standards:

- **Test Coverage**: 90%+ required (enforced by JaCoCo)
- **Static Analysis**: SpotBugs, PMD, Checkstyle
- **Code Style**: Google Java Style Guide
- **Documentation**: Comprehensive JavaDoc for all public APIs

### Adding New APIs

#### Via Admin Interface (Recommended)
1. **Navigate to** `http://localhost:8080/admin`
2. **Click "Add New API"** in the dashboard
3. **Fill in the form** with API details and authentication
4. **Test connection** to verify configuration
5. **Generate tools** - MCP tools created automatically

#### Via Configuration Files
1. **Add configuration** to `application.yml`:
   ```yaml
   # Database-driven configuration (preferred)
   spring:
     datasource:
       url: jdbc:postgresql://localhost:5432/mcp_adapter
   ```
2. **Use admin interface** to manage APIs dynamically
3. **No restart required** - changes applied immediately

#### Via REST API
```bash
# Create new API configuration
curl -X POST http://localhost:8080/api/v1/configurations \
  -H "Content-Type: application/json" \
  -d '{
    "name": "My API",
    "baseUrl": "https://api.example.com",
    "openapiUrl": "https://api.example.com/openapi.json",
    "enabled": true,
    "authConfig": {
      "type": "API_KEY",
      "apiKeyHeader": "X-API-Key",
      "apiKeyValue": "your-api-key"
    }
  }'

# Generate tools from configuration
curl -X POST http://localhost:8080/api/v1/configurations/{id}/generate-tools
```

## 📊 Monitoring

### Health Checks

- **Liveness**: `GET /actuator/health/liveness`
- **Readiness**: `GET /actuator/health/readiness`  
- **Overall Health**: `GET /actuator/health`

### Metrics

Prometheus metrics available at `/actuator/prometheus`:

- `mcp_adapter_tool_invocations_total` - Total tool invocations
- `mcp_adapter_tool_execution_duration_seconds` - Execution time distribution
- `mcp_adapter_tool_errors_total` - Error count by type
- `mcp_adapter_active_executions` - Currently executing tools

### Distributed Tracing

OpenTelemetry integration provides distributed tracing:

```yaml
mcp:
  observability:
    tracing-enabled: true
    trace-sampling-probability: 0.1
```

## 🚢 Deployment

### Docker

```dockerfile
FROM openjdk:21-jre-slim

COPY target/mcp-rest-adapter-1.0.0.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app.jar"]
```

Build and run:
```bash
docker build -t mcp-rest-adapter .
docker run -p 8080:8080 mcp-rest-adapter
```

### Kubernetes

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: mcp-rest-adapter
spec:
  replicas: 3
  selector:
    matchLabels:
      app: mcp-rest-adapter
  template:
    metadata:
      labels:
        app: mcp-rest-adapter
    spec:
      containers:
      - name: mcp-rest-adapter
        image: mcp-rest-adapter:1.0.0
        ports:
        - containerPort: 8080
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "prod"
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 5
          periodSeconds: 5
```

## 🔒 Security

### Security Features

- **Input Validation**: JSON Schema validation prevents malformed requests
- **Authentication**: Multiple auth methods with secure credential handling
- **Rate Limiting**: Configurable request throttling per tool/user
- **CORS Protection**: Configurable cross-origin request policies
- **Request Size Limits**: Prevents DoS attacks via large payloads
- **Timeout Enforcement**: Prevents resource exhaustion from slow APIs

### Security Best Practices

1. **Never hardcode credentials** - use environment variables
2. **Enable authentication** in production environments
3. **Configure appropriate CORS** policies for your use case
4. **Monitor security metrics** and set up alerting
5. **Keep dependencies updated** for security patches

## 🤝 Contributing

We welcome contributions! Please see our [Contributing Guide](CONTRIBUTING.md) for details.

### Development Workflow

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/amazing-feature`
3. Make your changes with tests
4. Ensure code quality: `mvn verify`
5. Commit your changes: `git commit -m 'Add amazing feature'`
6. Push to the branch: `git push origin feature/amazing-feature`
7. Open a Pull Request

## 📚 Documentation

### Built-in Documentation
- **Admin Interface**: `http://localhost:8080/admin` - Complete web-based management
- **API Documentation**: `http://localhost:8080/admin/mcp-docs.html` - Full endpoint reference
- **Postman Collection**: Download from admin interface for API testing
- **Health Checks**: `http://localhost:8080/actuator/health` - System status
- **Metrics**: `http://localhost:8080/actuator/prometheus` - Monitoring data

### Additional Resources
- **[PROJECT_PLAN.md](PROJECT_PLAN.md)** - Complete implementation roadmap and architecture
- **[Database Schema](src/main/resources/db/migration/)** - Flyway migrations and schema
- **[Configuration Reference](#-configuration)** - Complete configuration options
- **[API Examples](#-usage)** - Practical usage examples

## 📄 License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

## 🚀 Getting Started Checklist

- [ ] **Install Prerequisites**: Java 21, PostgreSQL, Maven
- [ ] **Clone Repository**: `git clone https://github.com/nyadav/mcp-rest-adapter.git`
- [ ] **Setup Database**: Create PostgreSQL database and configure connection
- [ ] **Build Application**: `mvn clean package`
- [ ] **Start Server**: `java -jar target/mcp-rest-adapter-1.0.0.jar`
- [ ] **Access Admin UI**: Open `http://localhost:8080/admin`
- [ ] **Add First API**: Use admin interface to configure your first API
- [ ] **Generate Tools**: Let the system create MCP tools automatically
- [ ] **Test Integration**: Use Postman collection or curl commands
- [ ] **Monitor System**: Check health endpoints and metrics

## 👨‍💻 Author

**Neeraj Yadav**
- GitHub: [@neeraj-agentic-lab](https://github.com/neeraj-agentic-lab)
- LinkedIn: [Neeraj Yadav](https://www.linkedin.com/in/n-yadav)
- Email: neeraj@nyadav.com

## 🙏 Acknowledgments

- [Model Context Protocol](https://github.com/modelcontextprotocol) specification
- [Spring Boot](https://spring.io/projects/spring-boot) reactive framework
- [OpenAPI Initiative](https://www.openapis.org/) for API specifications
- [Resilience4j](https://resilience4j.readme.io/) for resilience patterns

---

**Built with ❤️ for the LLM agent ecosystem**
