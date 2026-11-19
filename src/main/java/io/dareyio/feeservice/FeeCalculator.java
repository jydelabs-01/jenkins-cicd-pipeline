package io.dareyio.feeservice;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Calculates a flat, tiered transfer fee for an amount in Naira.
 *
 * Sample schedule (illustrative only):
 *   amount <= 5,000            -> 10.75
 *   5,000 < amount <= 50,000   -> 26.88
 *   amount > 50,000            -> 53.75
 */
public class FeeCalculator {

    static final BigDecimal TIER_1_LIMIT = new BigDecimal("5000");
    static final BigDecimal TIER_2_LIMIT = new BigDecimal("50000");
    static final BigDecimal MAX_AMOUNT = new BigDecimal("10000000");

    static final BigDecimal TIER_1_FEE = new BigDecimal("10.75");
    static final BigDecimal TIER_2_FEE = new BigDecimal("26.88");
    static final BigDecimal TIER_3_FEE = new BigDecimal("53.75");

    public BigDecimal feeFor(BigDecimal amount) {
        validate(amount);
        if (amount.compareTo(TIER_1_LIMIT) <= 0) {
            return TIER_1_FEE;
        }
        if (amount.compareTo(TIER_2_LIMIT) <= 0) {
            return TIER_2_FEE;
        }
        return TIER_3_FEE;
    }

    public BigDecimal totalDebit(BigDecimal amount) {
        return amount.add(feeFor(amount)).setScale(2, RoundingMode.HALF_UP);
    }

    private void validate(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("amount is required");
        }
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be greater than zero");
        }
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            throw new IllegalArgumentException("amount exceeds single-transfer limit of " + MAX_AMOUNT.toPlainString());
        }
        if (amount.scale() > 2) {
            throw new IllegalArgumentException("amount cannot have more than 2 decimal places");
        }
    }
}
