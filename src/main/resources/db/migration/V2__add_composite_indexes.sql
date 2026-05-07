-- =============================================================================
-- V2: Índices compuestos para búsquedas eficientes por payment_id y fecha
-- =============================================================================

-- internal_payments: filtra por payment y luego acota por rango de fecha
CREATE INDEX idx_internal_payments_pid_txn_date
    ON internal_payments (payment_id, transaction_date);

-- internal_payments: escanea un rango de fechas y opcionalmente filtra por payment
CREATE INDEX idx_internal_payments_txn_date_pid
    ON internal_payments (transaction_date, payment_id);

-- reconciliation_results: historial cronológico de conciliaciones por pago
CREATE INDEX idx_reconciliation_pid_reconciled_at
    ON reconciliation_results (payment_id, reconciled_at DESC);

-- reconciliation_results: escanea rango de fechas de conciliación filtrado por pago
CREATE INDEX idx_reconciliation_reconciled_at_pid
    ON reconciliation_results (reconciled_at DESC, payment_id);
