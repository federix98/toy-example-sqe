package com.example.orderrisk.processing;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RiskScorerTest {

    private static final RiskScorer.Customer LOYAL_CUSTOMER =
            new RiskScorer.Customer(1500, 30, 0, new BigDecimal("60.00"), "DE");
    private static final RiskScorer.Customer NEW_CUSTOMER =
            new RiskScorer.Customer(3, 0, 0, new BigDecimal("40.00"), "DE");

    private static RiskScorer.Input bookOrder() {
        return new RiskScorer.Input(List.of(new RiskScorer.Item("SKU-1001", "BOOKS", 2, new BigDecimal("12.90"))),
                "Main Street 1, Berlin", "DE", LOYAL_CUSTOMER);
    }

    private static RiskScorer.Input electronicsOrder() {
        return new RiskScorer.Input(List.of(
                new RiskScorer.Item("SKU-4003", "ELECTRONICS", 2, new BigDecimal("1299.00")),
                new RiskScorer.Item("SKU-9002", "GIFT_CARD", 5, new BigDecimal("200.00"))),
                "PO Box 42, Lagos", "NG", NEW_CUSTOMER);
    }

    @Test
    void sameInputGivesSameResult() {
        assertThat(RiskScorer.evaluate(electronicsOrder(), 1000))
                .isEqualTo(RiskScorer.evaluate(electronicsOrder(), 1000));
    }

    @Test
    void smallDomesticOrderOfLoyalCustomerIsLowRisk() {
        RiskScorer.Result result = RiskScorer.evaluate(bookOrder(), 1000);

        assertThat(result.category()).isEqualTo("LOW");
        assertThat(result.totalAmount()).isEqualByComparingTo("25.80");
    }

    @Test
    void largeForeignElectronicsOrderOfNewCustomerIsHighRisk() {
        assertThat(RiskScorer.evaluate(electronicsOrder(), 1000).category()).isEqualTo("HIGH");
    }

    @Test
    void categoriesUseFixedThresholds() {
        assertThat(RiskScorer.category(34.99)).isEqualTo("LOW");
        assertThat(RiskScorer.category(35.0)).isEqualTo("MEDIUM");
        assertThat(RiskScorer.category(65.0)).isEqualTo("HIGH");
    }
}
