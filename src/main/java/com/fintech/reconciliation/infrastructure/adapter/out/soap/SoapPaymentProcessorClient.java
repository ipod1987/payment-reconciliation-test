package com.fintech.reconciliation.infrastructure.adapter.out.soap;

import com.fintech.reconciliation.application.port.out.LoadSoapProcessorPaymentPort;
import com.fintech.reconciliation.domain.exception.ProcessorUnavailableException;
import com.fintech.reconciliation.domain.model.Payment;
import com.fintech.reconciliation.infrastructure.adapter.out.soap.acl.SoapPaymentAcl;
import com.fintech.reconciliation.infrastructure.adapter.out.soap.trama.SoapEnvelope;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Optional;

/**
 * SOAP adapter that implements {@link LoadSoapProcessorPaymentPort}.
 * Internally reactive (WebFlux WebClient + Mono), exposes a blocking port interface
 * so the service can compose it via {@code Mono.fromCallable} for concurrent execution.
 */
@Slf4j
@Component
public class SoapPaymentProcessorClient extends AbstractSoapConnector implements LoadSoapProcessorPaymentPort {

    private static final String SOAP_ACTION  = "GetPayment";
    private static final String SERVICE_PATH = "/soap/PaymentService";

    private final SoapPaymentAcl acl;

    public SoapPaymentProcessorClient(
        @Qualifier("soapWebClient") WebClient soapWebClient,
        SoapPaymentAcl acl
    ) {
        super(soapWebClient);
        this.acl = acl;
    }

    @Override
    @CircuitBreaker(name = "soapProcessor", fallbackMethod = "soapUnavailableFallback")
    @Retry(name = "soapProcessor")
    public Optional<Payment> findSoapProcessorPaymentById(String paymentId) {
        log.debug("Fetching payment from SOAP processor: paymentId={}", paymentId);

        try {
            SoapEnvelope envelope = executeRequest(SERVICE_PATH, paymentId, SOAP_ACTION).block();

            if (envelope == null || envelope.getBody() == null) {
                return Optional.empty();
            }
            if (envelope.getBody().getFault() != null) {
                log.info("SOAP fault for paymentId={}: {}", paymentId,
                    envelope.getBody().getFault().getFaultString());
                return Optional.empty();
            }
            return Optional.ofNullable(envelope.getBody().getPayment())
                .map(acl::toDomain);

        } catch (WebClientResponseException.ServiceUnavailable e) {
            log.warn("SOAP processor returned 503 for paymentId={}", paymentId);
            throw new ProcessorUnavailableException("SOAP processor returned 503 for: " + paymentId, e);
        }
    }

    @SuppressWarnings("unused")
    public Optional<Payment> soapUnavailableFallback(String paymentId, Exception ex) {
        log.error("SOAP circuit breaker open for paymentId={}: {}", paymentId, ex.getMessage());
        throw new ProcessorUnavailableException(
            "SOAP payment processor is currently unavailable for paymentId: " + paymentId, ex
        );
    }

    @Override
    protected String buildSoapEnvelope(String paymentId) {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
              <soap:Body>
                <GetPaymentRequest xmlns="http://fintech.com/payment/soap">
                  <paymentId>%s</paymentId>
                </GetPaymentRequest>
              </soap:Body>
            </soap:Envelope>
            """.formatted(paymentId);
    }
}
