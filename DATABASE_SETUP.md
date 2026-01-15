# MCP REST Adapter - Database Setup Guide

This guide explains how to set up and manage the PostgreSQL database for the MCP REST Adapter using Docker and Flyway migrations.

## 🗄️ Database Architecture

The MCP REST Adapter uses PostgreSQL as its primary database with Flyway for migration management. The database stores:

- **API Configurations**: External REST API settings and authentication
- **Tool Definitions**: Generated MCP tools from OpenAPI specifications  
- **Usage Statistics**: Tool invocation metrics and performance data
- **Audit Logs**: Complete change history for compliance
- **Environment Variables**: Encrypted secrets and configuration
- **Configuration Templates**: Reusable API configuration patterns

## 🚀 Quick Start

### 1. Start PostgreSQL Database

```bash
# Start PostgreSQL only
./scripts/start-database.sh

# Or start all services (PostgreSQL + Redis + Monitoring)
./scripts/start-database.sh --all
```

### 2. Run Database Migrations

```bash
# Run all pending migrations
./scripts/flyway-migrate.sh migrate

# Check migration status
./scripts/flyway-migrate.sh info
```

### 3. Verify Setup

```bash
# Check database connectivity
./scripts/flyway-migrate.sh validate

# View connection information
./scripts/start-database.sh --status
```

## 📋 Database Schema Overview

### Core Tables

| Table | Purpose | Key Features |
|-------|---------|--------------|
| `api_configurations` | Store REST API settings | JSONB auth config, status tracking |
| `tool_definitions` | Cache generated MCP tools | Tool metadata, deprecation flags |
| `configuration_audit` | Track all changes | Full audit trail, IP tracking |
| `tool_usage_stats` | Monitor tool performance | Success rates, execution times |
| `environment_variables` | Store encrypted secrets | Encryption, masking support |
| `api_health_status` | Monitor API health | Uptime tracking, failure counts |
| `configuration_templates` | Reusable configurations | Public templates, ratings |

### Key Features

- **UUID Primary Keys**: All tables use UUIDs for distributed system compatibility
- **JSONB Columns**: Flexible storage for configuration and metadata
- **Automatic Timestamps**: Created/updated timestamps with triggers
- **Comprehensive Indexing**: Optimized for common query patterns
- **Data Integrity**: Foreign key constraints and check constraints
- **Audit Trail**: Complete change tracking for compliance

## 🔧 Flyway Migration Management

### Available Commands

```bash
# Core migration commands
./scripts/flyway-migrate.sh migrate     # Run pending migrations
./scripts/flyway-migrate.sh info       # Show migration status
./scripts/flyway-migrate.sh validate   # Validate applied migrations

# Development commands
./scripts/flyway-migrate.sh clean      # Clean database (dev only)
./scripts/flyway-migrate.sh baseline   # Baseline existing database
./scripts/flyway-migrate.sh repair     # Repair schema history
./scripts/flyway-migrate.sh reset      # Clean + migrate (dev only)
```

### Migration Files

Migrations are located in `src/main/resources/db/migration/`:

- `V1__Initial_Schema.sql` - Complete database schema
- `V2__Initial_Data.sql` - Default data and templates
- `V3__*` - Future migrations (as needed)

### Migration Naming Convention

```
V{version}__{description}.sql
```

Examples:
- `V1__Initial_Schema.sql`
- `V2__Initial_Data.sql`
- `V3__Add_Tool_Categories.sql`
- `V4__Update_Auth_Config_Schema.sql`

## 🐳 Docker Configuration

### Services Included

| Service | Port | Purpose | Credentials |
|---------|------|---------|-------------|
| PostgreSQL | 5434 | Main database | mcpuser/mcppassword |
| Redis | 6379 | Caching (optional) | redispassword |
| pgAdmin | 5050 | Database management | admin@mcpwrapper.com/adminpassword |
| Prometheus | 9090 | Metrics collection | - |
| Grafana | 3000 | Monitoring dashboard | admin/grafanapassword |

### Environment Variables

Create a `.env` file (copy from `.env.example`):

```bash
# Database Configuration
DB_HOST=localhost
DB_PORT=5434
DB_NAME=mcpwrapper
DB_USERNAME=mcpuser
DB_PASSWORD=mcppassword

# Application Configuration
MCP_SERVER_PORT=8080
MCP_LOG_LEVEL=DEBUG
MCP_PROFILE=dev
```

## 🔍 Database Management

### Using pgAdmin

1. **Access pgAdmin**: http://localhost:5050
2. **Login**: admin@mcpwrapper.com / adminpassword
3. **Add Server**:
   - Name: MCP REST Adapter
   - Host: postgres (or localhost if external)
   - Port: 5434
   - Database: mcpwrapper
   - Username: mcpuser
   - Password: mcppassword

### Using Command Line

```bash
# Connect to database
docker-compose exec postgres psql -U mcpuser -d mcpwrapper

# Run SQL queries
docker-compose exec postgres psql -U mcpuser -d mcpwrapper -c "SELECT * FROM api_configurations;"

# Backup database
docker-compose exec postgres pg_dump -U mcpuser mcpwrapper > backup.sql

# Restore database
docker-compose exec -T postgres psql -U mcpuser -d mcpwrapper < backup.sql
```

## 📊 Monitoring and Maintenance

### Health Checks

```bash
# Check database health
docker-compose exec postgres pg_isready -U mcpuser -d mcpwrapper

# View database size
docker-compose exec postgres psql -U mcpuser -d mcpwrapper -c "
SELECT 
    pg_size_pretty(pg_database_size('mcpwrapper')) as database_size,
    pg_size_pretty(pg_total_relation_size('api_configurations')) as configs_size,
    pg_size_pretty(pg_total_relation_size('tool_definitions')) as tools_size;
"
```

### Performance Monitoring

```bash
# View active connections
docker-compose exec postgres psql -U mcpuser -d mcpwrapper -c "
SELECT count(*) as active_connections 
FROM pg_stat_activity 
WHERE state = 'active';
"

# View table statistics
docker-compose exec postgres psql -U mcpuser -d mcpwrapper -c "
SELECT schemaname, tablename, n_tup_ins, n_tup_upd, n_tup_del 
FROM pg_stat_user_tables 
ORDER BY n_tup_ins DESC;
"
```

## 🔒 Security Considerations

### Production Setup

1. **Change Default Passwords**:
   ```bash
   # Update .env file with secure passwords
   DB_PASSWORD=your-secure-password
   REDIS_PASSWORD=your-redis-password
   ```

2. **Enable SSL/TLS**:
   ```yaml
   # In docker-compose.yml
   postgres:
     command: postgres -c ssl=on -c ssl_cert_file=/var/lib/postgresql/server.crt
   ```

3. **Network Security**:
   ```yaml
   # Restrict network access
   networks:
     mcp-network:
       driver: bridge
       internal: true
   ```

### Backup Strategy

```bash
# Automated backup script
#!/bin/bash
DATE=$(date +%Y%m%d_%H%M%S)
docker-compose exec postgres pg_dump -U mcpuser mcpwrapper | gzip > "backup_${DATE}.sql.gz"

# Retention policy (keep last 7 days)
find . -name "backup_*.sql.gz" -mtime +7 -delete
```

## 🚨 Troubleshooting

### Common Issues

1. **Database Connection Failed**:
   ```bash
   # Check if database is running
   docker-compose ps postgres
   
   # Check logs
   docker-compose logs postgres
   
   # Restart database
   docker-compose restart postgres
   ```

2. **Migration Failed**:
   ```bash
   # Check migration status
   ./scripts/flyway-migrate.sh info
   
   # Repair schema history
   ./scripts/flyway-migrate.sh repair
   
   # Validate migrations
   ./scripts/flyway-migrate.sh validate
   ```

3. **Performance Issues**:
   ```bash
   # Analyze table statistics
   docker-compose exec postgres psql -U mcpuser -d mcpwrapper -c "ANALYZE;"
   
   # Check slow queries
   docker-compose exec postgres psql -U mcpuser -d mcpwrapper -c "
   SELECT query, mean_time, calls 
   FROM pg_stat_statements 
   ORDER BY mean_time DESC LIMIT 10;
   "
   ```

### Reset Database (Development Only)

```bash
# Complete reset
./scripts/flyway-migrate.sh reset

# Or manual reset
docker-compose down -v  # Remove volumes
docker-compose up -d postgres
./scripts/flyway-migrate.sh migrate
```

## 📚 Additional Resources

- [Flyway Documentation](https://flywaydb.org/documentation/)
- [PostgreSQL Documentation](https://www.postgresql.org/docs/)
- [Docker Compose Reference](https://docs.docker.com/compose/)
- [Spring Boot Database Configuration](https://docs.spring.io/spring-boot/docs/current/reference/html/data.html)

---

**Author**: Neeraj Yadav  
**Version**: 1.0.0  
**Last Updated**: 2025-12-16
