# Contexto de Sesión — Payment Reconciliation System

## Estado del proyecto

Proyecto completamente scaffoldeado y listo para compilar/ejecutar.
Ubicación: `/Users/engelsmedina/projects/test-java/payment-reconciliation-test`
NO es un repositorio git todavía (`git init` pendiente si se desea).

---

## Objetivo del sistema

API REST para conciliación de pagos entre un **sistema interno** y un **procesador externo**.
Verifica paridad de datos (monto, fecha, existencia) y expone el resultado con estado semántico.

---

## Stack técnico

| Capa | Tecnología |
|------|------------|
| Lenguaje | Java 21 |
| Framework | Spring Boot 3.2.5 |
| Persistencia | Spring Data JPA + PostgreSQL 16 |
| Migraciones | Flyway |
| Cliente HTTP | Spring WebFlux (WebClient) |
| Resiliencia | Resilience4j (Circuit Breaker + Retry) |
| Mapeo | MapStruct 1.5.5 |
| Boilerplate | Lombok 1.18.32 |
| Documentación API | SpringDoc OpenAPI 3 / Swagger UI |
| Tests | JUnit 5 + Mockito + MockMvc |
| Contenedores | Docker + Docker Compose |
| Mock externo | WireMock 3.6.0 |

---

## Arquitectura: Hexagonal (Ports & Adapters)

```
domain/          → núcleo puro, cero dependencias de framework
application/     → casos de uso + puertos (interfaces)
infrastructure/  → adaptadores (REST, JPA, WebClient)
```

### Regla de dependencias

```
infrastructure → application → domain
```

Ninguna capa interna importa nada de la capa externa.

---

## Estructura de archivos completa

```
payment-reconciliation-test/
├── .dockerignore
├── .env                                         ← credenciales Docker Compose (no commitear)
├── Dockerfile                                   ← multi-stage: Maven builder + JRE Alpine runtime
├── Makefile                                     ← comandos: make up / make seed / make logs-app
├── docker-compose.yml                           ← servicios: postgres + wiremock + app
├── pom.xml                                      ← Java 21, Spring Boot 3.2.5
├── docker/
│   ├── postgres/init/01_seed_data.sql           ← datos de prueba (5 pagos)
│   └── wiremock/mappings/processor-payments.json ← stubs del procesador externo (5 escenarios)
├── docs/
│   ├── ARCHITECTURE.md                          ← árbol, estados, tabla técnica, cobertura
│   ├── DESIGN_DECISIONS.md                      ← razonamiento: modelo de datos, idempotencia,
│   │                                               Circuit Breaker, hoja de ruta Event-Driven
│   ├── RUNNING.md                               ← instrucciones de arranque + curls de prueba
│   └── SESSION_CONTEXT.md                       ← este archivo
└── src/
    ├── main/
    │   ├── java/com/fintech/reconciliation/
    │   │   ├── PaymentReconciliationApplication.java
    │   │   ├── domain/
    │   │   │   ├── model/
    │   │   │   │   ├── Payment.java                    ← entidad con PaymentSource enum
    │   │   │   │   ├── ReconciliationResult.java       ← agrega status + discrepancias
    │   │   │   │   ├── Discrepancy.java                ← VO con factory methods
    │   │   │   │   └── valueobject/
    │   │   │   │       ├── PaymentId.java              ← record Java
    │   │   │   │       ├── Money.java                  ← record, tolerancia 0.01, ISO 4217
    │   │   │   │       └── ReconciliationStatus.java   ← enum con 6 estados
    │   │   │   └── exception/
    │   │   │       ├── PaymentNotFoundException.java
    │   │   │       └── ProcessorUnavailableException.java
    │   │   ├── application/
    │   │   │   ├── port/in/
    │   │   │   │   └── ReconcilePaymentUseCase.java    ← driving port
    │   │   │   ├── port/out/
    │   │   │   │   ├── LoadInternalPaymentPort.java
    │   │   │   │   ├── LoadProcessorPaymentPort.java
    │   │   │   │   ├── LoadReconciliationResultPort.java
    │   │   │   │   └── SaveReconciliationResultPort.java
    │   │   │   └── service/
    │   │   │       └── ReconciliationService.java      ← lógica de conciliación pura
    │   │   └── infrastructure/
    │   │       ├── adapter/in/rest/
    │   │       │   ├── ReconciliationController.java   ← GET /v1/reconciliation/{paymentId}
    │   │       │   ├── GlobalExceptionHandler.java     ← @RestControllerAdvice
    │   │       │   ├── dto/
    │   │       │   │   ├── ReconciliationResponseDto.java
    │   │       │   │   ├── PaymentSummaryDto.java
    │   │       │   │   ├── DiscrepancyDetailDto.java
    │   │       │   │   └── ApiErrorDto.java
    │   │       │   └── mapper/
    │   │       │       └── ReconciliationResponseMapper.java  ← MapStruct
    │   │       ├── adapter/out/
    │   │       │   ├── persistence/
    │   │       │   │   ├── ReconciliationJpaAdapter.java      ← impl Load + Save ports
    │   │       │   │   ├── InternalPaymentJpaAdapter.java     ← impl LoadInternal port
    │   │       │   │   ├── entity/
    │   │       │   │   │   ├── ReconciliationResultEntity.java ← JSONB snapshots
    │   │       │   │   │   └── InternalPaymentEntity.java
    │   │       │   │   ├── repository/
    │   │       │   │   │   ├── ReconciliationJpaRepository.java
    │   │       │   │   │   └── InternalPaymentJpaRepository.java
    │   │       │   │   └── mapper/
    │   │       │   │       └── ReconciliationEntityMapper.java ← MapStruct
    │   │       │   └── client/
    │   │       │       ├── PaymentProcessorClient.java  ← WebClient + CircuitBreaker + Retry
    │   │       │       └── dto/ProcessorPaymentDto.java
    │   │       └── config/
    │   │           ├── WebClientConfig.java
    │   │           └── OpenApiConfig.java
    │   └── resources/
    │       ├── application.yml                  ← config base (localhost para dev)
    │       ├── application-docker.yml           ← override para perfil docker
    │       └── db/migration/
    │           └── V1__create_reconciliation_tables.sql
    └── test/
        └── java/com/fintech/reconciliation/
            ├── application/service/
            │   └── ReconciliationServiceTest.java   ← 8 casos unitarios con Mockito
            ├── domain/model/
            │   └── MoneyTest.java                   ← 5 casos del value object
            └── infrastructure/adapter/in/rest/
                └── ReconciliationControllerTest.java ← 4 casos con MockMvc
```

---

## Estados de conciliación

| Estado | Cuándo se asigna |
|--------|-----------------|
| `CONCILIATED` | Monto y fecha coinciden en ambas fuentes |
| `DISCREPANCY_AMOUNT` | Solo el monto difiere (> 0.01 de tolerancia) |
| `DISCREPANCY_DATE` | Solo la fecha difiere (> 5 min de tolerancia) |
| `MULTIPLE_DISCREPANCIES` | Monto Y fecha difieren simultáneamente |
| `MISSING_IN_INTERNAL` | Pago existe en procesador, no en sistema interno |
| `MISSING_IN_PROCESSOR` | Pago existe internamente, no llegó al procesador |

---

## Lógica de conciliación (ReconciliationService)

```
reconcile(paymentId)
  ├── loadInternal(id)   → Optional<Payment>
  ├── loadProcessor(id)  → Optional<Payment>
  ├── ambos vacíos       → throw PaymentNotFoundException (no se persiste)
  ├── solo interno       → MISSING_IN_PROCESSOR
  ├── solo procesador    → MISSING_IN_INTERNAL
  └── ambos presentes    → comparePayments()
        ├── detectAmountDiscrepancy()  (tolerancia: 0.01)
        ├── detectDateDiscrepancy()    (tolerancia: 5 minutos)
        ├── 0 discrepancias → CONCILIATED
        ├── 1 discrepancia  → DISCREPANCY_AMOUNT | DISCREPANCY_DATE
        └── 2 discrepancias → MULTIPLE_DISCREPANCIES
  └── saveReconciliationResult.save(result)  ← siempre persiste
```

---

## Decisiones de diseño clave

1. **Snapshots JSONB**: `reconciliation_results` guarda snapshots del estado de los pagos al momento de conciliar, no foreign keys. Garantiza auditoría self-contained aunque los datos cambien después.

2. **Append-only**: Cada llamada inserta un registro nuevo, nunca actualiza. Produce historial natural y evita contención de escrituras.

3. **Tolerancias configurables**: `DATE_TOLERANCE_MINUTES = 5` en `ReconciliationService` y `TOLERANCE = 0.01` en `Money`. Fácil de externalizar a `application.yml` si se necesita por tipo de pago.

4. **Circuit Breaker**: Resilience4j sobre `PaymentProcessorClient`. Si el procesador falla el 50% de las llamadas en una ventana de 10, el circuito abre 30 segundos. El fallback lanza `ProcessorUnavailableException` → HTTP 503.

5. **Puerto `LoadReconciliationResultPort`** existe pero el TTL-cache no está activado en el servicio. La infraestructura está lista para activarlo con una línea en `ReconciliationService` cuando se requiera.

---

## Docker — cómo funciona

```
docker-compose.yml
  ├── postgres (postgres:16-alpine)
  │     └── healthcheck: pg_isready cada 10s
  ├── wiremock (wiremock/wiremock:3.6.0-alpine)
  │     └── monta docker/wiremock/mappings/ como stubs
  └── app (imagen construida desde Dockerfile)
        ├── depends_on: postgres (condition: service_healthy)
        ├── SPRING_DATASOURCE_URL → jdbc:postgresql://postgres:5435/reconciliation_db
        └── SPRING_PROFILES_ACTIVE=docker
```

El `Dockerfile` es **multi-stage**:
- Stage `builder`: `maven:3.9.7-eclipse-temurin-21-alpine` — compila el JAR
- Stage `runtime`: `eclipse-temurin:21-jre-alpine` — solo el JRE, usuario no-root, `~150MB`

---

## Comandos para continuar

```bash
cd /Users/engelsmedina/projects/test-java/payment-reconciliation-test

# Levantar el entorno completo
make up

# Insertar datos de prueba
make seed

# Ver logs de la app
make logs-app

# Probar los 5 escenarios (requiere jq)
curl -s http://localhost:8080/api/v1/reconciliation/pay_abc123  | jq .   # CONCILIATED
curl -s http://localhost:8080/api/v1/reconciliation/pay_disc001 | jq .   # DISCREPANCY_AMOUNT
curl -s http://localhost:8080/api/v1/reconciliation/pay_missing | jq .   # MISSING_IN_PROCESSOR
curl -s http://localhost:8080/api/v1/reconciliation/pay_date001 | jq .   # DISCREPANCY_DATE
curl -s http://localhost:8080/api/v1/reconciliation/pay_extonly | jq .   # MISSING_IN_INTERNAL

# Swagger UI
open http://localhost:8080/api/swagger-ui.html

# Ejecutar tests
./mvnw test
```

---

## Posibles próximos pasos

- [ ] Agregar endpoint `GET /v1/reconciliation?status=DISCREPANCY_AMOUNT&from=2024-06-01` para reportes
- [ ] Activar TTL-cache en `ReconciliationService` usando `LoadReconciliationResultPort`
- [ ] Agregar `POST /v1/reconciliation/batch` para conciliación masiva por rango de fechas
- [ ] Integrar Spring Security (JWT) — `OpenApiConfig` ya tiene el esquema bearer definido
- [ ] Agregar plugin JaCoCo al `pom.xml` para reporte de cobertura
- [x] Configurar `git init` y `.gitignore` (excluir `.env`, `target/`)
- [ ] Migrar a modelo Event-Driven (Kafka) — roadmap detallado en `docs/DESIGN_DECISIONS.md`
