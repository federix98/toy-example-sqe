package com.example.orderrisk.processing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The CPU-bound kernel of the system: a deterministic order-risk score.
 * <p>
 * Step 1 derives ten normalised features (0..1) from the order amount, the item composition,
 * the shipping address and the customer's purchase history.
 * Step 2 evaluates a fixed set of weighted rules {@code workFactor} times, each round with a
 * different rule steepness, and averages the results. The work grows linearly with
 * {@code workFactor}; the result depends only on the inputs (no randomness, no clock, and
 * {@link StrictMath} gives bit-identical results on every JVM).
 * <p>
 * This class has no dependencies on HTTP, RabbitMQ or tracing so it can be benchmarked with JMH.
 */
public final class RiskScorer {

    /** Scores below this value are LOW risk. */
    public static final double MEDIUM_THRESHOLD = 35.0;
    /** Scores at or above this value are HIGH risk. */
    public static final double HIGH_THRESHOLD = 65.0;

    private static final Set<String> RISKY_CATEGORIES = Set.of("ELECTRONICS", "GIFT_CARD");
    private static final Set<String> LOW_RISK_COUNTRIES =
            Set.of("DE", "FR", "IT", "ES", "NL", "AT", "PL", "SE", "BE", "DK");

    // One rule per feature: weight and threshold. Negative weight = feature lowers the risk.
    private static final String[] FEATURE_NAMES = {
            "amount", "quantity", "riskyCategoryShare", "countryMismatch", "poBoxAddress",
            "countryRisk", "newAccount", "chargebackRate", "spendDeviation", "loyalty"};
    private static final double[] WEIGHTS = {0.18, 0.06, 0.16, 0.12, 0.08, 0.08, 0.10, 0.14, 0.08, -0.10};
    private static final double[] THRESHOLDS = {0.40, 0.50, 0.30, 0.50, 0.50, 0.50, 0.60, 0.20, 0.40, 0.50};

    public record Item(String sku, String category, int quantity, BigDecimal unitPrice) {
    }

    public record Customer(int accountAgeDays, int previousOrders, int chargebacks,
                           BigDecimal averageOrderValue, String homeCountry) {
    }

    public record Input(List<Item> items, String shippingAddress, String shippingCountry, Customer customer) {
    }

    public record Result(double score, String category, BigDecimal totalAmount) {
    }

    private RiskScorer() {
    }

    /** Full evaluation: features + repeated rule evaluation + category. */
    public static Result evaluate(Input input, int workFactor) {
        double score = score(features(input), workFactor);
        double rounded = Math.round(score * 100.0) / 100.0;
        return new Result(rounded, category(rounded), totalAmount(input.items()));
    }

    public static String category(double score) {
        if (score >= HIGH_THRESHOLD) {
            return "HIGH";
        }
        return score >= MEDIUM_THRESHOLD ? "MEDIUM" : "LOW";
    }

    public static BigDecimal totalAmount(List<Item> items) {
        BigDecimal total = BigDecimal.ZERO;
        for (Item item : items) {
            total = total.add(item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    /** Step 1: feature extraction. Every feature is normalised to the range 0..1. */
    public static double[] features(Input input) {
        double total = totalAmount(input.items()).doubleValue();
        int quantity = 0;
        double riskyValue = 0;
        for (Item item : input.items()) {
            quantity += item.quantity();
            if (RISKY_CATEGORIES.contains(item.category())) {
                riskyValue += item.unitPrice().doubleValue() * item.quantity();
            }
        }
        Customer customer = input.customer();
        String address = input.shippingAddress().toUpperCase(Locale.ROOT);
        double averageOrderValue = Math.max(1.0, customer.averageOrderValue().doubleValue());

        double[] f = new double[FEATURE_NAMES.length];
        f[0] = Math.min(1.0, total / 2000.0);
        f[1] = Math.min(1.0, quantity / 20.0);
        f[2] = total == 0 ? 0 : riskyValue / total;
        f[3] = input.shippingCountry().equals(customer.homeCountry()) ? 0.0 : 1.0;
        f[4] = address.contains("PO BOX") || address.contains("POSTFACH") ? 1.0 : 0.0;
        f[5] = LOW_RISK_COUNTRIES.contains(input.shippingCountry()) ? 0.1 : 0.7;
        f[6] = 1.0 - Math.min(1.0, customer.accountAgeDays() / 365.0);
        f[7] = Math.min(1.0, 5.0 * customer.chargebacks() / Math.max(1, customer.previousOrders()));
        f[8] = Math.min(1.0, Math.max(0.0, (total / averageOrderValue - 1.0) / 9.0));
        f[9] = Math.min(1.0, customer.previousOrders() / 20.0);
        return f;
    }

    /**
     * Step 2: repeated rule evaluation. Each round evaluates every rule as a sigmoid
     * {@code weight / (1 + e^(-k * (feature - threshold)))} with a round-dependent steepness k.
     * The averaged sum is scaled to 0..100.
     */
    public static double score(double[] features, int workFactor) {
        if (workFactor < 1) {
            throw new IllegalArgumentException("workFactor must be >= 1");
        }
        double sum = 0;
        for (int round = 0; round < workFactor; round++) {
            double steepness = 6.0 + (round % 7);
            double roundScore = 0;
            for (int rule = 0; rule < WEIGHTS.length; rule++) {
                double activation = 1.0 / (1.0 + StrictMath.exp(-steepness * (features[rule] - THRESHOLDS[rule])));
                roundScore += WEIGHTS[rule] * activation;
            }
            sum += roundScore;
        }
        double average = sum / workFactor;
        double min = 0;
        double max = 0;
        for (double w : WEIGHTS) {
            if (w < 0) {
                min += w;
            } else {
                max += w;
            }
        }
        return 100.0 * (average - min) / (max - min);
    }
}
