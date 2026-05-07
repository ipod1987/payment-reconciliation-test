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

    public enum DiscrepancyType {
        AMOUNT_MISMATCH,
        DATE_MISMATCH,
        STATUS_MISMATCH,
        MISSING_RECORD
    }

    public static Discrepancy amountMismatch(String internalAmount, String processorAmount) {
        return Discrepancy.builder()
            .type(DiscrepancyType.AMOUNT_MISMATCH)
            .field("amount")
            .internalValue(internalAmount)
            .processorValue(processorAmount)
            .build();
    }

    public static Discrepancy dateMismatch(String internalDate, String processorDate) {
        return Discrepancy.builder()
            .type(DiscrepancyType.DATE_MISMATCH)
            .field("transactionDate")
            .internalValue(internalDate)
            .processorValue(processorDate)
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
}
