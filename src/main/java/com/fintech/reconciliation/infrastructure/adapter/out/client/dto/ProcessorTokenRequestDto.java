package com.fintech.reconciliation.infrastructure.adapter.out.client.dto;

public record ProcessorTokenRequestDto(
    String username,
    String password,
    String channel
) {}
