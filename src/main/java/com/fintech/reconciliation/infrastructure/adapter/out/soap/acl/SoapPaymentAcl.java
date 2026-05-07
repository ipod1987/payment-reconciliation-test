package com.fintech.reconciliation.infrastructure.adapter.out.soap.acl;

import com.fintech.reconciliation.domain.model.Payment;
import com.fintech.reconciliation.domain.model.valueobject.Money;
import com.fintech.reconciliation.domain.model.valueobject.PaymentId;
import com.fintech.reconciliation.infrastructure.adapter.out.soap.trama.SoapPaymentResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Anti-Corruption Layer: translates SOAP trama DTOs into domain Payment objects.
 * No domain type ever leaks into the trama layer, and no SOAP type ever enters the domain.
 */
@Component
public class SoapPaymentAcl {

    private static final DateTimeFormatter SOAP_DATE_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    public Payment toDomain(SoapPaymentResponse response) {
        return Payment.builder()
            .id(PaymentId.of(response.getPaymentId()))
            .amount(Money.of(new BigDecimal(response.getAmount()), response.getCurrency()))
            .status(response.getStatus())
            .description(response.getDescription())
            .transactionDate(parseDate(response.getProcessedAt()))
            .source(Payment.PaymentSource.SOAP_PROCESSOR)
            .build();
    }

    private LocalDateTime parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return LocalDateTime.parse(raw, SOAP_DATE_FORMAT);
    }
}
