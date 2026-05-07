package com.fintech.reconciliation.infrastructure.adapter.in.rest;

import com.fintech.reconciliation.application.port.in.ReconcilePaymentUseCase;
import com.fintech.reconciliation.domain.exception.PaymentNotFoundException;
import com.fintech.reconciliation.domain.exception.ProcessorUnavailableException;
import com.fintech.reconciliation.domain.model.Discrepancy;
import com.fintech.reconciliation.domain.model.Payment;
import com.fintech.reconciliation.domain.model.ReconciliationResult;
import com.fintech.reconciliation.domain.model.valueobject.Money;
import com.fintech.reconciliation.domain.model.valueobject.PaymentId;
import com.fintech.reconciliation.domain.model.valueobject.ReconciliationStatus;
import com.fintech.reconciliation.infrastructure.adapter.in.rest.mapper.ReconciliationResponseMapperImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ReconciliationController.class)
@Import({ReconciliationResponseMapperImpl.class, GlobalExceptionHandler.class})
@DisplayName("ReconciliationController")
class ReconciliationControllerTest {

    private static final String BASE_URL = "/v1/reconciliation";
    private static final LocalDateTime NOW = LocalDateTime.of(2024, 6, 15, 10, 0, 0);

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReconcilePaymentUseCase reconcilePaymentUseCase;

    @Test
    @DisplayName("GET /{paymentId} returns 200 with CONCILIATED status")
    void returns200WithConciliatedStatus() throws Exception {
        String paymentId = "pay_abc123";
        ReconciliationResult conciliated = buildConciliatedResult(paymentId);
        when(reconcilePaymentUseCase.reconcile(paymentId)).thenReturn(conciliated);

        mockMvc.perform(get(BASE_URL + "/{paymentId}", paymentId)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.paymentId").value(paymentId))
            .andExpect(jsonPath("$.status").value("CONCILIATED"))
            .andExpect(jsonPath("$.fullyReconciled").value(true))
            .andExpect(jsonPath("$.discrepancies").isEmpty())
            .andExpect(jsonPath("$.internalPayment").exists())
            .andExpect(jsonPath("$.processorPayment").exists());
    }

    @Test
    @DisplayName("GET /{paymentId} returns 200 with DISCREPANCY_AMOUNT and discrepancy detail")
    void returns200WithDiscrepancyAmountAndDetail() throws Exception {
        String paymentId = "pay_disc001";
        ReconciliationResult discrepancy = buildDiscrepancyResult(paymentId);
        when(reconcilePaymentUseCase.reconcile(paymentId)).thenReturn(discrepancy);

        mockMvc.perform(get(BASE_URL + "/{paymentId}", paymentId)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DISCREPANCY_AMOUNT"))
            .andExpect(jsonPath("$.fullyReconciled").value(false))
            .andExpect(jsonPath("$.discrepancies[0].type").value("AMOUNT_MISMATCH"))
            .andExpect(jsonPath("$.discrepancies[0].field").value("amount"))
            .andExpect(jsonPath("$.discrepancies[0].internalValue").value("100.00 USD"))
            .andExpect(jsonPath("$.discrepancies[0].processorValue").value("95.00 USD"));
    }

    @Test
    @DisplayName("GET /{paymentId} returns 404 when payment not found in any source")
    void returns404WhenPaymentNotFound() throws Exception {
        String paymentId = "pay_unknown";
        when(reconcilePaymentUseCase.reconcile(paymentId))
            .thenThrow(new PaymentNotFoundException(paymentId));

        mockMvc.perform(get(BASE_URL + "/{paymentId}", paymentId)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("PAYMENT_NOT_FOUND"))
            .andExpect(jsonPath("$.httpStatus").value(404))
            .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("GET /{paymentId} returns 503 when processor is unavailable")
    void returns503WhenProcessorIsUnavailable() throws Exception {
        String paymentId = "pay_timeout";
        when(reconcilePaymentUseCase.reconcile(paymentId))
            .thenThrow(new ProcessorUnavailableException("Circuit breaker open"));

        mockMvc.perform(get(BASE_URL + "/{paymentId}", paymentId)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.errorCode").value("PROCESSOR_UNAVAILABLE"));
    }

    // -------------------------------------------------------------------------

    private ReconciliationResult buildConciliatedResult(String paymentId) {
        Payment internal = buildPayment(paymentId, "100.00", Payment.PaymentSource.INTERNAL);
        Payment processor = buildPayment(paymentId, "100.00", Payment.PaymentSource.PROCESSOR);
        return ReconciliationResult.conciliated(PaymentId.of(paymentId), internal, processor);
    }

    private ReconciliationResult buildDiscrepancyResult(String paymentId) {
        Payment internal = buildPayment(paymentId, "100.00", Payment.PaymentSource.INTERNAL);
        Payment processor = buildPayment(paymentId, "95.00", Payment.PaymentSource.PROCESSOR);
        return ReconciliationResult.withDiscrepancies(
            PaymentId.of(paymentId),
            ReconciliationStatus.DISCREPANCY_AMOUNT,
            List.of(Discrepancy.amountMismatch("100.00 USD", "95.00 USD")),
            internal,
            processor
        );
    }

    private Payment buildPayment(String paymentId, String amount, Payment.PaymentSource source) {
        return Payment.builder()
            .id(PaymentId.of(paymentId))
            .amount(Money.of(new BigDecimal(amount), "USD"))
            .status("APPROVED")
            .transactionDate(NOW)
            .source(source)
            .build();
    }
}
