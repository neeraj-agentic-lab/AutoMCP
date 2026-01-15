#!/bin/bash

# MCP REST Adapter - Database Startup Script
# 
# This script starts the PostgreSQL database and related services using Docker Compose.
# It also performs basic health checks and provides connection information.
# 
# Usage:
#   ./scripts/start-database.sh           # Start database only
#   ./scripts/start-database.sh --all     # Start all services (db, redis, monitoring)
#   ./scripts/start-database.sh --stop    # Stop all services
# 
# @author Neeraj Yadav
# @version 1.0.0
# @since 2025-12-16

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
PROJECT_NAME="mcpwrapper"
COMPOSE_FILE="docker-compose.yml"

# Function to print colored output
print_status() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Function to check if Docker is running
check_docker() {
    if ! docker info >/dev/null 2>&1; then
        print_error "Docker is not running. Please start Docker and try again."
        exit 1
    fi
    print_success "Docker is running"
}

# Function to check if docker-compose is available
check_docker_compose() {
    if ! command -v docker-compose >/dev/null 2>&1; then
        print_error "docker-compose is not installed. Please install it and try again."
        exit 1
    fi
    print_success "docker-compose is available"
}

# Function to start database only
start_database() {
    print_status "Starting PostgreSQL database..."
    docker-compose up -d postgres
    
    print_status "Waiting for database to be ready..."
    sleep 5
    
    # Wait for database to be healthy
    local max_attempts=30
    local attempt=1
    
    while [ $attempt -le $max_attempts ]; do
        if docker-compose exec -T postgres pg_isready -U mcpuser -d mcpwrapper >/dev/null 2>&1; then
            print_success "Database is ready!"
            break
        fi
        
        if [ $attempt -eq $max_attempts ]; then
            print_error "Database failed to start after $max_attempts attempts"
            docker-compose logs postgres
            exit 1
        fi
        
        print_status "Attempt $attempt/$max_attempts - waiting for database..."
        sleep 2
        ((attempt++))
    done
}

# Function to start all services
start_all_services() {
    print_status "Starting all services (PostgreSQL, Redis, pgAdmin, Prometheus, Grafana)..."
    docker-compose up -d
    
    print_status "Waiting for services to be ready..."
    sleep 10
    
    # Check service health
    check_service_health
}

# Function to check service health
check_service_health() {
    print_status "Checking service health..."
    
    # Check PostgreSQL
    if docker-compose exec -T postgres pg_isready -U mcpuser -d mcpwrapper >/dev/null 2>&1; then
        print_success "✓ PostgreSQL is healthy"
    else
        print_warning "✗ PostgreSQL is not responding"
    fi
    
    # Check Redis
    if docker-compose exec -T redis redis-cli -a redispassword ping >/dev/null 2>&1; then
        print_success "✓ Redis is healthy"
    else
        print_warning "✗ Redis is not responding"
    fi
    
    # Check if pgAdmin is running
    if docker-compose ps pgadmin | grep -q "Up"; then
        print_success "✓ pgAdmin is running"
    else
        print_warning "✗ pgAdmin is not running"
    fi
}

# Function to stop all services
stop_services() {
    print_status "Stopping all services..."
    docker-compose down
    print_success "All services stopped"
}

# Function to show connection information
show_connection_info() {
    echo ""
    echo "=================================="
    echo "  MCP REST Adapter - Database Info"
    echo "=================================="
    echo ""
    echo "📊 PostgreSQL Database:"
    echo "   Host: localhost"
    echo "   Port: 5432"
    echo "   Database: mcpwrapper"
    echo "   Username: mcpuser"
    echo "   Password: mcppassword"
    echo ""
    echo "🔧 pgAdmin (Database Management):"
    echo "   URL: http://localhost:5050"
    echo "   Email: admin@mcpwrapper.com"
    echo "   Password: adminpassword"
    echo ""
    echo "🗄️ Redis Cache:"
    echo "   Host: localhost"
    echo "   Port: 6379"
    echo "   Password: redispassword"
    echo ""
    echo "📈 Monitoring (if started with --all):"
    echo "   Prometheus: http://localhost:9090"
    echo "   Grafana: http://localhost:3000 (admin/grafanapassword)"
    echo ""
    echo "🔗 JDBC Connection String:"
    echo "   jdbc:postgresql://localhost:5432/mcpwrapper"
    echo ""
    echo "💡 To connect from your application, use the environment variables:"
    echo "   DB_HOST=localhost"
    echo "   DB_PORT=5432"
    echo "   DB_NAME=mcpwrapper"
    echo "   DB_USERNAME=mcpuser"
    echo "   DB_PASSWORD=mcppassword"
    echo ""
}

# Function to show logs
show_logs() {
    local service=${1:-postgres}
    print_status "Showing logs for $service..."
    docker-compose logs -f "$service"
}

# Function to show usage
show_usage() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  --all, -a        Start all services (database, redis, monitoring)"
    echo "  --stop, -s       Stop all services"
    echo "  --logs [service] Show logs for a service (default: postgres)"
    echo "  --status         Show status of all services"
    echo "  --help, -h       Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0               Start PostgreSQL database only"
    echo "  $0 --all         Start all services"
    echo "  $0 --stop        Stop all services"
    echo "  $0 --logs redis  Show Redis logs"
    echo ""
}

# Function to show service status
show_status() {
    print_status "Service Status:"
    docker-compose ps
    echo ""
    check_service_health
}

# Main script logic
main() {
    # Change to project directory
    cd "$(dirname "$0")/.."
    
    case "${1:-}" in
        --all|-a)
            check_docker
            check_docker_compose
            start_all_services
            show_connection_info
            ;;
        --stop|-s)
            check_docker
            check_docker_compose
            stop_services
            ;;
        --logs)
            check_docker
            check_docker_compose
            show_logs "${2:-postgres}"
            ;;
        --status)
            check_docker
            check_docker_compose
            show_status
            ;;
        --help|-h)
            show_usage
            ;;
        "")
            check_docker
            check_docker_compose
            start_database
            show_connection_info
            ;;
        *)
            print_error "Unknown option: $1"
            show_usage
            exit 1
            ;;
    esac
}

# Run main function with all arguments
main "$@"
