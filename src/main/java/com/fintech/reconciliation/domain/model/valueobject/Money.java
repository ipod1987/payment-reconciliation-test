package com.fintech.reconciliation.domain.model.valueobject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

public record Money(BigDecimal amount, String currencyCode) {

    private static final int SCALE = 2;
    private static final BigDecimal TOLERANCE = new BigDecimal("0.01");

    public Money {
        Objects.requireNonNull(amount, "Amount cannot be null");
        Objects.requireNonNull(currencyCode, "Currency cannot be null");
        Currency.getInstance(currencyCode); // valida ISO 4217
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount cannot be negative");
        }
        amount = amount.setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static Money of(BigDecimal amount, String currencyCode) {
        return new Money(amount, currencyCode);
    }

    public static Money of(String amount, String currencyCode) {
        return new Money(new BigDecimal(amount), currencyCode);
    }

    /**
     * Compara ignorando diferencias de centavos por redondeo (tolerancia 0.01).
     * Útil para detectar discrepancias reales vs. artefactos de precisión.
     */
    public boolean hasMaterialDiscrepancyWith(Money other) {
        if (!this.currencyCode.equals(other.currencyCode)) {
            return true;
        }
        return this.amount.subtract(other.amount).abs().compareTo(TOLERANCE) > 0;
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currencyCode;
    }
}
