package com.fintech.reconciliation.domain.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class Discrepancy {

    DiscrepancyType type;
    String field;
    String internalValue;
    String processorValue;
    /** Which external source this discrepancy was detected against. Null in legacy data. */
    String processorSource;

    public enum DiscrepancyType {
        AMOUNT_MISMATCH,
        DATE_MISMATCH,
        STATUS_MISMATCH,
        MISSING_RECORD
    }

    // ── factory methods with explicit source ─────────────────────────────────

    public static Discrepancy amountMismatch(String internalAmount, String processorAmount, String source) {
        return Discrepancy.builder()
            .type(DiscrepancyType.AMOUNT_MISMATCH)
            .field("amount")
            .internalValue(internalAmount)
            .processorValue(processorAmount)
            .processorSource(source)
            .build();
    }

    public static Discrepancy dateMismatch(String internalDate, String processorDate, String source) {
        return Discrepancy.builder()
            .type(DiscrepancyType.DATE_MISMATCH)
            .field("transactionDate")
            .internalValue(internalDate)
            .processorValue(processorDate)
            .processorSource(source)
            .build();
    }

    public static Discrepancy missingInInternal(String paymentId) {
        return Discrepancy.builder()
            .type(DiscrepancyType.MISSING_RECORD)
            .field("paymentId")
            .internalValue(null)
            .processorValue(paymentId)
            .build();
    }

    public static Discrepancy missingInProcessor(String paymentId) {
        return Discrepancy.builder()
            .type(DiscrepancyType.MISSING_RECORD)
            .field("paymentId")
            .internalValue(paymentId)
            .processorValue(null)
            .build();
    }

    public static Discrepancy missingInJsonProcessor(String paymentId) {
        return Discrepancy.builder()
            .type(DiscrepancyType.MISSING_RECORD)
            .field("paymentId")
            .internalValue(paymentId)
            .processorValue(null)
            .processorSource("JSON_PROCESSOR")
            .build();
    }

    public static Discrepancy missingInSoapProcessor(String paymentId) {
        return Discrepancy.builder()
            .type(DiscrepancyType.MISSING_RECORD)
            .field("paymentId")
            .internalValue(paymentId)
            .processorValue(null)
            .processorSource("SOAP_PROCESSOR")
            .build();
    }

    // ── backward-compat overloads (no source label) ──────────────────────────

    public static Discrepancy amountMismatch(String internalAmount, String processorAmount) {
        return amountMismatch(internalAmount, processorAmount, null);
    }

    public static Discrepancy dateMismatch(String internalDate, String processorDate) {
        return dateMismatch(internalDate, processorDate, null);
    }
}
