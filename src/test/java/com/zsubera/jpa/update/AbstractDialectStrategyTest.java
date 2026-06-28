package com.zsubera.jpa.update;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class AbstractDialectStrategyTest {

    private final PostgresDialect postgres = new PostgresDialect();
    private final MysqlDialect mysql = new MysqlDialect();
    private final OracleDialect oracle = new OracleDialect();
    private final SqlServerDialect sqlserver = new SqlServerDialect();

    @Test
    void escapeIdentifier_null_throws() {
        assertThrows(IllegalArgumentException.class, () -> postgres.escapeIdentifier(null));
    }

    @Test
    void escapeIdentifier_postgres_usesDoubleQuotes() {
        assertEquals("\"users\"", postgres.escapeIdentifier("users"));
    }

    @Test
    void escapeIdentifier_mysql_usesBackticks() {
        assertEquals("`users`", mysql.escapeIdentifier("users"));
    }

    @Test
    void escapeIdentifier_oracle_usesDoubleQuotes() {
        assertEquals("\"users\"", oracle.escapeIdentifier("users"));
    }

    @Test
    void escapeIdentifier_sqlserver_usesSquareBrackets() {
        assertEquals("[users]", sqlserver.escapeIdentifier("users"));
    }

    @Test
    void escapeIdentifier_dotSeparated_postgres() {
        assertEquals("\"public\".\"users\"", postgres.escapeIdentifier("public.users"));
    }

    @Test
    void escapeIdentifier_dotSeparated_mysql() {
        assertEquals("`schema`.`table`", mysql.escapeIdentifier("schema.table"));
    }

    @Test
    void escapeIdentifier_dotSeparated_oracle() {
        assertEquals("\"schema\".\"table\"", oracle.escapeIdentifier("schema.table"));
    }

    @Test
    void escapeIdentifier_dotSeparated_sqlserver() {
        assertEquals("[schema].[table]", sqlserver.escapeIdentifier("schema.table"));
    }

    @Test
    void escapeIdentifier_withEmbeddedQuote_postgres() {
        assertEquals("\"user\"\"name\"", postgres.escapeIdentifier("user\"name"));
    }

    @Test
    void escapeIdentifier_withEmbeddedBacktick_mysql() {
        assertEquals("`user``name`", mysql.escapeIdentifier("user`name"));
    }

    @Test
    void escapeIdentifier_withEmbeddedBracket_sqlserver() {
        assertEquals("[user]]name]", sqlserver.escapeIdentifier("user]name"));
    }

    @Test
    void escapeIdentifier_tripleDotSeparated_postgres() {
        assertEquals("\"catalog\".\"schema\".\"table\"", postgres.escapeIdentifier("catalog.schema.table"));
    }

    @Test
    void escapeIdentifier_specialChars_preserved() {
        assertEquals("\"my-table\"", postgres.escapeIdentifier("my-table"));
        assertEquals("`my-table`", mysql.escapeIdentifier("my-table"));
    }
}
