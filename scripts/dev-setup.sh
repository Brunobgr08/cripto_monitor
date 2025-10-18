#!/bin/bash
set -e

echo "🐳 Setting up Cripto Monitor development environment..."

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo "❌ Docker is not running. Please start Docker and try again."
    exit 1
fi

# Check if Docker Compose is available
if ! command -v docker-compose &> /dev/null; then
    echo "❌ Docker Compose is not installed. Please install Docker Compose and try again."
    exit 1
fi

# Create necessary directories
echo "📁 Creating directories..."
mkdir -p logs
mkdir -p backups
mkdir -p nginx/ssl

# Create .env file if it doesn't exist
if [ ! -f .env ]; then
    echo "📝 Creating .env file..."
    cat > .env << EOF
# Development environment variables
ENV=development
DATABASE_URL=jdbc:postgresql://postgres:5432/cripto_monitor_dev?user=dev_user&password=dev_pass
REDIS_URL=redis://redis:6379
LOG_LEVEL=debug

# API Keys (optional for development)
# COINGECKO_API_KEY=your_coingecko_api_key
# BINANCE_API_KEY=your_binance_api_key
# BINANCE_SECRET_KEY=your_binance_secret_key
EOF
    echo "✅ .env file created. You can edit it to add your API keys."
fi

# Stop any existing containers
echo "🛑 Stopping existing containers..."
docker-compose -f docker-compose.dev.yml down --remove-orphans || true

# Build development image
echo "🔨 Building development image..."
docker-compose -f docker-compose.dev.yml build --no-cache

# Start database and Redis first
echo "🗄️ Starting PostgreSQL and Redis..."
docker-compose -f docker-compose.dev.yml up -d postgres redis

# Wait for PostgreSQL to be ready
echo "⏳ Waiting for PostgreSQL to be ready..."
timeout=60
counter=0
until docker-compose -f docker-compose.dev.yml exec -T postgres pg_isready -U dev_user -d cripto_monitor_dev; do
    if [ $counter -ge $timeout ]; then
        echo "❌ PostgreSQL failed to start within $timeout seconds"
        docker-compose -f docker-compose.dev.yml logs postgres
        exit 1
    fi
    echo "Waiting for PostgreSQL... ($counter/$timeout)"
    sleep 2
    counter=$((counter + 2))
done

# Wait for Redis to be ready
echo "⏳ Waiting for Redis to be ready..."
timeout=30
counter=0
until docker-compose -f docker-compose.dev.yml exec -T redis redis-cli ping | grep -q PONG; do
    if [ $counter -ge $timeout ]; then
        echo "❌ Redis failed to start within $timeout seconds"
        docker-compose -f docker-compose.dev.yml logs redis
        exit 1
    fi
    echo "Waiting for Redis... ($counter/$timeout)"
    sleep 2
    counter=$((counter + 2))
done

echo "✅ Development environment setup complete!"
echo ""
echo "🚀 To start the application:"
echo "   docker-compose -f docker-compose.dev.yml up app"
echo ""
echo "🔧 Useful commands:"
echo "   # View logs"
echo "   docker-compose -f docker-compose.dev.yml logs -f app"
echo ""
echo "   # Run tests"
echo "   docker-compose -f docker-compose.dev.yml exec app clojure -M:test"
echo ""
echo "   # Connect to REPL"
echo "   docker-compose -f docker-compose.dev.yml exec app clojure -M:repl"
echo ""
echo "   # Access database (Adminer)"
echo "   docker-compose -f docker-compose.dev.yml --profile tools up -d adminer"
echo "   # Then visit: http://localhost:8080"
echo ""
echo "   # Stop all services"
echo "   docker-compose -f docker-compose.dev.yml down"
echo ""
echo "🌐 Services will be available at:"
echo "   Application: http://localhost:3000"
echo "   Database: localhost:5432"
echo "   Redis: localhost:6379"
echo "   Adminer (optional): http://localhost:8080"
