package com.fintech.reconciliation.infrastructure.adapter.out.persistence.repository;

import com.fintech.reconciliation.infrastructure.adapter.out.persistence.entity.InternalPaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface InternalPaymentJpaRepository extends JpaRepository<InternalPaymentEntity, UUID> {

    Optional<InternalPaymentEntity> findByPaymentId(String paymentId);
}
