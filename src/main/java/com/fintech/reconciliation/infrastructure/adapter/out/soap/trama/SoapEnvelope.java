package com.fintech.reconciliation.infrastructure.adapter.out.soap.trama;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import lombok.Data;

/**
 * Full SOAP envelope trama. Namespace prefixes are stripped by
 * AbstractSoapConnector before deserialization so Jackson XML only
 * sees plain local names.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JacksonXmlRootElement(localName = "Envelope")
public class SoapEnvelope {

    @JacksonXmlProperty(localName = "Body")
    private Body body;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Body {

        @JacksonXmlProperty(localName = "GetPaymentResponse")
        private SoapPaymentResponse payment;

        @JacksonXmlProperty(localName = "Fault")
        private SoapFault fault;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SoapFault {

        @JacksonXmlProperty(localName = "faultstring")
        private String faultString;
    }
}
