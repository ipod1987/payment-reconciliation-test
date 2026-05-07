package com.fintech.reconciliation.infrastructure.adapter.out.soap;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fintech.reconciliation.infrastructure.adapter.out.soap.trama.SoapEnvelope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Base class for SOAP/XML integrations over WebFlux WebClient.
 *
 * <p>Responsibility split:
 * <ul>
 *   <li>This class owns HTTP transport and trama parsing (namespace stripping + Jackson XML).</li>
 *   <li>Subclasses own envelope construction and the concrete port contract.</li>
 * </ul>
 *
 * <p>Namespace prefixes are stripped via a lightweight regex before deserialization so that
 * Jackson XML can match on plain local names without requiring a namespace-aware parser.
 */
@Slf4j
public abstract class AbstractSoapConnector {

    protected final WebClient webClient;
    protected final XmlMapper xmlMapper;

    protected AbstractSoapConnector(WebClient soapWebClient) {
        this.webClient = soapWebClient;
        this.xmlMapper = XmlMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build();
    }

    /**
     * Posts the SOAP envelope to the given path and returns a deserialized
     * {@link SoapEnvelope} as a non-blocking {@link Mono}.
     */
    protected Mono<SoapEnvelope> executeRequest(String servicePath, String paymentId, String soapAction) {
        String requestBody = buildSoapEnvelope(paymentId);
        log.debug("SOAP POST {} [SOAPAction={}] paymentId={}", servicePath, soapAction, paymentId);

        return webClient.post()
            .uri(servicePath)
            .header("SOAPAction", soapAction)
            .contentType(MediaType.TEXT_XML)
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(String.class)
            .map(this::stripNamespacePrefixes)
            .map(this::parseEnvelope)
            .doOnError(e -> log.error("SOAP call failed [path={}, paymentId={}]: {}", servicePath, paymentId, e.getMessage()));
    }

    /**
     * Strips XML namespace declarations and prefixes so Jackson XML matches on local names only.
     * Input:  {@code <soap:Envelope xmlns:soap="..."><soap:Body>...}
     * Output: {@code <Envelope><Body>...}
     */
    private String stripNamespacePrefixes(String xml) {
        return xml
            .replaceAll(" xmlns(?::[a-zA-Z0-9]+)?=\"[^\"]*\"", "")
            .replaceAll("<(/?)([a-zA-Z0-9]+):([a-zA-Z])", "<$1$3");
    }

    private SoapEnvelope parseEnvelope(String cleanXml) {
        try {
            return xmlMapper.readValue(cleanXml, SoapEnvelope.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse SOAP response trama", e);
        }
    }

    /** Subclasses build the complete SOAP request envelope for a given paymentId. */
    protected abstract String buildSoapEnvelope(String paymentId);
}
