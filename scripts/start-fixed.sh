#!/bin/bash
# Full system start script, with all known issues fixed

set -e

echo "======================================"
echo "c2csectrade full start script"
echo "======================================"

# 1. Check and install frontend dependencies
echo ""
echo "[1/5] Checking frontend dependencies..."
cd frontend
if [ ! -d "node_modules" ] || [ ! -f "node_modules/@stomp/stompjs/package.json" ]; then
    echo "Installing frontend dependencies..."
    npm install
else
    echo "✓ Frontend dependencies installed"
fi
cd ..

# 2. Build the backend
echo ""
echo "[2/5] Building the backend..."
mvn clean package -DskipTests

# 3. Build the frontend
echo ""
echo "[3/5] Building the frontend..."
cd frontend
npm run build
cd ..

# 4. Stop existing containers
echo ""
echo "[4/5] Stopping existing Docker containers..."
docker compose down || true

# 5. Start all services
echo ""
echo "[5/5] Starting all services..."
docker compose up -d --build

# 6. Wait for services to start
echo ""
echo "Waiting for services to start..."
sleep 10

# 7. Check service status
echo ""
echo "======================================"
echo "Service status:"
echo "======================================"
docker compose ps

echo ""
echo "======================================"
echo "System started!"
echo "======================================"
echo ""
echo "URLs:"
echo "  Frontend: http://localhost"
echo "  Backend API: http://localhost:8080"
echo "  RabbitMQ management: http://localhost:15672 (admin/admin123)"
echo "  MinIO console: http://localhost:9001 (minioadmin/minioadmin)"
echo ""
echo "Default administrator account:"
echo "  Username: admin"
echo "  Password: admin123"
echo ""
echo "Logs:"
echo "  docker compose logs -f backend"
echo "  docker compose logs -f frontend"
echo ""

