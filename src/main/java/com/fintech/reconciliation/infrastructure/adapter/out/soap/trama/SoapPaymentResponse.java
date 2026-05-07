package com.fintech.reconciliation.infrastructure.adapter.out.soap.trama;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import lombok.Data;

/**
 * Trama (frame) DTO that maps one-to-one with the XML payload inside &lt;soap:Body&gt;.
 * Isolated here so parsing details never leak into the domain or application layers.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JacksonXmlRootElement(localName = "GetPaymentResponse")
public class SoapPaymentResponse {

    @JacksonXmlProperty(localName = "paymentId")
    private String paymentId;

    @JacksonXmlProperty(localName = "amount")
    private String amount;

    @JacksonXmlProperty(localName = "currency")
    private String currency;

    @JacksonXmlProperty(localName = "status")
    private String status;

    @JacksonXmlProperty(localName = "description")
    private String description;

    @JacksonXmlProperty(localName = "processedAt")
    private String processedAt;
}
