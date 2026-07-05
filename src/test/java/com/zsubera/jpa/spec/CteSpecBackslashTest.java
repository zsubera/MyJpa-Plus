package com.zsubera.jpa.spec;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Regression tests for CteSpec.asSafe() backslash handling in string literals.
 * Verifies that parameter replacement works correctly when SQL contains
 * backslash-escaped characters inside string literals.
 */
class CteSpecBackslashTest {

    @Test
    void asSafe_backslashInString_paramsReplacedOutsideString() {
        // SQL: path = 'C:\test' AND col = ?1
        // The \t inside the string should be skipped, and ?1 is outside the string
        CteSpec cte = CteSpec.with("test_cte").asSafe("SELECT id FROM t WHERE path = 'C:\\test' AND col = ?1", "value")
            .select("SELECT * FROM test_cte");

        String sql = cte.buildSql();
        assertTrue(sql.contains(":_cte_0_param_0"), "Parameter should be replaced, got: " + sql);
        assertFalse(sql.contains("?1"), "Raw parameter marker should not remain, got: " + sql);
    }

    @Test
    void asSafe_doubleBackslashInString_paramsReplaced() {
        // SQL: path = 'C:\\server' AND col = ?1
        // The \\ inside the string should be skipped as a pair
        CteSpec cte =
            CteSpec.with("test_cte").asSafe("SELECT id FROM t WHERE path = 'C:\\\\server' AND col = ?1", "value")
                .select("SELECT * FROM test_cte");

        String sql = cte.buildSql();
        assertTrue(sql.contains(":_cte_0_param_0"), "Parameter should be replaced, got: " + sql);
        assertFalse(sql.contains("?1"), "Raw parameter marker should not remain, got: " + sql);
    }

    @Test
    void asSafe_noBackslash_paramsReplaced() {
        CteSpec cte = CteSpec.with("test_cte").asSafe("SELECT id FROM t WHERE x = 'hello' AND col = ?1", "value")
            .select("SELECT * FROM test_cte");

        String sql = cte.buildSql();
        assertTrue(sql.contains(":_cte_0_param_0"), "Parameter should be replaced, got: " + sql);
    }

    @Test
    void asSafe_multipleParamsAfterBackslashString_allReplaced() {
        CteSpec cte = CteSpec.with("test_cte")
            .asSafe("SELECT id FROM t WHERE path = 'C:\\test' AND y = ?1 AND z = ?2", "val1", "val2")
            .select("SELECT * FROM test_cte");

        String sql = cte.buildSql();
        assertTrue(sql.contains(":_cte_0_param_0"), "First parameter should be replaced, got: " + sql);
        assertTrue(sql.contains(":_cte_0_param_1"), "Second parameter should be replaced, got: " + sql);
        assertFalse(sql.contains("?1"), "Raw ?1 should not remain, got: " + sql);
        assertFalse(sql.contains("?2"), "Raw ?2 should not remain, got: " + sql);
    }

    @Test
    void asSafe_paramInsideString_notReplaced() {
        // ?1 is inside a string literal — should NOT be replaced
        CteSpec cte = CteSpec.with("test_cte").asSafe("SELECT id FROM t WHERE name = '?1'", "unused")
            .select("SELECT * FROM test_cte");

        String sql = cte.buildSql();
        assertFalse(sql.contains(":_cte_0_param_0"), "Parameter inside string should NOT be replaced, got: " + sql);
        assertTrue(sql.contains("?1"), "Raw parameter marker should remain inside string, got: " + sql);
    }

    @Test
    void asSafe_backslashEscapedQuote_paramsReplaced() {
        // SQL: name = 'it''s' AND col = ?1
        // The '' is an escaped quote in SQL
        CteSpec cte = CteSpec.with("test_cte").asSafe("SELECT id FROM t WHERE name = 'it''s' AND col = ?1", "value")
            .select("SELECT * FROM test_cte");

        String sql = cte.buildSql();
        assertTrue(sql.contains(":_cte_0_param_0"), "Parameter should be replaced, got: " + sql);
        assertFalse(sql.contains("?1"), "Raw parameter marker should not remain, got: " + sql);
    }
}
