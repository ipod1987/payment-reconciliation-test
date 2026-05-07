package com.fintech.reconciliation.infrastructure.adapter.out.persistence.mapper;

import com.fintech.reconciliation.domain.model.Discrepancy;
import com.fintech.reconciliation.domain.model.Payment;
import com.fintech.reconciliation.domain.model.ReconciliationResult;
import com.fintech.reconciliation.domain.model.valueobject.Money;
import com.fintech.reconciliation.domain.model.valueobject.PaymentId;
import com.fintech.reconciliation.domain.model.valueobject.ReconciliationStatus;
import com.fintech.reconciliation.infrastructure.adapter.out.persistence.entity.ReconciliationResultEntity;
import com.fintech.reconciliation.infrastructure.adapter.out.persistence.entity.ReconciliationResultEntity.DiscrepancySnapshot;
import com.fintech.reconciliation.infrastructure.adapter.out.persistence.entity.ReconciliationResultEntity.PaymentSnapshot;
import com.fintech.reconciliation.infrastructure.adapter.out.persistence.entity.ReconciliationResultEntity.ReconciliationStatusJpa;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.math.BigDecimal;
import java.util.List;

@Mapper(componentModel = "spring")
public interface ReconciliationEntityMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "paymentId", expression = "java(result.getPaymentId().value())")
    @Mapping(target = "status", source = "status", qualifiedByName = "toJpaStatus")
    @Mapping(target = "internalPayment", expression = "java(result.getInternalPayment().map(p -> toPaymentSnapshot(p)).orElse(null))")
    @Mapping(target = "processorPayment", expression = "java(result.getProcessorPayment().map(p -> toPaymentSnapshot(p)).orElse(null))")
    @Mapping(target = "discrepancies", source = "discrepancies", qualifiedByName = "toDiscrepancySnapshots")
    ReconciliationResultEntity toEntity(ReconciliationResult result);

    @Mapping(target = "paymentId", expression = "java(com.fintech.reconciliation.domain.model.valueobject.PaymentId.of(entity.getPaymentId()))")
    @Mapping(target = "status", source = "status", qualifiedByName = "toDomainStatus")
    @Mapping(target = "internalPayment", source = "internalPayment", qualifiedByName = "toDomainPayment")
    @Mapping(target = "processorPayment", source = "processorPayment", qualifiedByName = "toDomainPayment")
    @Mapping(target = "discrepancies", source = "discrepancies", qualifiedByName = "toDomainDiscrepancies")
    ReconciliationResult toDomain(ReconciliationResultEntity entity);

    @Named("toJpaStatus")
    default ReconciliationStatusJpa toJpaStatus(ReconciliationStatus status) {
        return ReconciliationStatusJpa.valueOf(status.name());
    }

    @Named("toDomainStatus")
    default ReconciliationStatus toDomainStatus(ReconciliationStatusJpa status) {
        return ReconciliationStatus.valueOf(status.name());
    }

    @Named("toPaymentSnapshot")
    default PaymentSnapshot toPaymentSnapshot(Payment payment) {
        if (payment == null) return null;
        return new PaymentSnapshot(
            payment.getId().value(),
            payment.getAmount().amount().toPlainString(),
            payment.getAmount().currencyCode(),
            payment.getStatus(),
            payment.getTransactionDate(),
            payment.getSource().name()
        );
    }

    @Named("toDomainPayment")
    default Payment toDomainPayment(PaymentSnapshot snapshot) {
        if (snapshot == null) return null;
        return Payment.builder()
            .id(PaymentId.of(snapshot.paymentId()))
            .amount(Money.of(new BigDecimal(snapshot.amount()), snapshot.currency()))
            .status(snapshot.status())
            .transactionDate(snapshot.transactionDate())
            .source(Payment.PaymentSource.valueOf(snapshot.source()))
            .build();
    }

    @Named("toDiscrepancySnapshots")
    default List<DiscrepancySnapshot> toDiscrepancySnapshots(List<Discrepancy> discrepancies) {
        if (discrepancies == null) return List.of();
        return discrepancies.stream()
            .map(d -> new DiscrepancySnapshot(
                d.getType().name(),
                d.getField(),
                d.getInternalValue(),
                d.getProcessorValue()
            ))
            .toList();
    }

    @Named("toDomainDiscrepancies")
    default List<Discrepancy> toDomainDiscrepancies(List<DiscrepancySnapshot> snapshots) {
        if (snapshots == null) return List.of();
        return snapshots.stream()
            .map(s -> Discrepancy.builder()
                .type(Discrepancy.DiscrepancyType.valueOf(s.type()))
                .field(s.field())
                .internalValue(s.internalValue())
                .processorValue(s.processorValue())
                .build()
            )
            .toList();
    }
}
