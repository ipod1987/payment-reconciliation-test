package com.fintech.reconciliation.infrastructure.adapter.in.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Resumen de los datos del pago en una fuente específica")
public record PaymentSummaryDto(

    @Schema(description = "ID del pago", example = "pay_abc123")
    String paymentId,

    @Schema(description = "Monto de la transacción", example = "100.00")
    String amount,

    @Schema(description = "Moneda ISO 4217", example = "USD")
    String currency,

    @Schema(description = "Estado del pago en la fuente", example = "APPROVED")
    String status,

    @Schema(description = "Fecha y hora de la transacción")
    LocalDateTime transactionDate,

    @Schema(description = "Fuente del dato", example = "INTERNAL")
    String source
) {}
