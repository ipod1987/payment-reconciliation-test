package com.fintech.reconciliation.domain.model.valueobject;

import java.util.Objects;
import java.util.UUID;

public record PaymentId(String value) {

    public PaymentId {
        Objects.requireNonNull(value, "PaymentId cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("PaymentId cannot be blank");
        }
    }

    public static PaymentId of(String value) {
        return new PaymentId(value);
    }

    public static PaymentId generate() {
        return new PaymentId(UUID.randomUUID().toString());
    }

    @Override
    public String toString() {
        return value;
    }
}
