package com.zsubera.jpa.autoconfigure;

import static org.junit.jupiter.api.Assertions.*;

import com.zsubera.jpa.template.MyJpaTemplate;
import org.junit.jupiter.api.Test;

class MyJpaPlusAutoConfigurationTest {

    @Test
    void properties_defaultValues() {
        MyJpaPlusProperties properties = new MyJpaPlusProperties();
        assertTrue(properties.getSoftDelete().isAutoFilter());
        assertEquals(10000, properties.getQuery().getMaxResults());
        assertEquals(100000, properties.getQuery().getDeepPaginationOffsetThreshold());
    }

    @Test
    void properties_softDelete_setterGetter() {
        MyJpaPlusProperties properties = new MyJpaPlusProperties();
        properties.getSoftDelete().setAutoFilter(false);
        assertFalse(properties.getSoftDelete().isAutoFilter());
    }

    @Test
    void properties_query_setMaxResults() {
        MyJpaPlusProperties properties = new MyJpaPlusProperties();
        properties.getQuery().setMaxResults(5000);
        assertEquals(5000, properties.getQuery().getMaxResults());
    }

    @Test
    void properties_query_setMaxResults_zero_throwsException() {
        MyJpaPlusProperties properties = new MyJpaPlusProperties();
        assertThrows(IllegalArgumentException.class, () -> properties.getQuery().setMaxResults(0));
    }

    @Test
    void properties_query_setMaxResults_negative_throwsException() {
        MyJpaPlusProperties properties = new MyJpaPlusProperties();
        assertThrows(IllegalArgumentException.class, () -> properties.getQuery().setMaxResults(-1));
    }

    @Test
    void properties_query_setDeepPaginationOffsetThreshold() {
        MyJpaPlusProperties properties = new MyJpaPlusProperties();
        properties.getQuery().setDeepPaginationOffsetThreshold(50000);
        assertEquals(50000, properties.getQuery().getDeepPaginationOffsetThreshold());
    }

    @Test
    void properties_query_setDeepPaginationOffsetThreshold_zero_throwsException() {
        MyJpaPlusProperties properties = new MyJpaPlusProperties();
        assertThrows(IllegalArgumentException.class, () -> properties.getQuery().setDeepPaginationOffsetThreshold(0));
    }

    @Test
    void properties_query_setDeepPaginationOffsetThreshold_negative_throwsException() {
        MyJpaPlusProperties properties = new MyJpaPlusProperties();
        assertThrows(IllegalArgumentException.class, () -> properties.getQuery().setDeepPaginationOffsetThreshold(-1));
    }

    @Test
    void autoConfiguration_constructor_acceptsProperties() {
        MyJpaPlusProperties properties = new MyJpaPlusProperties();
        assertDoesNotThrow(() -> new MyJpaPlusAutoConfiguration(properties));
    }

    @Test
    void autoConfiguration_myJpaTemplate_returnsConfiguredTemplate() {
        MyJpaPlusProperties properties = new MyJpaPlusProperties();
        properties.getQuery().setMaxResults(5000);
        properties.getQuery().setDeepPaginationOffsetThreshold(50000);
        MyJpaPlusAutoConfiguration configuration = new MyJpaPlusAutoConfiguration(properties);
        MyJpaTemplate template = configuration.myJpaTemplate(properties);
        assertNotNull(template);
    }
}
