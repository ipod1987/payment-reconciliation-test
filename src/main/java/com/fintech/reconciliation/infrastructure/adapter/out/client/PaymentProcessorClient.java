package com.fintech.reconciliation.infrastructure.adapter.out.client;

import com.fintech.reconciliation.application.port.out.LoadProcessorPaymentPort;
import com.fintech.reconciliation.domain.exception.ProcessorUnavailableException;
import com.fintech.reconciliation.domain.model.Payment;
import com.fintech.reconciliation.domain.model.valueobject.Money;
import com.fintech.reconciliation.domain.model.valueobject.PaymentId;
import com.fintech.reconciliation.infrastructure.adapter.out.client.dto.ProcessorPaymentDto;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Optional;

@Slf4j
@Component
public class PaymentProcessorClient implements LoadProcessorPaymentPort {

    private final WebClient processorWebClient;
    private final ProcessorAuthService authService;

    public PaymentProcessorClient(
        @Qualifier("processorWebClient") WebClient processorWebClient,
        ProcessorAuthService authService
    ) {
        this.processorWebClient = processorWebClient;
        this.authService = authService;
    }

    @Override
    @CircuitBreaker(name = "paymentProcessor", fallbackMethod = "processorUnavailableFallback")
    @Retry(name = "paymentProcessor")
    public Optional<Payment> findProcessorPaymentById(String paymentId) {
        log.debug("Fetching payment from JSON processor: paymentId={}", paymentId);

        try {
            ProcessorPaymentDto dto = processorWebClient.get()
                .uri("/payments/{id}", paymentId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + authService.getValidToken())
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response -> response.createException().map(ex -> ex))
                .bodyToMono(ProcessorPaymentDto.class)
                .block();

            return Optional.ofNullable(dto).map(this::toDomain);

        } catch (WebClientResponseException.NotFound e) {
            log.info("Payment not found in JSON processor: paymentId={}", paymentId);
            return Optional.empty();

        } catch (WebClientResponseException.Unauthorized e) {
            log.warn("Received 401 from JSON processor, invalidating cached token: paymentId={}", paymentId);
            authService.invalidateToken();
            throw e;
        }
    }

    @SuppressWarnings("unused")
    public Optional<Payment> processorUnavailableFallback(String paymentId, Exception ex) {
        log.error("Circuit breaker open for JSON processor, paymentId={}: {}", paymentId, ex.getMessage());
        throw new ProcessorUnavailableException(
            "JSON payment processor is currently unavailable for paymentId: " + paymentId, ex
        );
    }

    private Payment toDomain(ProcessorPaymentDto dto) {
        return Payment.builder()
            .id(PaymentId.of(dto.paymentId()))
            .amount(Money.of(dto.amount(), dto.currency()))
            .status(dto.status())
            .description(dto.description())
            .transactionDate(dto.processedAt())
            .source(Payment.PaymentSource.PROCESSOR)
            .build();
    }
}
