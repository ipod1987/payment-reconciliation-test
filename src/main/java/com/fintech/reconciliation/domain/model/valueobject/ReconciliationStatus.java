package com.fintech.reconciliation.domain.model.valueobject;

public enum ReconciliationStatus {

    /**
     * El pago existe en ambos sistemas con datos idénticos.
     */
    CONCILIATED("Payment matches across both systems"),

    /**
     * El monto difiere entre el sistema interno y el procesador.
     */
    DISCREPANCY_AMOUNT("Amount mismatch between internal and processor"),

    /**
     * La fecha/hora de la transacción difiere entre fuentes.
     */
    DISCREPANCY_DATE("Transaction date mismatch between internal and processor"),

    /**
     * Existen múltiples campos con discrepancia simultánea.
     */
    MULTIPLE_DISCREPANCIES("Multiple fields mismatch between internal and processor"),

    /**
     * El pago existe en el procesador externo pero no en el sistema interno.
     */
    MISSING_IN_INTERNAL("Payment found in processor but missing in internal system"),

    /**
     * El pago existe en el sistema interno pero no fue recibido por el procesador.
     */
    MISSING_IN_PROCESSOR("Payment found in internal system but missing in processor");

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
