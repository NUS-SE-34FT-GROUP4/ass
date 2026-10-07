#!/bin/bash

# API test script - checks every feature works

echo "=========================================="
echo "  API Test Script"
echo "=========================================="
echo ""

BASE_URL="http://localhost:8080/api"

# Colours
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Test function
test_endpoint() {
    local name=$1
    local url=$2
    local method=$3
    local expected_status=$4

    echo -n "Testing $name ... "

    if [ "$method" == "GET" ]; then
        status=$(curl -s -o /dev/null -w "%{http_code}" "$url")
    else
        status=$(curl -s -o /dev/null -w "%{http_code}" -X "$method" "$url")
    fi

    if [ "$status" == "$expected_status" ]; then
        echo -e "${GREEN}✓ OK${NC} (HTTP $status)"
    else
        echo -e "${RED}✗ Failed${NC} (expected $expected_status, got $status)"
    fi
}

echo "1. Testing core services..."
echo "----------------------------"
test_endpoint "Health check" "$BASE_URL/../actuator/health" "GET" "200"
test_endpoint "Get captcha" "$BASE_URL/captcha/image" "GET" "200"

echo ""
echo "2. Testing the product API..."
echo "----------------------------"
test_endpoint "List products" "$BASE_URL/products" "GET" "200"
test_endpoint "Search products" "$BASE_URL/products/search" "GET" "200"

echo ""
echo "3. Testing external services..."
echo "----------------------------"
echo -n "Testing MySQL ... "
if docker exec c2c_mysql mysql -uroot -pQsycl741 -e "SELECT 1" > /dev/null 2>&1; then
    echo -e "${GREEN}✓ Connected${NC}"
else
    echo -e "${RED}✗ Connection failed${NC}"
fi

echo -n "Testing Redis ... "
if docker exec c2c_redis redis-cli ping > /dev/null 2>&1; then
    echo -e "${GREEN}✓ Connected${NC}"
else
    echo -e "${RED}✗ Connection failed${NC}"
fi

echo -n "Testing MinIO ... "
minio_status=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:9000/minio/health/live")
if [ "$minio_status" == "200" ]; then
    echo -e "${GREEN}✓ Running${NC}"
else
    echo -e "${RED}✗ Connection failed${NC}"
fi

echo -n "Testing Elasticsearch ... "
es_status=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:9200")
if [ "$es_status" == "200" ]; then
    echo -e "${GREEN}✓ Running${NC}"
    # Check the index
    echo -n "  - Checking the products index ... "
    index_exists=$(curl -s "http://localhost:9200/products" | grep -o "products")
    if [ -n "$index_exists" ]; then
        echo -e "${GREEN}✓ Index exists${NC}"
    else
        echo -e "${YELLOW}⚠ Index missing (normal on first run)${NC}"
    fi
else
    echo -e "${RED}✗ Connection failed${NC}"
fi

echo ""
echo "4. Testing database tables..."
echo "----------------------------"
echo -n "Testing users table ... "
if docker exec c2c_mysql mysql -uroot -pQsycl741 trade -e "SELECT COUNT(*) FROM users" > /dev/null 2>&1; then
    echo -e "${GREEN}✓ Table exists${NC}"
else
    echo -e "${RED}✗ Table missing${NC}"
fi

echo -n "Testing pms_product table ... "
if docker exec c2c_mysql mysql -uroot -pQsycl741 trade -e "SELECT COUNT(*) FROM pms_product" > /dev/null 2>&1; then
    echo -e "${GREEN}✓ Table exists${NC}"
else
    echo -e "${RED}✗ Table missing${NC}"
fi

echo -n "Testing pms_product_media table ... "
if docker exec c2c_mysql mysql -uroot -pQsycl741 trade -e "SELECT COUNT(*) FROM pms_product_media" > /dev/null 2>&1; then
    echo -e "${GREEN}✓ Table exists${NC}"
else
    echo -e "${RED}✗ Table missing${NC}"
fi

echo ""
echo "5. Frontend..."
echo "----------------------------"
echo -n "Testing frontend ... "
frontend_status=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:8081")
if [ "$frontend_status" == "200" ]; then
    echo -e "${GREEN}✓ Running${NC}"
else
    echo -e "${YELLOW}⚠ Not running or not fully started${NC}"
fi

echo ""
echo "=========================================="
echo "  Tests complete!"
echo "=========================================="
echo ""
echo "📋 Quick links:"
echo "  - Frontend: http://localhost:8081"
echo "  - Backend: http://localhost:8080"
echo "  - MinIO: http://localhost:9001"
echo ""
echo "👤 Default account: admin / admin123"
echo ""

