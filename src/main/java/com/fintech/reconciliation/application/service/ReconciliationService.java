package com.fintech.reconciliation.application.service;

import com.fintech.reconciliation.application.port.in.ReconcilePaymentUseCase;
import com.fintech.reconciliation.application.port.out.LoadInternalPaymentPort;
import com.fintech.reconciliation.application.port.out.LoadProcessorPaymentPort;
import com.fintech.reconciliation.application.port.out.LoadReconciliationResultPort;
import com.fintech.reconciliation.application.port.out.LoadSoapProcessorPaymentPort;
import com.fintech.reconciliation.application.port.out.SaveReconciliationResultPort;
import com.fintech.reconciliation.domain.exception.PaymentNotFoundException;
import com.fintech.reconciliation.domain.kernel.PaymentComparisonKernel;
import com.fintech.reconciliation.domain.model.Payment;
import com.fintech.reconciliation.domain.model.ReconciliationResult;
import com.fintech.reconciliation.domain.model.valueobject.PaymentId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationService implements ReconcilePaymentUseCase {

    private final LoadInternalPaymentPort      loadInternalPayment;
    private final LoadProcessorPaymentPort     loadProcessorPayment;
    private final LoadSoapProcessorPaymentPort loadSoapProcessorPayment;
    private final LoadReconciliationResultPort loadReconciliationResult;
    private final SaveReconciliationResultPort saveReconciliationResult;

    @Override
    public ReconciliationResult reconcile(String rawPaymentId) {
        log.info("Starting 3-way reconciliation for paymentId={}", rawPaymentId);

        PaymentId paymentId = PaymentId.of(rawPaymentId);

        // ── fetch all three sources concurrently ──────────────────────────────
        Mono<Optional<Payment>> internalMono = Mono
            .fromCallable(() -> loadInternalPayment.findInternalPaymentById(rawPaymentId))
            .subscribeOn(Schedulers.boundedElastic());

        Mono<Optional<Payment>> jsonProcessorMono = Mono
            .fromCallable(() -> loadProcessorPayment.findProcessorPaymentById(rawPaymentId))
            .subscribeOn(Schedulers.boundedElastic());

        Mono<Optional<Payment>> soapProcessorMono = Mono
            .fromCallable(() -> loadSoapProcessorPayment.findSoapProcessorPaymentById(rawPaymentId))
            .subscribeOn(Schedulers.boundedElastic());

        ReconciliationResult result = Mono.zip(internalMono, jsonProcessorMono, soapProcessorMono)
            .map(tuple -> {
                Optional<Payment> internal  = tuple.getT1();
                Optional<Payment> json      = tuple.getT2();
                Optional<Payment> soap      = tuple.getT3();

                if (internal.isEmpty() && json.isEmpty() && soap.isEmpty()) {
                    log.warn("Payment not found in any source: paymentId={}", rawPaymentId);
                    throw new PaymentNotFoundException(rawPaymentId);
                }

                return PaymentComparisonKernel.buildResult(paymentId, internal, json, soap);
            })
            .block();

        if (result == null) {
            throw new PaymentNotFoundException(rawPaymentId);
        }

        ReconciliationResult persisted = saveReconciliationResult.save(result);
        log.info("Reconciliation complete: paymentId={}, status={}", rawPaymentId, persisted.getStatus());
        return persisted;
    }
}
