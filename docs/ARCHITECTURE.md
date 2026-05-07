# Payment Reconciliation — Arquitectura y Resumen Técnico

## Estructura del Proyecto (Hexagonal / Ports & Adapters)

```
payment-reconciliation/
├── domain/                               ← NÚCLEO: sin dependencias externas
│   ├── model/
│   │   ├── Payment.java                  ← Entidad con source (INTERNAL/JSON_PROCESSOR/SOAP_PROCESSOR)
│   │   ├── ReconciliationResult.java     ← Agrega estado + discrepancias + snapshots
│   │   ├── Discrepancy.java              ← Value Object con factory methods semánticos
│   │   └── valueobject/
│   │       ├── PaymentId.java            ← Record Java, immutable, validated
│   │       ├── Money.java                ← Tolerancia 0.01, validación ISO 4217
│   │       └── ReconciliationStatus.java ← 8 estados con descripción legible
│   ├── kernel/
│   │   └── PaymentComparisonKernel.java  ← Lógica de comparación pura (clase estática final)
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
│   │   ├── LoadSoapProcessorPaymentPort.java    ← Driven port (SOAP client lo implementa)
│   │   └── SaveReconciliationResultPort.java
│   └── service/
│       └── ReconciliationService.java           ← Orquesta concurrencia; delega lógica al kernel
│
└── infrastructure/                       ← ADAPTADORES: detalles técnicos
    ├── adapter/in/rest/
    │   ├── ReconciliationController.java        ← GET /v1/reconciliation/{paymentId}
    │   ├── AuthController.java                  ← POST /v1/auth/token (JWT)
    │   ├── StatusController.java                ← GET /v1/status
    │   ├── GlobalExceptionHandler.java          ← @RestControllerAdvice
    │   ├── dto/  (Records Java)
    │   └── mapper/ReconciliationResponseMapper.java  (MapStruct)
    ├── adapter/out/
    │   ├── persistence/  (JPA + Flyway + PostgreSQL JSONB)
    │   ├── client/       (WebClient + Circuit Breaker + Retry — procesador JSON)
    │   └── soap/         (WebClient XML + Circuit Breaker + Retry — procesador SOAP)
    │       ├── AbstractSoapConnector.java       ← Stripping de namespaces + deserialización
    │       ├── SoapPaymentProcessorClient.java
    │       └── acl/SoapPaymentAcl.java          ← Anti-Corruption Layer SOAP → dominio
    ├── security/
    │   ├── JwtTokenProvider.java                ← Firma HS256, validación, claims
    │   └── JwtAuthenticationFilter.java         ← OncePerRequestFilter
    └── config/
        ├── WebClientConfig.java
        ├── SecurityConfig.java                  ← Rutas públicas vs. protegidas
        └── OpenApiConfig.java
```

---

## Estados de Conciliación (8 estados)

| Estado | Descripción |
|--------|-------------|
| `CONCILIATED` | El pago existe en las tres fuentes con datos idénticos |
| `DISCREPANCY_AMOUNT` | Solo el monto difiere entre fuentes |
| `DISCREPANCY_DATE` | Solo la fecha difiere más allá de la tolerancia |
| `MULTIPLE_DISCREPANCIES` | Más de un campo presenta diferencias simultáneas |
| `MISSING_IN_INTERNAL` | El pago existe en procesadores externos pero no en el sistema interno |
| `MISSING_IN_PROCESSOR` | El pago existe internamente pero no en ningún procesador externo |
| `MISSING_IN_JSON_PROCESSOR` | El pago existe en interno y SOAP, pero no en el procesador JSON |
| `MISSING_IN_SOAP_PROCESSOR` | El pago existe en interno y JSON, pero no en el procesador SOAP |

---

## Decisiones Técnicas Clave

| Área | Decisión | Impacto |
|------|----------|---------|
| **PaymentComparisonKernel** | Clase `final` con solo métodos `static`, sin Spring | Testeable sin contexto, lógica aislada del framework |
| **Concurrencia (`Mono.zip`)** | Las 3 fuentes se consultan en paralelo con Project Reactor | Latencia = max(fuente más lenta), no suma de las tres |
| **ACL para SOAP** | `SoapPaymentAcl` traduce DTO SOAP → dominio | Aísla el dominio del schema legacy del tercero |
| **Regex namespace stripping** | `AbstractSoapConnector` elimina prefijos SOAP antes de deserializar | Robusto ante cambios de namespace URI sin lógica adicional |
| **Value Objects** | `record` Java 21 para `PaymentId` y `Money` | Inmutabilidad garantizada por compilador |
| **Snapshots JSONB** | Historial de conciliación en columnas JSONB (3 snapshots por fila) | Auditoría self-contained; queries analíticas con índice GIN |
| **Append-only audit** | Cada conciliación inserta un registro nuevo, nunca actualiza | Log natural de estado, sin conflictos de concurrencia |
| **Tolerancia de fechas** | Ventana de ±5 minutos para `transactionDate` | Absorbe latencia de settlement y diferencias de zona horaria |
| **Circuit Breaker** | Resilience4j sobre ambos clientes externos (JSON y SOAP) | Falla rápida con HTTP 503; protege ambos sistemas |
| **JWT stateless** | HS256, 1 hora de expiración, solo username como subject | Escalabilidad horizontal sin sesión compartida |
| **MapStruct** | Generado en compile-time para Entity ↔ Domain ↔ DTO | Zero reflection, null-safety |
| **Gradle (Kotlin DSL)** | Gradle 8.8 con `build.gradle.kts` | Builds incrementales, tipado estático, ordering Lombok→MapStruct explícito |

---

## Cobertura de Tests

| Clase de test | Casos cubiertos |
|---------------|-----------------|
| `ReconciliationServiceTest` | CONCILIATED, DISCREPANCY_AMOUNT, DISCREPANCY_DATE, MULTIPLE_DISCREPANCIES, MISSING_IN_INTERNAL, MISSING_IN_PROCESSOR, MISSING_IN_JSON_PROCESSOR, MISSING_IN_SOAP_PROCESSOR, PaymentNotFound, persistencia siempre ejecutada, fetch concurrente |
| `ReconciliationControllerTest` | HTTP 200 con CONCILIATED, HTTP 200 con discrepancias, HTTP 404, HTTP 503 |
| `MoneyTest` | Tolerancia de centavos, currency mismatch, importe negativo, código ISO inválido |

---

## Stack Tecnológico

| Componente | Tecnología |
|------------|------------|
| Lenguaje | Java 21 |
| Framework | Spring Boot 3.2.5 |
| Persistencia | Spring Data JPA + PostgreSQL 16 |
| Migraciones | Flyway |
| Cliente HTTP | Spring WebFlux (WebClient) |
| Parsing XML | Jackson Dataformat XML |
| Resiliencia | Resilience4j (Circuit Breaker + Retry) |
| Autenticación | Spring Security + JJWT 0.12.6 (HS256) |
| Mapeo | MapStruct 1.5.5 |
| Boilerplate | Lombok 1.18.32 |
| Documentación API | SpringDoc OpenAPI 3 (Swagger UI) |
| Tests | JUnit 5 + Mockito + MockMvc |
| Build | Gradle 8.8 (Kotlin DSL) |
| Mocks externos | WireMock 3.6.0 (dos instancias independientes: JSON y SOAP) |
