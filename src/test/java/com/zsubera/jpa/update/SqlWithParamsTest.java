package com.zsubera.jpa.update;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SqlWithParams} record — immutability and correctness.
 */
class SqlWithParamsTest {

    @Test
    void constructorStoresSqlAndParams() {
        SqlWithParams sp = new SqlWithParams("SELECT 1", Arrays.asList("a", "b"));
        assertEquals("SELECT 1", sp.sql());
        assertEquals(2, sp.params().size());
    }

    @Test
    void paramsListIsUnmodifiable() {
        SqlWithParams sp = new SqlWithParams("SELECT 1", new ArrayList<>(Arrays.asList("x")));
        assertThrows(UnsupportedOperationException.class, () -> sp.params().add("y"));
    }

    @Test
    void paramsListDefensiveCopy() {
        List<Object> original = new ArrayList<>(Arrays.asList("a", "b"));
        SqlWithParams sp = new SqlWithParams("SELECT 1", original);
        original.add("c");
        assertEquals(2, sp.params().size(), "External mutation should not affect internal params");
    }

    @Test
    void paramsAllowNullElements() {
        SqlWithParams sp = new SqlWithParams("INSERT INTO t VALUES (?)", Arrays.asList((Object)null));
        assertNull(sp.params().get(0));
    }

    @Test
    void paramsAllowMixedTypes() {
        SqlWithParams sp = new SqlWithParams("INSERT INTO t VALUES (?, ?, ?)", Arrays.asList("str", 42, 3.14));
        assertEquals(3, sp.params().size());
        assertEquals("str", sp.params().get(0));
        assertEquals(42, sp.params().get(1));
        assertEquals(3.14, sp.params().get(2));
    }

    @Test
    void emptyParamsList() {
        SqlWithParams sp = new SqlWithParams("SELECT 1", List.of());
        assertTrue(sp.params().isEmpty());
    }

    @Test
    void equalityByValue() {
        SqlWithParams a = new SqlWithParams("SELECT 1", List.of("x"));
        SqlWithParams b = new SqlWithParams("SELECT 1", List.of("x"));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void inequalityByDifferentSql() {
        SqlWithParams a = new SqlWithParams("SELECT 1", List.of("x"));
        SqlWithParams b = new SqlWithParams("SELECT 2", List.of("x"));
        assertNotEquals(a, b);
    }

    @Test
    void inequalityByDifferentParams() {
        SqlWithParams a = new SqlWithParams("SELECT 1", List.of("x"));
        SqlWithParams b = new SqlWithParams("SELECT 1", List.of("y"));
        assertNotEquals(a, b);
    }
}
