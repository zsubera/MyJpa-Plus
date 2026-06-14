package com.zsubera.jpa.monitor;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QueryMetricsCollectorTest {

    private QueryMetricsCollector collector;

    @BeforeEach
    void setUp() {
        collector = QueryMetricsCollector.getInstance();
        collector.reset();
        collector.setEnabled(true);
        collector.setSlowQueryThresholdMs(1000);
    }

    @Test
    void getInstance_returnsSameInstance() {
        assertSame(QueryMetricsCollector.getInstance(), QueryMetricsCollector.getInstance());
    }

    @Test
    void recordQuery_basicRecording() {
        collector.recordQuery("testQuery", 5_000_000); // 5ms
        collector.recordQuery("testQuery", 10_000_000); // 10ms

        QueryMetricsCollector.QueryStats stats = collector.getStats("testQuery");
        assertNotNull(stats);
        assertEquals(2, stats.getExecutionCount());
        assertEquals(15.0, stats.getTotalTimeMs(), 0.1);
        assertEquals(7.5, stats.getAverageTimeMs(), 0.1);
        assertEquals(10.0, stats.getMaxTimeMs(), 0.1);
    }

    @Test
    void recordQuery_disabled_doesNothing() {
        collector.setEnabled(false);
        collector.recordQuery("testQuery", 5_000_000);
        assertNull(collector.getStats("testQuery"));
    }

    @Test
    void recordQuery_nullQueryName_doesNothing() {
        collector.recordQuery(null, 5_000_000);
        // Should not throw
    }

    @Test
    void getStats_nullQueryName_returnsNull() {
        assertNull(collector.getStats(null));
    }

    @Test
    void getStats_unknownQuery_returnsNull() {
        assertNull(collector.getStats("nonexistent"));
    }

    @Test
    void getAllStats_returnsAllMetrics() {
        collector.recordQuery("q1", 1_000_000);
        collector.recordQuery("q2", 2_000_000);

        Map<String, QueryMetricsCollector.QueryStats> allStats = collector.getAllStats();
        assertEquals(2, allStats.size());
        assertNotNull(allStats.get("q1"));
        assertNotNull(allStats.get("q2"));
    }

    @Test
    void reset_clearsAllMetrics() {
        collector.recordQuery("testQuery", 5_000_000);
        collector.reset();
        assertNull(collector.getStats("testQuery"));
    }

    @Test
    void reset_byName_clearsSpecificMetric() {
        collector.recordQuery("q1", 1_000_000);
        collector.recordQuery("q2", 2_000_000);
        collector.reset("q1");
        assertNull(collector.getStats("q1"));
        assertNotNull(collector.getStats("q2"));
    }

    @Test
    void reset_nullName_doesNothing() {
        collector.reset(null); // Should not throw
    }

    @Test
    void getExecutionCount_returnsCorrectCount() {
        collector.recordQuery("test", 1_000_000);
        collector.recordQuery("test", 2_000_000);
        collector.recordQuery("test", 3_000_000);
        assertEquals(3, collector.getExecutionCount("test"));
    }

    @Test
    void getExecutionCount_unknownQuery_returnsZero() {
        assertEquals(0, collector.getExecutionCount("nonexistent"));
    }

    @Test
    void setSlowQueryThresholdMs_validValue() {
        collector.setSlowQueryThresholdMs(500);
        // Record a query that takes 600ms (> 500ms threshold)
        collector.recordQuery("slowQuery", 600_000_000L);
        assertNotNull(collector.getStats("slowQuery"));
    }

    @Test
    void setSlowQueryThresholdMs_invalidValue_throws() {
        assertThrows(IllegalArgumentException.class, () -> collector.setSlowQueryThresholdMs(0));
        assertThrows(IllegalArgumentException.class, () -> collector.setSlowQueryThresholdMs(-1));
    }

    @Test
    void setEnabled_togglesState() {
        collector.setEnabled(false);
        assertFalse(collector.isEnabled());
        collector.setEnabled(true);
        assertTrue(collector.isEnabled());
    }

    @Test
    void recordQuery_updatesMaxTime() {
        collector.recordQuery("test", 5_000_000);
        collector.recordQuery("test", 20_000_000);
        collector.recordQuery("test", 10_000_000);

        QueryMetricsCollector.QueryStats stats = collector.getStats("test");
        assertEquals(20.0, stats.getMaxTimeMs(), 0.1);
    }

    @Test
    void queryStats_toString_containsFields() {
        collector.recordQuery("test", 5_000_000);
        QueryMetricsCollector.QueryStats stats = collector.getStats("test");
        String str = stats.toString();
        assertTrue(str.contains("count=1"));
        assertTrue(str.contains("total="));
        assertTrue(str.contains("avg="));
        assertTrue(str.contains("max="));
    }

    @Test
    void queryStats_getters_returnCorrectValues() {
        collector.recordQuery("test", 10_000_000);
        QueryMetricsCollector.QueryStats stats = collector.getStats("test");
        assertEquals(1, stats.getExecutionCount());
        assertEquals(10.0, stats.getTotalTimeMs(), 0.1);
        assertEquals(10.0, stats.getAverageTimeMs(), 0.1);
        assertEquals(10.0, stats.getMaxTimeMs(), 0.1);
    }
}
