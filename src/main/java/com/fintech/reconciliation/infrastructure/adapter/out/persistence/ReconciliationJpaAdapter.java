package com.fintech.reconciliation.infrastructure.adapter.out.persistence;

import com.fintech.reconciliation.application.port.out.LoadReconciliationResultPort;
import com.fintech.reconciliation.application.port.out.SaveReconciliationResultPort;
import com.fintech.reconciliation.domain.model.ReconciliationResult;
import com.fintech.reconciliation.infrastructure.adapter.out.persistence.entity.ReconciliationResultEntity;
import com.fintech.reconciliation.infrastructure.adapter.out.persistence.mapper.ReconciliationEntityMapper;
import com.fintech.reconciliation.infrastructure.adapter.out.persistence.repository.ReconciliationJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Adaptador que implementa ambos puertos de persistencia.
 * Un solo adaptador implementando múltiples puertos de salida es válido cuando
 * las operaciones comparten la misma tecnología subyacente (JPA en este caso).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReconciliationJpaAdapter implements LoadReconciliationResultPort, SaveReconciliationResultPort {

    private final ReconciliationJpaRepository jpaRepository;
    private final ReconciliationEntityMapper mapper;

    @Override
    public Optional<ReconciliationResult> findLatestByPaymentId(String paymentId) {
        return jpaRepository.findLatestByPaymentId(paymentId)
            .map(mapper::toDomain);
    }

    @Override
    public ReconciliationResult save(ReconciliationResult result) {
        ReconciliationResultEntity entity = mapper.toEntity(result);
        ReconciliationResultEntity saved = jpaRepository.save(entity);
        log.debug("Persisted reconciliation result: id={}, paymentId={}, status={}",
            saved.getId(), saved.getPaymentId(), saved.getStatus());
        return mapper.toDomain(saved);
    }
}
