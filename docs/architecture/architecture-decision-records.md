# Architecture Decision Records

- C2C SecTrade, SWE5001 Team 4, 15 October 2026. These records expand the eight decisions AD-01 to AD-08 behind the logical architecture in Figure 3-1 (`figure-3-1-logical-architecture.png`).
- They describe `main` at commit `26394bb`.
- Each record gives the context, the decision, the alternatives and why they were not chosen, and the consequences, including the costs the team accepted.

## AD-01 Three services, split by scaling profile and transaction boundary

**Context.** The lecturer's review of the first proposal asked for a horizontally scalable, cloud-native microservice system. The existing application was one Spring Boot deployment covering users, listings, search, cart, orders, payment, bargaining, chat, reviews and recommendation. The team had two weeks, from 7 to 21 October.

**Decision.** Run three services: Core, Search and Chat. Search and Chat were extracted because their load differs most from the rest. Search is read-heavy and can be served from an index alone. Chat holds long-lived WebSocket connections, so its load follows connected users rather than request rate. Neither takes part in a purchase.

**Alternatives.**

- One service per business module (about eight). Not chosen: most modules join each other's tables in one transaction, so separating them needs sagas the team could not build and test in two weeks.
- Keep one deployment and scale it as a whole. Not chosen: a burst of searches would add copies of everything, and it does not meet the lecturer's requirement.

**Consequences.**

- Search and Chat each scale on their own policy; a search burst does not add Core or Chat tasks.
- Core is still large, and its modules cannot scale separately.
- The next candidates for extraction are review, credit and recommendation, which only react to completed orders.

## AD-02 A purchase completes inside one service and one local transaction

**Context.** Paying for an order checks the buyer's payment password and balance, deducts stock, debits the buyer, credits each seller, records a transaction and changes the order's state. If only some of these happen, money or stock is wrong.

**Decision.** Cart, order, payment and stock stay in Core and share one database transaction. An order leaves the pending state through an update that applies only while it is still pending, so of payment, cancellation and expiry exactly one succeeds; a payment that loses rolls back its stock and balance changes. Stock is deducted at payment, so an unpaid order reserves nothing.

**Alternatives.**

- Separate order and payment services coordinated by a saga. Not chosen: it needs compensating actions for every step and a way to recover half-finished purchases, for no scaling benefit at the project's load.
- Reserve stock when the order is created. Not chosen: expiry would then have to return stock, which is the source of the stock errors found and fixed in the earlier code.

**Consequences.**

- A purchase is consistent without any cross-service coordination.
- Order and payment cannot scale apart from the rest of Core.
- Two buyers can each hold an unpaid order for the last item; the second to pay is refused. This is the accepted cost of not reserving stock.

## AD-03 Changes travel as events; the one synchronous call has a timeout and a fallback

**Context.** When a listing changes, the search index must follow. When an administrator delists a listing, the seller must be told through chat. If Core called Search and Chat directly for these, a slow or failed Search or Chat would slow or fail the write in Core.

**Decision.** A change made in one service reaches another only as an event on RabbitMQ: Core publishes listing events after the database commit, and Search and Chat consume them. There is one synchronous call between services, for reading: Core's keyword search asks Search for the matching listings and then loads their details from MySQL, and Core passes an administrator's index rebuild on to Search. The search call times out after two seconds, after which Core searches MySQL directly.

**Alternatives.**

- No synchronous call at all, as the proposal stated: the web application would ask Search for matches and then ask Core for the details. Not chosen for the home page search, because it takes two requests from the browser and loses the database fallback. The web application's dedicated search does go straight to Search.
- Synchronous calls for writes as well. Not chosen, for the reason given in the context.

**Consequences.**

- A failed Search or Chat cannot fail a write in Core. Events wait in durable queues until the consumer returns.
- The search index is eventually consistent with the database.
- Keyword search through Core gives full results only when both services are up; otherwise it degrades to a plainer search of the database.
- This departs from the approved proposal and must be stated in the report.

## AD-04 One shared MySQL schema, one database user per service

**Context.** The three services read and write tables in one MySQL schema. Without a rule, any service could change any table, and "each service owns its data" would be a convention nobody could check.

**Decision.** Each service connects as its own database user with table-level grants. `core_svc` has full access to every table except `chat_message`, on which it has none. `chat_svc` can read, insert and update `chat_message` and can only read the three user tables. `search_svc` can only read the listing table. None can change the schema; the tables are created by the administrator account. Passwords are held in Secrets Manager and never appear in the repository.

**Alternatives.**

- A database per service. Not chosen: Chat needs usernames and roles, so it would need either a copy of the user data kept up to date by events, or a call to Core on every WebSocket handshake. It would also add two database instances to the cost.
- A shared schema with one shared user. Not chosen: ownership would not be enforced.

**Consequences.**

- The database refuses a query that crosses an ownership boundary, so a mistake shows up as an error in testing rather than as silent coupling.
- The services still share an instance and a schema: a schema change can affect more than one service, and a heavy query from one can slow the others.
- Moving a service to its own database later changes its connection settings, not the ownership rules.

## AD-05 The search index is a read model built from listing events

**Context.** Search must stay up to date with listings that Core owns, without reading Core's tables on every query and without Core knowing how the index works.

**Decision.** After each commit that creates, updates or deletes a listing, Core publishes an event carrying the fields the index needs. Search consumes these from a single queue and applies them. The queue has a single active consumer, so with several Search tasks running, events for a listing are still applied in the order they were published. As a fallback, Search can rebuild the whole index from the listing table: it runs when Search creates a new index at start-up and when an administrator asks for it.

**Alternatives.**

- Core writes to the index directly. Not chosen: a slow or failed index would then slow or fail the write, and Core would be tied to the index's structure.
- Search polls the listing table for changes. Not chosen: it adds load whether or not anything changed, and deletions are not visible to a poll.
- A separate queue for each event type. Not chosen: an update and a delete for the same listing could then be applied in the wrong order.

**Consequences.**

- A new or changed listing becomes searchable shortly after the commit rather than at once.
- An event that still fails after three delivery attempts is dropped; there is no dead-letter queue. Until the next rebuild, that listing is missing from or out of date in the index.
- The rebuild reads every listing, so its cost grows with the catalogue.

## AD-06 Chat delivers across instances through a fanout exchange

**Context.** A WebSocket connection belongs to one Chat instance. When there are several instances, the sender and the recipient of a message are usually connected to different ones.

**Decision.** The instance that receives a message saves it, then publishes it to a fanout exchange. Each instance has its own private queue bound to that exchange, so every instance receives every message, and each pushes it only to connections it holds for the target user.

**Alternatives.**

- Sticky sessions at the load balancer. Not chosen: they keep one user on one instance but do nothing to connect two users on different instances.
- A shared registry of which instance holds which user, with direct delivery. Not chosen: the registry must be kept correct as connections drop and instances are replaced, which is more to build and to get wrong.
- An external STOMP broker relay. Not chosen: it adds a component to run and secure for the same result at this scale.

**Consequences.**

- Chat instances can be added and removed freely; a new instance needs only its own queue.
- Every instance processes every message, so the work per message grows with the number of instances.
- A message is saved before it is published, so a recipient who is offline, or whose instance fails, finds it in the chat history.

## AD-07 Each service authenticates a request itself from the token

**Context.** Core and Chat serve requests that need a signed-in user. Search serves only public queries, and its rebuild endpoint is not reachable through the load balancer.

**Decision.** Core issues a signed JWT at sign-in. Core and Chat each validate the token themselves, with the token-handling code from the shared library and the same signing key. Chat also loads the user and their roles from the user tables, which it may only read.

**Alternatives.**

- A central authentication service called on every request. Not chosen: it adds a network call to every request and a single point of failure.
- Authentication at the load balancer only. Not chosen: a service would then trust any request that reached it, and Chat's WebSocket handshake needs the user's identity inside the service.

**Consequences.**

- No request calls another service to authenticate, so sign-in state does not limit scaling.
- The signing key is shared by two services; changing it means redeploying both.
- Search's rebuild endpoint has no authentication of its own and relies on the network: the load balancer does not route it, and only the services' security group can reach it.

## AD-08 One build, one shared library, one image per service

**Context.** The three services came from one code base. The messages they exchange must have the same format on both sides.

**Decision.** A Maven multi-module build with a `common` library and one application module per service. `common` holds the event classes, token handling and error handling. One Dockerfile builds each service's image, selected by a build argument, and the pipeline builds and scans the three images separately.

**Alternatives.**

- A repository per service. Not chosen: a change to an event would need coordinated releases across repositories, with five people and two weeks.
- Copy the event classes into each service. Not chosen: the publisher and the consumer could drift apart, and the failure would appear only at run time.

**Consequences.**

- A change to an event is compiled against its publisher and its consumers in the same build.
- A change in `common` rebuilds all three services.
- Chat still has its own copies of the user entity, user mapper and JWT filter, taken from Core when it was extracted. They should move into `common`.
