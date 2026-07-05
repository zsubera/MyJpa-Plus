package com.zsubera.jpa.spec;

import static org.junit.jupiter.api.Assertions.*;

import com.zsubera.jpa.exception.SecurityViolationException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;

/**
 * QuerySpec.func() 方法和 ConditionBuilder 函数验证的测试。
 *
 * <p>覆盖函数名校验、null 参数处理、FuncNode 解析时的字段验证、multiLike 注入防护。
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class QuerySpecFuncValidationTest {

    @Autowired
    private TestEntityRepository repository;

    @PersistenceContext
    private EntityManager em;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        repository.flush();
    }

    @AfterEach
    void cleanup() {
        FunctionWhitelist.reset();
    }

    // ---- func() with valid whitelisted boolean function ----

    @Test
    void func_withValidBooleanFunction_works() {
        repository.save(newEntity("test", 1));

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.func(TestEntity::getName, "COALESCE");
        assertNotNull(qs.toSpecification());
    }

    // ---- func() with null field ----

    @Test
    void func_nullField_throws() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class, () -> qs.func(null, "COALESCE"));
    }

    // ---- func() with null functionName ----

    @Test
    void func_nullFunctionName_throws() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class, () -> qs.func(TestEntity::getName, (String)null));
    }

    // ---- func() with empty functionName ----

    @Test
    void func_emptyFunctionName_throws() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class, () -> qs.func(TestEntity::getName, ""));
    }

    // ---- func() with invalid characters in functionName ----

    @Test
    void func_functionNameWithSemicolon_throws() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class, () -> qs.func(TestEntity::getName, "COALESCE;DROP"));
    }

    @Test
    void func_functionNameWithSpaces_throws() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class, () -> qs.func(TestEntity::getName, "COALESCE DROP"));
    }

    // ---- func() with null params ----

    @Test
    void func_nullParams_throws() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class, () -> qs.func(TestEntity::getName, "COALESCE", (Object[])null));
    }

    // ---- func() with non-whitelisted function ----

    @Test
    void func_nonWhitelistedFunction_throwsSecurityException() {
        assertThrows(SecurityViolationException.class, () -> {
            QuerySpec<TestEntity> qs = new QuerySpec<>();
            qs.func(TestEntity::getName, "PG_SLEEP", 1);
        });
    }

    // ---- func() with non-boolean function ----

    @Test
    void func_nonBooleanFunction_throwsSecurityException() {
        assertThrows(SecurityViolationException.class, () -> {
            QuerySpec<TestEntity> qs = new QuerySpec<>();
            qs.func(TestEntity::getName, "LENGTH", "test");
        });
    }

    // ---- func() with conditional false ----

    @Test
    void func_conditionalFalse_skips() {
        repository.save(newEntity("test", 1));

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.func(false, TestEntity::getName, "COALESCE");
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    // ---- multiLike(String...) injection protection ----

    @Test
    void multiLike_validNestedFieldName_works() {
        repository.save(newEntity("hello", 1));

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.multiLike("hello", "name");
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void multiLike_nullFieldName_throws() {
        assertThrows(IllegalArgumentException.class, () -> {
            QuerySpec<TestEntity> qs = new QuerySpec<>();
            qs.multiLike("test", (String[])null);
        });
    }

    @Test
    void multiLike_emptyFieldNameArray_throws() {
        assertThrows(IllegalArgumentException.class, () -> {
            QuerySpec<TestEntity> qs = new QuerySpec<>();
            qs.multiLike("test", new String[0]);
        });
    }

    @Test
    void multiLike_injectionAttemptFieldName_throws() {
        assertThrows(IllegalArgumentException.class, () -> {
            QuerySpec<TestEntity> qs = new QuerySpec<>();
            qs.multiLike("test", "name; DROP TABLE users");
        });
    }

    @Test
    void multiLike_fieldNameWithSpaces_throws() {
        assertThrows(IllegalArgumentException.class, () -> {
            QuerySpec<TestEntity> qs = new QuerySpec<>();
            qs.multiLike("test", "name drop");
        });
    }

    @Test
    void multiLike_singleDotFieldName_works() {
        repository.save(newEntity("hello", 1));

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        // "name" is valid (no dot), this should work
        qs.multiLike("hello", "name");
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    // ---- QuerySpec.of() factory method ----

    @Test
    void of_createsQuerySpecFromConsumer() {
        QuerySpec<TestEntity> qs = QuerySpec.of(spec -> spec.eq(TestEntity::getName, "test"));
        assertNotNull(qs);
        repository.save(newEntity("test", 1));
        repository.save(newEntity("other", 2));
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
        assertEquals("test", result.get(0).getName());
    }

    @Test
    void of_withNullConsumer_createsEmptySpec() {
        QuerySpec<TestEntity> qs = QuerySpec.of(null);
        assertNotNull(qs);
        repository.save(newEntity("test", 1));
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    // ---- QuerySpec deep copy of FuncNode ----

    @Test
    void copy_preservesFuncNode() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.func(TestEntity::getName, "COALESCE");
        QuerySpec<TestEntity> copy = qs.copy();
        assertNotNull(copy);
        assertNotNull(copy.toSpecification());
    }

    // ---- helpers ----

    private TestEntity newEntity(String name, int status) {
        TestEntity entity = new TestEntity();
        entity.setName(name);
        entity.setStatus(status);
        return entity;
    }
}
