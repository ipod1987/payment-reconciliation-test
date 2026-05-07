package com.fintech.reconciliation.application.port.out;

import com.fintech.reconciliation.domain.model.Payment;

import java.util.Optional;

/**
 * Puerto de salida (driven port): contrato para consultar pagos del procesador externo.
 * El adaptador concreto usa WebClient + Circuit Breaker, pero el dominio no sabe nada de eso.
 */
public interface LoadProcessorPaymentPort {

    Optional<Payment> findProcessorPaymentById(String paymentId);
}
