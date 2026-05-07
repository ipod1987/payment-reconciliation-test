package com.fintech.reconciliation.infrastructure.adapter.out.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ProcessorTokenResponseDto(

    @JsonProperty("access_token")
    String accessToken,

    @JsonProperty("token_type")
    String tokenType,

    @JsonProperty("expires_in")
    long expiresIn
) {}
