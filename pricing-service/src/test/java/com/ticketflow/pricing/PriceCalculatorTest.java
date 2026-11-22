package com.ticketflow.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ticketflow.pricing.service.PriceCalculator;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Test unitaire du calcul : rapide, sans base de données ni Spring. */
class PriceCalculatorTest {

    @Test
    void withoutPromotion_totalIsUnitPriceTimesQuantity() {
        var result = PriceCalculator.compute(30000, 2, null);

        assertThat(result.subtotalMillimes()).isEqualTo(60000);
        assertThat(result.discountMillimes()).isZero();
        assertThat(result.totalMillimes()).isEqualTo(60000);
    }

    @Test
    void tenPercentPromotion_reducesTheSubtotal() {
        var result = PriceCalculator.compute(60000, 2, new BigDecimal("10.00"));

        assertThat(result.subtotalMillimes()).isEqualTo(120000);
        assertThat(result.discountMillimes()).isEqualTo(12000);
        assertThat(result.totalMillimes()).isEqualTo(108000);
    }

    @Test
    void discountIsRoundedToTheNearestMillime_halfUp() {
        // 12345 x 3 = 37035 ; 10 % = 3703,5 -> arrondi à 3704
        var result = PriceCalculator.compute(12345, 3, new BigDecimal("10.00"));

        assertThat(result.discountMillimes()).isEqualTo(3704);
        assertThat(result.totalMillimes()).isEqualTo(33331);
    }

    @Test
    void hundredPercentPromotion_makesItFree() {
        var result = PriceCalculator.compute(30000, 1, new BigDecimal("100.00"));

        assertThat(result.totalMillimes()).isZero();
    }

    @Test
    void overflow_isDetected() {
        assertThatThrownBy(() -> PriceCalculator.compute(Long.MAX_VALUE, 2, null))
                .isInstanceOf(ArithmeticException.class);
    }
}