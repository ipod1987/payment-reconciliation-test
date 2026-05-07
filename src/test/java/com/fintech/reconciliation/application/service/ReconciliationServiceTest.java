package com.fintech.reconciliation.application.service;

import com.fintech.reconciliation.application.port.out.LoadInternalPaymentPort;
import com.fintech.reconciliation.application.port.out.LoadProcessorPaymentPort;
import com.fintech.reconciliation.application.port.out.LoadReconciliationResultPort;
import com.fintech.reconciliation.application.port.out.LoadSoapProcessorPaymentPort;
import com.fintech.reconciliation.application.port.out.SaveReconciliationResultPort;
import com.fintech.reconciliation.domain.exception.PaymentNotFoundException;
import com.fintech.reconciliation.domain.model.Discrepancy;
import com.fintech.reconciliation.domain.model.Payment;
import com.fintech.reconciliation.domain.model.ReconciliationResult;
import com.fintech.reconciliation.domain.model.valueobject.Money;
import com.fintech.reconciliation.domain.model.valueobject.PaymentId;
import com.fintech.reconciliation.domain.model.valueobject.ReconciliationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReconciliationService — 3-way reconciliation")
class ReconciliationServiceTest {

    private static final String PAYMENT_ID = "pay_test_001";
    private static final LocalDateTime BASE_DATE = LocalDateTime.of(2024, 6, 15, 10, 0, 0);

    @Mock private LoadInternalPaymentPort      loadInternalPayment;
    @Mock private LoadProcessorPaymentPort     loadProcessorPayment;
    @Mock private LoadSoapProcessorPaymentPort loadSoapProcessorPayment;
    @Mock private LoadReconciliationResultPort loadReconciliationResult;
    @Mock private SaveReconciliationResultPort saveReconciliationResult;

    @InjectMocks
    private ReconciliationService sut;

    @BeforeEach
    void configureSaveMock() {
        lenient().when(saveReconciliationResult.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Payment internal(String amount) {
        return buildPayment(amount, "USD", BASE_DATE, Payment.PaymentSource.INTERNAL);
    }

    private Payment jsonProcessor(String amount) {
        return buildPayment(amount, "USD", BASE_DATE, Payment.PaymentSource.PROCESSOR);
    }

    private Payment soapProcessor(String amount) {
        return buildPayment(amount, "USD", BASE_DATE, Payment.PaymentSource.SOAP_PROCESSOR);
    }

    private Payment buildPayment(String amount, String currency, LocalDateTime date, Payment.PaymentSource source) {
        return Payment.builder()
            .id(PaymentId.of(PAYMENT_ID))
            .amount(Money.of(new BigDecimal(amount), currency))
            .status("APPROVED")
            .transactionDate(date)
            .source(source)
            .build();
    }

    private void stubAll(Payment internal, Payment json, Payment soap) {
        when(loadInternalPayment.findInternalPaymentById(PAYMENT_ID))
            .thenReturn(Optional.ofNullable(internal));
        when(loadProcessorPayment.findProcessorPaymentById(PAYMENT_ID))
            .thenReturn(Optional.ofNullable(json));
        when(loadSoapProcessorPayment.findSoapProcessorPaymentById(PAYMENT_ID))
            .thenReturn(Optional.ofNullable(soap));
    }

    // ── all three present ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("All three sources present")
    class AllThreeSources {

        @Test
        @DisplayName("CONCILIATED when amount and date match across all sources")
        void conciliatedWhenAllMatch() {
            stubAll(internal("100.00"), jsonProcessor("100.00"), soapProcessor("100.00"));

            ReconciliationResult result = sut.reconcile(PAYMENT_ID);

            assertThat(result.getStatus()).isEqualTo(ReconciliationStatus.CONCILIATED);
            assertThat(result.hasDiscrepancies()).isFalse();
            assertThat(result.getInternalPayment()).isPresent();
            assertThat(result.getProcessorPayment()).isPresent();
            assertThat(result.getSoapProcessorPayment()).isPresent();
        }

        @Test
        @DisplayName("DISCREPANCY_AMOUNT when JSON processor amount differs (SOAP matches)")
        void discrepancyAmountFromJsonProcessor() {
            // JSON processor has wrong amount; SOAP matches internal
            stubAll(internal("100.00"), jsonProcessor("95.00"), soapProcessor("100.00"));

            ReconciliationResult result = sut.reconcile(PAYMENT_ID);

            assertThat(result.getStatus()).isEqualTo(ReconciliationStatus.DISCREPANCY_AMOUNT);
            assertThat(result.getDiscrepancies()).hasSize(1);
            assertThat(result.getDiscrepancies().get(0).getType())
                .isEqualTo(Discrepancy.DiscrepancyType.AMOUNT_MISMATCH);
            assertThat(result.getDiscrepancies().get(0).getProcessorSource())
                .isEqualTo("JSON_PROCESSOR");
        }

        @Test
        @DisplayName("DISCREPANCY_AMOUNT when SOAP processor amount differs (JSON matches)")
        void discrepancyAmountFromSoapProcessor() {
            stubAll(internal("100.00"), jsonProcessor("100.00"), soapProcessor("90.00"));

            ReconciliationResult result = sut.reconcile(PAYMENT_ID);

            assertThat(result.getStatus()).isEqualTo(ReconciliationStatus.DISCREPANCY_AMOUNT);
            assertThat(result.getDiscrepancies()).hasSize(1);
            assertThat(result.getDiscrepancies().get(0).getProcessorSource())
                .isEqualTo("SOAP_PROCESSOR");
        }

        @Test
        @DisplayName("MULTIPLE_DISCREPANCIES when both processors disagree on different fields")
        void multipleDiscrepanciesAcrossBothSources() {
            // JSON disagrees on amount, SOAP disagrees on date
            Payment jsonWithDiffAmount = buildPayment("88.00", "USD", BASE_DATE, Payment.PaymentSource.PROCESSOR);
            Payment soapWithDiffDate   = buildPayment("100.00", "USD", BASE_DATE.plusHours(2), Payment.PaymentSource.SOAP_PROCESSOR);

            when(loadInternalPayment.findInternalPaymentById(PAYMENT_ID)).thenReturn(Optional.of(internal("100.00")));
            when(loadProcessorPayment.findProcessorPaymentById(PAYMENT_ID)).thenReturn(Optional.of(jsonWithDiffAmount));
            when(loadSoapProcessorPayment.findSoapProcessorPaymentById(PAYMENT_ID)).thenReturn(Optional.of(soapWithDiffDate));

            ReconciliationResult result = sut.reconcile(PAYMENT_ID);

            assertThat(result.getStatus()).isEqualTo(ReconciliationStatus.MULTIPLE_DISCREPANCIES);
            assertThat(result.getDiscrepancies()).hasSize(2);
        }

        @Test
        @DisplayName("DISCREPANCY_DATE when JSON date differs beyond tolerance (SOAP matches)")
        void discrepancyDateFromJsonProcessor() {
            Payment jsonLate = buildPayment("100.00", "USD", BASE_DATE.plusMinutes(10), Payment.PaymentSource.PROCESSOR);
            when(loadInternalPayment.findInternalPaymentById(PAYMENT_ID)).thenReturn(Optional.of(internal("100.00")));
            when(loadProcessorPayment.findProcessorPaymentById(PAYMENT_ID)).thenReturn(Optional.of(jsonLate));
            when(loadSoapProcessorPayment.findSoapProcessorPaymentById(PAYMENT_ID)).thenReturn(Optional.of(soapProcessor("100.00")));

            ReconciliationResult result = sut.reconcile(PAYMENT_ID);

            assertThat(result.getStatus()).isEqualTo(ReconciliationStatus.DISCREPANCY_DATE);
            assertThat(result.getDiscrepancies()).hasSize(1);
            assertThat(result.getDiscrepancies().get(0).getType())
                .isEqualTo(Discrepancy.DiscrepancyType.DATE_MISMATCH);
        }

        @Test
        @DisplayName("CONCILIATED when dates differ within tolerance window across all sources")
        void conciliatedWithinDateTolerance() {
            Payment jsonSlightlyLate = buildPayment("100.00", "USD", BASE_DATE.plusMinutes(3), Payment.PaymentSource.PROCESSOR);
            Payment soapSlightlyLate = buildPayment("100.00", "USD", BASE_DATE.plusMinutes(2), Payment.PaymentSource.SOAP_PROCESSOR);

            when(loadInternalPayment.findInternalPaymentById(PAYMENT_ID)).thenReturn(Optional.of(internal("100.00")));
            when(loadProcessorPayment.findProcessorPaymentById(PAYMENT_ID)).thenReturn(Optional.of(jsonSlightlyLate));
            when(loadSoapProcessorPayment.findSoapProcessorPaymentById(PAYMENT_ID)).thenReturn(Optional.of(soapSlightlyLate));

            ReconciliationResult result = sut.reconcile(PAYMENT_ID);

            assertThat(result.getStatus()).isEqualTo(ReconciliationStatus.CONCILIATED);
        }
    }

    // ── one source missing ────────────────────────────────────────────────────

    @Nested
    @DisplayName("One source missing")
    class OneMissing {

        @Test
        @DisplayName("MISSING_IN_PROCESSOR when internal exists but both processors are absent")
        void missingInBothProcessors() {
            stubAll(internal("100.00"), null, null);

            ReconciliationResult result = sut.reconcile(PAYMENT_ID);

            assertThat(result.getStatus()).isEqualTo(ReconciliationStatus.MISSING_IN_PROCESSOR);
            assertThat(result.getInternalPayment()).isPresent();
            assertThat(result.getProcessorPayment()).isEmpty();
            assertThat(result.getSoapProcessorPayment()).isEmpty();
        }

        @Test
        @DisplayName("MISSING_IN_JSON_PROCESSOR when internal + SOAP present, JSON absent")
        void missingInJsonProcessor() {
            stubAll(internal("100.00"), null, soapProcessor("100.00"));

            ReconciliationResult result = sut.reconcile(PAYMENT_ID);

            assertThat(result.getStatus()).isEqualTo(ReconciliationStatus.MISSING_IN_JSON_PROCESSOR);
            assertThat(result.getInternalPayment()).isPresent();
            assertThat(result.getProcessorPayment()).isEmpty();
            assertThat(result.getSoapProcessorPayment()).isPresent();
        }

        @Test
        @DisplayName("MISSING_IN_SOAP_PROCESSOR when internal + JSON present, SOAP absent")
        void missingInSoapProcessor() {
            stubAll(internal("100.00"), jsonProcessor("100.00"), null);

            ReconciliationResult result = sut.reconcile(PAYMENT_ID);

            assertThat(result.getStatus()).isEqualTo(ReconciliationStatus.MISSING_IN_SOAP_PROCESSOR);
            assertThat(result.getInternalPayment()).isPresent();
            assertThat(result.getProcessorPayment()).isPresent();
            assertThat(result.getSoapProcessorPayment()).isEmpty();
        }

        @Test
        @DisplayName("MISSING_IN_INTERNAL when internal absent, both processors present")
        void missingInInternal() {
            stubAll(null, jsonProcessor("100.00"), soapProcessor("100.00"));

            ReconciliationResult result = sut.reconcile(PAYMENT_ID);

            assertThat(result.getStatus()).isEqualTo(ReconciliationStatus.MISSING_IN_INTERNAL);
            assertThat(result.getInternalPayment()).isEmpty();
            assertThat(result.getProcessorPayment()).isPresent();
            assertThat(result.getSoapProcessorPayment()).isPresent();
        }
    }

    // ── not found anywhere ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Payment absent in all three sources")
    class NotFound {

        @Test
        @DisplayName("throws PaymentNotFoundException when absent in all sources")
        void throwsWhenAbsentEverywhere() {
            stubAll(null, null, null);

            assertThatThrownBy(() -> sut.reconcile(PAYMENT_ID))
                .isInstanceOf(PaymentNotFoundException.class)
                .hasMessageContaining(PAYMENT_ID);
        }
    }

    // ── persistence ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Persistence behaviour")
    class Persistence {

        @Test
        @DisplayName("always persists the reconciliation result regardless of status")
        void alwaysPersistsResult() {
            stubAll(internal("100.00"), jsonProcessor("100.00"), soapProcessor("100.00"));

            sut.reconcile(PAYMENT_ID);

            ArgumentCaptor<ReconciliationResult> captor = ArgumentCaptor.forClass(ReconciliationResult.class);
            verify(saveReconciliationResult).save(captor.capture());
            assertThat(captor.getValue().getPaymentId().value()).isEqualTo(PAYMENT_ID);
        }

        @Test
        @DisplayName("does not save when payment not found in any source")
        void doesNotSaveWhenNotFound() {
            stubAll(null, null, null);

            assertThatThrownBy(() -> sut.reconcile(PAYMENT_ID))
                .isInstanceOf(PaymentNotFoundException.class);

            verifyNoInteractions(saveReconciliationResult);
        }
    }
}
