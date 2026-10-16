# Staging integration test, 16 Oct 2026

## Result

**29 passed, 0 failed.** Every cross-service check passed on staging, including the delisting flow (Search removes the listing and the seller is notified through Chat).

| Item | Value |
|---|---|
| Environment | staging, `https://dxjzlpbh4ve2y.cloudfront.net` (CloudFront → ALB → ECS) |
| Script | `scripts/integration-test.sh` from `main` at `a08d567` (code freeze tag `code-freeze-2026-10-15`) |
| Run | 16 Oct 2026, 17:30:02–17:30:21 (+08:00), 19 seconds |
| Accounts | seller `sharon_seller`, buyer `sharon_buyer` (both ordinary accounts registered for this test), administrator `admin` |
| Mode | token mode (`SELLER_TOKEN`, `BUYER_TOKEN`, `ADMIN_TOKEN`), `SKIP_HEALTH=1` |
| Test marker | `it179214300218854` (name prefix of the test listing) |

## Why 29 and not 32

The full script has 32 checks. On staging the three `/actuator/health` checks are skipped (`SKIP_HEALTH=1`), because the health endpoints are not exposed behind the load balancer. The ECS target groups check service health instead. The other 29 checks ran.

## Checks

| Group | Checks | What it shows |
|---|---|---|
| Sign-in | 1 | Tokens for seller, buyer and administrator are accepted |
| Chat authentication | 3 | Chat accepts a token issued by Core, refuses a request without one, and refuses a system message from a non-administrator |
| Routing | 4 | CloudFront and the ALB send `/api/search` to Search, `/api/chat` to Chat and other `/api` paths to Core; Search's internal rebuild is not exposed |
| Listing | 2 | A new listing (stock 2) becomes searchable through the product event |
| Payment | 7 | An order starts PENDING and reserves no stock; paying moves it to PAID and deducts stock once; a paid order cannot be paid again or cancelled |
| Cancellation | 6 | An order from the cart can be cancelled; cancelling leaves the cart and stock unchanged; a cancelled order cannot be paid |
| System message | 2 | An administrator's system message reaches the seller's chat history |
| Delisting | 4 | Buyer reports the listing; administrator delists it; the seller is notified through Chat (`product.delisted`); the listing leaves the search results |

## Side effects on staging

The test left one delisted listing (`it179214300218854 lamp`), two orders (one paid, one cancelled), one report and one system message. They belong to `sharon_seller` and `sharon_buyer` only. The buyer's payment password is now `135790`. The demo seed accounts were not used.

## Not covered

- Health endpoints (skipped, see above).
- Shipping, confirming receipt and releasing escrow.
- Production. Run the same command there only if test data on production is acceptable.

## How to repeat

Sign in on staging as each user and copy `localStorage.jwt_token` from the browser, then:

```bash
url=https://dxjzlpbh4ve2y.cloudfront.net
CORE_URL=$url SEARCH_URL=$url CHAT_URL=$url GATEWAY_URL=$url SKIP_HEALTH=1 \
SELLER_TOKEN=... BUYER_TOKEN=... ADMIN_TOKEN=... scripts/integration-test.sh
```

Tokens expire one hour after sign-in.

## Output

```
Sign-in
  PASS  seller, buyer and administrator can sign in
Chat authentication
  PASS  Chat accepts a token issued by Core
  PASS  Chat refuses a request without a token
  PASS  Chat refuses a system message from a non-administrator
Routing
  PASS  the proxy sends /api/search to Search
  PASS  the proxy sends /api/chat to Chat
  PASS  the proxy sends other /api paths to Core
  PASS  the proxy does not expose Search's internal rebuild
Listing
  PASS  seller creates a listing with stock 2
  PASS  the listing becomes searchable
Payment
  PASS  buyer creates an order, which is PENDING
  PASS  an unpaid order reserves no stock
  PASS  paying moves the order to PAID
  PASS  paying deducts the stock
  PASS  a paid order cannot be paid again
  PASS  a paid order cannot be cancelled
  PASS  the refused payment and cancellation leave the stock alone
Cancellation
  PASS  the listing is in the buyer's cart
  PASS  buyer creates an order from the cart
  PASS  cancelling moves the order to CANCELED
  PASS  cancelling leaves the cart quantity at 1
  PASS  cancelling leaves the stock alone
  PASS  a cancelled order cannot be paid
System message
  PASS  administrator sends a system message through Chat
  PASS  the system message is in the seller's chat history
Delisting
  PASS  buyer reports the listing
  PASS  administrator delists the listing
  PASS  the seller is notified through Chat (product.delisted)
  PASS  the delisted listing leaves the search results
29 passed, 0 failed
```
