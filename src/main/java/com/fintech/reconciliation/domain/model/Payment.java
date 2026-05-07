package com.fintech.reconciliation.domain.model;

import com.fintech.reconciliation.domain.model.valueobject.Money;
import com.fintech.reconciliation.domain.model.valueobject.PaymentId;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class Payment {

    PaymentId id;
    Money amount;
    String status;
    String description;
    LocalDateTime transactionDate;
    PaymentSource source;

    public enum PaymentSource {
        INTERNAL, PROCESSOR
    }

    public boolean hasTransactionDateWithin(Payment other, long toleranceMinutes) {
        if (this.transactionDate == null || other.transactionDate == null) {
            return false;
        }
        long minutesDiff = Math.abs(
            java.time.Duration.between(this.transactionDate, other.transactionDate).toMinutes()
        );
        return minutesDiff <= toleranceMinutes;
    }
}
