package com.fintech.reconciliation.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
    name = "reconciliation_results",
    indexes = {
        @Index(name = "idx_reconciliation_payment_id",          columnList = "payment_id"),
        @Index(name = "idx_reconciliation_status",              columnList = "status"),
        @Index(name = "idx_reconciliation_reconciled_at",       columnList = "reconciled_at DESC"),
        @Index(name = "idx_reconciliation_pid_reconciled_at",   columnList = "payment_id, reconciled_at DESC"),
        @Index(name = "idx_reconciliation_reconciled_at_pid",   columnList = "reconciled_at DESC, payment_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReconciliationResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "payment_id", nullable = false, length = 100)
    private String paymentId;

    @Column(name = "status", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private ReconciliationStatusJpa status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "internal_payment_snapshot", columnDefinition = "jsonb")
    private PaymentSnapshot internalPayment;

    /** JSON (REST) processor snapshot. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "processor_payment_snapshot", columnDefinition = "jsonb")
    private PaymentSnapshot processorPayment;

    /** SOAP processor snapshot — added in V3 migration. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "soap_processor_payment_snapshot", columnDefinition = "jsonb")
    private PaymentSnapshot soapProcessorPayment;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "discrepancies", columnDefinition = "jsonb")
    private List<DiscrepancySnapshot> discrepancies;

    @Column(name = "reconciled_at", nullable = false)
    private LocalDateTime reconciledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onPrePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public record PaymentSnapshot(
        String paymentId,
        String amount,
        String currency,
        String status,
        LocalDateTime transactionDate,
        String source
    ) {}

    public record DiscrepancySnapshot(
        String type,
        String field,
        String internalValue,
        String processorValue,
        String processorSource     // nullable — null in data written before V3
    ) {}

    public enum ReconciliationStatusJpa {
        CONCILIATED, DISCREPANCY_AMOUNT, DISCREPANCY_DATE,
        MULTIPLE_DISCREPANCIES, MISSING_IN_INTERNAL, MISSING_IN_PROCESSOR,
        MISSING_IN_JSON_PROCESSOR, MISSING_IN_SOAP_PROCESSOR
    }
}
