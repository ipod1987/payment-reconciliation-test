package com.fintech.reconciliation.infrastructure.adapter.out.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProcessorPaymentDto(
    @JsonProperty("id")           String paymentId,
    @JsonProperty("amount")       BigDecimal amount,
    @JsonProperty("currency")     String currency,
    @JsonProperty("status")       String status,
    @JsonProperty("description")  String description,
    @JsonProperty("processed_at") LocalDateTime processedAt
) {}
