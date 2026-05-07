package com.fintech.reconciliation.application.port.out;

import com.fintech.reconciliation.domain.model.Payment;

import java.util.Optional;

public interface LoadSoapProcessorPaymentPort {
    Optional<Payment> findSoapProcessorPaymentById(String paymentId);
}
