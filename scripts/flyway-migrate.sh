#!/bin/bash

# MCP REST Adapter - Flyway Database Migration Script
# 
# This script manages database migrations using Flyway. It provides commands
# to migrate, validate, clean, and get information about the database schema.
# 
# Usage:
#   ./scripts/flyway-migrate.sh migrate     # Run pending migrations
#   ./scripts/flyway-migrate.sh info       # Show migration status
#   ./scripts/flyway-migrate.sh validate   # Validate applied migrations
#   ./scripts/flyway-migrate.sh clean      # Clean database (dev only)
#   ./scripts/flyway-migrate.sh baseline   # Baseline existing database
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
FLYWAY_CONF="flyway.conf"

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

# Function to check if database is running
check_database() {
    local db_host=${DB_HOST:-localhost}
    local db_port=${DB_PORT:-5434}
    local db_name=${DB_NAME:-mcpwrapper}
    local db_user=${DB_USERNAME:-mcpuser}
    
    print_status "Checking database connectivity..."
    
    if ! command -v pg_isready >/dev/null 2>&1; then
        print_warning "pg_isready not found, trying Docker container..."
        if docker-compose exec -T postgres pg_isready -U "$db_user" -d "$db_name" >/dev/null 2>&1; then
            print_success "Database is accessible via Docker"
            return 0
        else
            print_error "Database is not accessible"
            return 1
        fi
    else
        if pg_isready -h "$db_host" -p "$db_port" -U "$db_user" -d "$db_name" >/dev/null 2>&1; then
            print_success "Database is accessible"
            return 0
        else
            print_error "Database is not accessible at $db_host:$db_port"
            return 1
        fi
    fi
}

# Function to load environment variables
load_env() {
    if [ -f .env ]; then
        print_status "Loading environment variables from .env file..."
        export $(grep -v '^#' .env | xargs)
    else
        print_warning "No .env file found, using default values"
    fi
}

# Function to run Flyway migrate
flyway_migrate() {
    print_status "Running Flyway migrations..."
    
    if mvn flyway:migrate -Dspring.profiles.active=dev; then
        print_success "Database migrations completed successfully"
        flyway_info
    else
        print_error "Migration failed"
        exit 1
    fi
}

# Function to show migration info
flyway_info() {
    print_status "Showing migration status..."
    mvn flyway:info -Dspring.profiles.active=dev
}

# Function to validate migrations
flyway_validate() {
    print_status "Validating applied migrations..."
    
    if mvn flyway:validate -Dspring.profiles.active=dev; then
        print_success "All migrations are valid"
    else
        print_error "Migration validation failed"
        exit 1
    fi
}

# Function to clean database (development only)
flyway_clean() {
    local profile=${SPRING_PROFILES_ACTIVE:-dev}
    
    if [ "$profile" != "dev" ]; then
        print_error "Clean operation is only allowed in development environment"
        print_error "Current profile: $profile"
        exit 1
    fi
    
    print_warning "This will delete all objects in the database!"
    read -p "Are you sure you want to continue? (yes/no): " confirm
    
    if [ "$confirm" = "yes" ]; then
        print_status "Cleaning database..."
        if mvn flyway:clean -Dspring.profiles.active=dev; then
            print_success "Database cleaned successfully"
        else
            print_error "Database clean failed"
            exit 1
        fi
    else
        print_status "Clean operation cancelled"
    fi
}

# Function to baseline existing database
flyway_baseline() {
    print_status "Creating Flyway baseline..."
    
    if mvn flyway:baseline -Dspring.profiles.active=dev; then
        print_success "Baseline created successfully"
        flyway_info
    else
        print_error "Baseline creation failed"
        exit 1
    fi
}

# Function to repair migration history
flyway_repair() {
    print_status "Repairing Flyway schema history..."
    
    if mvn flyway:repair -Dspring.profiles.active=dev; then
        print_success "Schema history repaired successfully"
        flyway_info
    else
        print_error "Schema history repair failed"
        exit 1
    fi
}

# Function to show help
show_help() {
    echo "MCP REST Adapter - Flyway Migration Management"
    echo ""
    echo "Usage: $0 [COMMAND]"
    echo ""
    echo "Commands:"
    echo "  migrate     Run pending database migrations"
    echo "  info        Show current migration status"
    echo "  validate    Validate applied migrations"
    echo "  clean       Clean database (development only)"
    echo "  baseline    Create Flyway baseline for existing database"
    echo "  repair      Repair Flyway schema history table"
    echo "  reset       Clean database and run all migrations (dev only)"
    echo "  help        Show this help message"
    echo ""
    echo "Environment Variables:"
    echo "  DB_HOST              Database host (default: localhost)"
    echo "  DB_PORT              Database port (default: 5432)"
    echo "  DB_NAME              Database name (default: mcpwrapper)"
    echo "  DB_USERNAME          Database username (default: mcpuser)"
    echo "  DB_PASSWORD          Database password (default: mcppassword)"
    echo "  SPRING_PROFILES_ACTIVE  Spring profile (default: dev)"
    echo ""
    echo "Examples:"
    echo "  $0 migrate           # Run all pending migrations"
    echo "  $0 info              # Show migration status"
    echo "  $0 validate          # Validate current state"
    echo "  $0 reset             # Reset and migrate (dev only)"
    echo ""
}

# Function to reset database (clean + migrate)
flyway_reset() {
    local profile=${SPRING_PROFILES_ACTIVE:-dev}
    
    if [ "$profile" != "dev" ]; then
        print_error "Reset operation is only allowed in development environment"
        exit 1
    fi
    
    print_warning "This will completely reset the database!"
    read -p "Are you sure you want to continue? (yes/no): " confirm
    
    if [ "$confirm" = "yes" ]; then
        flyway_clean
        flyway_migrate
    else
        print_status "Reset operation cancelled"
    fi
}

# Function to check prerequisites
check_prerequisites() {
    # Check if Maven is available
    if ! command -v mvn >/dev/null 2>&1; then
        print_error "Maven is not installed or not in PATH"
        exit 1
    fi
    
    # Check if we're in the project directory
    if [ ! -f "pom.xml" ]; then
        print_error "Not in project root directory (pom.xml not found)"
        exit 1
    fi
    
    print_success "Prerequisites check passed"
}

# Main script logic
main() {
    # Change to project directory
    cd "$(dirname "$0")/.."
    
    # Check prerequisites
    check_prerequisites
    
    # Load environment variables
    load_env
    
    # Check database connectivity
    if ! check_database; then
        print_error "Database is not accessible. Please start the database first:"
        print_error "  ./scripts/start-database.sh"
        exit 1
    fi
    
    # Execute command
    case "${1:-help}" in
        migrate)
            flyway_migrate
            ;;
        info)
            flyway_info
            ;;
        validate)
            flyway_validate
            ;;
        clean)
            flyway_clean
            ;;
        baseline)
            flyway_baseline
            ;;
        repair)
            flyway_repair
            ;;
        reset)
            flyway_reset
            ;;
        help|--help|-h)
            show_help
            ;;
        *)
            print_error "Unknown command: $1"
            echo ""
            show_help
            exit 1
            ;;
    esac
}

# Run main function with all arguments
main "$@"
