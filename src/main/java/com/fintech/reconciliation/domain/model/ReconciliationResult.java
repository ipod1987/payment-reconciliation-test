package com.fintech.reconciliation.domain.model;

import com.fintech.reconciliation.domain.model.valueobject.PaymentId;
import com.fintech.reconciliation.domain.model.valueobject.ReconciliationStatus;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Value
@Builder
public class ReconciliationResult {

    PaymentId paymentId;
    ReconciliationStatus status;
    List<Discrepancy> discrepancies;
    Payment internalPayment;
    Payment processorPayment;
    LocalDateTime reconciledAt;

    public Optional<Payment> getInternalPayment() {
        return Optional.ofNullable(internalPayment);
    }

    public Optional<Payment> getProcessorPayment() {
        return Optional.ofNullable(processorPayment);
    }

    public boolean hasDiscrepancies() {
        return discrepancies != null && !discrepancies.isEmpty();
    }

    /**
     * Factory: resultado para pago completamente conciliado (sin discrepancias).
     */
    public static ReconciliationResult conciliated(
        PaymentId paymentId,
        Payment internalPayment,
        Payment processorPayment
    ) {
        return ReconciliationResult.builder()
            .paymentId(paymentId)
            .status(ReconciliationStatus.CONCILIATED)
            .discrepancies(List.of())
            .internalPayment(internalPayment)
            .processorPayment(processorPayment)
            .reconciledAt(LocalDateTime.now())
            .build();
    }

    /**
     * Factory: resultado con discrepancias detectadas.
     */
    public static ReconciliationResult withDiscrepancies(
        PaymentId paymentId,
        ReconciliationStatus status,
        List<Discrepancy> discrepancies,
        Payment internalPayment,
        Payment processorPayment
    ) {
        return ReconciliationResult.builder()
            .paymentId(paymentId)
            .status(status)
            .discrepancies(discrepancies)
            .internalPayment(internalPayment)
            .processorPayment(processorPayment)
            .reconciledAt(LocalDateTime.now())
            .build();
    }
}
