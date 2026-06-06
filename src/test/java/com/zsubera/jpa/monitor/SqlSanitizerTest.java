package com.zsubera.jpa.monitor;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SqlSanitizerTest {

    @Test
    void sanitize_null_returnsNullString() {
        assertEquals("null", SqlSanitizer.sanitize(null));
    }

    @Test
    void sanitize_singleQuotedStrings() {
        assertEquals("SELECT * FROM users WHERE name = ?",
            SqlSanitizer.sanitize("SELECT * FROM users WHERE name = 'John'"));
    }

    @Test
    void sanitize_escapedSingleQuotes() {
        assertEquals("SELECT * FROM t WHERE c = ?", SqlSanitizer.sanitize("SELECT * FROM t WHERE c = 'it''s'"));
    }

    @Test
    void sanitize_backslashEscapedQuotes() {
        assertEquals("SELECT * FROM t WHERE c = ?", SqlSanitizer.sanitize("SELECT * FROM t WHERE c = 'it\\'s'"));
    }

    @Test
    void sanitize_multipleStringValues() {
        String sql = "INSERT INTO t (a, b, c) VALUES ('x', 'y', 'z')";
        assertEquals("INSERT INTO t (a, b, c) VALUES (?, ?, ?)", SqlSanitizer.sanitize(sql));
    }

    @Test
    void sanitize_numberLiterals() {
        assertEquals("SELECT * FROM t WHERE id = ?", SqlSanitizer.sanitize("SELECT * FROM t WHERE id = 123"));
    }

    @Test
    void sanitize_decimalNumbers() {
        assertEquals("SELECT * FROM t WHERE price > ?", SqlSanitizer.sanitize("SELECT * FROM t WHERE price > 19.99"));
    }

    @Test
    void sanitize_scientificNotation() {
        assertEquals("SELECT * FROM t WHERE val = ?", SqlSanitizer.sanitize("SELECT * FROM t WHERE val = 1.5e10"));
    }

    @Test
    void sanitize_limitOffsetPreserved() {
        String sql = "SELECT * FROM t LIMIT 10 OFFSET 20";
        assertEquals("SELECT * FROM t LIMIT 10 OFFSET 20", SqlSanitizer.sanitize(sql));
    }

    @Test
    void sanitize_fetchFirstPreserved() {
        String sql = "SELECT * FROM t FETCH FIRST 5 ROWS ONLY";
        assertEquals("SELECT * FROM t FETCH FIRST 5 ROWS ONLY", SqlSanitizer.sanitize(sql));
    }

    @Test
    void sanitize_limitWithNumberReplaced() {
        String sql = "SELECT * FROM t WHERE id = 1 LIMIT 10";
        assertEquals("SELECT * FROM t WHERE id = ? LIMIT 10", SqlSanitizer.sanitize(sql));
    }

    @Test
    void sanitize_postgresDollarParams() {
        assertEquals("SELECT * FROM t WHERE id = ?", SqlSanitizer.sanitize("SELECT * FROM t WHERE id = $1"));
    }

    @Test
    void sanitize_hexLiterals() {
        assertEquals("SELECT * FROM t WHERE hash = ?",
            SqlSanitizer.sanitize("SELECT * FROM t WHERE hash = X'0123456789ABCDEF'"));
    }

    @Test
    void sanitize_unicodeStrings() {
        assertEquals("SELECT * FROM t WHERE name = ?", SqlSanitizer.sanitize("SELECT * FROM t WHERE name = N'John'"));
    }

    @Test
    void sanitize_postgresDollarQuotedStrings() {
        assertEquals("SELECT * FROM t WHERE code = ?",
            SqlSanitizer.sanitize("SELECT * FROM t WHERE code = $$function body$$"));
    }

    @Test
    void sanitize_postgresDollarTaggedStrings() {
        assertEquals("SELECT * FROM t WHERE code = ?",
            SqlSanitizer.sanitize("SELECT * FROM t WHERE code = $func$CREATE FUNCTION$func$"));
    }

    @Test
    void sanitize_oracleQQuotedStrings() {
        assertEquals("SELECT * FROM t WHERE c = ?",
            SqlSanitizer.sanitize("SELECT * FROM t WHERE c = q'[hello world]'"));
        assertEquals("SELECT * FROM t WHERE c = ?",
            SqlSanitizer.sanitize("SELECT * FROM t WHERE c = q'(hello world)'"));
        assertEquals("SELECT * FROM t WHERE c = ?",
            SqlSanitizer.sanitize("SELECT * FROM t WHERE c = q'{hello world}'"));
        assertEquals("SELECT * FROM t WHERE c = ?",
            SqlSanitizer.sanitize("SELECT * FROM t WHERE c = q'<hello world>'"));
    }

    @Test
    void sanitize_singleLineCommentRemoved() {
        String result = SqlSanitizer.sanitize("SELECT * FROM t -- this is a comment");
        assertTrue(result.startsWith("SELECT * FROM t"));
        assertFalse(result.contains("comment"));
    }

    @Test
    void sanitize_multiLineCommentRemoved() {
        String result = SqlSanitizer.sanitize("SELECT * FROM t /* comment */");
        assertTrue(result.startsWith("SELECT * FROM t"));
        assertFalse(result.contains("comment"));
    }

    @Test
    void sanitize_complexQuery() {
        String sql = "SELECT u.id, u.name FROM users u " + "WHERE u.name = 'John' AND u.age > 25 "
            + "ORDER BY u.id LIMIT 10 OFFSET 0";
        String expected =
            "SELECT u.id, u.name FROM users u WHERE u.name = ? AND u.age > ? ORDER BY u.id LIMIT 10 OFFSET 0";
        assertEquals(expected, SqlSanitizer.sanitize(sql));
    }

    @Test
    void sanitize_emptyString() {
        assertEquals("", SqlSanitizer.sanitize(""));
    }

    @Test
    void sanitize_identifiersPreserved() {
        String sql = "SELECT `user_id`, [order_date] FROM `my_table`";
        assertEquals("SELECT `user_id`, [order_date] FROM `my_table`", SqlSanitizer.sanitize(sql));
    }
}
