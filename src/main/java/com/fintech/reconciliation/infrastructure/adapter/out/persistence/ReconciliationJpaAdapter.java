package com.fintech.reconciliation.infrastructure.adapter.out.persistence;

import com.fintech.reconciliation.application.port.out.SaveReconciliationResultPort;
import com.fintech.reconciliation.domain.model.ReconciliationResult;
import com.fintech.reconciliation.infrastructure.adapter.out.persistence.entity.ReconciliationResultEntity;
import com.fintech.reconciliation.infrastructure.adapter.out.persistence.mapper.ReconciliationEntityMapper;
import com.fintech.reconciliation.infrastructure.adapter.out.persistence.repository.ReconciliationJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Adaptador JPA para persistir resultados de conciliación (append-only).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReconciliationJpaAdapter implements SaveReconciliationResultPort {

    private final ReconciliationJpaRepository jpaRepository;
    private final ReconciliationEntityMapper mapper;

    @Override
    public ReconciliationResult save(ReconciliationResult result) {
        ReconciliationResultEntity entity = mapper.toEntity(result);
        ReconciliationResultEntity saved = jpaRepository.save(entity);
        log.debug("Persisted reconciliation result: id={}, paymentId={}, status={}",
            saved.getId(), saved.getPaymentId(), saved.getStatus());
        return mapper.toDomain(saved);
    }
}
