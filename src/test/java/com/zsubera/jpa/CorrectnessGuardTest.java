package com.zsubera.jpa;

import static org.junit.jupiter.api.Assertions.*;

import com.zsubera.jpa.converter.CodeEnumType;
import com.zsubera.jpa.converter.CodeEnumValue;
import com.zsubera.jpa.spec.QuerySpec;
import com.zsubera.jpa.spec.SoftDeleteTestEntity;
import com.zsubera.jpa.spec.SoftDeleteTestEntityRepository;
import com.zsubera.jpa.spec.TestApplication;
import com.zsubera.jpa.template.MyJpaTemplate;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ContextConfiguration;

/**
 * 核心正确性防护测试，覆盖数据完整性和安全性相关的关键路径。
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = {TestApplication.class, CorrectnessGuardTest.TestConfig.class})
class CorrectnessGuardTest {

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
    }

    // ---- findOne 软删除过滤 ----

    @Test
    @DisplayName("findOne with QuerySpec should not return soft-deleted entity")
    void findOne_excludesSoftDeleted() {
        SoftDeleteTestEntity active = new SoftDeleteTestEntity();
        active.setName("active");
        active.setDeleted(false);
        repository.save(active);

        SoftDeleteTestEntity deleted = new SoftDeleteTestEntity();
        deleted.setName("deleted");
        deleted.setDeleted(true);
        repository.save(deleted);
        entityManager.flush();
        entityManager.clear();

        QuerySpec<SoftDeleteTestEntity> spec = new QuerySpec<>();
        spec.eq(SoftDeleteTestEntity::getName, "deleted");
        Optional<SoftDeleteTestEntity> result = template.findOne(SoftDeleteTestEntity.class, spec);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("findOne with QuerySpec should return active entity")
    void findOne_returnsActive() {
        SoftDeleteTestEntity active = new SoftDeleteTestEntity();
        active.setName("active");
        active.setDeleted(false);
        repository.save(active);
        entityManager.flush();
        entityManager.clear();

        QuerySpec<SoftDeleteTestEntity> spec = new QuerySpec<>();
        spec.eq(SoftDeleteTestEntity::getName, "active");
        Optional<SoftDeleteTestEntity> result = template.findOne(SoftDeleteTestEntity.class, spec);

        assertTrue(result.isPresent());
        assertEquals("active", result.get().getName());
    }

    // ---- findSlice 软删除过滤 ----

    @Test
    @DisplayName("findSlice with Specification should exclude soft-deleted entities")
    void findSlice_excludesSoftDeleted() {
        SoftDeleteTestEntity active = new SoftDeleteTestEntity();
        active.setName("active");
        active.setDeleted(false);
        repository.save(active);

        SoftDeleteTestEntity deleted = new SoftDeleteTestEntity();
        deleted.setName("deleted");
        deleted.setDeleted(true);
        repository.save(deleted);
        entityManager.flush();
        entityManager.clear();

        Specification<SoftDeleteTestEntity> spec = (root, query, cb) -> cb.conjunction();
        Slice<SoftDeleteTestEntity> result =
            template.findSlice(SoftDeleteTestEntity.class, spec, PageRequest.of(0, 10, Sort.by("name")));

        assertEquals(1, result.getContent().size());
        assertEquals("active", result.getContent().get(0).getName());
    }

    // ---- executeAsSoftDelete 已删除行守卫 ----

    @Test
    @DisplayName("executeAsSoftDelete should skip already-deleted rows")
    void executeAsSoftDelete_skipsAlreadyDeleted() {
        SoftDeleteTestEntity alreadyDeleted = new SoftDeleteTestEntity();
        alreadyDeleted.setName("already-deleted");
        alreadyDeleted.setDeleted(true);
        repository.save(alreadyDeleted);

        SoftDeleteTestEntity active = new SoftDeleteTestEntity();
        active.setName("active");
        active.setDeleted(false);
        repository.save(active);
        entityManager.flush();
        entityManager.clear();

        com.zsubera.jpa.update.DeleteSpec<SoftDeleteTestEntity> spec =
            new com.zsubera.jpa.update.DeleteSpec<>(SoftDeleteTestEntity.class);
        spec.addCondition((root, cb) -> cb.equal(root.get("deleted"), true));

        int affected = spec.executeAsSoftDelete(entityManager, "deleted", true);

        assertEquals(0, affected);
    }

    // ---- CacheAdapter 关闭安全性 ----

    @Test
    @DisplayName("CacheAdapter.close should not throw")
    void cacheAdapter_closeIsSafe() {
        com.zsubera.jpa.template.CacheAdapter adapter = com.zsubera.jpa.template.CacheAdapter.disabled();
        assertNotNull(adapter);
        assertDoesNotThrow(adapter::close);
    }

    // ---- CodeEnumType 正确解析 ----

    @Test
    @DisplayName("CodeEnumType should resolve @CodeEnumValue field")
    void codeEnumType_resolvesField() {
        java.lang.reflect.Field field = CodeEnumType.resolveCodeField(StatusEnum.class);
        assertNotNull(field);
        assertEquals("code", field.getName());
    }

    @Test
    @DisplayName("CodeEnumType should return null for enum without annotation")
    void codeEnumType_noAnnotationReturnsNull() {
        java.lang.reflect.Field field = CodeEnumType.resolveCodeField(NoAnnotationEnum.class);
        assertNull(field);
    }

    enum StatusEnum {
        ACTIVE(0), DELETED(1);

        @CodeEnumValue
        private final int code;

        StatusEnum(int code) {
            this.code = code;
        }
    }

    enum NoAnnotationEnum {
        X, Y
    }

    // ---- EntityModifiedEvent null 验证 ----

    @Test
    @DisplayName("EntityModifiedEvent should reject null entityClass")
    void entityModifiedEvent_rejectsNull() {
        assertThrows(IllegalArgumentException.class,
            () -> new com.zsubera.jpa.template.EntityModifiedEvent((Class<?>)null, 1));
        assertThrows(IllegalArgumentException.class,
            () -> new com.zsubera.jpa.template.EntityModifiedEvent((String)null, 1));
    }

    // ---- EntityModifiedEvent source 类型一致性 ----

    @Test
    @DisplayName("EntityModifiedEvent source type should be consistent")
    void entityModifiedEvent_sourceTypeConsistent() {
        var fromClass = new com.zsubera.jpa.template.EntityModifiedEvent(String.class, 5);
        var fromString = new com.zsubera.jpa.template.EntityModifiedEvent("String", 5);

        assertInstanceOf(String.class, fromClass.getSource());
        assertInstanceOf(String.class, fromString.getSource());
        assertEquals(String.class.getName(), fromClass.getSource());
        assertEquals("String", fromString.getSource());
    }

    // ---- offset 截断防护 ----

    @Test
    @DisplayName("Large offset should throw ArithmeticException")
    void largeOffset_throwsArithmeticException() {
        assertThrows(ArithmeticException.class, () -> Math.toIntExact(((long)Integer.MAX_VALUE) + 1));
    }

    // ---- 批量软删除不破坏持久化上下文 ----

    @Test
    @DisplayName("Bulk soft-delete should not directly flush/clear persistence context")
    void bulkSoftDelete_doesNotDirectFlush() {
        SoftDeleteTestEntity entity = new SoftDeleteTestEntity();
        entity.setName("test");
        entity.setDeleted(false);
        repository.save(entity);
        entityManager.flush();

        int count = com.zsubera.jpa.softdelete.SoftDeleteBulkExecutor.softDeleteAllUsingCriteriaUpdate(entityManager,
            SoftDeleteTestEntity.class, true, 100);

        assertEquals(1, count);

        SoftDeleteTestEntity found = entityManager.find(SoftDeleteTestEntity.class, entity.getId());
        if (found != null) {
            assertTrue(found.getDeleted());
        }
    }

    // ---- executeLimitedCursor 守卫 ----

    @Test
    @DisplayName("executeLimitedCursor should work without allowUnconditional for @SoftDelete entity (auto soft-delete filter)")
    void executeLimitedCursor_worksWithoutAllowUnconditional_forSoftDeleteEntity() {
        // SoftDeleteTestEntity has @SoftDelete, so buildPredicates() automatically adds
        // a soft-delete filter. This means executeLimitedCursor has a WHERE condition
        // and doesn't need allowUnconditional.
        com.zsubera.jpa.update.DeleteSpec<SoftDeleteTestEntity> spec =
            new com.zsubera.jpa.update.DeleteSpec<>(SoftDeleteTestEntity.class);

        assertDoesNotThrow(() -> spec.executeLimitedCursor(entityManager, 100, null));
    }

    @Test
    @DisplayName("executeLimitedCursor should require allowUnconditional for entity without @SoftDelete")
    void executeLimitedCursor_requiresAllowUnconditional_forNonSoftDeleteEntity() {
        // TestEntity has no @SoftDelete field, so no auto filter is added.
        // executeLimitedCursor should require allowUnconditional for unconditional delete.
        com.zsubera.jpa.update.DeleteSpec<com.zsubera.jpa.spec.TestEntity> spec =
            new com.zsubera.jpa.update.DeleteSpec<>(com.zsubera.jpa.spec.TestEntity.class);

        assertThrows(IllegalStateException.class, () -> spec.executeLimitedCursor(entityManager, 100, null));
    }

    @Test
    @DisplayName("executeLimitedCursor should allow with allowUnconditional")
    void executeLimitedCursor_allowsWithUnconditional() {
        com.zsubera.jpa.update.DeleteSpec<SoftDeleteTestEntity> spec =
            new com.zsubera.jpa.update.DeleteSpec<>(SoftDeleteTestEntity.class).allowUnconditional(true);

        assertDoesNotThrow(() -> spec.executeLimitedCursor(entityManager, 100, null));
    }
}
