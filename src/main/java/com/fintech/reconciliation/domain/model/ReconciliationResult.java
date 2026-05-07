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
    Payment soapProcessorPayment;
    LocalDateTime reconciledAt;

    public Optional<Payment> getInternalPayment() {
        return Optional.ofNullable(internalPayment);
    }

    /** JSON processor snapshot. */
    public Optional<Payment> getProcessorPayment() {
        return Optional.ofNullable(processorPayment);
    }

    /** SOAP processor snapshot. */
    public Optional<Payment> getSoapProcessorPayment() {
        return Optional.ofNullable(soapProcessorPayment);
    }

    public boolean hasDiscrepancies() {
        return discrepancies != null && !discrepancies.isEmpty();
    }

    // ── 3-source factory methods ──────────────────────────────────────────────

    public static ReconciliationResult conciliated(
        PaymentId paymentId,
        Payment internalPayment,
        Payment jsonProcessorPayment,
        Payment soapProcessorPayment
    ) {
        return ReconciliationResult.builder()
            .paymentId(paymentId)
            .status(ReconciliationStatus.CONCILIATED)
            .discrepancies(List.of())
            .internalPayment(internalPayment)
            .processorPayment(jsonProcessorPayment)
            .soapProcessorPayment(soapProcessorPayment)
            .reconciledAt(LocalDateTime.now())
            .build();
    }

    public static ReconciliationResult withDiscrepancies(
        PaymentId paymentId,
        ReconciliationStatus status,
        List<Discrepancy> discrepancies,
        Payment internalPayment,
        Payment jsonProcessorPayment,
        Payment soapProcessorPayment
    ) {
        return ReconciliationResult.builder()
            .paymentId(paymentId)
            .status(status)
            .discrepancies(discrepancies)
            .internalPayment(internalPayment)
            .processorPayment(jsonProcessorPayment)
            .soapProcessorPayment(soapProcessorPayment)
            .reconciledAt(LocalDateTime.now())
            .build();
    }

    // ── 2-source factory methods (backward-compat for tests/existing code) ───

    public static ReconciliationResult conciliated(
        PaymentId paymentId,
        Payment internalPayment,
        Payment processorPayment
    ) {
        return conciliated(paymentId, internalPayment, processorPayment, null);
    }

    public static ReconciliationResult withDiscrepancies(
        PaymentId paymentId,
        ReconciliationStatus status,
        List<Discrepancy> discrepancies,
        Payment internalPayment,
        Payment processorPayment
    ) {
        return withDiscrepancies(paymentId, status, discrepancies, internalPayment, processorPayment, null);
    }
}
