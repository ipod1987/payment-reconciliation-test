# Instrucciones de Ejecución y Testing

## Prerrequisitos

- Java 21+
- Docker & Docker Compose

---

## 1. Levantar el entorno completo

```bash
# Construye la imagen y arranca PostgreSQL, WireMock (JSON + SOAP) y la aplicación
docker compose up -d

# Ver logs de la aplicación
make logs-app
```

La API quedará disponible en: `http://localhost:8080/api`  
Swagger UI: `http://localhost:8080/api/swagger-ui.html`

> **Solo infraestructura (sin la app):** `make up-db`  
> Útil para ejecutar la app desde el IDE con perfil `local`.

---

## 2. Insertar datos de prueba

```bash
make seed
```

Esto ejecuta `docker/postgres/init/01_seed_data.sql` e inserta 5 pagos en `internal_payments`.

---

## 3. Obtener token JWT

Todos los endpoints de reconciliación requieren Bearer token.

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/token \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | jq -r .accessToken
```

Credenciales de desarrollo disponibles:

| Username | Password |
|----------|----------|
| `admin` | `admin123` |
| `reconciler` | `reconciler2024` |

Guarda el token para usarlo en las siguientes peticiones:

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/token \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | jq -r .accessToken)
```

---

## 4. curl de prueba

### Caso 1: Pago conciliado correctamente (CONCILIATED)

```bash
curl -s http://localhost:8080/api/v1/reconciliation/pay_abc123 \
  -H "Authorization: Bearer $TOKEN" | jq .
```

**Respuesta esperada:**
```json
{
  "paymentId": "pay_abc123",
  "status": "CONCILIATED",
  "statusDescription": "Payment matches across all three systems",
  "fullyReconciled": true,
  "discrepancies": []
}
```

---

### Caso 2: Discrepancia de monto (DISCREPANCY_AMOUNT)

```bash
curl -s http://localhost:8080/api/v1/reconciliation/pay_disc001 \
  -H "Authorization: Bearer $TOKEN" | jq .
```

---

### Caso 3: Ausente en procesador SOAP (MISSING_IN_SOAP_PROCESSOR)

```bash
curl -s http://localhost:8080/api/v1/reconciliation/pay_missing \
  -H "Authorization: Bearer $TOKEN" | jq .
```

---

### Caso 4: Discrepancia de fecha (DISCREPANCY_DATE)

```bash
curl -s http://localhost:8080/api/v1/reconciliation/pay_date001 \
  -H "Authorization: Bearer $TOKEN" | jq .
```

---

### Caso 5: Pago ausente en sistema interno (MISSING_IN_INTERNAL)

```bash
curl -s http://localhost:8080/api/v1/reconciliation/pay_extonly \
  -H "Authorization: Bearer $TOKEN" | jq .
```

---

### Caso 6: Pago no encontrado en ninguna fuente (404)

```bash
curl -s http://localhost:8080/api/v1/reconciliation/pay_ghost \
  -H "Authorization: Bearer $TOKEN" | jq .
```

**Respuesta esperada:**
```json
{
  "httpStatus": 404,
  "errorCode": "PAYMENT_NOT_FOUND",
  "message": "Payment not found in any source: pay_ghost"
}
```

---

## 5. Ejecutar tests

```bash
# Todos los tests
./gradlew test

# Solo tests del servicio de dominio
./gradlew test --tests "*.ReconciliationServiceTest"

# Solo tests del controlador
./gradlew test --tests "*.ReconciliationControllerTest"

# Tests del value object Money
./gradlew test --tests "*.MoneyTest"
```

---

## 6. Compilar sin tests

```bash
./gradlew build -x test
```

---

## 7. Verificar Circuit Breaker (Actuator)

```bash
# Estado del Circuit Breaker
curl -s http://localhost:8080/api/actuator/circuitbreakers | jq .

# Métricas de la aplicación
curl -s http://localhost:8080/api/actuator/metrics | jq .names
```

---

## Docker — estructura de servicios

```
docker-compose.yml
  ├── postgres (postgres:16-alpine)
  │     └── healthcheck: pg_isready cada 10s
  ├── wiremock-json (wiremock/wiremock:3.6.0-alpine) — puerto 9091
  │     └── monta docker/wiremock/json/mappings/ — stubs REST
  ├── wiremock-soap (wiremock/wiremock:3.6.0-alpine) — puerto 9092
  │     └── monta docker/wiremock/soap/mappings/ — stubs SOAP/XML
  └── app (imagen multi-stage desde Dockerfile)
        ├── depends_on: postgres (condition: service_healthy)
        └── SPRING_PROFILES_ACTIVE=docker
```
