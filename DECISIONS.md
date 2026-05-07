# Architectural Decision Record — Payment Reconciliation System

This document explains the key technical decisions made in this project, the reasoning behind each one, and the trade-offs considered. Decisions are ordered from the most structural to the most implementation-specific.

---

## 1. Hexagonal Architecture (Ports & Adapters)

**Decision:** The codebase is structured in three strict layers — `domain`, `application`, and `infrastructure` — with dependency arrows pointing inward only. No Spring annotations exist inside the domain.

**Why:**
The core requirement is that the reconciliation logic remains independent of whether data comes from a REST API, a SOAP service, a database, or a mock. Hexagonal architecture enforces that contract by making the domain speak only in terms of interfaces (ports). The application service (`ReconciliationService`) does not know that `LoadProcessorPaymentPort` is implemented by an HTTP client — it could be swapped for a file reader without changing a single line of business logic.

This also makes unit testing the business rules trivial: `ReconciliationServiceTest` and `PaymentComparisonKernel` tests run with zero Spring context, zero database, and zero network.

**Trade-off:** More files and more indirection than a layered MVC approach. Justified here because the reconciliation logic is the point of the system — it needs to be testable and portable.

---

## 2. Three-Way Reconciliation (Internal + JSON Processor + SOAP Processor)

**Decision:** The system fetches from three independent sources simultaneously and compares all three, producing 8 distinct reconciliation statuses.

**Why:**
Real payment ecosystems rarely have a single external processor. This system models the realistic scenario where a fintech operates a legacy SOAP integration alongside a modern REST integration. Checking only one external source would produce false positives: a payment can be correct in the JSON processor but corrupted in the SOAP processor, and the system must catch that.

The 8 statuses are: `CONCILIATED`, `DISCREPANCY_AMOUNT`, `DISCREPANCY_DATE`, `MULTIPLE_DISCREPANCIES`, `MISSING_IN_INTERNAL`, `MISSING_IN_PROCESSOR` (both absent), `MISSING_IN_JSON_PROCESSOR`, `MISSING_IN_SOAP_PROCESSOR`.

**Trade-off:** More complex decision logic in `PaymentComparisonKernel`. Mitigated by isolating the decision table in a pure-domain, statically tested class with no side effects.

---

## 3. Domain Kernel as a Pure Static Class

**Decision:** All comparison and status-resolution logic lives in `PaymentComparisonKernel` — a `final` class with only `static` methods and no Spring dependencies whatsoever.

**Why:**
Reconciliation logic is pure function: given three optional payments, produce a result. There is no state to manage, no dependency to inject. Making it a Spring `@Service` would add nothing and prevent testing it without a Spring context. A static utility class signals clearly to the reader: this is a deterministic pure function, not a bean.

The tolerance constants (`DATE_TOLERANCE_MINUTES = 5`, amount tolerance `0.01`) live here too, making them easy to find, change, and test against.

**Trade-off:** Cannot be mocked with Mockito. That is intentional — the kernel is pure domain logic that should be tested directly, not stubbed away.

---

## 4. Concurrent Fetching with `Mono.zip`

**Decision:** The three source calls (internal DB, JSON processor, SOAP processor) are executed in parallel using Project Reactor's `Mono.zip` with `Schedulers.boundedElastic()`. A single `.block()` is called at the service boundary to materialize the result.

**Why:**
Sequential fetching would mean the total latency is the sum of all three call durations. With `Mono.zip`, latency is determined by the slowest of the three, which is the best achievable. Both external adapters (JSON and SOAP) can take up to 5 seconds each — sequential would be up to 10 seconds; concurrent reduces that to ~5 seconds worst-case.

The internal JPA call is blocking by nature (no R2DBC in scope), so it is also wrapped in `Mono.fromCallable().subscribeOn(Schedulers.boundedElastic())` to avoid blocking the reactor thread pool.

**Trade-off:** `.block()` at the service boundary is a deliberate hybrid approach. The rest of the application is Servlet-based (Spring MVC), not fully reactive. A fully reactive stack (R2DBC, reactive JPA) would remove the need for `.block()` but would be a larger refactor out of scope for this system.

---

## 5. Two Separate WireMock Instances (Not One Shared)

**Decision:** `wiremock-json` (port 9091) and `wiremock-soap` (port 9092) run as independent Docker containers, each mounting only its own mappings directory.

**Why:**
A single WireMock instance serving both JSON and SOAP stubs blurs the boundary between two logically independent external systems. Separating them means:

- Routing is explicit: `processorWebClient` always talks to port 9091, `soapWebClient` always to port 9092. No shared paths, no risk of a SOAP stub accidentally matching a JSON request.
- Each stub file stays focused: `processor-payments.json` contains only REST mappings, `soap-payments.json` contains only SOAP/XML mappings.
- It mirrors production topology where the two processors are indeed separate hosts.

**Trade-off:** One more Docker service to start. Mitigated by `make up-db` starting both automatically.

---

## 6. Anti-Corruption Layer (ACL) for SOAP

**Decision:** A dedicated `SoapPaymentAcl` component is responsible for translating SOAP DTOs (`SoapPaymentResponse`) into domain `Payment` objects. The `SoapPaymentProcessorClient` does not touch the domain model directly.

**Why:**
The SOAP response schema is a legacy contract owned by a third party. Letting that schema leak into the domain would couple the business model to external naming conventions (`processedAt` vs `transactionDate`, string amounts vs `Money`, etc.). The ACL acts as a translation boundary: the domain defines its own model, and the ACL is the only code that knows how to map between the two worlds.

This follows the same pattern used by the JSON processor, which also maps its DTOs (`ProcessorPaymentDto`) to the domain before crossing the port boundary.

**Trade-off:** An extra class per external source. Worth it because the ACL is the only place to change if the external schema changes — the domain and service remain untouched.

---

## 7. SOAP Namespace Stripping via Regex

**Decision:** Before deserializing SOAP XML with Jackson, `AbstractSoapConnector` strips all namespace declarations and prefixes using two regex replacements.

**Why:**
Jackson's `XmlMapper` deserializes local element names but struggles with `soap:Body`, `soap:Envelope`, and custom namespace prefixes when the binding annotations use plain local names. Writing Jackson XML mixins or custom deserializers for every possible namespace variant would be brittle — legacy SOAP services tend to change namespace URIs between versions.

The regex approach is namespace-agnostic: after stripping, `<soap:Envelope xmlns:soap="...">` becomes `<Envelope>`, which Jackson can bind with `@JacksonXmlRootElement(localName = "Envelope")` reliably.

**Trade-off:** Regex on XML is generally fragile and not suitable for production-grade XML parsing (consider JAXB or XPath). Here it is acceptable because the SOAP response structure is small, well-known, and under our control via WireMock. If the SOAP contract becomes more complex, replace with JAXB binding.

---

## 8. Resilience4j Circuit Breaker and Retry on External Processors

**Decision:** Both `PaymentProcessorClient` and `SoapPaymentProcessorClient` are annotated with `@CircuitBreaker` and `@Retry` from Resilience4j. The circuit breaker trips after 50% failures over a 10-call sliding window; the retry policy makes up to 3 attempts with 500ms exponential backoff.

**Why:**
External HTTP services fail. Without a circuit breaker, a slow or downed processor would cause every reconciliation request to hang for 5 seconds (the timeout), exhausting the Hikari connection pool and cascading into a full outage. The circuit breaker detects the failure pattern and opens, letting the fallback (`ProcessorUnavailableException`) fire immediately, keeping the application responsive.

The retry handles transient failures (network blips, momentary 503s) without burdening the caller.

**Trade-off:** The circuit breaker configuration (`minimum-number-of-calls: 5`) means the circuit won't trip on a cold start with very low traffic. This is intentional — we don't want a single flaky test call to open the circuit in development.

---

## 9. Stateless JWT Authentication

**Decision:** All API endpoints (except `/v1/auth/token`, Swagger UI, and `/actuator/health`) require a Bearer JWT token. Tokens are signed with HS256, expire in 1 hour, and carry only the username as subject. No session storage.

**Why:**
The consumers of this API are internal services or operational tooling — not browsers with cookies. A stateless token scheme scales horizontally (any instance can validate the token without shared session state) and is straightforward to integrate with service-to-service calls.

The 1-hour expiry balances security (short enough to limit exposure of a leaked token) against operational convenience (long enough to not interrupt a batch reconciliation job).

**Trade-off:** The development credential store is an in-memory `Map` in `AuthController`. This is explicitly a dev shortcut — production must replace it with a `UserDetailsService` backed by a database or an external identity provider (Auth0, Keycloak, etc.). The code comment in `AuthController` flags this.

---

## 10. Append-Only Audit Log with JSONB Snapshots

**Decision:** `reconciliation_results` is insert-only. Each row captures the full payment snapshots (internal, JSON processor, SOAP processor) as JSONB columns alongside the status and discrepancies. Rows are never updated or deleted.

**Why:**
Reconciliation is inherently an audit activity. If an ops team investigates a discrepancy reported last week, they need to see the exact values that were seen at reconciliation time — not the current values in the DB, which may have changed. JSONB snapshots preserve the state as observed.

The append-only design also means the table is a reliable event log: you can replay the history of a payment, see how many times it was reconciled, and track when discrepancies first appeared.

**Trade-off:** The table grows without bound. Operational concerns (archiving, partitioning by `reconciled_at`, TTL policies) are out of scope here but would be required before production. The `reconciled_at DESC` indexes are designed with future range-based archiving in mind.

---

## 11. Tolerance-Based Comparison (Amount and Date)

**Decision:** An amount difference of ≤ 0.01 is not flagged as a discrepancy. A date difference of ≤ 5 minutes is not flagged as a discrepancy.

**Why:**
Payment processors apply rounding differently (some round half-up, some truncate). A difference of one cent between systems is almost always a rounding artifact, not a real error. Flagging it would generate noise that ops teams would immediately learn to ignore — defeating the purpose of reconciliation.

Similarly, clocks between systems are rarely perfectly in sync (NTP drift, timezone handling, processing latency). A 5-minute window absorbs normal clock skew while still catching real date discrepancies (same-day vs. next-day settlement, for example).

Both constants (`DATE_TOLERANCE_MINUTES`, `MATERIAL_AMOUNT_THRESHOLD`) are defined in `PaymentComparisonKernel` and `Money` respectively — one place to change if business rules evolve.

**Trade-off:** Tolerances are currently hardcoded constants. A future improvement would be to make them configurable per-currency or per-processor via `application.yml`.

---

## 12. `ReconciliationResponseDto` Design (API Consumer Perspective)

**Decision:** The REST response includes: a machine-readable `status` string, a human-readable `statusDescription`, a `fullyReconciled` boolean, a list of `discrepancies` with field-level detail, and full payment snapshots from each source.

**Why:**
The API is consumed by at least two types of clients:

- **Automated systems** (dashboards, alerting pipelines): need `fullyReconciled` as a simple boolean and `status` as a filterable enum string.
- **Ops/finance analysts** investigating exceptions: need `statusDescription` in plain language, the exact `discrepancies` (what field, what value from each side), and the full payment snapshots to compare amounts, dates, and currencies side by side without having to query each source separately.

Both needs are served by the same endpoint. `@JsonInclude(NON_NULL)` keeps the response clean when optional fields (snapshots of missing payments) are absent.

**Trade-off:** The response is verbose for the CONCILIATED happy path. This is acceptable — bandwidth is cheap compared to the cost of an analyst opening multiple tabs to correlate data they could have received in one response.

---

## 13. Local Execution (No Cloud Dependency)

**Decision:** The entire system runs locally with `docker compose up`. PostgreSQL, two WireMock instances, and the Spring Boot app are all containerized. Flyway applies migrations automatically on startup. Seed data is loaded via `make seed`.

**Why:**
The challenge requirement is that the code must run locally. Docker Compose eliminates "works on my machine" problems by declaring all infrastructure as code. WireMock stubs provide deterministic external data without requiring real credentials or network access. Flyway migrations run in-container, so there is no manual SQL step.

The `make up-db` target starts only PostgreSQL and both WireMocks, allowing the Spring Boot app to run from the IDE against the same infrastructure — the typical inner-loop development workflow.

**Quick start:**
```bash
docker compose up -d          # starts postgres + wiremock-json + wiremock-soap + app
make seed                     # inserts demo payments into internal_payments
# Obtain a token:
curl -s -X POST http://localhost:8080/api/v1/auth/token \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | jq .access_token
# Query reconciliation:
curl -s http://localhost:8080/api/v1/reconciliation/pay_abc123 \
  -H "Authorization: Bearer <token>" | jq .
```

**Swagger UI:** `http://localhost:8080/api/swagger-ui.html`

---

## Requirement Coverage Matrix

| Challenge Requirement | Where It Is Implemented |
|---|---|
| Query if a payment is conciliated or not | `GET /api/v1/reconciliation/{paymentId}` → `fullyReconciled` boolean + `status` enum |
| Handle payment missing in one system | 6 `MISSING_*` statuses + `MISSING_RECORD` discrepancy type; `@JsonInclude(NON_NULL)` omits absent snapshots |
| Consider who consumes the API | `statusDescription` (plain text), `discrepancies` with field-level detail, full snapshots per source — Decision 12 |
| Code runs locally | Docker Compose + WireMock stubs + Flyway + seed data — Decision 13 |
| Document decisions | This document |
