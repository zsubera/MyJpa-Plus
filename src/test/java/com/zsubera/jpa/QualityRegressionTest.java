package com.zsubera.jpa;

import static org.junit.jupiter.api.Assertions.*;

import com.zsubera.jpa.converter.EncryptConverter;
import com.zsubera.jpa.exception.MyJpaPlusException;
import com.zsubera.jpa.monitor.SqlSanitizer;
import com.zsubera.jpa.spec.ConditionNode;
import com.zsubera.jpa.spec.QuerySpec;
import com.zsubera.jpa.spec.TestEntity;
import com.zsubera.jpa.util.InClauseBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 代码质量相关的回归测试，覆盖防御性编程和边界条件。
 */
class QualityRegressionTest {

    @BeforeEach
    void setUp() {
        System.setProperty("myjpa.encrypt.key", "1234567890123456");
        System.setProperty("myjpa.encrypt.salt", "test-salt");
        EncryptConverter.clearCaches();
    }

    @AfterEach
    void tearDown() {
        System.clearProperty("myjpa.encrypt.key");
        System.clearProperty("myjpa.encrypt.salt");
        System.clearProperty("myjpa-plus.encrypt.skip-salt-check");
        EncryptConverter.clearCaches();
    }

    // ---- SqlSanitizer 反斜杠结尾字符串 ----

    @Test
    @DisplayName("SqlSanitizer should handle string ending with backslash-quote")
    void sqlSanitizer_backslashEndingString() {
        String sql = "SELECT * FROM t WHERE c = 'test\\'";
        String sanitized = SqlSanitizer.sanitize(sql);
        assertFalse(sanitized.contains("'test\\'"));
        assertTrue(sanitized.contains("?"));
    }

    @Test
    @DisplayName("SqlSanitizer should handle normal strings")
    void sqlSanitizer_normalStrings() {
        assertEquals("SELECT * FROM t WHERE c = ?",
            SqlSanitizer.sanitize("SELECT * FROM t WHERE c = 'hello'"));
    }

    @Test
    @DisplayName("SqlSanitizer should handle escaped quotes")
    void sqlSanitizer_escapedQuotes() {
        assertEquals("SELECT * FROM t WHERE c = ?",
            SqlSanitizer.sanitize("SELECT * FROM t WHERE c = 'it''s'"));
    }

    // ---- FuncNode 构造函数白名单 ----

    @Test
    @DisplayName("FuncNode constructor should reject non-whitelisted functions")
    void funcNode_rejectsNonWhitelisted() {
        java.lang.reflect.InvocationTargetException ex = assertThrows(
            java.lang.reflect.InvocationTargetException.class, () -> {
                java.lang.reflect.Method ofMethod =
                    ConditionNode.FuncNode.class.getDeclaredMethod("of", String.class, Object[].class);
                ofMethod.setAccessible(true);
                ofMethod.invoke(null, "pg_sleep", new Object[]{1});
            });
        assertInstanceOf(com.zsubera.jpa.exception.SecurityViolationException.class, ex.getCause());
    }

    @Test
    @DisplayName("FuncNode constructor should allow whitelisted functions")
    void funcNode_allowsWhitelisted() {
        assertDoesNotThrow(() -> {
            java.lang.reflect.Constructor<ConditionNode.FuncNode> ctor =
                ConditionNode.FuncNode.class.getDeclaredConstructor(String.class, Object[].class);
            ctor.setAccessible(true);
            ctor.newInstance("COALESCE", new Object[]{"a", "b"});
        });
    }

    // ---- InClauseBuilder 配置验证 ----

    @Test
    @DisplayName("InClauseBuilder Config should accept valid values")
    void inClauseBuilder_validConfig() {
        InClauseBuilder.Config original = new InClauseBuilder.Config(
            InClauseBuilder.getMaxInClauseSize(), InClauseBuilder.getHardLimit());

        try {
            InClauseBuilder.Config config = new InClauseBuilder.Config(500, 1000);
            InClauseBuilder.setConfig(config);
            assertEquals(500, InClauseBuilder.getMaxInClauseSize());
            assertEquals(1000, InClauseBuilder.getHardLimit());
        } finally {
            InClauseBuilder.setConfig(original);
        }
    }

    @Test
    @DisplayName("InClauseBuilder Config should reject values above limit")
    void inClauseBuilder_rejectsOverLimit() {
        assertThrows(IllegalArgumentException.class,
            () -> new InClauseBuilder.Config(200000, 200000));
    }

    // ---- BulkTransactionHelper 类加载 ----

    @Test
    @DisplayName("BulkTransactionHelper should be loadable")
    void bulkTransactionHelper_loadable() {
        assertDoesNotThrow(() -> Class.forName("com.zsubera.jpa.update.BulkTransactionHelper"));
    }

    // ---- EncryptConverter 预热异常处理 ----

    @Test
    @DisplayName("warmUpKeyCacheSync should not throw on config error")
    void warmUpKeyCacheSync_noThrow() {
        System.clearProperty("myjpa.encrypt.key");
        EncryptConverter.clearCaches();
        assertDoesNotThrow(EncryptConverter::warmUpKeyCacheSync);
    }

    // ---- EncryptConverter 错误消息改进 ----

    @Test
    @DisplayName("Decrypt error message should hint at non-encrypted data")
    void decrypt_errorMessage_hint() {
        EncryptConverter converter = new EncryptConverter();

        MyJpaPlusException ex = assertThrows(MyJpaPlusException.class,
            () -> converter.convertToEntityAttribute("not-encrypted-data"));

        String msg = ex.getMessage().toLowerCase();
        assertTrue(msg.contains("not be encrypted") || msg.contains("plain text")
            || msg.contains("corrupted"));
    }

    // ---- EncryptionKeyManager dev salt ----

    @Test
    @DisplayName("Dev salt should allow encryption/decryption")
    void devSalt_encryptionWorks() {
        System.clearProperty("myjpa.encrypt.salt");
        System.setProperty("myjpa-plus.encrypt.skip-salt-check", "true");

        try {
            EncryptConverter converter = new EncryptConverter();
            String encrypted = converter.convertToDatabaseColumn("test");
            assertNotNull(encrypted);

            String decrypted = converter.convertToEntityAttribute(encrypted);
            assertEquals("test", decrypted);
        } finally {
            System.clearProperty("myjpa-plus.encrypt.skip-salt-check");
            System.setProperty("myjpa.encrypt.salt", "test-salt");
            EncryptConverter.clearCaches();
        }
    }

    // ---- EntityFieldExtractor 类加载 ----

    @Test
    @DisplayName("EntityFieldExtractor should be loadable")
    void entityFieldExtractor_loadable() {
        assertDoesNotThrow(() -> Class.forName("com.zsubera.jpa.update.EntityFieldExtractor"));
    }

    // ---- or() 合并语义 ----

    @Test
    @DisplayName("Consecutive or() calls should merge correctly")
    void orMerge_consecutiveOr() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.or(o -> o.eq(TestEntity::getStatus, 1))
           .or(o -> o.eq(TestEntity::getStatus, 2));

        assertDoesNotThrow(() -> qs.toSpecification());
    }

    // ---- clearCaches 并发安全 ----

    @Test
    @DisplayName("Concurrent clearCaches should not throw")
    void clearCaches_concurrentSafe() throws Exception {
        System.setProperty("myjpa.encrypt.key", "1234567890123456");
        System.setProperty("myjpa.encrypt.salt", "test-salt");

        Thread[] threads = new Thread[10];
        java.util.concurrent.CountDownLatch latch =
            new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger errors =
            new java.util.concurrent.atomic.AtomicInteger(0);

        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(() -> {
                try {
                    latch.await();
                    EncryptConverter.clearCaches();
                } catch (Exception e) {
                    errors.incrementAndGet();
                }
            });
            threads[i].start();
        }

        latch.countDown();
        for (Thread t : threads) {
            t.join(5000);
        }

        assertEquals(0, errors.get());
    }
}
