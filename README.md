# Payment Reconciliation System

A REST API that reconciles payments between an **internal system** and an **external payment processor**. It verifies data parity (amount, date, existence) across both sources and exposes the result with a semantic reconciliation status.

---

## Tech Stack

| Layer | Technology |
|-------|------------|
| Language | Java 21 |
| Build | Gradle 8.8 |
| Framework | Spring Boot 3.2 |
| Persistence | Spring Data JPA + PostgreSQL 16 |
| Migrations | Flyway |
| HTTP Client | Spring WebFlux (WebClient) |
| Resilience | Resilience4j (Circuit Breaker + Retry) |
| Mapping | MapStruct 1.5 |
| Boilerplate | Lombok |
| API Docs | SpringDoc OpenAPI 3 / Swagger UI |
| Testing | JUnit 5 + Mockito + MockMvc |
| Containers | Docker + Docker Compose |
| External mock | WireMock 3.6 |

---

## Architecture — Hexagonal (Ports & Adapters)

```
domain/         → pure core, zero framework dependencies
application/    → use cases + ports (interfaces)
infrastructure/ → adapters (REST, JPA, WebClient)
```

Dependency rule: `infrastructure → application → domain`. No inner layer imports anything from an outer layer.

---

## Reconciliation Statuses

| Status | When assigned |
|--------|---------------|
| `CONCILIATED` | Amount and date match across both sources |
| `DISCREPANCY_AMOUNT` | Only the amount differs (> 0.01 tolerance) |
| `DISCREPANCY_DATE` | Only the date differs (> 5 min tolerance) |
| `MULTIPLE_DISCREPANCIES` | Amount AND date differ simultaneously |
| `MISSING_IN_INTERNAL` | Payment exists in processor but not in internal system |
| `MISSING_IN_PROCESSOR` | Payment exists internally but never reached the processor |

---

## Prerequisites

- **Java 21+**
- **Gradle 8.8+** (or use the `./gradlew` wrapper — no local install required once bootstrapped)
- **Docker & Docker Compose**
- **`jq`** (optional, for readable curl output)

---

## Running with Docker Compose (recommended)

This spins up PostgreSQL, WireMock (external processor mock), and the application together.

```bash
# Start all services
make up

# Seed test data into the database
make seed

# Tail application logs
make logs-app
```

Services started:

| Service | URL |
|---------|-----|
| API | http://localhost:8080/api |
| Swagger UI | http://localhost:8080/api/swagger-ui.html |
| WireMock | http://localhost:8081 |
| PostgreSQL | localhost:5435 / `reconciliation_db` |

---

## Running Locally (without Docker for the app)

**1. Start PostgreSQL**

```bash
docker run -d \
  --name reconciliation-db \
  -e POSTGRES_DB=reconciliation_db \
  -e POSTGRES_USER=reconciliation_user \
  -e POSTGRES_PASSWORD=reconciliation_pass \
  -p 5435:5432 \
  postgres:16-alpine
```

**2. Bootstrap the Gradle wrapper (first time only)**

If you have Gradle installed locally:

```bash
gradle wrapper --gradle-version 8.8
```

This generates `gradlew`, `gradlew.bat`, and `gradle/wrapper/gradle-wrapper.jar`. After this step you no longer need a local Gradle install — the wrapper manages it.

**3. Build and run**

```bash
# Compile (also generates MapStruct code)
./gradlew classes

# Run the application
./gradlew bootRun
```

**4. Seed test data**

```bash
psql -h localhost -U reconciliation_user -d reconciliation_db
```

```sql
INSERT INTO internal_payments (payment_id, amount, currency, status, description, transaction_date)
VALUES
  ('pay_abc123',  100.00, 'USD', 'APPROVED', 'E-commerce purchase',  '2024-06-15 10:00:00'),
  ('pay_disc001', 100.00, 'USD', 'APPROVED', 'Subscription payment', '2024-06-15 11:00:00'),
  ('pay_missing', 250.00, 'USD', 'APPROVED', 'Wire transfer',        '2024-06-15 12:00:00');
```

---

## Testing the API

### Scenario 1 — Fully reconciled payment

```bash
curl -s http://localhost:8080/api/v1/reconciliation/pay_abc123 | jq .
```

```json
{
  "paymentId": "pay_abc123",
  "status": "CONCILIATED",
  "fullyReconciled": true,
  "discrepancies": []
}
```

### Scenario 2 — Amount discrepancy

```bash
curl -s http://localhost:8080/api/v1/reconciliation/pay_disc001 | jq .
```

```json
{
  "paymentId": "pay_disc001",
  "status": "DISCREPANCY_AMOUNT",
  "fullyReconciled": false,
  "discrepancies": [
    { "type": "AMOUNT_MISMATCH", "field": "amount", "internalValue": "100.00 USD", "processorValue": "95.00 USD" }
  ]
}
```

### Scenario 3 — Payment missing in processor

```bash
curl -s http://localhost:8080/api/v1/reconciliation/pay_missing | jq .
```

```json
{
  "paymentId": "pay_missing",
  "status": "MISSING_IN_PROCESSOR",
  "fullyReconciled": false,
  "discrepancies": [
    { "type": "MISSING_RECORD", "field": "paymentId", "internalValue": "pay_missing", "processorValue": null }
  ]
}
```

### Scenario 4 — Payment not found anywhere (404)

```bash
curl -s http://localhost:8080/api/v1/reconciliation/pay_ghost | jq .
```

```json
{
  "httpStatus": 404,
  "errorCode": "PAYMENT_NOT_FOUND",
  "message": "Payment not found in any source: pay_ghost"
}
```

### Scenario 5 — Date discrepancy

```bash
curl -s http://localhost:8080/api/v1/reconciliation/pay_date001 | jq .
```

### Scenario 6 — Payment exists only in processor

```bash
curl -s http://localhost:8080/api/v1/reconciliation/pay_extonly | jq .
```

---

## Running Tests

```bash
# All tests
./gradlew test

# Domain service only
./gradlew test --tests "*.ReconciliationServiceTest"

# Controller layer only
./gradlew test --tests "*.ReconciliationControllerTest"

# Money value object only
./gradlew test --tests "*.MoneyTest"
```

### Test coverage

| Test class | Cases |
|------------|-------|
| `ReconciliationServiceTest` | CONCILIATED, DISCREPANCY_AMOUNT, DISCREPANCY_DATE, MULTIPLE_DISCREPANCIES, MISSING_IN_INTERNAL, MISSING_IN_PROCESSOR, PaymentNotFound, persistence always called |
| `ReconciliationControllerTest` | HTTP 200 (reconciled), HTTP 200 (discrepancies), HTTP 404, HTTP 503 |
| `MoneyTest` | Cent tolerance, currency mismatch, negative amount, invalid ISO code |

---

## Observability

```bash
# Circuit Breaker state (Actuator)
curl -s http://localhost:8080/api/actuator/circuitbreakers | jq .

# All metrics
curl -s http://localhost:8080/api/actuator/metrics | jq .names
```

---

## Key Design Decisions

- **JSONB snapshots**: `reconciliation_results` stores a full snapshot of both payment states at reconciliation time — not foreign keys. Guarantees self-contained audit records even if source data changes later.
- **Append-only writes**: every reconciliation call inserts a new row, never updates. Produces a natural audit log with no write contention.
- **Configurable tolerances**: amount tolerance is `0.01` (`Money`), date tolerance is `5 minutes` (`ReconciliationService.DATE_TOLERANCE_MINUTES`). Both are easy to externalize to `application.yml`.
- **Circuit Breaker**: Resilience4j over `PaymentProcessorClient` — opens after 50% failure rate in a 10-call window, stays open 30 seconds. Fallback returns HTTP 503.
- **MapStruct over ModelMapper**: compile-time mapping with null-safety, ~10× faster than reflection-based mappers.
- **Pure domain**: `ReconciliationService` has zero framework annotations. Migrating adapters (e.g., replacing WebClient with a Kafka consumer) requires no changes to domain or application layers.

---

## Project Structure

```
src/main/java/com/fintech/reconciliation/
├── domain/             # Entities, value objects, domain exceptions
├── application/        # Use cases, driving/driven ports, ReconciliationService
└── infrastructure/     # REST controllers, JPA adapters, WebClient, config
```

For a full file tree and deeper architecture notes see [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).  
For design rationale see [docs/DESIGN_DECISIONS.md](docs/DESIGN_DECISIONS.md).
