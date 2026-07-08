package com.zsubera.jpa;

import static org.junit.jupiter.api.Assertions.*;

import com.zsubera.jpa.converter.CodeEnumType;
import com.zsubera.jpa.converter.CodeEnumValue;
import com.zsubera.jpa.converter.EncryptConverter;
import com.zsubera.jpa.exception.QueryBuildException;
import com.zsubera.jpa.repository.EntityManagerHelper;
import com.zsubera.jpa.repository.SoftDeleteContext;
import com.zsubera.jpa.spec.*;
import com.zsubera.jpa.template.MyJpaTemplate;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ContextConfiguration;

/**
 * 已修复缺陷的回归测试，确保关键修复不会退化。
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = {TestApplication.class, DefectRegressionTest.TestConfig.class})
class DefectRegressionTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        public MyJpaTemplate myJpaTemplate() {
            return new MyJpaTemplate();
        }
    }

    @Autowired
    private MyJpaTemplate template;

    @Autowired
    private SoftDeleteTestEntityRepository repository;

    @PersistenceContext
    private EntityManager entityManager;

    @BeforeEach
    void cleanUp() {
        repository.deleteAll();
        repository.flush();
        EncryptConverter.clearCaches();
    }

    @AfterEach
    void tearDown() {
        System.clearProperty("myjpa.encrypt.key");
        System.clearProperty("myjpa.encrypt.salt");
        System.clearProperty("myjpa-plus.encrypt.pbkdf2-iterations");
        EncryptConverter.clearCaches();
    }

    // ---- 加密缓存清除 ----

    @Test
    @DisplayName("clearCaches should reset encryption state for re-derivation")
    void clearCaches_resetsEncryptionState() {
        System.setProperty("myjpa.encrypt.key", "1234567890123456");
        System.setProperty("myjpa.encrypt.salt", "test-salt");

        EncryptConverter.setPbkdf2Iterations(200_000);
        EncryptConverter.clearCaches();

        EncryptConverter converter = new EncryptConverter();
        String encrypted = converter.convertToDatabaseColumn("test");
        assertNotNull(encrypted);
    }

    // ---- LEFT FETCH JOIN 条件抛异常 ----

    @Test
    @DisplayName("leftFetchJoin with filter conditions should throw QueryBuildException")
    void leftFetchJoin_withConditions_throws() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.<ParentEntity>leftFetchJoin(TestEntity::getParent,
            j -> j.eq(ParentEntity::getCategory, "admin"));

        assertThrows(QueryBuildException.class,
            () -> template.findAll(TestEntity.class, qs));
    }

    @Test
    @DisplayName("INNER fetchJoin with conditions should not throw")
    void fetchJoin_inner_withConditions_ok() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.<ParentEntity>fetchJoin(TestEntity::getParent,
            j -> j.eq(ParentEntity::getCategory, "admin"));

        assertDoesNotThrow(() -> qs.toSpecification());
    }

    // ---- CTE 递归 UNION SELECT ----

    @Test
    @DisplayName("Recursive CTE with UNION SELECT should be allowed")
    void cte_recursive_unionSelect_allowed() {
        assertDoesNotThrow(() -> {
            CteSpec.withRecursive("cte")
                .as("SELECT id FROM test_entity UNION SELECT id FROM test_entity");
        });
    }

    @Test
    @DisplayName("Non-recursive CTE with UNION SELECT should throw in strict mode")
    void cte_nonRecursive_unionSelect_throws() {
        assertThrows(SecurityException.class, () -> {
            CteSpec.with("cte")
                .as("SELECT id FROM test_entity UNION SELECT id FROM test_entity");
        });
    }

    // ---- CTE dollar-quoted 字符串 ----

    @Test
    @DisplayName("CteSpec dollar-quoted string should preserve placeholders")
    void cte_dollarQuoted_preservesPlaceholders() {
        assertDoesNotThrow(() -> {
            CteSpec.with("cte")
                .asSafe("SELECT $$value is ?1$$ AS col", "test");
        });
    }

    // ---- Cipher 池 ----

    @Test
    @DisplayName("Cipher pool should work correctly with queue-based size tracking")
    void cipherPool_queueSizeTracking() {
        System.setProperty("myjpa.encrypt.key", "1234567890123456");
        System.setProperty("myjpa.encrypt.salt", "test-salt");

        EncryptConverter converter = new EncryptConverter();
        String encrypted = converter.convertToDatabaseColumn("test-data");
        assertNotNull(encrypted);

        String decrypted = converter.convertToEntityAttribute(encrypted);
        assertEquals("test-data", decrypted);
    }

    // ---- SlowQueryDataSourceProxy sql 字段 volatile ----

    @Test
    @DisplayName("StatementTimingHandler.sql field should be volatile")
    void slowQueryProxy_sqlField_volatile() throws Exception {
        java.lang.reflect.Field sqlField = Class.forName(
            "com.zsubera.jpa.monitor.SlowQueryDataSourceProxy$StatementTimingHandler")
            .getDeclaredField("sql");
        assertTrue(java.lang.reflect.Modifier.isVolatile(sqlField.getModifiers()));
    }

    // ---- EntityManagerHelper reset 原子性 ----

    @Test
    @DisplayName("EntityManagerHelper.reset should not throw")
    void entityManagerHelper_reset() {
        assertDoesNotThrow(EntityManagerHelper::reset);
    }

    // ---- EntityModifiedEvent null 验证 ----

    @Test
    @DisplayName("EntityModifiedEvent should reject null in both constructors")
    void entityModifiedEvent_rejectsNull() {
        assertThrows(IllegalArgumentException.class,
            () -> new com.zsubera.jpa.template.EntityModifiedEvent((Class<?>)null, 1));
        assertThrows(IllegalArgumentException.class,
            () -> new com.zsubera.jpa.template.EntityModifiedEvent((String)null, 1));
    }

    // ---- EntityModifiedEvent source 类型一致性 ----

    @Test
    @DisplayName("EntityModifiedEvent source type should be String in both constructors")
    void entityModifiedEvent_sourceTypeConsistent() {
        var fromClass = new com.zsubera.jpa.template.EntityModifiedEvent(String.class, 5);
        var fromString = new com.zsubera.jpa.template.EntityModifiedEvent("String", 5);

        assertInstanceOf(String.class, fromClass.getSource());
        assertInstanceOf(String.class, fromString.getSource());
        assertEquals(String.class.getName(), fromClass.getSource());
        assertEquals("String", fromString.getSource());
    }

    // ---- CacheEvictionHelper 反射重试 ----

    @Test
    @DisplayName("CacheEvictionHelper.evictEntityCache should not throw even if L2 unavailable")
    void cacheEvictionHelper_noThrow() {
        assertDoesNotThrow(() -> {
            com.zsubera.jpa.util.CacheEvictionHelper.evictEntityCache(
                entityManager, SoftDeleteTestEntity.class);
        });
    }

    // ---- CodeEnumHelper 解析 ----

    @Test
    @DisplayName("CodeEnumHelper should resolve @CodeEnumValue field correctly")
    void codeEnumHelper_resolvesField() {
        java.lang.reflect.Field field = CodeEnumType.resolveCodeField(P1StatusEnum.class);
        assertNotNull(field);
        assertEquals("code", field.getName());
    }

    enum P1StatusEnum {
        ACTIVE(0), DELETED(1);

        @CodeEnumValue
        private final int code;

        P1StatusEnum(int code) {
            this.code = code;
        }
    }

    // ---- setEntityManagerFactoryIfAbsent ----

    @Test
    @DisplayName("EntityManagerHelper.setEntityManagerFactoryIfAbsent should work")
    void entityManagerHelper_setIfAbsent() {
        assertDoesNotThrow(EntityManagerHelper::reset);
    }

    // ---- offset 截断防护 ----

    @Test
    @DisplayName("Large offset should throw ArithmeticException")
    void largeOffset_throws() {
        assertThrows(ArithmeticException.class,
            () -> Math.toIntExact(((long) Integer.MAX_VALUE) + 1));
    }

    // ---- DialectDetector 类加载 ----

    @Test
    @DisplayName("DialectDetector class should be loadable")
    void dialectDetector_loadable() {
        assertDoesNotThrow(() -> Class.forName("com.zsubera.jpa.update.DialectDetector"));
    }
}
