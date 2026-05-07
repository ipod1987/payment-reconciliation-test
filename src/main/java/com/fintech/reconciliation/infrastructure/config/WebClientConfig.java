package com.fintech.reconciliation.infrastructure.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class WebClientConfig {

    // ── JSON processor ────────────────────────────────────────────────────────

    @Value("${payment-processor.base-url}")
    private String processorBaseUrl;

    @Value("${payment-processor.timeout-ms:5000}")
    private int processorTimeoutMs;

    @Bean
    public WebClient processorWebClient() {
        return WebClient.builder()
            .baseUrl(processorBaseUrl)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .clientConnector(new ReactorClientHttpConnector(httpClient(processorTimeoutMs)))
            .build();
    }

    // ── SOAP processor ────────────────────────────────────────────────────────

    @Value("${soap-processor.base-url}")
    private String soapBaseUrl;

    @Value("${soap-processor.timeout-ms:5000}")
    private int soapTimeoutMs;

    @Value("${soap-processor.api-key}")
    private String soapApiKey;

    @Bean
    public WebClient soapWebClient() {
        return WebClient.builder()
            .baseUrl(soapBaseUrl)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_XML_VALUE)
            .defaultHeader("X-Api-Key", soapApiKey)
            .clientConnector(new ReactorClientHttpConnector(httpClient(soapTimeoutMs)))
            .build();
    }

    // ── shared ────────────────────────────────────────────────────────────────

    private HttpClient httpClient(int timeoutMs) {
        return HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, timeoutMs)
            .responseTimeout(Duration.ofMillis(timeoutMs))
            .doOnConnected(conn -> conn
                .addHandlerLast(new ReadTimeoutHandler(timeoutMs, TimeUnit.MILLISECONDS))
                .addHandlerLast(new WriteTimeoutHandler(timeoutMs, TimeUnit.MILLISECONDS))
            );
    }
}
