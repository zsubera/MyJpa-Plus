package com.zsubera.jpa.softdelete;

import static org.junit.jupiter.api.Assertions.*;

import com.zsubera.jpa.repository.DefaultMyJpaRepository;
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
 * 4. UpdateSpec 自动感知软删除状态（修复竞态条件）
 * 5. 通过 SoftDeleteContext.ignoreSoftDelete() 可绕过软删除过滤
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
     * UpdateSpec 自动感知软删除状态，不会更新已软删除的行。
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

        // 再通过 UpdateSpec 更新（自动感知软删除，只影响未删除的行）
        SFunction<SoftDeleteTestEntity, String> nameField = SoftDeleteTestEntity::getName;
        UpdateSpec<SoftDeleteTestEntity> spec = new UpdateSpec<>(SoftDeleteTestEntity.class);
        spec.set(nameField, "after").allowUnconditional(true);
        int updated = spec.execute(em);

        // UpdateSpec 自动感知软删除，所以 0 行受影响（所有行已被软删除）
        assertEquals(0, updated);

        // 验证：所有行仍保持软删除状态，name 未被修改
        em.clear();
        var allEntities = repository.findAll();
        for (SoftDeleteTestEntity e : allEntities) {
            assertTrue(e.getDeleted());
            assertNotEquals("after", e.getName());
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
     * UpdateSpec 自动感知软删除状态，不会更新已软删除的行。
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

        // 再通过 UpdateSpec 更新，带 WHERE 条件（自动感知软删除）
        SFunction<SoftDeleteTestEntity, String> nameField = SoftDeleteTestEntity::getName;
        UpdateSpec<SoftDeleteTestEntity> spec = new UpdateSpec<>(SoftDeleteTestEntity.class);
        spec.set(nameField, "after").eq(nameField, "before0");
        int updated = spec.execute(em);

        // UpdateSpec 自动感知软删除，所以 0 行受影响（name='before0' 的行已被软删除）
        assertEquals(0, updated);

        // 验证：该行仍保持软删除状态
        em.clear();
        var allEntities = repository.findAll();
        for (SoftDeleteTestEntity e : allEntities) {
            assertTrue(e.getDeleted());
        }
    }

    /**
     * 验证通过 SoftDeleteContext.ignoreSoftDelete() 可以绕过软删除过滤，
     * 更新已软删除的行（用于数据恢复等场景）。
     */
    @Test
    void softDeleteAll_thenUpdateAll_withIgnoreSoftDelete_updatesDeletedRows() {
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

        em.clear();

        // 通过 SoftDeleteContext.ignoreSoftDelete() 绕过软删除过滤，更新已软删除的行
        SFunction<SoftDeleteTestEntity, String> nameField = SoftDeleteTestEntity::getName;
        DefaultMyJpaRepository.withAutoFilterOverride(false, () -> {
            UpdateSpec<SoftDeleteTestEntity> spec = new UpdateSpec<>(SoftDeleteTestEntity.class);
            spec.set(nameField, "recovered").allowUnconditional(true);
            int updated = spec.execute(em);
            // 绕过软删除后，更新了所有行（包括已软删除的）
            assertEquals(5, updated);
        });

        // 验证：所有行的 name 被更新，但 deleted 状态不变
        em.clear();
        var allEntities = repository.findAll();
        for (SoftDeleteTestEntity e : allEntities) {
            assertTrue(e.getDeleted());
            assertEquals("recovered", e.getName());
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

    /**
     * 验证 UpdateSpec 自动感知软删除：只更新未删除的行。
     */
    @Test
    void updateSpec_automaticallyRespectsSoftDelete() {
        // 创建 3 个实体
        for (int i = 0; i < 3; i++) {
            SoftDeleteTestEntity e = new SoftDeleteTestEntity();
            e.setName("active" + i);
            repository.save(e);
        }
        repository.flush();
        em.clear();

        // 软删除第 1 个实体
        SoftDeleteTestEntity first = repository.findAll().get(0);
        repository.deleteById(first.getId());
        repository.flush();
        em.clear();

        // 通过 UpdateSpec 更新所有实体（allowUnconditional + 软删除过滤）
        SFunction<SoftDeleteTestEntity, String> nameField = SoftDeleteTestEntity::getName;
        UpdateSpec<SoftDeleteTestEntity> spec = new UpdateSpec<>(SoftDeleteTestEntity.class);
        spec.set(nameField, "updated").allowUnconditional(true);
        int updated = spec.execute(em);

        // 只有 2 个未删除的行被更新（第 1 个已软删除，不参与更新）
        assertEquals(2, updated);

        // 验证：已软删除的行 name 未被修改
        em.clear();
        var allEntities = repository.findAll();
        for (SoftDeleteTestEntity e : allEntities) {
            if (e.getDeleted()) {
                assertEquals("active0", e.getName()); // 未被更新
            } else {
                assertEquals("updated", e.getName()); // 被更新
            }
        }
    }
}
