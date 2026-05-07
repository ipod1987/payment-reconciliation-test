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

    // Snapshot del pago interno al momento de la conciliación (JSONB para flexibilidad)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "internal_payment_snapshot", columnDefinition = "jsonb")
    private PaymentSnapshot internalPayment;

    // Snapshot del pago del procesador al momento de la conciliación
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "processor_payment_snapshot", columnDefinition = "jsonb")
    private PaymentSnapshot processorPayment;

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

    // Value objects embebidos como JSON para evitar joins costosos en auditoría
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
        String processorValue
    ) {}

    public enum ReconciliationStatusJpa {
        CONCILIATED, DISCREPANCY_AMOUNT, DISCREPANCY_DATE,
        MULTIPLE_DISCREPANCIES, MISSING_IN_INTERNAL, MISSING_IN_PROCESSOR
    }
}
