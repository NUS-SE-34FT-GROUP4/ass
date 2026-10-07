#!/bin/bash
# Captcha endpoint diagnostics

echo "=========================================="
echo "Captcha endpoint diagnostics"
echo "=========================================="
echo ""

echo "1. Checking container status..."
docker ps --filter name=c2csectrade --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" | head -10

echo -e "\n2. Testing the backend captcha endpoint..."
echo "Request: http://localhost:8080/api/captcha/generate"
RESPONSE=$(curl -s -o /tmp/captcha_direct.png -w "%{http_code}|%{content_type}|%{size_download}" http://localhost:8080/api/captcha/generate)
HTTP_CODE=$(echo $RESPONSE | cut -d'|' -f1)
CONTENT_TYPE=$(echo $RESPONSE | cut -d'|' -f2)
SIZE=$(echo $RESPONSE | cut -d'|' -f3)

echo "HTTP status: $HTTP_CODE"
echo "Content-Type: $CONTENT_TYPE"
echo "Size: $SIZE bytes"
if [ "$HTTP_CODE" = "200" ]; then
    echo "✅ Backend endpoint OK"
    file /tmp/captcha_direct.png
else
    echo "❌ Backend endpoint problem, response body:"
    cat /tmp/captcha_direct.png
fi

echo -e "\n3. Testing the captcha endpoint through the Nginx proxy..."
echo "Request: http://localhost/api/captcha/generate"
RESPONSE2=$(curl -s -o /tmp/captcha_nginx.png -w "%{http_code}|%{content_type}|%{size_download}" http://localhost/api/captcha/generate)
HTTP_CODE2=$(echo $RESPONSE2 | cut -d'|' -f1)
CONTENT_TYPE2=$(echo $RESPONSE2 | cut -d'|' -f2)
SIZE2=$(echo $RESPONSE2 | cut -d'|' -f3)

echo "HTTP status: $HTTP_CODE2"
echo "Content-Type: $CONTENT_TYPE2"
echo "Size: $SIZE2 bytes"
if [ "$HTTP_CODE2" = "200" ]; then
    echo "✅ Nginx proxy OK"
    file /tmp/captcha_nginx.png
else
    echo "❌ Nginx proxy problem, response body:"
    cat /tmp/captcha_nginx.png
fi

echo -e "\n4. Recent backend logs (last 20 lines)..."
docker logs c2csectrade-backend --tail 20 2>&1 | tail -20

echo -e "\n5. Recent frontend Nginx logs (last 10 lines)..."
docker logs c2csectrade-frontend --tail 10 2>&1 | tail -10

echo -e "\n=========================================="
echo "Diagnostics complete"
echo "=========================================="

