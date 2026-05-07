package com.fintech.reconciliation.infrastructure.adapter.in.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Resultado completo de la conciliación de un pago")
public record ReconciliationResponseDto(

    @Schema(description = "ID del pago conciliado", example = "pay_abc123")
    String paymentId,

    @Schema(
        description = "Estado de conciliación",
        example = "CONCILIATED",
        allowableValues = {
            "CONCILIATED", "DISCREPANCY_AMOUNT", "DISCREPANCY_DATE",
            "MULTIPLE_DISCREPANCIES", "MISSING_IN_INTERNAL", "MISSING_IN_PROCESSOR"
        }
    )
    String status,

    @Schema(description = "Descripción legible del estado de conciliación")
    String statusDescription,

    @Schema(description = "Indica si el pago está completamente conciliado")
    boolean fullyReconciled,

    @Schema(description = "Lista de discrepancias detectadas (vacía si está conciliado)")
    List<DiscrepancyDetailDto> discrepancies,

    @Schema(description = "Datos del pago en el sistema interno (null si no existe)")
    PaymentSummaryDto internalPayment,

    @Schema(description = "Datos del pago en el procesador externo (null si no existe)")
    PaymentSummaryDto processorPayment,

    @Schema(description = "Timestamp en que se realizó la conciliación")
    LocalDateTime reconciledAt
) {}
