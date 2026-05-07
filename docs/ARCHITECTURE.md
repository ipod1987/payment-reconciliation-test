# Payment Reconciliation — Arquitectura y Resumen Técnico

## Estructura del Proyecto (Hexagonal / Ports & Adapters)

```
payment-reconciliation/
├── domain/                               ← NÚCLEO: sin dependencias externas
│   ├── model/
│   │   ├── Payment.java                  ← Entidad con source (INTERNAL/PROCESSOR)
│   │   ├── ReconciliationResult.java     ← Agrega estado + discrepancias + snapshots
│   │   ├── Discrepancy.java              ← Value Object con factory methods semánticos
│   │   └── valueobject/
│   │       ├── PaymentId.java            ← Record Java, immutable, validated
│   │       ├── Money.java                ← Tolerancia 0.01, validación ISO 4217
│   │       └── ReconciliationStatus.java ← 6 estados con descripción legible
│   └── exception/
│       ├── PaymentNotFoundException.java
│       └── ProcessorUnavailableException.java
│
├── application/                          ← CASOS DE USO: orquesta puertos
│   ├── port/in/
│   │   └── ReconcilePaymentUseCase.java        ← Driving port (contrato del controller)
│   ├── port/out/
│   │   ├── LoadInternalPaymentPort.java         ← Driven port (JPA lo implementa)
│   │   ├── LoadProcessorPaymentPort.java        ← Driven port (WebClient lo implementa)
│   │   ├── LoadReconciliationResultPort.java
│   │   └── SaveReconciliationResultPort.java
│   └── service/
│       └── ReconciliationService.java           ← Lógica pura, sin frameworks
│
└── infrastructure/                       ← ADAPTADORES: detalles técnicos
    ├── adapter/in/rest/
    │   ├── ReconciliationController.java        ← GET /v1/reconciliation/{paymentId}
    │   ├── GlobalExceptionHandler.java          ← @RestControllerAdvice
    │   ├── dto/  (Records Java)
    │   └── mapper/ReconciliationResponseMapper.java  (MapStruct)
    ├── adapter/out/
    │   ├── persistence/  (JPA + Flyway + PostgreSQL JSONB)
    │   └── client/       (WebClient + Circuit Breaker + Retry)
    └── config/           (WebClient, OpenAPI/Swagger)
```

---

## Estados de Conciliación

| Estado | Descripción |
|--------|-------------|
| `CONCILIATED` | El pago existe en ambos sistemas con datos idénticos |
| `DISCREPANCY_AMOUNT` | El monto difiere entre sistema interno y procesador |
| `DISCREPANCY_DATE` | La fecha de la transacción difiere más allá de la tolerancia |
| `MULTIPLE_DISCREPANCIES` | Más de un campo presenta diferencias simultáneas |
| `MISSING_IN_INTERNAL` | El pago existe en el procesador pero no en el sistema interno |
| `MISSING_IN_PROCESSOR` | El pago existe internamente pero no llegó al procesador |

---

## Decisiones Técnicas

| Área | Decisión | Impacto |
|------|----------|---------|
| **Value Objects** | `record` Java 21 para `PaymentId` y `Money` | Inmutabilidad garantizada por compilador, no por convención |
| **Snapshots JSONB** | Historial de conciliación en columnas JSONB de PostgreSQL | Auditoría self-contained; queries analíticas con índice GIN |
| **Append-only audit** | Cada conciliación inserta un registro nuevo, nunca actualiza | Log natural de estado, sin conflictos de concurrencia |
| **Tolerancia de fechas** | Ventana de ±5 minutos para `transactionDate` | Absorbe latencia de settlement y diferencias de zona horaria |
| **Circuit Breaker** | Resilience4j sobre WebClient al procesador externo | Falla rápida con HTTP 503 legible; protege ambos sistemas |
| **MapStruct** | Generado en compile-time para Entity ↔ Domain ↔ DTO | Zero reflection, null-safety, ~10× más rápido que ModelMapper |

---

## Cobertura de Tests

| Clase de test | Casos cubiertos |
|---------------|-----------------|
| `ReconciliationServiceTest` | CONCILIATED, DISCREPANCY_AMOUNT, DISCREPANCY_DATE, MULTIPLE_DISCREPANCIES, MISSING_IN_INTERNAL, MISSING_IN_PROCESSOR, PaymentNotFound, persistencia siempre ejecutada |
| `ReconciliationControllerTest` | HTTP 200 con CONCILIATED, HTTP 200 con discrepancias, HTTP 404, HTTP 503 |
| `MoneyTest` | Tolerancia de centavos, currency mismatch, importe negativo, código ISO inválido |

---

## Stack Tecnológico

| Componente | Tecnología |
|------------|------------|
| Lenguaje | Java 21 |
| Framework | Spring Boot 3.2 |
| Persistencia | Spring Data JPA + PostgreSQL 16 |
| Migraciones | Flyway |
| Cliente HTTP | Spring WebFlux (WebClient) |
| Resiliencia | Resilience4j (Circuit Breaker + Retry) |
| Mapeo | MapStruct 1.5 |
| Boilerplate | Lombok |
| Documentación API | SpringDoc OpenAPI 3 (Swagger UI) |
| Tests | JUnit 5 + Mockito + MockMvc |
