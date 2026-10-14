#!/usr/bin/env bash
# Integration test across Core, Search and Chat on the local docker compose
# stack. It covers what crosses a service boundary or a transaction:
#
#   - a token issued by Core is accepted by Chat
#   - an order leaves PENDING once; stock moves only at payment; cancelling
#     leaves the cart alone
#   - delisting a product notifies the seller through Chat (product.delisted)
#     and removes the product from search
#   - an administrator's system message reaches the user's chat history
#   - the frontend proxy sends each path to the right service, as the load
#     balancer does on AWS
#
# Product indexing itself is covered by scripts/smoke-search.sh.
#
# Usage: scripts/integration-test.sh
#   CORE_URL, SEARCH_URL, CHAT_URL   default http://localhost:8080, :8081, :8082
#   GATEWAY_URL                      the frontend proxy (default http://localhost);
#                                    its checks are skipped if it does not answer
#   REDIS_CONTAINER                  container to read login captchas from
#                                    (default c2csectrade-redis)
#   ADMIN_USER, ADMIN_PASSWORD       default admin / admin123 (seeded by init.sql)
#   SELLER_TOKEN, BUYER_TOKEN,       JWTs to use as they are instead of signing in;
#   ADMIN_TOKEN                      needed where the captcha cannot be read from
#                                    Redis. Seller and buyer must be two different
#                                    ordinary accounts; ADMIN_TOKEN an administrator.
#   SKIP_HEALTH=1                    skip the /actuator/health checks (not exposed
#                                    behind the load balancer)
#
# On staging every service sits behind one address. Sign in on the site as each
# user and copy localStorage "jwt_token" from the browser, then:
#   url=https://<staging address>
#   CORE_URL=$url SEARCH_URL=$url CHAT_URL=$url GATEWAY_URL=$url SKIP_HEALTH=1 \
#   SELLER_TOKEN=... BUYER_TOKEN=... ADMIN_TOKEN=... scripts/integration-test.sh
# The buyer's payment password is set to 135790 if it has none; a buyer with a
# different one fails the payment checks. Delisting changes the test product only.
#
# Run scripts/use-service-db-users.sh first to test with the per-service
# database users. Exits non-zero if any check fails. Needs curl and jq, and
# docker unless all three tokens are given.

set -uo pipefail

CORE_URL="${CORE_URL:-http://localhost:8080}"
SEARCH_URL="${SEARCH_URL:-http://localhost:8081}"
CHAT_URL="${CHAT_URL:-http://localhost:8082}"
GATEWAY_URL="${GATEWAY_URL:-http://localhost}"
REDIS_CONTAINER="${REDIS_CONTAINER:-c2csectrade-redis}"
ADMIN_USER="${ADMIN_USER:-admin}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-admin123}"
TEST_PASSWORD='Integration#Test1'
PAYMENT_PASSWORD='135790'
EVENT_TIMEOUT=20   # seconds to wait for an event to take effect

MARKER="it$(date +%s)$RANDOM"
PASSED=0
FAILED=0
PRODUCT=""
SELLER_TOKEN="${SELLER_TOKEN:-}"
BUYER_TOKEN="${BUYER_TOKEN:-}"
ADMIN_TOKEN="${ADMIN_TOKEN:-}"

pass() { echo "  PASS  $1"; PASSED=$((PASSED + 1)); }
fail() { echo "  FAIL  $1"; FAILED=$((FAILED + 1)); }
# check "<name>" <command...>: passes if the command succeeds
check() {
    local name="$1"
    shift
    if "$@"; then pass "$name"; else fail "$name"; fi
}

# api <token> <method> <url> [json]: prints the body, then the status on a last line
api() {
    local token="$1" method="$2" url="$3" body="${4:-}"
    local args=(-s -w '\n%{http_code}' -X "$method" "$url")
    [ -n "$token" ] && args+=(-H "Authorization: Bearer $token")
    [ -n "$body" ] && args+=(-H 'Content-Type: application/json' -d "$body")
    curl "${args[@]}"
}
status_of() { tail -n 1 <<< "$1"; }
body_of() { sed '$d' <<< "$1"; }
# ok <response>: the status is 2xx
ok() { [[ "$(status_of "$1")" == 2* ]]; }

captcha() {
    local id code
    id="$(curl -sf -D - -o /dev/null "$CORE_URL/api/captcha/generate" \
        | awk -F': ' 'tolower($1) == "captcha-id" { print $2 }' | tr -d '\r')"
    code="$(docker exec "$REDIS_CONTAINER" redis-cli --raw GET "captcha:$id" | tr -d '"')"
    echo "$id $code"
}

login() {
    local user="$1" password="$2" id code body
    read -r id code < <(captcha)
    body="$(jq -n --arg u "$user" --arg p "$password" --arg i "$id" --arg c "$code" \
        '{username: $u, password: $p, captchaId: $i, captchaCode: $c}')"
    curl -sf -X POST "$CORE_URL/api/auth/login" -H 'Content-Type: application/json' -d "$body" \
        | jq -r '.token // empty'
}

register() {
    local user="$1" id code body
    read -r id code < <(captcha)
    body="$(jq -n --arg u "$user" --arg p "$TEST_PASSWORD" --arg i "$id" --arg c "$code" \
        '{username: $u, displayName: $u, email: ($u + "@example.com"),
          password: $p, captchaId: $i, captchaCode: $c}')"
    curl -sf -o /dev/null -X POST "$CORE_URL/api/auth/register" -H 'Content-Type: application/json' -d "$body"
}

# jwt_subject <token>: prints the username the token was issued to
jwt_subject() {
    local payload
    payload="$(cut -d. -f2 <<< "$1" | tr '_-' '/+')"
    while [ $(( ${#payload} % 4 )) -ne 0 ]; do payload="$payload="; done
    base64 -d <<< "$payload" 2> /dev/null | jq -r '.sub // empty'
}

# Signs in, registering the account first if it does not exist yet
token_for() {
    local user="$1" token
    token="$(login "$user" "$TEST_PASSWORD" || true)"
    if [ -z "$token" ]; then
        register "$user" || true
        token="$(login "$user" "$TEST_PASSWORD" || true)"
    fi
    echo "$token"
}

stock() { curl -sf "$CORE_URL/api/products/$PRODUCT" | jq -r '.stock'; }
order_status() { body_of "$1" | jq -r '.status // empty'; }
cart_quantity() {
    body_of "$(api "$BUYER_TOKEN" GET "$CORE_URL/api/cart/list")" \
        | jq -r --argjson p "$PRODUCT" '[.. | objects | select((.productId? // .product?.id?) == $p) | .quantity] | first // 0'
}
search_has_product() {
    curl -sf "$SEARCH_URL/api/search?keyword=$MARKER" | jq -e --argjson p "$PRODUCT" 'any(.[]; .productId == $p)' > /dev/null
}
system_history_has() {
    body_of "$(api "$SELLER_TOKEN" GET "$CHAT_URL/api/chat/history/System?limit=50")" \
        | jq -e --arg t "$1" 'any(.[]; .content | contains($t))' > /dev/null
}
# wait_until <command...>: retries until it succeeds or the timeout passes
wait_until() {
    local waited=0
    until "$@"; do
        [ "$waited" -ge "$EVENT_TIMEOUT" ] && return 1
        sleep 1
        waited=$((waited + 1))
    done
}
wait_while() {
    local waited=0
    while "$@"; do
        [ "$waited" -ge "$EVENT_TIMEOUT" ] && return 1
        sleep 1
        waited=$((waited + 1))
    done
}

cleanup() {
    [ -n "$PRODUCT" ] && [ -n "$SELLER_TOKEN" ] \
        && curl -s -o /dev/null -X DELETE "$CORE_URL/api/products/$PRODUCT" -H "Authorization: Bearer $SELLER_TOKEN"
}
trap cleanup EXIT

echo "Integration test: Core $CORE_URL, Search $SEARCH_URL, Chat $CHAT_URL (marker: $MARKER)"

echo "Services"
if [ "${SKIP_HEALTH:-}" = 1 ]; then
    echo "  skipped: SKIP_HEALTH=1"
else
    for url in "$CORE_URL" "$SEARCH_URL" "$CHAT_URL"; do
        check "health is UP at $url" eval "curl -sf '$url/actuator/health' | jq -e '.status == \"UP\"' > /dev/null"
    done
fi

echo "Sign-in"
SELLER="it_seller"
BUYER="it_buyer"
if [ -n "$SELLER_TOKEN" ]; then SELLER="$(jwt_subject "$SELLER_TOKEN")"; else SELLER_TOKEN="$(token_for "$SELLER")"; fi
[ -n "$BUYER_TOKEN" ] || BUYER_TOKEN="$(token_for "$BUYER")"
[ -n "$ADMIN_TOKEN" ] || ADMIN_TOKEN="$(login "$ADMIN_USER" "$ADMIN_PASSWORD" || true)"
check "seller, buyer and administrator can sign in" test -n "$SELLER_TOKEN" -a -n "$BUYER_TOKEN" -a -n "$ADMIN_TOKEN"
if [ -z "$SELLER_TOKEN" ] || [ -z "$BUYER_TOKEN" ] || [ -z "$ADMIN_TOKEN" ]; then
    echo "$PASSED passed, $((FAILED)) failed; cannot continue without tokens"
    exit 1
fi

echo "Chat authentication"
response="$(api "$BUYER_TOKEN" GET "$CHAT_URL/api/chat/unread-count")"
check "Chat accepts a token issued by Core" ok "$response"
response="$(api "" GET "$CHAT_URL/api/chat/unread-count")"
check "Chat refuses a request without a token" test "$(status_of "$response")" = 401 -o "$(status_of "$response")" = 403
response="$(api "$BUYER_TOKEN" POST "$CHAT_URL/api/chat/admin/send-system-message" \
    "$(jq -n --arg r "$SELLER" '{recipient: $r, content: "not allowed"}')")"
check "Chat refuses a system message from a non-administrator" test "$(status_of "$response")" = 403

echo "Routing"
if curl -sf -o /dev/null -m 5 "$GATEWAY_URL/"; then
    response="$(api "" GET "$GATEWAY_URL/api/search?keyword=$MARKER")"
    check "the proxy sends /api/search to Search" eval 'ok "$response" && body_of "$response" | jq -e "type == \"array\"" > /dev/null'
    response="$(api "$BUYER_TOKEN" GET "$GATEWAY_URL/api/chat/unread-count")"
    check "the proxy sends /api/chat to Chat" ok "$response"
    response="$(api "" GET "$GATEWAY_URL/api/products")"
    check "the proxy sends other /api paths to Core" ok "$response"
    response="$(api "" POST "$GATEWAY_URL/internal/reindex")"
    check "the proxy does not expose Search's internal rebuild" eval '! ok "$response"'
else
    echo "  skipped: no answer from $GATEWAY_URL"
fi

echo "Listing"
product_json="$(jq -n --arg name "$MARKER lamp" \
    '{name: $name, description: "Integration test listing", price: 10, conditionLevel: 4,
      location: "Kent Ridge", category: "Home", stock: 2}')"
PRODUCT="$(curl -sf -X POST "$CORE_URL/api/products" -H "Authorization: Bearer $SELLER_TOKEN" \
    -F "productData=$product_json;type=application/json" | jq -r '.id // empty')"
check "seller creates a listing with stock 2" test -n "$PRODUCT"
if [ -z "$PRODUCT" ]; then
    echo "$PASSED passed, $FAILED failed; cannot continue without a listing"
    exit 1
fi
check "the listing becomes searchable" wait_until search_has_product

echo "Payment"
response="$(api "$BUYER_TOKEN" GET "$CORE_URL/api/users/payment-password/check")"
if ! body_of "$response" | jq -e '[.. | booleans] | any' > /dev/null 2>&1; then
    api "$BUYER_TOKEN" POST "$CORE_URL/api/users/payment-password/set" \
        "$(jq -n --arg p "$PAYMENT_PASSWORD" '{password: $p, confirmPassword: $p}')" > /dev/null
fi
pay_body="$(jq -n --arg p "$PAYMENT_PASSWORD" '{paymentMethod: "alipay", password: $p}')"

response="$(api "$BUYER_TOKEN" POST "$CORE_URL/api/orders/buy-now" "$(jq -n --argjson p "$PRODUCT" '{productId: $p, quantity: 1}')")"
ORDER="$(body_of "$response" | jq -r '.id // empty')"
check "buyer creates an order, which is PENDING" test "$(order_status "$response")" = PENDING
check "an unpaid order reserves no stock" test "$(stock)" = 2
response="$(api "$BUYER_TOKEN" POST "$CORE_URL/api/orders/$ORDER/pay" "$pay_body")"
check "paying moves the order to PAID" test "$(order_status "$response")" = PAID
check "paying deducts the stock" test "$(stock)" = 1
response="$(api "$BUYER_TOKEN" POST "$CORE_URL/api/orders/$ORDER/pay" "$pay_body")"
check "a paid order cannot be paid again" eval '! ok "$response"'
response="$(api "$BUYER_TOKEN" POST "$CORE_URL/api/orders/$ORDER/cancel")"
check "a paid order cannot be cancelled" eval '! ok "$response"'
check "the refused payment and cancellation leave the stock alone" test "$(stock)" = 1

echo "Cancellation"
api "$BUYER_TOKEN" POST "$CORE_URL/api/cart/add" "$(jq -n --argjson p "$PRODUCT" '{productId: $p, quantity: 1}')" > /dev/null
check "the listing is in the buyer's cart" test "$(cart_quantity)" = 1
response="$(api "$BUYER_TOKEN" POST "$CORE_URL/api/orders")"
ORDER="$(body_of "$response" | jq -r '.id // empty')"
check "buyer creates an order from the cart" test "$(order_status "$response")" = PENDING
response="$(api "$BUYER_TOKEN" POST "$CORE_URL/api/orders/$ORDER/cancel")"
check "cancelling moves the order to CANCELED" test "$(order_status "$response")" = CANCELED
check "cancelling leaves the cart quantity at 1" test "$(cart_quantity)" = 1
check "cancelling leaves the stock alone" test "$(stock)" = 1
response="$(api "$BUYER_TOKEN" POST "$CORE_URL/api/orders/$ORDER/pay" "$pay_body")"
check "a cancelled order cannot be paid" eval '! ok "$response"'
api "$BUYER_TOKEN" DELETE "$CORE_URL/api/cart/remove/$PRODUCT" > /dev/null

echo "System message"
note="$MARKER notice"
response="$(api "$ADMIN_TOKEN" POST "$CHAT_URL/api/chat/admin/send-system-message" \
    "$(jq -n --arg r "$SELLER" --arg c "$note" '{recipient: $r, content: $c}')")"
check "administrator sends a system message through Chat" ok "$response"
check "the system message is in the seller's chat history" system_history_has "$note"

echo "Delisting"
response="$(api "$BUYER_TOKEN" POST "$CORE_URL/api/reports" \
    "$(jq -n --argjson p "$PRODUCT" '{productId: $p, reason: "Other", description: "Integration test report"}')")"
check "buyer reports the listing" ok "$response"
REPORT="$(body_of "$(api "$ADMIN_TOKEN" GET "$CORE_URL/api/admin/reports")" \
    | jq -r --argjson p "$PRODUCT" '[.[] | select(.productId == $p) | .id] | first // empty')"
response="$(api "$ADMIN_TOKEN" PUT "$CORE_URL/api/admin/products/$PRODUCT/delist?reportId=$REPORT")"
check "administrator delists the listing" ok "$response"
check "the seller is notified through Chat (product.delisted)" wait_until system_history_has "$MARKER lamp"
check "the delisted listing leaves the search results" wait_while search_has_product

echo "$PASSED passed, $FAILED failed"
[ "$FAILED" -eq 0 ]
