package com.fintech.reconciliation.infrastructure.adapter.in.rest;

import com.fintech.reconciliation.application.port.in.ReconcilePaymentUseCase;
import com.fintech.reconciliation.domain.model.ReconciliationResult;
import com.fintech.reconciliation.infrastructure.adapter.in.rest.dto.ReconciliationResponseDto;
import com.fintech.reconciliation.infrastructure.adapter.in.rest.mapper.ReconciliationResponseMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/reconciliation")
@RequiredArgsConstructor
@Validated
@Tag(name = "Reconciliation", description = "Payment reconciliation between internal system and external processor")
public class ReconciliationController {

    private final ReconcilePaymentUseCase reconcilePaymentUseCase;
    private final ReconciliationResponseMapper responseMapper;

    @GetMapping("/{paymentId}")
    @Operation(
        summary = "Get reconciliation status for a payment",
        description = "Returns the reconciliation state comparing internal system data against the external payment processor. " +
                      "Identifies discrepancies in amount, dates, or missing records in either source."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Reconciliation result returned successfully",
            content = @Content(schema = @Schema(implementation = ReconciliationResponseDto.class))
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Payment not found in any source",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorDto"))
        ),
        @ApiResponse(
            responseCode = "503",
            description = "External payment processor is unavailable",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorDto"))
        )
    })
    public ResponseEntity<ReconciliationResponseDto> getReconciliationStatus(
        @PathVariable
        @NotBlank(message = "paymentId must not be blank")
        @Parameter(description = "Unique payment identifier", example = "pay_abc123", required = true)
        String paymentId
    ) {
        ReconciliationResult result = reconcilePaymentUseCase.reconcile(paymentId);
        ReconciliationResponseDto response = responseMapper.toResponseDto(result);
        return ResponseEntity.ok(response);
    }
}
