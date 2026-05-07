package com.fintech.reconciliation.infrastructure.adapter.in.rest.dto;

public record AuthResponseDto(
    String accessToken,
    String tokenType,
    String error
) {}
