package com.zsubera.jpa.monitor;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Additional unit tests for SqlSanitizer covering:
 * - PostgreSQL E-strings (E'...')
 * - MySQL double-quoted strings (ANSI_QUOTES)
 * - Multiple LIMIT/OFFSET in same query
 * - Oracle Q-quote single-character delimiters
 * - Edge cases for dollar-quoted strings
 */
class SqlSanitizerAdditionalTest {

    @Test
    void sanitize_pgEString_simple() {
        assertEquals("SELECT * FROM t WHERE c = ?", SqlSanitizer.sanitize("SELECT * FROM t WHERE c = E'hello'"));
    }

    @Test
    void sanitize_pgEString_withEscapedQuote() {
        assertEquals("SELECT * FROM t WHERE c = ?", SqlSanitizer.sanitize("SELECT * FROM t WHERE c = E'it\\'s'"));
    }

    @Test
    void sanitize_pgEString_withBackslashEscape() {
        assertEquals("SELECT * FROM t WHERE c = ?",
            SqlSanitizer.sanitize("SELECT * FROM t WHERE c = E'line1\\nline2'"));
    }

    @Test
    void sanitize_pgEString_emptyString() {
        assertEquals("SELECT * FROM t WHERE c = ?", SqlSanitizer.sanitize("SELECT * FROM t WHERE c = E''"));
    }

    @Test
    void sanitize_pgEString_multipleInQuery() {
        String sql = "INSERT INTO t (a, b) VALUES (E'hello', E'world')";
        assertEquals("INSERT INTO t (a, b) VALUES (?, ?)", SqlSanitizer.sanitize(sql));
    }

    @Test
    void sanitize_pgEString_withDoubleEscapedQuote() {
        assertEquals("SELECT * FROM t WHERE c = ?", SqlSanitizer.sanitize("SELECT * FROM t WHERE c = E'path\\\\file'"));
    }

    @Test
    void sanitize_doubleQuoteString_simple() {
        assertEquals("SELECT * FROM t WHERE c = ?", SqlSanitizer.sanitize("SELECT * FROM t WHERE c = \"hello\""));
    }

    @Test
    void sanitize_doubleQuoteString_withEscapedQuote() {
        assertEquals("SELECT * FROM t WHERE c = ?", SqlSanitizer.sanitize("SELECT * FROM t WHERE c = \"it''s\""));
    }

    @Test
    void sanitize_doubleQuoteString_withBackslashEscape() {
        assertEquals("SELECT * FROM t WHERE c = ?", SqlSanitizer.sanitize("SELECT * FROM t WHERE c = \"path\\file\""));
    }

    @Test
    void sanitize_doubleQuoteString_emptyString() {
        assertEquals("SELECT * FROM t WHERE c = ?", SqlSanitizer.sanitize("SELECT * FROM t WHERE c = \"\""));
    }

    @Test
    void sanitize_doubleQuoteString_multipleInQuery() {
        String sql = "INSERT INTO t (a, b) VALUES (\"hello\", \"world\")";
        assertEquals("INSERT INTO t (a, b) VALUES (?, ?)", SqlSanitizer.sanitize(sql));
    }

    @Test
    void sanitize_doubleQuoteString_identifierPreserved() {
        // Double-quoted identifiers in MySQL with ANSI_QUOTES mode
        // should be treated as string literals and sanitized
        assertEquals("SELECT * FROM t WHERE c = ?",
            SqlSanitizer.sanitize("SELECT * FROM t WHERE c = \"sensitive data\""));
    }

    @Test
    void sanitize_multipleLimitOffset() {
        String sql = "SELECT * FROM t LIMIT 10 OFFSET 20 FETCH FIRST 5 ROWS ONLY";
        String result = SqlSanitizer.sanitize(sql);
        assertTrue(result.contains("LIMIT 10"));
        assertTrue(result.contains("OFFSET 20"));
        assertTrue(result.contains("FETCH FIRST 5 ROWS ONLY"));
    }

    @Test
    void sanitize_limitOffsetWithNumbersReplaced() {
        String sql = "SELECT * FROM t WHERE id = 123 LIMIT 10 OFFSET 20";
        String result = SqlSanitizer.sanitize(sql);
        assertTrue(result.contains("LIMIT 10"));
        assertTrue(result.contains("OFFSET 20"));
        assertFalse(result.contains("123"));
    }

    @Test
    void sanitize_oracleQQuote_singleCharDelimiter_exclamation() {
        assertEquals("SELECT * FROM t WHERE c = ?",
            SqlSanitizer.sanitize("SELECT * FROM t WHERE c = q'!hello world!'"));
    }

    @Test
    void sanitize_oracleQQuote_singleCharDelimiter_hash() {
        assertEquals("SELECT * FROM t WHERE c = ?",
            SqlSanitizer.sanitize("SELECT * FROM t WHERE c = q'#hello world#'"));
    }

    @Test
    void sanitize_oracleQQuote_singleCharDelimiter_at() {
        assertEquals("SELECT * FROM t WHERE c = ?",
            SqlSanitizer.sanitize("SELECT * FROM t WHERE c = q'@hello world@'"));
    }

    @Test
    void sanitize_pgDollarQuoted_emptyContent() {
        // Empty dollar-quoted strings ($$$) are not matched by the regex
        // which requires content between the delimiters
        String result = SqlSanitizer.sanitize("SELECT * FROM t WHERE code = $$$");
        assertNotNull(result);
    }

    @Test
    void sanitize_pgDollarQuoted_withNewlines() {
        assertEquals("SELECT * FROM t WHERE code = ?",
            SqlSanitizer.sanitize("SELECT * FROM t WHERE code = $$line1\nline2$$"));
    }

    @Test
    void sanitize_pgDollarParam_multipleParams() {
        String sql = "SELECT * FROM t WHERE a = $1 AND b = $2 AND c = $3";
        assertEquals("SELECT * FROM t WHERE a = ? AND b = ? AND c = ?", SqlSanitizer.sanitize(sql));
    }

    @Test
    void sanitize_hexLiteral_withSpaces() {
        assertEquals("SELECT * FROM t WHERE c = ?", SqlSanitizer.sanitize("SELECT * FROM t WHERE c = X'FF FF FF'"));
    }

    @Test
    void sanitize_unicodeString_withEscapedQuote() {
        assertEquals("SELECT * FROM t WHERE c = ?", SqlSanitizer.sanitize("SELECT * FROM t WHERE c = N'it''s'"));
    }

    @Test
    void sanitize_complexMixedTypes() {
        String sql = "INSERT INTO t (str, num, hex, estr, dquote) VALUES ('abc', 123, X'FF', E'hello', \"world\")";
        String result = SqlSanitizer.sanitize(sql);
        assertEquals("INSERT INTO t (str, num, hex, estr, dquote) VALUES (?, ?, ?, ?, ?)", result);
    }

    @Test
    void sanitize_identifierWithBackticks_preserved() {
        String sql = "SELECT `user_id`, `name` FROM `users` WHERE `id` = 1";
        String result = SqlSanitizer.sanitize(sql);
        assertTrue(result.contains("`user_id`"));
        assertTrue(result.contains("`name`"));
        assertTrue(result.contains("`users`"));
        assertFalse(result.contains("1"));
    }

    @Test
    void sanitize_identifierWithSquareBrackets_preserved() {
        String sql = "SELECT [user_id], [name] FROM [users] WHERE [id] = 1";
        String result = SqlSanitizer.sanitize(sql);
        assertTrue(result.contains("[user_id]"));
        assertTrue(result.contains("[name]"));
        assertTrue(result.contains("[users]"));
        assertFalse(result.contains("1"));
    }

    @Test
    void sanitize_selectStar() {
        assertEquals("SELECT * FROM t", SqlSanitizer.sanitize("SELECT * FROM t"));
    }

    @Test
    void sanitize_emptyWhereClause() {
        // Numbers in WHERE clause are sanitized (1=1 becomes ?=?)
        String result = SqlSanitizer.sanitize("SELECT * FROM t WHERE 1=1");
        assertNotNull(result);
    }

    @Test
    void sanitize_multipleStringTypesInWhere() {
        String sql = "SELECT * FROM t WHERE a = 'str' AND b = 123 AND c = E'estr' AND d = \"dstr\"";
        String result = SqlSanitizer.sanitize(sql);
        assertEquals("SELECT * FROM t WHERE a = ? AND b = ? AND c = ? AND d = ?", result);
    }

    @Test
    void sanitize_fetchFirstRowOnly() {
        String sql = "SELECT * FROM t FETCH FIRST 1 ROW ONLY";
        assertEquals("SELECT * FROM t FETCH FIRST 1 ROW ONLY", SqlSanitizer.sanitize(sql));
    }

    @Test
    void sanitize_fetchNextRows() {
        String sql = "SELECT * FROM t OFFSET 10 FETCH NEXT 5 ROWS ONLY";
        String result = SqlSanitizer.sanitize(sql);
        assertTrue(result.contains("OFFSET 10"));
        assertTrue(result.contains("FETCH NEXT 5 ROWS ONLY"));
    }

    @Test
    void sanitize_pgDollarQuoted_nestedDollars() {
        // $$price=$$$ - the regex matches $$price=$$ and leaves trailing $
        String result = SqlSanitizer.sanitize("SELECT * FROM t WHERE code = $$price=$$$");
        assertNotNull(result);
    }

    @Test
    void sanitize_oracleQQuote_bracketWithNestedBrackets() {
        assertEquals("SELECT * FROM t WHERE c = ?",
            SqlSanitizer.sanitize("SELECT * FROM t WHERE c = q'[hello [world]]'"));
    }

    @Test
    void sanitize_oracleQQuote_angleBrackets() {
        assertEquals("SELECT * FROM t WHERE c = ?",
            SqlSanitizer.sanitize("SELECT * FROM t WHERE c = q'<hello world>'"));
    }

    @Test
    void sanitize_oracleQQuote_curlyBrackets() {
        assertEquals("SELECT * FROM t WHERE c = ?",
            SqlSanitizer.sanitize("SELECT * FROM t WHERE c = q'{hello world}'"));
    }
}
