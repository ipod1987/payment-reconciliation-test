package com.fintech.reconciliation.infrastructure.adapter.out.client;

import com.fintech.reconciliation.domain.exception.ProcessorUnavailableException;
import com.fintech.reconciliation.infrastructure.adapter.out.client.dto.ProcessorTokenRequestDto;
import com.fintech.reconciliation.infrastructure.adapter.out.client.dto.ProcessorTokenResponseDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;

@Slf4j
@Component
public class ProcessorAuthService {

    private final WebClient webClient;

    @Value("${payment-processor.auth.url}")
    private String authUrl;

    @Value("${payment-processor.auth.username}")
    private String username;

    @Value("${payment-processor.auth.password}")
    private String password;

    @Value("${payment-processor.auth.channel}")
    private String channel;

    private volatile String cachedToken;
    private volatile Instant tokenExpiry = Instant.MIN;

    public ProcessorAuthService(@Qualifier("processorWebClient") WebClient processorWebClient) {
        this.webClient = processorWebClient;
    }

    public synchronized String getValidToken() {
        if (cachedToken == null || Instant.now().isAfter(tokenExpiry)) {
            refreshToken();
        }
        return cachedToken;
    }

    public synchronized void invalidateToken() {
        cachedToken = null;
        tokenExpiry = Instant.MIN;
    }

    private void refreshToken() {
        log.info("Requesting new auth token from processor (user={}, channel={})", username, channel);

        ProcessorTokenResponseDto response = webClient.post()
            .uri(authUrl)
            .bodyValue(new ProcessorTokenRequestDto(username, password, channel))
            .retrieve()
            .bodyToMono(ProcessorTokenResponseDto.class)
            .block();

        if (response == null || response.accessToken() == null) {
            throw new ProcessorUnavailableException("Failed to obtain auth token from JSON processor", null);
        }

        cachedToken = response.accessToken();
        tokenExpiry = Instant.now().plusSeconds(response.expiresIn() - 60);
        log.info("JSON processor auth token refreshed, valid for ~{} seconds", response.expiresIn() - 60);
    }
}
