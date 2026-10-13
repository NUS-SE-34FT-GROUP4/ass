#!/usr/bin/env bash
# Switches the local docker compose stack to per-service database users, as on
# AWS: creates core_svc, search_svc and chat_svc with infra/db/grants.sql, then
# restarts Core, Search and Chat so that each logs in as its own user.
#
# Usage: scripts/use-service-db-users.sh        (the stack must be running)
#
# The passwords are generated on each run and exist only in the containers'
# environment. To go back to the shared user: docker compose up -d
# Needs docker and curl.

set -euo pipefail

cd "$(dirname "$0")/.."

MYSQL_CONTAINER="${MYSQL_CONTAINER:-c2csectrade-mysql}"
MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-rootpassword}"
START_TIMEOUT=180   # seconds to wait for the three services to come back

CORE_SVC_PASSWORD="$(openssl rand -hex 16)"
SEARCH_SVC_PASSWORD="$(openssl rand -hex 16)"
CHAT_SVC_PASSWORD="$(openssl rand -hex 16)"
export CORE_SVC_PASSWORD SEARCH_SVC_PASSWORD CHAT_SVC_PASSWORD

echo "Creating the service users with infra/db/grants.sql"
sed -e "s/@@core_svc_password@@/$CORE_SVC_PASSWORD/g" \
    -e "s/@@search_svc_password@@/$SEARCH_SVC_PASSWORD/g" \
    -e "s/@@chat_svc_password@@/$CHAT_SVC_PASSWORD/g" infra/db/grants.sql \
    | docker exec -i -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" "$MYSQL_CONTAINER" mysql -uroot

echo "Restarting Core, Search and Chat as core_svc, search_svc and chat_svc"
docker compose -f compose.yaml -f compose.service-users.yaml up -d --no-build backend search chat

for port in 8080 8081 8082; do
    waited=0
    until curl -sf "http://localhost:$port/actuator/health" | grep -q '"status":"UP"'; do
        if [ "$waited" -ge "$START_TIMEOUT" ]; then
            echo "The service on port $port is not UP after ${START_TIMEOUT}s" >&2
            exit 1
        fi
        sleep 3
        waited=$((waited + 3))
    done
    echo "  port $port is UP"
done

# nginx resolved the service names when it started; the recreated containers
# have new addresses
if docker inspect c2csectrade-frontend > /dev/null 2>&1; then
    docker restart c2csectrade-frontend > /dev/null
    echo "  frontend proxy restarted"
fi
