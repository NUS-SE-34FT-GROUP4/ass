#!/usr/bin/env bash
# Smoke test for product search, end to end through the product events:
# create a product -> product.created -> RabbitMQ -> index -> searchable.
#
# Usage: scripts/smoke-search.sh [BASE_URL]      (default http://localhost:8080)
#
# Read-only checks (health, search endpoint) always run. The write checks
# (create, update, delete) need a signed-in user:
#   SMOKE_TOKEN       a JWT to use as is, or else
#   SMOKE_USER        account to sign in with (default smoke_search; registered
#   SMOKE_PASSWORD    if it does not exist yet; default Smoke#Search1)
#   REDIS_CONTAINER   container to read the login captcha from
#                     (default c2csectrade-redis, i.e. local docker compose)
# Without a token and without that container the write checks are skipped.
#
# Products are created under a random marker word, so the checks work on a
# database that already has data, and are deleted again at the end.
# Exits non-zero if any check fails. Needs curl and jq.

set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
BASE_URL="${BASE_URL%/}"
SMOKE_USER="${SMOKE_USER:-smoke_search}"
SMOKE_PASSWORD="${SMOKE_PASSWORD:-Smoke#Search1}"
REDIS_CONTAINER="${REDIS_CONTAINER:-c2csectrade-redis}"
INDEX_TIMEOUT=20   # seconds to wait for an event to reach the index

MARKER="smoke$(date +%s)$RANDOM"
PASSED=0
FAILED=0
CREATED_IDS=()
TOKEN="${SMOKE_TOKEN:-}"

pass() { echo "  PASS  $1"; PASSED=$((PASSED + 1)); }
fail() { echo "  FAIL  $1"; FAILED=$((FAILED + 1)); }

# GET /api/products/search with the given query string; prints the JSON body
search() {
    curl -sf "$BASE_URL/api/products/search?$1"
}

# Product ids returned by a search, sorted and space-separated
search_ids() {
    search "$1" | jq -r '[.[].productId] | sort | map(tostring) | join(" ")'
}

# Polls a search until its ids equal the expected ones or the timeout passes
wait_for_ids() {
    local query="$1" expected="$2" actual="" waited=0
    while [ "$waited" -lt "$INDEX_TIMEOUT" ]; do
        actual="$(search_ids "$query" || true)"
        [ "$actual" = "$expected" ] && return 0
        sleep 1
        waited=$((waited + 1))
    done
    echo "        expected [$expected], got [$actual] for ?$query" >&2
    return 1
}

# Prints "<id> <code>" for a new captcha read back from Redis
captcha() {
    local id code
    id="$(curl -sf -D - -o /dev/null "$BASE_URL/api/captcha/generate" \
        | awk -F': ' 'tolower($1) == "captcha-id" { print $2 }' | tr -d '\r')"
    code="$(docker exec "$REDIS_CONTAINER" redis-cli --raw GET "captcha:$id" | tr -d '"')"
    echo "$id $code"
}

login() {
    local id code body
    read -r id code < <(captcha)
    body="$(jq -n --arg u "$SMOKE_USER" --arg p "$SMOKE_PASSWORD" --arg i "$id" --arg c "$code" \
        '{username: $u, password: $p, captchaId: $i, captchaCode: $c}')"
    curl -sf -X POST "$BASE_URL/api/auth/login" -H 'Content-Type: application/json' -d "$body" \
        | jq -r '.token // empty'
}

register() {
    local id code body
    read -r id code < <(captcha)
    body="$(jq -n --arg u "$SMOKE_USER" --arg p "$SMOKE_PASSWORD" --arg i "$id" --arg c "$code" \
        '{username: $u, displayName: "Smoke test", email: ($u + "@example.com"),
          password: $p, captchaId: $i, captchaCode: $c}')"
    curl -sf -o /dev/null -X POST "$BASE_URL/api/auth/register" -H 'Content-Type: application/json' -d "$body"
}

# Sends product JSON as the multipart "productData" part; prints the response
send_product() {
    local method="$1" path="$2" json="$3"
    curl -sf -X "$method" "$BASE_URL$path" -H "Authorization: Bearer $TOKEN" \
        -F "productData=$json;type=application/json"
}

product_json() {
    jq -n --arg name "$1" --arg desc "$2" --argjson price "$3" \
        '{name: $name, description: $desc, price: $price, conditionLevel: 4,
          location: "Kent Ridge", category: "Books", stock: 1}'
}

cleanup() {
    local id
    for id in ${CREATED_IDS[@]+"${CREATED_IDS[@]}"}; do
        curl -sf -o /dev/null -X DELETE "$BASE_URL/api/products/$id" -H "Authorization: Bearer $TOKEN" || true
    done
}
trap cleanup EXIT

echo "Search smoke test against $BASE_URL (marker: $MARKER)"

echo "Read-only checks"
if curl -sf "$BASE_URL/actuator/health" | jq -e '.status == "UP"' > /dev/null; then
    pass "health is UP"
else
    fail "health is not UP"
fi
if search "keyword=$MARKER" | jq -e 'type == "array" and length == 0' > /dev/null; then
    pass "search answers with an empty list for an unknown word"
else
    fail "search endpoint did not answer with a JSON list"
fi

if [ -z "$TOKEN" ] && docker inspect "$REDIS_CONTAINER" > /dev/null 2>&1; then
    TOKEN="$(login || true)"
    if [ -z "$TOKEN" ]; then
        register || true
        TOKEN="$(login || true)"
    fi
fi

if [ -z "$TOKEN" ]; then
    echo "Write checks skipped: no SMOKE_TOKEN and no Redis container $REDIS_CONTAINER to sign in with"
else
    echo "Write checks"
    # A: name and description match, price 25. B: description only, price 60.
    A="$(send_product POST /api/products "$(product_json "$MARKER textbook" "Calculus, light notes" 25)" | jq -r '.id // empty' || true)"
    B="$(send_product POST /api/products "$(product_json "Desk lamp" "Good for reading a $MARKER at night" 60)" | jq -r '.id // empty' || true)"
    [ -n "$A" ] && CREATED_IDS+=("$A")
    [ -n "$B" ] && CREATED_IDS+=("$B")

    if [ -z "$A" ] || [ -z "$B" ]; then
        fail "create products"
    else
        expected="$(printf '%s\n' "$A" "$B" | sort | tr '\n' ' ' | sed 's/ $//')"
        if wait_for_ids "keyword=$MARKER" "$expected"; then
            pass "new products are searchable"
        else
            fail "new products are searchable"
        fi

        hit="$(search "keyword=$MARKER" | jq -r --argjson a "$A" '.[] | select(.productId == $a) | .highlightedName' || true)"
        if [ "$hit" = "<em class='highlight'>$MARKER</em> textbook" ]; then
            pass "keyword is highlighted"
        else
            fail "keyword is highlighted (got: $hit)"
        fi

        if [ "$(search_ids "keyword=$MARKER&minPrice=20&maxPrice=30")" = "$A" ]; then
            pass "price range filter"
        else
            fail "price range filter"
        fi

        send_product PUT "/api/products/$A" "$(product_json "$MARKER textbook" "Calculus, light notes" 45)" > /dev/null || true
        if wait_for_ids "keyword=$MARKER&minPrice=40&maxPrice=50" "$A"; then
            pass "update reaches the index"
        else
            fail "update reaches the index"
        fi

        curl -sf -o /dev/null -X DELETE "$BASE_URL/api/products/$B" -H "Authorization: Bearer $TOKEN" || true
        if wait_for_ids "keyword=$MARKER" "$A"; then
            pass "delete reaches the index"
        else
            fail "delete reaches the index"
        fi
    fi
fi

echo "$PASSED passed, $FAILED failed"
[ "$FAILED" -eq 0 ]
