package com.fintech.reconciliation.domain.model;

import com.fintech.reconciliation.domain.model.valueobject.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Money value object")
class MoneyTest {

    @Test
    @DisplayName("detects material discrepancy when amounts differ beyond tolerance")
    void detectsMaterialDiscrepancy() {
        Money internal = Money.of("100.00", "USD");
        Money processor = Money.of("95.00", "USD");

        assertThat(internal.hasMaterialDiscrepancyWith(processor)).isTrue();
    }

    @Test
    @DisplayName("ignores sub-cent rounding differences within tolerance")
    void ignoresSubCentRoundingDifferences() {
        Money internal = Money.of("100.00", "USD");
        Money processor = Money.of("100.005", "USD"); // se redondea a 100.01 -> diff = 0.01 -> en borde
        // La tolerancia exacta es 0.01, usamos compareTo > 0, así que 0.01 NO es discrepancia
        assertThat(internal.hasMaterialDiscrepancyWith(processor)).isFalse();
    }

    @Test
    @DisplayName("always flags currency mismatch as discrepancy regardless of amount")
    void flagsCurrencyMismatchAsDiscrepancy() {
        Money usd = Money.of("100.00", "USD");
        Money eur = Money.of("100.00", "EUR");

        assertThat(usd.hasMaterialDiscrepancyWith(eur)).isTrue();
    }

    @Test
    @DisplayName("rejects negative amounts")
    void rejectsNegativeAmounts() {
        assertThatThrownBy(() -> Money.of(new BigDecimal("-1.00"), "USD"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("negative");
    }

    @Test
    @DisplayName("rejects invalid ISO 4217 currency codes")
    void rejectsInvalidCurrencyCodes() {
        assertThatThrownBy(() -> Money.of("100.00", "XYZ"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
