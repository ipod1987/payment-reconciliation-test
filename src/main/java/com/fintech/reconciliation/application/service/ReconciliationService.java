package com.fintech.reconciliation.application.service;

import com.fintech.reconciliation.application.port.in.ReconcilePaymentUseCase;
import com.fintech.reconciliation.application.port.out.LoadInternalPaymentPort;
import com.fintech.reconciliation.application.port.out.LoadProcessorPaymentPort;
import com.fintech.reconciliation.application.port.out.LoadReconciliationResultPort;
import com.fintech.reconciliation.application.port.out.SaveReconciliationResultPort;
import com.fintech.reconciliation.domain.exception.PaymentNotFoundException;
import com.fintech.reconciliation.domain.model.Discrepancy;
import com.fintech.reconciliation.domain.model.Payment;
import com.fintech.reconciliation.domain.model.ReconciliationResult;
import com.fintech.reconciliation.domain.model.valueobject.PaymentId;
import com.fintech.reconciliation.domain.model.valueobject.ReconciliationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationService implements ReconcilePaymentUseCase {

    private static final long DATE_TOLERANCE_MINUTES = 5;

    private final LoadInternalPaymentPort loadInternalPayment;
    private final LoadProcessorPaymentPort loadProcessorPayment;
    private final LoadReconciliationResultPort loadReconciliationResult;
    private final SaveReconciliationResultPort saveReconciliationResult;

    @Override
    @Transactional
    public ReconciliationResult reconcile(String rawPaymentId) {
        log.info("Starting reconciliation for paymentId={}", rawPaymentId);

        PaymentId paymentId = PaymentId.of(rawPaymentId);

        Optional<Payment> internalPayment = loadInternalPayment.findInternalPaymentById(rawPaymentId);
        Optional<Payment> processorPayment = loadProcessorPayment.findProcessorPaymentById(rawPaymentId);

        if (internalPayment.isEmpty() && processorPayment.isEmpty()) {
            log.warn("Payment not found in any source: paymentId={}", rawPaymentId);
            throw new PaymentNotFoundException(rawPaymentId);
        }

        ReconciliationResult result = buildReconciliationResult(paymentId, internalPayment, processorPayment);
        ReconciliationResult persisted = saveReconciliationResult.save(result);

        log.info("Reconciliation complete: paymentId={}, status={}", rawPaymentId, persisted.getStatus());
        return persisted;
    }

    private ReconciliationResult buildReconciliationResult(
        PaymentId paymentId,
        Optional<Payment> internalOpt,
        Optional<Payment> processorOpt
    ) {
        if (internalOpt.isEmpty()) {
            return buildMissingInInternalResult(paymentId, processorOpt.get());
        }
        if (processorOpt.isEmpty()) {
            return buildMissingInProcessorResult(paymentId, internalOpt.get());
        }
        return comparePayments(paymentId, internalOpt.get(), processorOpt.get());
    }

    private ReconciliationResult buildMissingInInternalResult(PaymentId paymentId, Payment processorPayment) {
        List<Discrepancy> discrepancies = List.of(
            Discrepancy.missingInInternal(paymentId.value())
        );
        return ReconciliationResult.withDiscrepancies(
            paymentId,
            ReconciliationStatus.MISSING_IN_INTERNAL,
            discrepancies,
            null,
            processorPayment
        );
    }

    private ReconciliationResult buildMissingInProcessorResult(PaymentId paymentId, Payment internalPayment) {
        List<Discrepancy> discrepancies = List.of(
            Discrepancy.missingInProcessor(paymentId.value())
        );
        return ReconciliationResult.withDiscrepancies(
            paymentId,
            ReconciliationStatus.MISSING_IN_PROCESSOR,
            discrepancies,
            internalPayment,
            null
        );
    }

    private ReconciliationResult comparePayments(
        PaymentId paymentId,
        Payment internal,
        Payment processor
    ) {
        List<Discrepancy> discrepancies = new ArrayList<>();

        detectAmountDiscrepancy(internal, processor).ifPresent(discrepancies::add);
        detectDateDiscrepancy(internal, processor).ifPresent(discrepancies::add);

        if (discrepancies.isEmpty()) {
            return ReconciliationResult.conciliated(paymentId, internal, processor);
        }

        ReconciliationStatus status = resolveStatusFromDiscrepancies(discrepancies);
        return ReconciliationResult.withDiscrepancies(paymentId, status, discrepancies, internal, processor);
    }

    private Optional<Discrepancy> detectAmountDiscrepancy(Payment internal, Payment processor) {
        if (internal.getAmount().hasMaterialDiscrepancyWith(processor.getAmount())) {
            return Optional.of(Discrepancy.amountMismatch(
                internal.getAmount().toString(),
                processor.getAmount().toString()
            ));
        }
        return Optional.empty();
    }

    private Optional<Discrepancy> detectDateDiscrepancy(Payment internal, Payment processor) {
        if (!internal.hasTransactionDateWithin(processor, DATE_TOLERANCE_MINUTES)) {
            String internalDate = internal.getTransactionDate() != null
                ? internal.getTransactionDate().toString() : "null";
            String processorDate = processor.getTransactionDate() != null
                ? processor.getTransactionDate().toString() : "null";
            return Optional.of(Discrepancy.dateMismatch(internalDate, processorDate));
        }
        return Optional.empty();
    }

    private ReconciliationStatus resolveStatusFromDiscrepancies(List<Discrepancy> discrepancies) {
        if (discrepancies.size() > 1) {
            return ReconciliationStatus.MULTIPLE_DISCREPANCIES;
        }
        return switch (discrepancies.get(0).getType()) {
            case AMOUNT_MISMATCH -> ReconciliationStatus.DISCREPANCY_AMOUNT;
            case DATE_MISMATCH   -> ReconciliationStatus.DISCREPANCY_DATE;
            default              -> ReconciliationStatus.MULTIPLE_DISCREPANCIES;
        };
    }
}
