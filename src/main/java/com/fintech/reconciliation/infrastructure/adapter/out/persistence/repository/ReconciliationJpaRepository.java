package com.fintech.reconciliation.infrastructure.adapter.out.persistence.repository;

import com.fintech.reconciliation.infrastructure.adapter.out.persistence.entity.ReconciliationResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReconciliationJpaRepository extends JpaRepository<ReconciliationResultEntity, UUID> {

    @Query("""
        SELECT r FROM ReconciliationResultEntity r
        WHERE r.paymentId = :paymentId
        ORDER BY r.reconciledAt DESC
        LIMIT 1
        """)
    Optional<ReconciliationResultEntity> findLatestByPaymentId(@Param("paymentId") String paymentId);
}
