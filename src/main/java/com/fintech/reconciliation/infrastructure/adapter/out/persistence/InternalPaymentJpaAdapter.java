package com.fintech.reconciliation.infrastructure.adapter.out.persistence;

import com.fintech.reconciliation.application.port.out.LoadInternalPaymentPort;
import com.fintech.reconciliation.domain.model.Payment;
import com.fintech.reconciliation.domain.model.valueobject.Money;
import com.fintech.reconciliation.domain.model.valueobject.PaymentId;
import com.fintech.reconciliation.infrastructure.adapter.out.persistence.entity.InternalPaymentEntity;
import com.fintech.reconciliation.infrastructure.adapter.out.persistence.repository.InternalPaymentJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class InternalPaymentJpaAdapter implements LoadInternalPaymentPort {

    private final InternalPaymentJpaRepository jpaRepository;

    @Override
    public Optional<Payment> findInternalPaymentById(String paymentId) {
        log.debug("Fetching internal payment: paymentId={}", paymentId);
        return jpaRepository.findByPaymentId(paymentId).map(this::toDomain);
    }

    private Payment toDomain(InternalPaymentEntity entity) {
        return Payment.builder()
            .id(PaymentId.of(entity.getPaymentId()))
            .amount(Money.of(entity.getAmount(), entity.getCurrency()))
            .status(entity.getStatus())
            .description(entity.getDescription())
            .transactionDate(entity.getTransactionDate())
            .source(Payment.PaymentSource.INTERNAL)
            .build();
    }
}
