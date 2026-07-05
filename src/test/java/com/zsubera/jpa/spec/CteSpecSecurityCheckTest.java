package com.zsubera.jpa.spec;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;

/**
 * CteSpec SQL 安全检查的直接单元测试。
 *
 * <p>覆盖 checkSqlSafety() 中的全部检测路径和 validateSelectOnly() 的 DDL/DML 拦截。
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CteSpecSecurityCheckTest {

    @Autowired
    private TestEntityRepository repository;

    @PersistenceContext
    private EntityManager em;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        repository.flush();
    }

    // ---- validateSelectOnly: non-SELECT blocked ----

    @Test
    void as_withDropTable_throwsSecurityException() {
        assertThrows(SecurityException.class, () -> CteSpec.with("t").as("DROP TABLE users"));
    }

    @Test
    void as_withDeleteFrom_throwsSecurityException() {
        assertThrows(SecurityException.class, () -> CteSpec.with("t").as("DELETE FROM users WHERE id=1"));
    }

    @Test
    void as_withInsertInto_throwsSecurityException() {
        assertThrows(SecurityException.class, () -> CteSpec.with("t").as("INSERT INTO users VALUES(1)"));
    }

    @Test
    void as_withUpdateSet_throwsSecurityException() {
        assertThrows(SecurityException.class, () -> CteSpec.with("t").as("UPDATE users SET name='x'"));
    }

    @Test
    void select_withDropTable_throwsSecurityException() {
        CteSpec spec = CteSpec.with("ct").as("SELECT 1");
        assertThrows(SecurityException.class, () -> spec.select("DROP TABLE users"));
    }

    @Test
    void select_withTruncate_throwsSecurityException() {
        CteSpec spec = CteSpec.with("ct").as("SELECT 1");
        assertThrows(SecurityException.class, () -> spec.select("TRUNCATE TABLE users"));
    }

    // ---- checkSqlSafety: dangerous DDL/admin keywords ----

    @Test
    void as_withGrantKeyword_throwsSecurityException() {
        assertThrows(SecurityException.class, () -> CteSpec.with("ct").as("SELECT GRANT ALL ON users TO public"));
    }

    @Test
    void as_withRevokeKeyword_throwsSecurityException() {
        assertThrows(SecurityException.class, () -> CteSpec.with("ct").as("SELECT REVOKE ALL ON users FROM public"));
    }

    @Test
    void as_withAlterKeyword_throwsSecurityException() {
        assertThrows(SecurityException.class, () -> CteSpec.with("ct").as("SELECT ALTER TABLE users ADD col INT"));
    }

    @Test
    void as_withCreateKeyword_throwsSecurityException() {
        assertThrows(SecurityException.class, () -> CteSpec.with("ct").as("SELECT CREATE TABLE evil(id INT)"));
    }

    @Test
    void as_withExecKeyword_throwsSecurityException() {
        assertThrows(SecurityException.class, () -> CteSpec.with("ct").as("SELECT EXEC('bad stuff')"));
    }

    @Test
    void as_withExecuteKeyword_throwsSecurityException() {
        assertThrows(SecurityException.class, () -> CteSpec.with("ct").as("SELECT EXECUTE('bad stuff')"));
    }

    // ---- dangerous procedure names ----

    @Test
    void as_withXpCmdshell_throwsSecurityException() {
        assertThrows(SecurityException.class, () -> CteSpec.with("ct").as("SELECT xp_cmdshell('dir')"));
    }

    @Test
    void as_withSpExecutesql_throwsSecurityException() {
        assertThrows(SecurityException.class, () -> CteSpec.with("ct").as("SELECT sp_executesql('SELECT 1')"));
    }

    // ---- comment injection ----

    @Test
    void as_withBlockComment_throwsSecurityException() {
        assertThrows(SecurityException.class, () -> CteSpec.with("ct").as("SELECT 1 /* comment */ FROM users"));
    }

    @Test
    void as_withEndBlockComment_throwsSecurityException() {
        assertThrows(SecurityException.class, () -> CteSpec.with("ct").as("SELECT 1 */ FROM users"));
    }

    @Test
    void as_withLineComment_throwsSecurityException() {
        assertThrows(SecurityException.class, () -> CteSpec.with("ct").as("SELECT 1 -- comment"));
    }

    // ---- semicolon injection ----

    @Test
    void as_withSemicolonInjection_throwsSecurityException() {
        assertThrows(SecurityException.class, () -> CteSpec.with("ct").as("SELECT 1; DROP TABLE users"));
    }

    // ---- UNION SELECT injection ----

    @Test
    void as_withUnionSelect_throwsSecurityException() {
        assertThrows(SecurityException.class, () -> CteSpec.with("ct").as("SELECT 1 UNION SELECT password FROM admin"));
    }

    @Test
    void as_withUnionAllSelect_throwsSecurityException() {
        // UNION ALL SELECT is not valid in non-recursive CTEs — treated as injection attempt
        assertThrows(SecurityException.class, () -> CteSpec.with("ct").as("SELECT 1 UNION ALL SELECT 2"));
    }

    // ---- WAITFOR DELAY (SQL Server blind injection) ----

    @Test
    void as_withWaitforDelay_throwsSecurityException() {
        assertThrows(SecurityException.class, () -> CteSpec.with("ct").as("SELECT 1; WAITFOR DELAY '0:0:5'"));
    }

    // ---- unbound parameters ----

    @Test
    void buildSql_withUnboundParam_throwsIllegalState() {
        CteSpec spec = CteSpec.with("ct").as("SELECT * FROM users WHERE id = :userId").select("SELECT * FROM ct");
        // No setParameter called for :userId
        assertThrows(IllegalStateException.class, spec::buildSql);
    }

    @Test
    void buildSql_withBoundParam_succeeds() {
        CteSpec spec = CteSpec.with("ct").as("SELECT * FROM users WHERE id = :userId").select("SELECT * FROM ct")
            .setParameter("userId", 1);
        assertDoesNotThrow(spec::buildSql);
    }

    @Test
    void buildSql_asSafeParams_notFlaggedAsUnbound() {
        // asSafe() internally rewrites ?1 to named params like :_cte_0_param_0
        // These should NOT be flagged as unbound
        CteSpec spec =
            CteSpec.with("ct").asSafe("SELECT * FROM users WHERE status = ?1", "ACTIVE").select("SELECT * FROM ct");
        assertDoesNotThrow(spec::buildSql);
    }

    // ---- PostgreSQL type cast :: not treated as unbound param ----

    @Test
    void buildSql_withPostgresCast_notFlaggedAsUnbound() {
        CteSpec spec = CteSpec.with("ct").as("SELECT id::text FROM users").select("SELECT * FROM ct");
        assertDoesNotThrow(spec::buildSql);
    }

    // ---- safe SELECT passes ----

    @Test
    void as_withValidSelect_noException() {
        assertDoesNotThrow(() -> CteSpec.with("ct").as("SELECT id, name FROM users WHERE active = true"));
    }

    @Test
    void as_withValidSelectWith_with_noException() {
        assertDoesNotThrow(() -> CteSpec.with("ct").as("WITH sub AS (SELECT 1) SELECT * FROM sub"));
    }

    // ---- safe CTE names with reserved words rejected ----

    @Test
    void with_reservedWordSelect_throws() {
        assertThrows(IllegalArgumentException.class, () -> CteSpec.with("SELECT"));
    }

    @Test
    void with_reservedWordFrom_throws() {
        assertThrows(IllegalArgumentException.class, () -> CteSpec.with("FROM"));
    }

    @Test
    void with_reservedWordDrop_throws() {
        assertThrows(IllegalArgumentException.class, () -> CteSpec.with("DROP"));
    }

    // ---- CTE name validation ----

    @Test
    void with_nullName_throws() {
        assertThrows(IllegalArgumentException.class, () -> CteSpec.with(null));
    }

    @Test
    void with_emptyName_throws() {
        assertThrows(IllegalArgumentException.class, () -> CteSpec.with(""));
    }

    @Test
    void with_invalidChars_throws() {
        assertThrows(IllegalArgumentException.class, () -> CteSpec.with("ct-name"));
    }

    @Test
    void with_specialChars_throws() {
        assertThrows(IllegalArgumentException.class, () -> CteSpec.with("ct;name"));
    }

    // ---- isStrictMode always true ----

    @Test
    void strictMode_alwaysTrue() {
        assertTrue(CteSpec.isStrictMode());
    }
}
