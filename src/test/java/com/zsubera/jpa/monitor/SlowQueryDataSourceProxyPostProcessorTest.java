package com.zsubera.jpa.monitor;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class SlowQueryDataSourceProxyPostProcessorTest {

    @Test
    void wrapsPlainDataSource() {
        var processor = new SlowQueryDataSourceProxyPostProcessor(500L);
        DataSource raw = mockDataSource();
        Object result = processor.postProcessAfterInitialization(raw, "myDataSource");
        assertNotSame(raw, result);
        assertTrue(result instanceof DataSource);
        assertTrue(SlowQueryDataSourceProxy.isWrapped((DataSource) result));
    }

    @Test
    void skipsFlywayDataSource() {
        var processor = new SlowQueryDataSourceProxyPostProcessor(500L);
        DataSource raw = mockDataSource();
        Object result = processor.postProcessAfterInitialization(raw, "flywayDataSource");
        assertSame(raw, result, "flywayDataSource should not be wrapped");
    }

    @Test
    void skipsLiquibaseDataSource() {
        var processor = new SlowQueryDataSourceProxyPostProcessor(500L);
        DataSource raw = mockDataSource();
        Object result = processor.postProcessAfterInitialization(raw, "liquibaseDataSource");
        assertSame(raw, result);
    }

    @Test
    void skipsMigrationDataSource() {
        var processor = new SlowQueryDataSourceProxyPostProcessor(500L);
        DataSource raw = mockDataSource();
        Object result = processor.postProcessAfterInitialization(raw, "migrationDataSource");
        assertSame(raw, result);
    }

    @Test
    void skipsSchemaInitializerDataSource() {
        var processor = new SlowQueryDataSourceProxyPostProcessor(500L);
        DataSource raw = mockDataSource();
        Object result = processor.postProcessAfterInitialization(raw, "schemaInitializerDataSource");
        assertSame(raw, result);
    }

    @Test
    void returnsNonDataSourceBeanUnchanged() {
        var processor = new SlowQueryDataSourceProxyPostProcessor(500L);
        String notDataSource = "a string bean";
        Object result = processor.postProcessAfterInitialization(notDataSource, "myBean");
        assertSame(notDataSource, result);
    }

    @Test
    void alreadyWrappedDataSourceIsSkipped() {
        var processor = new SlowQueryDataSourceProxyPostProcessor(500L);
        DataSource raw = mockDataSource();
        DataSource alreadyWrapped = SlowQueryDataSourceProxy.wrap(raw, 1000L);
        assertTrue(SlowQueryDataSourceProxy.isWrapped(alreadyWrapped));
        Object result = processor.postProcessAfterInitialization(alreadyWrapped, "primaryDataSource");
        assertSame(alreadyWrapped, result, "Already wrapped DataSource should not be double-wrapped");
    }

    @Test
    void wrapsCustomNamedDataSource() {
        var processor = new SlowQueryDataSourceProxyPostProcessor(2000L);
        DataSource raw = mockDataSource();
        Object result = processor.postProcessAfterInitialization(raw, "analyticsDataSource");
        assertNotSame(raw, result);
        assertTrue(SlowQueryDataSourceProxy.isWrapped((DataSource) result));
    }

    @Test
    void preservesThresholdConfiguration() {
        long threshold = 3000L;
        var processor = new SlowQueryDataSourceProxyPostProcessor(threshold);
        DataSource raw = mockDataSource();
        DataSource wrapped = (DataSource) processor.postProcessAfterInitialization(raw, "appDs");
        assertNotNull(wrapped);
        assertTrue(SlowQueryDataSourceProxy.isWrapped(wrapped));
    }

    private static DataSource mockDataSource() {
        return (DataSource) Proxy.newProxyInstance(
            DataSource.class.getClassLoader(),
            new Class<?>[] {DataSource.class},
            (proxy, method, args) -> null);
    }
}
