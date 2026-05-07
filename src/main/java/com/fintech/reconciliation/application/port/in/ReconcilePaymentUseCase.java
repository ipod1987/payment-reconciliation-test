package com.fintech.reconciliation.application.port.in;

import com.fintech.reconciliation.domain.model.ReconciliationResult;
import jakarta.validation.constraints.NotBlank;

/**
 * Puerto de entrada (driving port): contrato que expone la capa de aplicación
 * hacia los adaptadores de entrada (REST, gRPC, mensajería, etc.).
 *
 * Cualquier adaptador de entrada depende de esta interfaz, nunca del servicio concreto.
 */
public interface ReconcilePaymentUseCase {

    /**
     * Concilia un pago verificando consistencia entre sistema interno y procesador externo.
     *
     * @param paymentId identificador único del pago a conciliar
     * @return resultado detallado de la conciliación con estado y discrepancias
     * @throws com.fintech.reconciliation.domain.exception.PaymentNotFoundException si el pago
     *         no existe en ninguna fuente
     */
    ReconciliationResult reconcile(@NotBlank String paymentId);
}
