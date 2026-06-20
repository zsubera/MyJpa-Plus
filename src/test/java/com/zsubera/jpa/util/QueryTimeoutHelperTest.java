package com.zsubera.jpa.util;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.persistence.Query;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class QueryTimeoutHelperTest {

    @AfterEach
    void tearDown() {
        QueryTimeoutHelper.setDefaultTimeoutSeconds(30);
    }

    @Test
    void setDefaultTimeoutSeconds_positiveValue() {
        QueryTimeoutHelper.setDefaultTimeoutSeconds(10);
        assertEquals(10_000, QueryTimeoutHelper.getDefaultTimeoutMs());
        assertTrue(QueryTimeoutHelper.isTimeoutConfigured());
    }

    @Test
    void setDefaultTimeoutSeconds_minusOneDisables() {
        QueryTimeoutHelper.setDefaultTimeoutSeconds(-1);
        assertEquals(-1, QueryTimeoutHelper.getDefaultTimeoutMs());
        assertFalse(QueryTimeoutHelper.isTimeoutConfigured());
    }

    @Test
    void setDefaultTimeoutSeconds_zeroThrows() {
        assertThrows(IllegalArgumentException.class, () -> QueryTimeoutHelper.setDefaultTimeoutSeconds(0));
    }

    @Test
    void setDefaultTimeoutSeconds_negativeThrows() {
        assertThrows(IllegalArgumentException.class, () -> QueryTimeoutHelper.setDefaultTimeoutSeconds(-5));
    }

    @Test
    void setDefaultTimeoutSeconds_overflowThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> QueryTimeoutHelper.setDefaultTimeoutSeconds(Integer.MAX_VALUE / 1000 + 1));
    }

    @Test
    void applyTimeout_withPositiveTimeout_setsHint() {
        QueryTimeoutHelper.setDefaultTimeoutSeconds(5);
        Query query = Mockito.mock(Query.class);
        QueryTimeoutHelper.applyTimeout(query);
        Mockito.verify(query).setHint("jakarta.persistence.query.timeout", 5_000);
    }

    @Test
    void applyTimeout_withDisabledTimeout_doesNotSetHint() {
        QueryTimeoutHelper.setDefaultTimeoutSeconds(-1);
        Query query = Mockito.mock(Query.class);
        QueryTimeoutHelper.applyTimeout(query);
        Mockito.verify(query, Mockito.never()).setHint(Mockito.anyString(), Mockito.any());
    }

    @Test
    void isTimeoutConfigured_defaultIsTrue() {
        QueryTimeoutHelper.setDefaultTimeoutSeconds(30);
        assertTrue(QueryTimeoutHelper.isTimeoutConfigured());
    }

    @Test
    void getDefaultTimeoutMs_returnsMillisecondValue() {
        QueryTimeoutHelper.setDefaultTimeoutSeconds(7);
        assertEquals(7_000, QueryTimeoutHelper.getDefaultTimeoutMs());
    }
}
