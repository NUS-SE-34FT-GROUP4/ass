#!/bin/bash

# Start script for the second-hand marketplace
# Starts every required service: MySQL, Redis, MinIO, Elasticsearch, backend and frontend

echo "=========================================="
echo "  Second-hand Marketplace - Start Script"
echo "=========================================="

# Use Java 17 (avoids compatibility problems with Java 25)
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
echo "✓ Using Java 17: $JAVA_HOME"

# Check Docker is running
if ! docker info > /dev/null 2>&1; then
    echo "❌ Docker is not running. Please start Docker Desktop first"
    exit 1
fi
echo "✓ Docker is running"

# Stop and remove old containers
echo ""
echo "Removing old containers..."
docker-compose down

# Start all services
echo ""
echo "Starting service containers (MySQL, Redis, MinIO, Elasticsearch)..."
docker-compose up -d mysql-db redis-cache minio elasticsearch

# Wait for the database and Elasticsearch
echo ""
echo "Waiting for services to start (30 seconds)..."
sleep 30

# Check service status
echo ""
echo "Checking service status..."
docker-compose ps

# Build and start the backend
echo ""
echo "=========================================="
echo "Building and starting the backend..."
echo "=========================================="
JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn clean package -DskipTests
if [ $? -eq 0 ]; then
    echo "✓ Backend built"
    echo "Starting the Spring Boot application..."
    JAVA_HOME=$(/usr/libexec/java_home -v 17) java -jar core/target/c2c-core-0.0.1-SNAPSHOT.jar &
    BACKEND_PID=$!
    echo "✓ Backend started (PID: $BACKEND_PID)"
else
    echo "❌ Backend build failed"
    exit 1
fi

# Wait for the backend
echo "Waiting for the backend to start (15 seconds)..."
sleep 15

# Start the frontend dev server
echo ""
echo "=========================================="
echo "Starting the frontend dev server..."
echo "=========================================="
cd frontend
npm run serve &
FRONTEND_PID=$!
echo "✓ Frontend started (PID: $FRONTEND_PID)"

# Show access information
echo ""
echo "=========================================="
echo "  ✅ All services are running!"
echo "=========================================="
echo ""
echo "📋 Service URLs:"
echo "  - Frontend:        http://localhost:8081"
echo "  - Backend API:     http://localhost:8080"
echo "  - MinIO console:   http://localhost:9001"
echo "  - Elasticsearch:   http://localhost:9200"
echo ""
echo "📋 Default account:"
echo "  - Administrator: admin / admin123"
echo "  - MinIO:  minioadmin / minioadmin"
echo ""
echo "📋 Process IDs:"
echo "  - Backend PID: $BACKEND_PID"
echo "  - Frontend PID: $FRONTEND_PID"
echo ""
echo "🛑 To stop:"
echo "  - Press Ctrl+C to stop this script"
echo "  - Or run: docker-compose down"
echo "  - Or run: kill $BACKEND_PID $FRONTEND_PID"
echo ""
echo "=========================================="

# Wait for the user to interrupt
wait

