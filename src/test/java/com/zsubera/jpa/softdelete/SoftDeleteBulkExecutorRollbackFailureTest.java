package com.zsubera.jpa.softdelete;

import static org.junit.jupiter.api.Assertions.*;

import com.zsubera.jpa.spec.SoftDeleteTestEntity;
import com.zsubera.jpa.spec.SoftDeleteTestEntityRepository;
import com.zsubera.jpa.spec.TestApplication;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;

/**
 * 测试 softDeleteAll 的 maxRows 限制和边界条件。
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = TestApplication.class)
class SoftDeleteBulkExecutorRollbackFailureTest {

    @Autowired
    private SoftDeleteTestEntityRepository repository;

    @PersistenceContext
    private EntityManager em;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        repository.flush();
    }

    /**
     * 验证 maxRows=-1（无限制）时 softDeleteAll 正常执行。
     */
    @Test
    void softDeleteAll_unlimited_succeeds() {
        for (int i = 0; i < 5; i++) {
            SoftDeleteTestEntity e = new SoftDeleteTestEntity();
            e.setName("unlimited" + i);
            repository.save(e);
        }
        repository.flush();
        em.clear();

        int count = SoftDeleteBulkExecutor.softDeleteAll(em, SoftDeleteTestEntity.class, true, -1);
        assertEquals(5, count);

        em.clear();
        var allEntities = repository.findAll();
        for (SoftDeleteTestEntity e : allEntities) {
            assertTrue(e.getDeleted());
        }
    }

    /**
     * 验证 softDeleteAllUsingCriteriaUpdate 的 maxRows 限制。
     */
    @Test
    void softDeleteAllUsingCriteriaUpdate_exceedsMaxRows_throwsBeforeExecution() {
        for (int i = 0; i < 3; i++) {
            SoftDeleteTestEntity e = new SoftDeleteTestEntity();
            e.setName("criteria-limit" + i);
            repository.save(e);
        }
        repository.flush();
        em.clear();

        // maxRows=2，但有 3 条活跃记录
        assertThrows(IllegalStateException.class,
            () -> SoftDeleteBulkExecutor.softDeleteAllUsingCriteriaUpdate(em, SoftDeleteTestEntity.class, true, 2));

        // 验证：预检查失败，UPDATE 未执行
        em.clear();
        var allEntities = repository.findAll();
        for (SoftDeleteTestEntity e : allEntities) {
            assertFalse(e.getDeleted(), "Entity should not be soft-deleted after pre-check failure");
        }
    }

    /**
     * 验证 softDeleteAll 在 allowUnconditional=false 时抛异常。
     */
    @Test
    void softDeleteAll_notAllowed_throwsIllegalState() {
        assertThrows(IllegalStateException.class,
            () -> SoftDeleteBulkExecutor.softDeleteAll(em, SoftDeleteTestEntity.class, false));
    }

    /**
     * 验证 softDeleteAllUsingCriteriaUpdate 在 allowUnconditional=false 时抛异常。
     */
    @Test
    void softDeleteAllUsingCriteriaUpdate_notAllowed_throwsIllegalState() {
        assertThrows(IllegalStateException.class,
            () -> SoftDeleteBulkExecutor.softDeleteAllUsingCriteriaUpdate(em, SoftDeleteTestEntity.class, false));
    }

    /**
     * 验证 softDeleteByIds 空列表返回 0。
     */
    @Test
    void softDeleteByIds_emptyList_returnsZero() {
        int count = SoftDeleteBulkExecutor.softDeleteByIds(em, SoftDeleteTestEntity.class, java.util.List.of());
        assertEquals(0, count);
    }

    /**
     * 验证 softDeleteByIds 超出 hardLimit 抛异常。
     */
    @Test
    void softDeleteByIds_exceedsHardLimit_throwsIllegalArgument() {
        java.util.List<Long> ids = new java.util.ArrayList<>();
        for (int i = 0; i < 100000; i++) {
            ids.add((long)i);
        }

        assertThrows(IllegalArgumentException.class,
            () -> SoftDeleteBulkExecutor.softDeleteByIds(em, SoftDeleteTestEntity.class, ids));
    }

    /**
     * 验证 softDeleteByIds 正常执行。
     */
    @Test
    void softDeleteByIds_normal_succeeds() {
        SoftDeleteTestEntity e1 = new SoftDeleteTestEntity();
        e1.setName("byIds1");
        repository.save(e1);

        SoftDeleteTestEntity e2 = new SoftDeleteTestEntity();
        e2.setName("byIds2");
        repository.save(e2);
        repository.flush();
        em.clear();

        int count = SoftDeleteBulkExecutor.softDeleteByIds(em, SoftDeleteTestEntity.class,
            java.util.List.of(e1.getId(), e2.getId()));
        assertEquals(2, count);

        em.clear();
        var allEntities = repository.findAll();
        for (SoftDeleteTestEntity e : allEntities) {
            assertTrue(e.getDeleted());
        }
    }
}
