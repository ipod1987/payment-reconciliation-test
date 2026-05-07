package com.fintech.reconciliation.application.port.out;

import com.fintech.reconciliation.domain.model.Payment;

import java.util.Optional;

/**
 * Puerto de salida (driven port): contrato para consultar pagos del sistema interno.
 * La implementación (JPA, JDBC, cache, etc.) vive en infrastructure y es invisible al dominio.
 */
public interface LoadInternalPaymentPort {

    Optional<Payment> findInternalPaymentById(String paymentId);
}
