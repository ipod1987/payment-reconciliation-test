package com.fintech.reconciliation.infrastructure.adapter.in.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Respuesta de error estandarizada")
public record ApiErrorDto(

    @Schema(description = "Código HTTP", example = "404")
    int httpStatus,

    @Schema(description = "Código de error interno", example = "PAYMENT_NOT_FOUND")
    String errorCode,

    @Schema(description = "Mensaje descriptivo del error")
    String message,

    @Schema(description = "Errores de validación de campo (solo para 400)")
    List<FieldErrorDto> fieldErrors,

    @Schema(description = "Timestamp del error")
    LocalDateTime timestamp
) {
    public record FieldErrorDto(String field, String message) {}
}
