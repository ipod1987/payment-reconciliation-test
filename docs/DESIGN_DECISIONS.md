# Design Decisions — Payment Reconciliation System

## 1. Modelo de Datos

### Por qué snapshots JSON en lugar de tablas relacionales para el historial

La tabla `reconciliation_results` almacena **snapshots JSONB** del estado de los pagos en el momento exacto de la conciliación, en lugar de foreign keys a `internal_payments`.

**Razonamiento:**
- Los datos del procesador externo son efímeros (no se persisten localmente). Guardar un snapshot garantiza que el historial de auditoría sea self-contained.
- Un auditor que revisa una conciliación de hace 6 meses necesita ver *qué datos había entonces*, no los datos actuales (que pudieron haber cambiado por correcciones o actualizaciones).
- PostgreSQL's JSONB con índices GIN permite queries analíticas sobre discrepancias (`WHERE discrepancies @> '[{"type": "AMOUNT_MISMATCH"}]'`) sin joins costosos.

### Por qué `reconciliation_results` es append-only

Cada llamada a `reconcile()` genera un **nuevo registro** en lugar de actualizar el existente.

**Razonamiento:**
- Produce un log de auditoría natural: se puede ver la evolución del estado de un pago en el tiempo.
- Simplifica la implementación de idempotencia: la lógica de "si ya existe un resultado reciente, devuélvelo" puede aplicarse en la capa de aplicación sin UPDATE contencioso.
- Compatible con arquitecturas Event-Driven futuras (cada registro puede ser un evento proyectado).

---

## 2. Idempotencia y Consistencia

### Estrategia actual (Request-Time Reconciliation)

En la implementación actual, cada `GET /v1/reconciliation/{paymentId}` dispara una nueva conciliación en tiempo real. Esto garantiza datos frescos pero implica llamar siempre al procesador externo.

**Cuándo extender esto:**

```java
// Patrón para activar cache de corto plazo (ej: TTL de 60 segundos):
Optional<ReconciliationResult> cached = loadReconciliationResult
    .findLatestByPaymentId(paymentId)
    .filter(r -> r.getReconciledAt().isAfter(LocalDateTime.now().minusSeconds(60)));

if (cached.isPresent()) return cached.get();
// else: realizar reconciliación fresca
```

La infraestructura ya está preparada (`LoadReconciliationResultPort`) pero el TTL cache no está activado por defecto para garantizar consistencia en el MVP.

### Gestión de la tolerancia de fechas

Se usa una ventana de **±5 minutos** para comparar `transactionDate` entre ambas fuentes. Esto absorbe:
- Diferencias de zona horaria mal configuradas en el procesador.
- Latencia en el timestamping entre la autorización y el settlement.

Este valor está externalizable en `ReconciliationService.DATE_TOLERANCE_MINUTES` si se necesita ajustar por tipo de pago.

---

## 3. Autenticación con el Procesador Externo

Cada llamada al procesador requiere un Bearer token obtenido previamente mediante credenciales (usuario, contraseña, canal).

### Flujo

```
findProcessorPaymentById(paymentId)
  └─ ProcessorAuthService.getValidToken()
       ├─ token válido en caché → retorna inmediatamente
       └─ token ausente o expirado → POST /auth/token { username, password, channel }
                                          └─ cachea token con TTL (expiresIn - 60s)
  └─ GET /payments/{id}
       Authorization: Bearer <token>
```

### Decisiones

| Decisión | Razonamiento |
|----------|-------------|
| **Caché del token en memoria** | Evita un round-trip de autenticación en cada conciliación. El token se renueva solo cuando expira (TTL del procesador menos 60 s de margen). |
| **`synchronized` en `getValidToken()`** | Previene que múltiples threads concurrentes disparen varias solicitudes de token simultáneamente (thundering herd). El coste es mínimo porque la renovación es infrecuente. |
| **`invalidateToken()` ante HTTP 401** | Si el procesador rechaza el token (expiración server-side antes del TTL local), `PaymentProcessorClient` invalida el caché y relanza la excepción. El `@Retry` reintenta automáticamente, obteniendo un token fresco en el siguiente intento sin intervención manual. |
| **Credenciales en `application.yml` vía env vars** | `${PROCESSOR_USERNAME}`, `${PROCESSOR_PASSWORD}`, `${PROCESSOR_CHANNEL}` nunca se hardcodean. El perfil `local` las sobreescribe para apuntar al WireMock de desarrollo. |

### Configuración

```yaml
payment-processor:
  auth:
    url: /auth/token
    username: ${PROCESSOR_USERNAME:reconciliation-service}
    password: ${PROCESSOR_PASSWORD:secret-2024}
    channel: ${PROCESSOR_CHANNEL:API}
```

---

## 4. Circuit Breaker y Resiliencia

El cliente externo (`PaymentProcessorClient`) tiene dos capas de protección:

| Mecanismo | Configuración | Propósito |
|-----------|--------------|-----------|
| **Retry** | 3 intentos, backoff exponencial 500ms×2 | Errores transitorios de red y renovación de token tras 401 |
| **Circuit Breaker** | 50% failure rate / ventana de 10 calls | Proteger al procesador y al sistema propio |

Cuando el Circuit Breaker está abierto, el fallback lanza `ProcessorUnavailableException`, que el `GlobalExceptionHandler` convierte en **HTTP 503** con un mensaje explícito para el consumidor.

La interacción entre el Retry y la autenticación es deliberada: un HTTP 401 invalida el token y relanza la excepción, permitiendo que el Retry obtenga un token nuevo en el siguiente intento sin lógica adicional.

---

## 5. Evolución a Modelo Event-Driven

El diseño actual facilita la migración a un modelo reactivo/asíncrono en tres pasos:

### Paso 1: Separar la conciliación del request-response

Introducir un tópico Kafka `reconciliation.requested`:

```
Client → POST /v1/reconciliation/{paymentId} → KafkaProducer → Topic: reconciliation.requested
                                                              ↓
                                              ReconciliationConsumer (async)
                                                              ↓
                                              DB (reconciliation_results)
                                                              ↓
                                              Topic: reconciliation.completed
```

El `GET /v1/reconciliation/{paymentId}` solo lee de la DB (ya calculado).

### Paso 2: Batch nocturno de conciliación

Un job programado (Spring Batch o Quartz) consulta todos los pagos del día anterior y dispara eventos de conciliación en masa, sin sobrecargar el procesador en horas pico.

### Paso 3: Alertas automáticas por discrepancia

Un consumidor de `reconciliation.completed` filtra eventos con `status != CONCILIATED` y publica en `reconciliation.alerts`. Un microservicio de notificaciones los lee y alerta al equipo de operaciones vía Slack/PagerDuty.

### Por qué la arquitectura actual facilita esto

- **Los puertos son contratos**: `LoadProcessorPaymentPort` puede ser implementado por un consumidor Kafka en lugar de un HTTP client sin cambiar nada en el dominio.
- **El dominio es puro**: `ReconciliationService` no tiene `@Async`, ni Kafka, ni WebClient. Solo Java. Migrar es cambiar los adaptadores, no el core.
- **Los snapshots en DB** ya funcionan como proyecciones: un event store puede reemplazarlos sin cambiar el modelo de lectura.

---

## 6. Escalabilidad Horizontal

La API es **stateless** por diseño. Para escalar:

- **Read replicas** de PostgreSQL para las queries de `GET` (reconciliaciones ya calculadas).
- **Connection pooling** con HikariCP (configurado con pool de 20 conexiones).
- **Caché distribuida** (Redis): añadir un adaptador `RedisCacheReconciliationAdapter` que implemente `LoadReconciliationResultPort`, anteponiendo Redis a la DB con TTL configurable.
- **Rate limiting** en el API Gateway para proteger el procesador externo de picos de tráfico.

---

## 7. Elecciones de Stack

| Decisión | Alternativa Considerada | Por qué esta |
|----------|------------------------|--------------|
| **MapStruct** sobre ModelMapper | ModelMapper (reflection) | Tiempo de compilación, null-safety, más rápido |
| **Records Java** para VOs | Clases con @Value Lombok | Inmutabilidad garantizada por el compilador, sintaxis más clara |
| **JSONB PostgreSQL** para snapshots | Tabla `discrepancies` separada | Menos joins, schema-flexible para añadir campos |
| **Flyway** sobre Liquibase | Liquibase | SQL puro, sin XML/YAML, menor curva de aprendizaje |
| **WebClient** sobre RestTemplate | RestTemplate, Feign | Non-blocking, soporte nativo para Circuit Breaker reactivo |
