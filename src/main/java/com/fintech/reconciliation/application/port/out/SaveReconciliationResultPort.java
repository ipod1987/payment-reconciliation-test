package com.fintech.reconciliation.application.port.out;

import com.fintech.reconciliation.domain.model.ReconciliationResult;

/**
 * Puerto de salida (driven port): contrato para persistir el resultado de conciliación.
 * Permite auditoría y evita recalcular si el resultado ya existe (idempotencia).
 */
public interface SaveReconciliationResultPort {

    ReconciliationResult save(ReconciliationResult result);
}
