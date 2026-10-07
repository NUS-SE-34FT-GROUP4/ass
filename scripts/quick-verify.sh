#!/bin/bash
echo "Waiting for containers to start..."
sleep 30

echo -e "\n=== Testing the captcha endpoint ==="
curl -v http://localhost:8080/api/captcha/generate 2>&1 | head -20

echo -e "\n\n=== Backend logs ==="
docker logs c2csectrade-backend --tail 30 2>&1 | tail -20

