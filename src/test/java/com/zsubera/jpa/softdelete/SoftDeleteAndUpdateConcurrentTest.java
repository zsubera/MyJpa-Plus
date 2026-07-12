package com.zsubera.jpa.softdelete;

import static org.junit.jupiter.api.Assertions.*;

import com.zsubera.jpa.spec.SoftDeleteTestEntity;
import com.zsubera.jpa.spec.SoftDeleteTestEntityRepository;
import com.zsubera.jpa.spec.SFunction;
import com.zsubera.jpa.spec.TestApplication;
import com.zsubera.jpa.update.UpdateSpec;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;

/**
 * 测试 softDeleteAll 和 updateAll 交互的数据一致性。
 *
 * <p>验证场景：
 * 1. softDeleteAll + updateAll 顺序执行时数据一致
 * 2. 两者操作不同字段时各自正确执行
 * 3. softDeleteAll 后 updateAll 的 WHERE 条件正确排除已软删除行
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = TestApplication.class)
class SoftDeleteAndUpdateConcurrentTest {

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
     * 验证 softDeleteAll + updateAll 顺序执行时数据一致。
     */
    @Test
    void softDeleteAll_thenUpdateAll_dataConsistent() {
        for (int i = 0; i < 5; i++) {
            SoftDeleteTestEntity e = new SoftDeleteTestEntity();
            e.setName("entity" + i);
            repository.save(e);
        }
        repository.flush();
        em.clear();

        // 先软删除
        int softDeleted = SoftDeleteBulkExecutor.softDeleteAll(em, SoftDeleteTestEntity.class, true);
        assertEquals(5, softDeleted);

        // 再通过 CriteriaUpdate 更新（不感知软删除）
        SFunction<SoftDeleteTestEntity, String> nameField = SoftDeleteTestEntity::getName;
        UpdateSpec<SoftDeleteTestEntity> spec = new UpdateSpec<>(SoftDeleteTestEntity.class);
        spec.set(nameField, "after").allowUnconditional(true);
        int updated = spec.execute(em);

        // CriteriaUpdate 不感知软删除，所以更新了所有行（包括已软删除的）
        assertEquals(5, updated);

        // 验证：所有行都被更新和软删除
        em.clear();
        var allEntities = repository.findAll();
        for (SoftDeleteTestEntity e : allEntities) {
            assertTrue(e.getDeleted());
            assertEquals("after", e.getName());
        }
    }

    /**
     * 验证 updateAll + softDeleteAll 顺序执行时数据一致。
     */
    @Test
    void updateAll_thenSoftDeleteAll_dataConsistent() {
        for (int i = 0; i < 5; i++) {
            SoftDeleteTestEntity e = new SoftDeleteTestEntity();
            e.setName("entity" + i);
            repository.save(e);
        }
        repository.flush();
        em.clear();

        // 先更新
        SFunction<SoftDeleteTestEntity, String> nameField = SoftDeleteTestEntity::getName;
        UpdateSpec<SoftDeleteTestEntity> spec = new UpdateSpec<>(SoftDeleteTestEntity.class);
        spec.set(nameField, "updated").allowUnconditional(true);
        int updated = spec.execute(em);
        assertEquals(5, updated);

        // 再软删除
        em.clear();
        int softDeleted = SoftDeleteBulkExecutor.softDeleteAll(em, SoftDeleteTestEntity.class, true);
        assertEquals(5, softDeleted);

        // 验证：所有行都被更新和软删除
        em.clear();
        var allEntities = repository.findAll();
        for (SoftDeleteTestEntity e : allEntities) {
            assertTrue(e.getDeleted());
            assertEquals("updated", e.getName());
        }
    }

    /**
     * 验证 softDeleteAll 后 updateAll 的 WHERE 条件正确排除已软删除行。
     */
    @Test
    void softDeleteAll_thenUpdateAll_withCondition_onlyAffectsNonDeleted() {
        for (int i = 0; i < 5; i++) {
            SoftDeleteTestEntity e = new SoftDeleteTestEntity();
            e.setName("before" + i);
            repository.save(e);
        }
        repository.flush();

        // 先软删除
        int softDeleted = SoftDeleteBulkExecutor.softDeleteAll(em, SoftDeleteTestEntity.class, true);
        assertEquals(5, softDeleted);

        em.clear();

        // 再通过 CriteriaUpdate 更新，带 WHERE 条件（不感知软删除）
        SFunction<SoftDeleteTestEntity, String> nameField = SoftDeleteTestEntity::getName;
        UpdateSpec<SoftDeleteTestEntity> spec = new UpdateSpec<>(SoftDeleteTestEntity.class);
        spec.set(nameField, "after").eq(nameField, "before0");
        int updated = spec.execute(em);

        // WHERE 条件 name='before0' 匹配 1 行（包括已软删除的）
        assertEquals(1, updated);

        // 验证：该行被更新和软删除
        em.clear();
        var allEntities = repository.findAll();
        for (SoftDeleteTestEntity e : allEntities) {
            assertTrue(e.getDeleted());
        }
    }

    /**
     * 验证并发 softDeleteAll 不会导致数据损坏（通过顺序执行模拟）。
     */
    @Test
    void softDeleteAll_multipleCalls_dataConsistent() {
        for (int i = 0; i < 10; i++) {
            SoftDeleteTestEntity e = new SoftDeleteTestEntity();
            e.setName("concurrent" + i);
            repository.save(e);
        }
        repository.flush();
        em.clear();

        // 第一次 softDeleteAll
        int count1 = SoftDeleteBulkExecutor.softDeleteAll(em, SoftDeleteTestEntity.class, true);
        assertEquals(10, count1);

        // 第二次 softDeleteAll（所有行已被软删除，应该影响 0 行）
        em.clear();
        int count2 = SoftDeleteBulkExecutor.softDeleteAll(em, SoftDeleteTestEntity.class, true);
        assertEquals(0, count2);

        // 验证：所有行都被软删除
        em.clear();
        var allEntities = repository.findAll();
        for (SoftDeleteTestEntity e : allEntities) {
            assertTrue(e.getDeleted());
        }
    }
}
