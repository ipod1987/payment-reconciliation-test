package com.fintech.reconciliation.infrastructure.adapter.in.rest.dto;

import jakarta.validation.constraints.NotBlank;

public record AuthRequestDto(
    @NotBlank String username,
    @NotBlank String password
) {}
