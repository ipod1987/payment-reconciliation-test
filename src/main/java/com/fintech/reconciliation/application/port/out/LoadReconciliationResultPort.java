package com.fintech.reconciliation.application.port.out;

import com.fintech.reconciliation.domain.model.ReconciliationResult;

import java.util.Optional;

/**
 * Puerto de salida (driven port): contrato para leer resultados de conciliación ya calculados.
 * Usado para implementar cache de conciliación y respuesta idempotente.
 */
public interface LoadReconciliationResultPort {

    Optional<ReconciliationResult> findLatestByPaymentId(String paymentId);
}
