-- =============================================================================
-- V1: Tablas base del sistema de conciliación de pagos
-- =============================================================================

-- Tabla de pagos internos (fuente de verdad del sistema propio)
CREATE TABLE IF NOT EXISTS internal_payments (
    id               UUID         NOT NULL DEFAULT gen_random_uuid(),
    payment_id       VARCHAR(100) NOT NULL,
    amount           NUMERIC(19, 2) NOT NULL,
    currency         VARCHAR(3)      NOT NULL,
    status           VARCHAR(50)  NOT NULL,
    description      VARCHAR(500),
    transaction_date TIMESTAMP    NOT NULL,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP,

    CONSTRAINT pk_internal_payments     PRIMARY KEY (id),
    CONSTRAINT uq_internal_payment_id   UNIQUE (payment_id),
    CONSTRAINT chk_internal_amount_pos  CHECK (amount >= 0)
);

CREATE INDEX idx_internal_payments_payment_id ON internal_payments (payment_id);
CREATE INDEX idx_internal_payments_status     ON internal_payments (status);
CREATE INDEX idx_internal_payments_txn_date   ON internal_payments (transaction_date);

COMMENT ON TABLE  internal_payments              IS 'Pagos registrados en el sistema interno de la plataforma';
COMMENT ON COLUMN internal_payments.payment_id   IS 'Identificador externo del pago (idempotency key)';
COMMENT ON COLUMN internal_payments.currency     IS 'Código ISO 4217 de tres letras';

-- =============================================================================

-- Tabla de resultados de conciliación (tabla de auditoría append-only)
-- Se escribe cada vez que se ejecuta una conciliación; nunca se actualiza.
CREATE TABLE IF NOT EXISTS reconciliation_results (
    id                       UUID        NOT NULL DEFAULT gen_random_uuid(),
    payment_id               VARCHAR(100) NOT NULL,
    status                   VARCHAR(50)  NOT NULL,
    internal_payment_snapshot JSONB,
    processor_payment_snapshot JSONB,
    discrepancies            JSONB,
    reconciled_at            TIMESTAMP   NOT NULL,
    created_at               TIMESTAMP   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_reconciliation_results PRIMARY KEY (id)
);

CREATE INDEX idx_reconciliation_payment_id   ON reconciliation_results (payment_id);
CREATE INDEX idx_reconciliation_status       ON reconciliation_results (status);
CREATE INDEX idx_reconciliation_reconciled_at ON reconciliation_results (reconciled_at DESC);

-- Índice GIN para consultas dentro del JSONB de discrepancias
CREATE INDEX idx_reconciliation_discrepancies_gin ON reconciliation_results USING GIN (discrepancies);

COMMENT ON TABLE  reconciliation_results            IS 'Historial inmutable de conciliaciones ejecutadas';
COMMENT ON COLUMN reconciliation_results.status     IS 'Estado: CONCILIATED | DISCREPANCY_AMOUNT | DISCREPANCY_DATE | MULTIPLE_DISCREPANCIES | MISSING_IN_INTERNAL | MISSING_IN_PROCESSOR';
COMMENT ON COLUMN reconciliation_results.internal_payment_snapshot IS 'Snapshot del pago interno al momento de la conciliación';
COMMENT ON COLUMN reconciliation_results.processor_payment_snapshot IS 'Snapshot del pago del procesador al momento de la conciliación';
COMMENT ON COLUMN reconciliation_results.discrepancies IS 'Array JSON de discrepancias detectadas';
