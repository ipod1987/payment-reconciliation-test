package com.fintech.reconciliation.infrastructure.adapter.out.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ProcessorTokenRequestDto(

    @JsonProperty("username")
    String username,

    @JsonProperty("password")
    String password,

    @JsonProperty("channel")
    String channel
) {}
