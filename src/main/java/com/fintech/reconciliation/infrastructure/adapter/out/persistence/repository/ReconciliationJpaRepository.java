package com.fintech.reconciliation.infrastructure.adapter.out.persistence.repository;

import com.fintech.reconciliation.infrastructure.adapter.out.persistence.entity.ReconciliationResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ReconciliationJpaRepository extends JpaRepository<ReconciliationResultEntity, UUID> {
}
