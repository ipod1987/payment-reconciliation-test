package com.fintech.reconciliation.application.service;

import com.fintech.reconciliation.application.port.out.LoadInternalPaymentPort;
import com.fintech.reconciliation.application.port.out.LoadProcessorPaymentPort;
import com.fintech.reconciliation.application.port.out.LoadReconciliationResultPort;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReconciliationService")
class ReconciliationServiceTest {

    private static final String PAYMENT_ID = "pay_test_001";
    private static final LocalDateTime BASE_DATE = LocalDateTime.of(2024, 6, 15, 10, 0, 0);

    @Mock private LoadInternalPaymentPort loadInternalPayment;
    @Mock private LoadProcessorPaymentPort loadProcessorPayment;
    @Mock private LoadReconciliationResultPort loadReconciliationResult;
    @Mock private SaveReconciliationResultPort saveReconciliationResult;

    @InjectMocks
    private ReconciliationService sut;

    @BeforeEach
    void configureSaveMock() {
        when(saveReconciliationResult.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Nested
    @DisplayName("Given payment exists in both systems")
    class BothSystems {

        @Test
        @DisplayName("returns CONCILIATED when amount and date match")
        void returnsConciliatedWhenPaymentMatchesInBothSystems() {
            Payment internal = buildPayment("100.00", "USD", BASE_DATE, Payment.PaymentSource.INTERNAL);
            Payment processor = buildPayment("100.00", "USD", BASE_DATE, Payment.PaymentSource.PROCESSOR);

            when(loadInternalPayment.findInternalPaymentById(PAYMENT_ID)).thenReturn(Optional.of(internal));
            when(loadProcessorPayment.findProcessorPaymentById(PAYMENT_ID)).thenReturn(Optional.of(processor));

            ReconciliationResult result = sut.reconcile(PAYMENT_ID);

            assertThat(result.getStatus()).isEqualTo(ReconciliationStatus.CONCILIATED);
            assertThat(result.hasDiscrepancies()).isFalse();
            assertThat(result.getDiscrepancies()).isEmpty();
        }

        @Test
        @DisplayName("returns DISCREPANCY_AMOUNT when amounts differ materially")
        void returnsDiscrepancyAmountWhenAmountsDiffer() {
            Payment internal = buildPayment("100.00", "USD", BASE_DATE, Payment.PaymentSource.INTERNAL);
            Payment processor = buildPayment("95.00", "USD", BASE_DATE, Payment.PaymentSource.PROCESSOR);

            when(loadInternalPayment.findInternalPaymentById(PAYMENT_ID)).thenReturn(Optional.of(internal));
            when(loadProcessorPayment.findProcessorPaymentById(PAYMENT_ID)).thenReturn(Optional.of(processor));

            ReconciliationResult result = sut.reconcile(PAYMENT_ID);

            assertThat(result.getStatus()).isEqualTo(ReconciliationStatus.DISCREPANCY_AMOUNT);
            assertThat(result.getDiscrepancies()).hasSize(1);
            assertThat(result.getDiscrepancies().get(0).getType())
                .isEqualTo(Discrepancy.DiscrepancyType.AMOUNT_MISMATCH);
            assertThat(result.getDiscrepancies().get(0).getInternalValue()).isEqualTo("100.00 USD");
            assertThat(result.getDiscrepancies().get(0).getProcessorValue()).isEqualTo("95.00 USD");
        }

        @Test
        @DisplayName("returns DISCREPANCY_DATE when dates differ beyond tolerance")
        void returnsDiscrepancyDateWhenDatesDifferBeyondTolerance() {
            // 10 minutos de diferencia, tolerancia es 5 minutos
            Payment internal = buildPayment("100.00", "USD", BASE_DATE, Payment.PaymentSource.INTERNAL);
            Payment processor = buildPayment("100.00", "USD", BASE_DATE.plusMinutes(10), Payment.PaymentSource.PROCESSOR);

            when(loadInternalPayment.findInternalPaymentById(PAYMENT_ID)).thenReturn(Optional.of(internal));
            when(loadProcessorPayment.findProcessorPaymentById(PAYMENT_ID)).thenReturn(Optional.of(processor));

            ReconciliationResult result = sut.reconcile(PAYMENT_ID);

            assertThat(result.getStatus()).isEqualTo(ReconciliationStatus.DISCREPANCY_DATE);
            assertThat(result.getDiscrepancies()).hasSize(1);
            assertThat(result.getDiscrepancies().get(0).getType())
                .isEqualTo(Discrepancy.DiscrepancyType.DATE_MISMATCH);
        }

        @Test
        @DisplayName("returns CONCILIATED when dates differ within tolerance window")
        void returnsConciliatedWhenDatesAreWithinTolerance() {
            // 3 minutos de diferencia, dentro del margen de 5 minutos
            Payment internal = buildPayment("100.00", "USD", BASE_DATE, Payment.PaymentSource.INTERNAL);
            Payment processor = buildPayment("100.00", "USD", BASE_DATE.plusMinutes(3), Payment.PaymentSource.PROCESSOR);

            when(loadInternalPayment.findInternalPaymentById(PAYMENT_ID)).thenReturn(Optional.of(internal));
            when(loadProcessorPayment.findProcessorPaymentById(PAYMENT_ID)).thenReturn(Optional.of(processor));

            ReconciliationResult result = sut.reconcile(PAYMENT_ID);

            assertThat(result.getStatus()).isEqualTo(ReconciliationStatus.CONCILIATED);
        }

        @Test
        @DisplayName("returns MULTIPLE_DISCREPANCIES when both amount and date differ")
        void returnsMultipleDiscrepanciesWhenBothAmountAndDateDiffer() {
            Payment internal = buildPayment("100.00", "USD", BASE_DATE, Payment.PaymentSource.INTERNAL);
            Payment processor = buildPayment("88.00", "USD", BASE_DATE.plusHours(2), Payment.PaymentSource.PROCESSOR);

            when(loadInternalPayment.findInternalPaymentById(PAYMENT_ID)).thenReturn(Optional.of(internal));
            when(loadProcessorPayment.findProcessorPaymentById(PAYMENT_ID)).thenReturn(Optional.of(processor));

            ReconciliationResult result = sut.reconcile(PAYMENT_ID);

            assertThat(result.getStatus()).isEqualTo(ReconciliationStatus.MULTIPLE_DISCREPANCIES);
            assertThat(result.getDiscrepancies()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("Given payment exists in only one source")
    class SingleSource {

        @Test
        @DisplayName("returns MISSING_IN_PROCESSOR when payment only exists internally")
        void returnsMissingInProcessorWhenOnlyInInternal() {
            Payment internal = buildPayment("100.00", "USD", BASE_DATE, Payment.PaymentSource.INTERNAL);

            when(loadInternalPayment.findInternalPaymentById(PAYMENT_ID)).thenReturn(Optional.of(internal));
            when(loadProcessorPayment.findProcessorPaymentById(PAYMENT_ID)).thenReturn(Optional.empty());

            ReconciliationResult result = sut.reconcile(PAYMENT_ID);

            assertThat(result.getStatus()).isEqualTo(ReconciliationStatus.MISSING_IN_PROCESSOR);
            assertThat(result.getInternalPayment()).isPresent();
            assertThat(result.getProcessorPayment()).isEmpty();
        }

        @Test
        @DisplayName("returns MISSING_IN_INTERNAL when payment only exists in processor")
        void returnsMissingInInternalWhenOnlyInProcessor() {
            Payment processor = buildPayment("100.00", "USD", BASE_DATE, Payment.PaymentSource.PROCESSOR);

            when(loadInternalPayment.findInternalPaymentById(PAYMENT_ID)).thenReturn(Optional.empty());
            when(loadProcessorPayment.findProcessorPaymentById(PAYMENT_ID)).thenReturn(Optional.of(processor));

            ReconciliationResult result = sut.reconcile(PAYMENT_ID);

            assertThat(result.getStatus()).isEqualTo(ReconciliationStatus.MISSING_IN_INTERNAL);
            assertThat(result.getInternalPayment()).isEmpty();
            assertThat(result.getProcessorPayment()).isPresent();
        }
    }

    @Nested
    @DisplayName("Given payment does not exist anywhere")
    class NotFound {

        @Test
        @DisplayName("throws PaymentNotFoundException when payment is absent in both systems")
        void throwsPaymentNotFoundExceptionWhenAbsentInBothSystems() {
            when(loadInternalPayment.findInternalPaymentById(PAYMENT_ID)).thenReturn(Optional.empty());
            when(loadProcessorPayment.findProcessorPaymentById(PAYMENT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> sut.reconcile(PAYMENT_ID))
                .isInstanceOf(PaymentNotFoundException.class)
                .hasMessageContaining(PAYMENT_ID);
        }
    }

    @Nested
    @DisplayName("Persistence behavior")
    class Persistence {

        @Test
        @DisplayName("always persists the reconciliation result regardless of status")
        void alwaysPersistsResult() {
            Payment internal = buildPayment("100.00", "USD", BASE_DATE, Payment.PaymentSource.INTERNAL);
            Payment processor = buildPayment("100.00", "USD", BASE_DATE, Payment.PaymentSource.PROCESSOR);

            when(loadInternalPayment.findInternalPaymentById(PAYMENT_ID)).thenReturn(Optional.of(internal));
            when(loadProcessorPayment.findProcessorPaymentById(PAYMENT_ID)).thenReturn(Optional.of(processor));

            sut.reconcile(PAYMENT_ID);

            ArgumentCaptor<ReconciliationResult> captor = ArgumentCaptor.forClass(ReconciliationResult.class);
            verify(saveReconciliationResult).save(captor.capture());
            assertThat(captor.getValue().getPaymentId().value()).isEqualTo(PAYMENT_ID);
        }

        @Test
        @DisplayName("does not save when payment not found in any source")
        void doesNotSaveWhenNotFound() {
            when(loadInternalPayment.findInternalPaymentById(PAYMENT_ID)).thenReturn(Optional.empty());
            when(loadProcessorPayment.findProcessorPaymentById(PAYMENT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> sut.reconcile(PAYMENT_ID))
                .isInstanceOf(PaymentNotFoundException.class);

            verifyNoInteractions(saveReconciliationResult);
        }
    }

    // -------------------------------------------------------------------------

    private Payment buildPayment(String amount, String currency, LocalDateTime date, Payment.PaymentSource source) {
        return Payment.builder()
            .id(PaymentId.of(PAYMENT_ID))
            .amount(Money.of(new BigDecimal(amount), currency))
            .status("APPROVED")
            .transactionDate(date)
            .source(source)
            .build();
    }
}
