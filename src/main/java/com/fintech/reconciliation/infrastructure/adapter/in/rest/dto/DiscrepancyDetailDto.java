package com.fintech.reconciliation.infrastructure.adapter.in.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Detalle de una discrepancia detectada durante la conciliación")
public record DiscrepancyDetailDto(

    @Schema(description = "Tipo de discrepancia", example = "AMOUNT_MISMATCH")
    String type,

    @Schema(description = "Campo donde se detectó la discrepancia", example = "amount")
    String field,

    @Schema(description = "Valor registrado en el sistema interno", example = "100.00 USD")
    String internalValue,

    @Schema(description = "Valor registrado en el procesador externo", example = "99.50 USD")
    String processorValue,

    @Schema(
        description = "Fuente externa donde se detectó la discrepancia",
        example = "JSON_PROCESSOR",
        allowableValues = {"JSON_PROCESSOR", "SOAP_PROCESSOR"}
    )
    String processorSource
) {}
