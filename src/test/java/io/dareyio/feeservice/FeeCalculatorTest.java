package io.dareyio.feeservice;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FeeCalculatorTest {

    private final FeeCalculator calculator = new FeeCalculator();

    @ParameterizedTest(name = "amount {0} -> fee {1}")
    @DisplayName("applies the correct tier, including boundaries")
    @CsvSource({
            "0.01,      10.75",
            "5000,      10.75",
            "5000.01,   26.88",
            "50000,     26.88",
            "50000.01,  53.75",
            "10000000,  53.75"
    })
    void appliesCorrectTier(String amount, String expectedFee) {
        assertEquals(new BigDecimal(expectedFee), calculator.feeFor(new BigDecimal(amount)));
    }

    @Test
    void totalDebitIsAmountPlusFee() {
        assertEquals(new BigDecimal("1010.75"), calculator.totalDebit(new BigDecimal("1000")));
        assertEquals(new BigDecimal("20026.88"), calculator.totalDebit(new BigDecimal("20000")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "-0.01", "10000000.01", "100.001"})
    void rejectsInvalidAmounts(String amount) {
        assertThrows(IllegalArgumentException.class, () -> calculator.feeFor(new BigDecimal(amount)));
    }

    @Test
    void rejectsNullAmount() {
        assertThrows(IllegalArgumentException.class, () -> calculator.feeFor(null));
    }
}
