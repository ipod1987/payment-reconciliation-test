# Instrucciones de Ejecución y Testing

## Prerrequisitos

- Java 21+
- Maven 3.9+
- Docker & Docker Compose (para PostgreSQL)

---

## 1. Levantar la base de datos

```bash
# Crear y arrancar PostgreSQL con Docker
docker run -d \
  --name reconciliation-db \
  -e POSTGRES_DB=reconciliation_db \
  -e POSTGRES_USER=reconciliation_user \
  -e POSTGRES_PASSWORD=reconciliation_pass \
  -p 5435:5432 \
  postgres:16-alpine

# Verificar que está corriendo
docker ps | grep reconciliation-db
```

---

## 2. Compilar y ejecutar

```bash
cd payment-reconciliation

# Compilar (genera código de MapStruct)
./mvnw clean compile

# Ejecutar tests unitarios
./mvnw test

# Arrancar la aplicación
./mvnw spring-boot:run
```

La API quedará disponible en: `http://localhost:8080/api`
Swagger UI: `http://localhost:8080/api/swagger-ui.html`

---

## 3. Insertar datos de prueba

```sql
-- Conectar a PostgreSQL
psql -h localhost -U reconciliation_user -d reconciliation_db

-- Insertar pagos de prueba en el sistema interno
INSERT INTO internal_payments (payment_id, amount, currency, status, description, transaction_date)
VALUES
  ('pay_abc123', 100.00, 'USD', 'APPROVED', 'E-commerce purchase', '2024-06-15 10:00:00'),
  ('pay_disc001', 100.00, 'USD', 'APPROVED', 'Subscription payment', '2024-06-15 11:00:00'),
  ('pay_missing', 250.00, 'USD', 'APPROVED', 'Wire transfer',       '2024-06-15 12:00:00');
```

---

## 4. curl de prueba

### Caso 1: Pago conciliado correctamente

```bash
curl -s -X GET "http://localhost:8080/api/v1/reconciliation/pay_abc123" \
  -H "Accept: application/json" | jq .
```

**Respuesta esperada:**
```json
{
  "paymentId": "pay_abc123",
  "status": "CONCILIATED",
  "statusDescription": "Payment matches across both systems",
  "fullyReconciled": true,
  "discrepancies": [],
  "internalPayment": {
    "paymentId": "pay_abc123",
    "amount": "100.00",
    "currency": "USD",
    "status": "APPROVED",
    "transactionDate": "2024-06-15T10:00:00",
    "source": "INTERNAL"
  },
  "processorPayment": {
    "paymentId": "pay_abc123",
    "amount": "100.00",
    "currency": "USD",
    "status": "APPROVED",
    "transactionDate": "2024-06-15T10:00:00",
    "source": "PROCESSOR"
  },
  "reconciledAt": "2024-06-15T10:05:00"
}
```

---

### Caso 2: Discrepancia de monto

```bash
curl -s -X GET "http://localhost:8080/api/v1/reconciliation/pay_disc001" \
  -H "Accept: application/json" | jq .
```

**Respuesta esperada:**
```json
{
  "paymentId": "pay_disc001",
  "status": "DISCREPANCY_AMOUNT",
  "statusDescription": "Amount mismatch between internal and processor",
  "fullyReconciled": false,
  "discrepancies": [
    {
      "type": "AMOUNT_MISMATCH",
      "field": "amount",
      "internalValue": "100.00 USD",
      "processorValue": "95.00 USD"
    }
  ],
  "internalPayment": { ... },
  "processorPayment": { ... },
  "reconciledAt": "2024-06-15T10:05:01"
}
```

---

### Caso 3: Pago ausente en procesador (MISSING_IN_PROCESSOR)

```bash
curl -s -X GET "http://localhost:8080/api/v1/reconciliation/pay_missing" \
  -H "Accept: application/json" | jq .
```

**Respuesta esperada:**
```json
{
  "paymentId": "pay_missing",
  "status": "MISSING_IN_PROCESSOR",
  "statusDescription": "Payment found in internal system but missing in processor",
  "fullyReconciled": false,
  "discrepancies": [
    {
      "type": "MISSING_RECORD",
      "field": "paymentId",
      "internalValue": "pay_missing",
      "processorValue": null
    }
  ],
  "internalPayment": { ... },
  "processorPayment": null,
  "reconciledAt": "2024-06-15T10:05:02"
}
```

---

### Caso 4: Pago no encontrado en ninguna fuente (404)

```bash
curl -s -X GET "http://localhost:8080/api/v1/reconciliation/pay_ghost" \
  -H "Accept: application/json" | jq .
```

**Respuesta esperada:**
```json
{
  "httpStatus": 404,
  "errorCode": "PAYMENT_NOT_FOUND",
  "message": "Payment not found in any source: pay_ghost",
  "fieldErrors": null,
  "timestamp": "2024-06-15T10:05:03"
}
```

---

## 5. Ejecutar tests

```bash
# Todos los tests
./mvnw test

# Solo tests unitarios del dominio
./mvnw test -Dtest=ReconciliationServiceTest

# Solo tests del controlador
./mvnw test -Dtest=ReconciliationControllerTest

# Tests del value object Money
./mvnw test -Dtest=MoneyTest

# Reporte de cobertura con JaCoCo (si se agrega el plugin)
./mvnw verify
```

---

## 6. Verificar Circuit Breaker (Actuator)

```bash
# Estado del Circuit Breaker del procesador externo
curl -s http://localhost:8080/api/actuator/circuitbreakers | jq .

# Métricas de la aplicación
curl -s http://localhost:8080/api/actuator/metrics | jq .names
```
