package com.fintech.reconciliation.domain.model.valueobject;

public enum ReconciliationStatus {

    CONCILIATED("Payment matches across all three systems"),

    DISCREPANCY_AMOUNT("Amount mismatch detected between sources"),

    DISCREPANCY_DATE("Transaction date mismatch detected between sources"),

    MULTIPLE_DISCREPANCIES("Multiple fields mismatch between sources"),

    MISSING_IN_INTERNAL("Payment found in processor(s) but missing in internal system"),

    MISSING_IN_PROCESSOR("Payment found in internal system but missing in all external processors"),

    MISSING_IN_JSON_PROCESSOR("Payment found in internal and SOAP processor but missing in JSON processor"),

    MISSING_IN_SOAP_PROCESSOR("Payment found in internal and JSON processor but missing in SOAP processor");

    private final String description;

    ReconciliationStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean isFullyReconciled() {
        return this == CONCILIATED;
    }

    public boolean requiresManualReview() {
        return this != CONCILIATED;
    }
}
