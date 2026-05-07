package com.fintech.reconciliation.domain.kernel;

import com.fintech.reconciliation.domain.model.Discrepancy;
import com.fintech.reconciliation.domain.model.Payment;
import com.fintech.reconciliation.domain.model.ReconciliationResult;
import com.fintech.reconciliation.domain.model.valueobject.PaymentId;
import com.fintech.reconciliation.domain.model.valueobject.ReconciliationStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Pure-domain 3-way comparison kernel — zero Spring/framework dependencies.
 *
 * <p>Source labels used in discrepancies:
 * <ul>
 *   <li>{@code "JSON_PROCESSOR"} — legacy REST/JSON external processor</li>
 *   <li>{@code "SOAP_PROCESSOR"} — legacy SOAP/XML external processor</li>
 * </ul>
 */
public final class PaymentComparisonKernel {

    public static final long DATE_TOLERANCE_MINUTES = 5;

    private static final String JSON_SOURCE = "JSON_PROCESSOR";
    private static final String SOAP_SOURCE = "SOAP_PROCESSOR";

    private PaymentComparisonKernel() {}

    /**
     * Builds a reconciliation result by comparing all three sources.
     *
     * <p>Decision table:
     * <pre>
     * Internal | JSON | SOAP | Status
     * ---------|------|------|-------
     *    ❌    |  any |  any | MISSING_IN_INTERNAL
     *    ✅    |  ❌  |  ❌  | MISSING_IN_PROCESSOR
     *    ✅    |  ❌  |  ✅  | MISSING_IN_JSON_PROCESSOR
     *    ✅    |  ✅  |  ❌  | MISSING_IN_SOAP_PROCESSOR
     *    ✅    |  ✅  |  ✅  | comparison result
     * </pre>
     */
    public static ReconciliationResult buildResult(
        PaymentId paymentId,
        Optional<Payment> internalOpt,
        Optional<Payment> jsonOpt,
        Optional<Payment> soapOpt
    ) {
        if (internalOpt.isEmpty()) {
            return missingInInternal(paymentId, jsonOpt.orElse(null), soapOpt.orElse(null));
        }

        Payment internal = internalOpt.get();
        boolean hasJson = jsonOpt.isPresent();
        boolean hasSoap = soapOpt.isPresent();

        if (!hasJson && !hasSoap) {
            return ReconciliationResult.withDiscrepancies(
                paymentId, ReconciliationStatus.MISSING_IN_PROCESSOR,
                List.of(Discrepancy.missingInProcessor(paymentId.value())),
                internal, null, null
            );
        }
        if (!hasJson) {
            return missingInJsonProcessor(paymentId, internal, soapOpt.get());
        }
        if (!hasSoap) {
            return missingInSoapProcessor(paymentId, internal, jsonOpt.get());
        }

        return compareAll(paymentId, internal, jsonOpt.get(), soapOpt.get());
    }

    // ── missing cases ─────────────────────────────────────────────────────────

    private static ReconciliationResult missingInInternal(
        PaymentId paymentId, Payment json, Payment soap
    ) {
        return ReconciliationResult.withDiscrepancies(
            paymentId, ReconciliationStatus.MISSING_IN_INTERNAL,
            List.of(Discrepancy.missingInInternal(paymentId.value())),
            null, json, soap
        );
    }

    private static ReconciliationResult missingInJsonProcessor(
        PaymentId paymentId, Payment internal, Payment soap
    ) {
        List<Discrepancy> disc = new ArrayList<>();
        disc.add(Discrepancy.missingInJsonProcessor(paymentId.value()));
        disc.addAll(detectDiscrepancies(internal, soap, SOAP_SOURCE));

        ReconciliationStatus status = disc.stream()
            .anyMatch(d -> d.getType() != Discrepancy.DiscrepancyType.MISSING_RECORD)
            ? resolveComparisonStatus(disc.stream()
                .filter(d -> d.getType() != Discrepancy.DiscrepancyType.MISSING_RECORD)
                .toList())
            : ReconciliationStatus.MISSING_IN_JSON_PROCESSOR;

        return ReconciliationResult.withDiscrepancies(paymentId, status, disc, internal, null, soap);
    }

    private static ReconciliationResult missingInSoapProcessor(
        PaymentId paymentId, Payment internal, Payment json
    ) {
        List<Discrepancy> disc = new ArrayList<>();
        disc.add(Discrepancy.missingInSoapProcessor(paymentId.value()));
        disc.addAll(detectDiscrepancies(internal, json, JSON_SOURCE));

        ReconciliationStatus status = disc.stream()
            .anyMatch(d -> d.getType() != Discrepancy.DiscrepancyType.MISSING_RECORD)
            ? resolveComparisonStatus(disc.stream()
                .filter(d -> d.getType() != Discrepancy.DiscrepancyType.MISSING_RECORD)
                .toList())
            : ReconciliationStatus.MISSING_IN_SOAP_PROCESSOR;

        return ReconciliationResult.withDiscrepancies(paymentId, status, disc, internal, json, null);
    }

    // ── 3-way comparison ──────────────────────────────────────────────────────

    private static ReconciliationResult compareAll(
        PaymentId paymentId, Payment internal, Payment json, Payment soap
    ) {
        List<Discrepancy> discrepancies = new ArrayList<>();
        discrepancies.addAll(detectDiscrepancies(internal, json, JSON_SOURCE));
        discrepancies.addAll(detectDiscrepancies(internal, soap, SOAP_SOURCE));

        if (discrepancies.isEmpty()) {
            return ReconciliationResult.conciliated(paymentId, internal, json, soap);
        }

        ReconciliationStatus status = resolveComparisonStatus(discrepancies);
        return ReconciliationResult.withDiscrepancies(paymentId, status, discrepancies, internal, json, soap);
    }

    // ── discrepancy detection ─────────────────────────────────────────────────

    private static List<Discrepancy> detectDiscrepancies(Payment internal, Payment external, String source) {
        List<Discrepancy> result = new ArrayList<>();
        if (internal.getAmount().hasMaterialDiscrepancyWith(external.getAmount())) {
            result.add(Discrepancy.amountMismatch(
                internal.getAmount().toString(),
                external.getAmount().toString(),
                source
            ));
        }
        if (!internal.hasTransactionDateWithin(external, DATE_TOLERANCE_MINUTES)) {
            String internalDate = internal.getTransactionDate() != null
                ? internal.getTransactionDate().toString() : "null";
            String externalDate = external.getTransactionDate() != null
                ? external.getTransactionDate().toString() : "null";
            result.add(Discrepancy.dateMismatch(internalDate, externalDate, source));
        }
        return result;
    }

    private static ReconciliationStatus resolveComparisonStatus(List<Discrepancy> discrepancies) {
        boolean hasAmount = discrepancies.stream()
            .anyMatch(d -> d.getType() == Discrepancy.DiscrepancyType.AMOUNT_MISMATCH);
        boolean hasDate = discrepancies.stream()
            .anyMatch(d -> d.getType() == Discrepancy.DiscrepancyType.DATE_MISMATCH);

        if (hasAmount && hasDate) return ReconciliationStatus.MULTIPLE_DISCREPANCIES;
        if (hasAmount)           return ReconciliationStatus.DISCREPANCY_AMOUNT;
        if (hasDate)             return ReconciliationStatus.DISCREPANCY_DATE;
        return ReconciliationStatus.MULTIPLE_DISCREPANCIES;
    }
}
