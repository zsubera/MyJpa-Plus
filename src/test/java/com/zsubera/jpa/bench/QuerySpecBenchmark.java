package com.zsubera.jpa.bench;

import com.zsubera.jpa.spec.QuerySpec;
import com.zsubera.jpa.spec.SFunction;
import com.zsubera.jpa.util.LambdaUtils;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Micro-benchmarks for core MyJpa-Plus operations. Run with: {@code mvn test -Pbenchmark} or {@code
 * java -jar target/benchmarks.jar}
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class QuerySpecBenchmark {

    private static final SFunction<BenchEntity, String> nameGetter = BenchEntity::getName;
    private static final SFunction<BenchEntity, String> statusGetter = BenchEntity::getStatus;

    @Benchmark
    public void buildSimpleQuery(Blackhole bh) {
        QuerySpec<BenchEntity> qs = new QuerySpec<BenchEntity>().eq(nameGetter, "test").eq(statusGetter, "ACTIVE");
        bh.consume(qs);
    }

    @Benchmark
    public void buildComplexQuery(Blackhole bh) {
        QuerySpec<BenchEntity> qs = new QuerySpec<BenchEntity>().eq(nameGetter, "test").like(nameGetter, "foo")
            .or(g -> g.eq(statusGetter, "A").eq(statusGetter, "B")).in(statusGetter, "A", "B", "C")
            .between(BenchEntity::getLevel, 1, 10).startsWith(nameGetter, "prefix");
        bh.consume(qs);
    }

    @Benchmark
    public void propertyNameExtraction(Blackhole bh) {
        String name = LambdaUtils.getPropertyName(nameGetter);
        bh.consume(name);
    }

    @Benchmark
    public void propertyNameExtractionCached(Blackhole bh) {
        String name = LambdaUtils.getPropertyName(nameGetter);
        bh.consume(name);
    }

    @Benchmark
    public void escapeLikeWildcards(Blackhole bh) {
        String escaped = com.zsubera.jpa.spec.PredicateHelper.escapeLikeWildcards("test_value%_with_wildcards");
        bh.consume(escaped);
    }

    static class BenchEntity {
        private String name;
        private String status;
        private Integer level;

        public String getName() {
            return name;
        }

        public String getStatus() {
            return status;
        }

        public Integer getLevel() {
            return level;
        }
    }
}
