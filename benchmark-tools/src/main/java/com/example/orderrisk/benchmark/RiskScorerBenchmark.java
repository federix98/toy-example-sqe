package com.example.orderrisk.benchmark;

import com.example.orderrisk.processing.RiskScorer;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Minimal JMH example for the CPU-bound kernel of risk-processing-service.
 * <p>
 * Run: {@code java -jar benchmark-tools/target/benchmarks.jar RiskScorerBenchmark}
 * <p>
 * Results are returned from the benchmark methods so that JMH consumes them and the JIT
 * compiler cannot remove the computation as dead code. Inputs are built once in {@link #setUp}
 * so their construction is not measured.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(1)
public class RiskScorerBenchmark {

    /** Number of scoring rounds; compare with processing.workFactor in risk-processing-service. */
    @Param({"100000", "500000"})
    public int workFactor;

    private RiskScorer.Input input;
    private double[] features;

    @Setup
    public void setUp() {
        input = new RiskScorer.Input(List.of(
                new RiskScorer.Item("SKU-4002", "ELECTRONICS", 1, new BigDecimal("699.00")),
                new RiskScorer.Item("SKU-1001", "BOOKS", 3, new BigDecimal("12.90"))),
                "Main Street 1, Berlin", "DE",
                new RiskScorer.Customer(400, 12, 1, new BigDecimal("80.00"), "FR"));
        features = RiskScorer.features(input);
    }

    /** The complete evaluation as executed for every order: features + rule rounds + category. */
    @Benchmark
    public RiskScorer.Result evaluate() {
        return RiskScorer.evaluate(input, workFactor);
    }

    /** Only the repeated rule evaluation (the part that scales with workFactor). */
    @Benchmark
    public double ruleEvaluationOnly() {
        return RiskScorer.score(features, workFactor);
    }
}
