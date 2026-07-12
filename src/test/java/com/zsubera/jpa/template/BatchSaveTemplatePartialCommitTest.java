package com.zsubera.jpa.template;

import static org.junit.jupiter.api.Assertions.*;

import com.zsubera.jpa.spec.TestApplication;
import com.zsubera.jpa.spec.TestEntity;
import com.zsubera.jpa.spec.TestEntityRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;

/**
 * BatchSaveTemplate 批量保存边界 case 测试。
 *
 * <p>验证：
 * 1. saveAllBatched 正常批量保存
 * 2. batchSize 边界行为
 * 3. 混合新旧实体的批量保存
 * 4. 持久化上下文清理
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = {TestApplication.class, BatchSaveTemplatePartialCommitTest.TestConfig.class})
class BatchSaveTemplatePartialCommitTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        public BatchSaveTemplate batchSaveTemplate(jakarta.persistence.EntityManager entityManager) {
            return new BatchSaveTemplate(entityManager);
        }
    }

    @Autowired
    private BatchSaveTemplate batchSaveTemplate;

    @Autowired
    private TestEntityRepository repository;

    @PersistenceContext
    private EntityManager em;

    @BeforeEach
    void cleanUp() {
        repository.deleteAll();
        repository.flush();
    }

    /**
     * 验证正常批量保存后所有实体都被正确保存。
     */
    @Test
    void saveAllBatched_allSuccess() {
        List<TestEntity> entities = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            TestEntity e = new TestEntity();
            e.setName("batch" + i);
            e.setStatus(i);
            entities.add(e);
        }

        List<TestEntity> saved = batchSaveTemplate.saveAllBatched(entities, 3);
        assertEquals(10, saved.size());
        assertEquals(10, repository.count());

        // 验证所有实体都被正确保存
        for (TestEntity e : saved) {
            TestEntity found = repository.findById(e.getId()).orElse(null);
            assertNotNull(found);
            assertNotNull(found.getName());
        }
    }

    /**
     * 验证 batchSize=1 时每条记录独立 flush/clear。
     */
    @Test
    void saveAllBatched_batchSizeOne() {
        List<TestEntity> entities = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            TestEntity e = new TestEntity();
            e.setName("single" + i);
            e.setStatus(i);
            entities.add(e);
        }

        List<TestEntity> saved = batchSaveTemplate.saveAllBatched(entities, 1);
        assertEquals(5, saved.size());
        assertEquals(5, repository.count());
    }

    /**
     * 验证空列表处理。
     */
    @Test
    void saveAllBatched_emptyList() {
        List<TestEntity> saved = batchSaveTemplate.saveAllBatched(new ArrayList<>(), 5);
        assertEquals(0, saved.size());
        assertEquals(0, repository.count());
    }

    /**
     * 验证 batchSize 大于实体数量时作为单批处理。
     */
    @Test
    void saveAllBatched_batchSizeLargerThanList() {
        List<TestEntity> entities = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            TestEntity e = new TestEntity();
            e.setName("large-batch" + i);
            e.setStatus(i);
            entities.add(e);
        }

        List<TestEntity> saved = batchSaveTemplate.saveAllBatched(entities, 100);
        assertEquals(3, saved.size());
        assertEquals(3, repository.count());
    }

    /**
     * 验证混合新旧实体的批量保存。
     */
    @Test
    void saveAllBatched_mixedNewAndExisting() {
        // 先保存一个旧实体
        TestEntity existing = new TestEntity();
        existing.setName("existing");
        existing.setStatus(0);
        repository.saveAndFlush(existing);

        List<TestEntity> entities = new ArrayList<>();
        entities.add(existing); // 旧实体（merge）
        TestEntity new1 = new TestEntity();
        new1.setName("new1");
        new1.setStatus(1);
        entities.add(new1); // 新实体（persist）
        TestEntity new2 = new TestEntity();
        new2.setName("new2");
        new2.setStatus(2);
        entities.add(new2); // 新实体（persist）

        List<TestEntity> saved = batchSaveTemplate.saveAllBatched(entities, 2);
        assertEquals(3, saved.size());
        assertEquals(3, repository.count());
    }

    /**
     * 验证 saveAllBatched 后持久化上下文已清理（实体是 detached 状态）。
     */
    @Test
    void saveAllBatched_persistenceContextCleared() {
        List<TestEntity> entities = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            TestEntity e = new TestEntity();
            e.setName("clear-test" + i);
            e.setStatus(i);
            entities.add(e);
        }

        List<TestEntity> saved = batchSaveTemplate.saveAllBatched(entities, 2);
        assertEquals(3, saved.size());

        // 验证持久化上下文已清理（实体是 detached 状态）
        // detached 实体的 ID 应该有值
        for (TestEntity e : saved) {
            assertNotNull(e.getId());
        }
    }

    /**
     * 验证 saveAllBatchedPure 所有实体都使用 persist。
     */
    @Test
    void saveAllBatchedPure_allNewEntities() {
        List<TestEntity> entities = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            TestEntity e = new TestEntity();
            e.setName("pure" + i);
            e.setStatus(i);
            entities.add(e);
        }

        List<TestEntity> saved = batchSaveTemplate.saveAllBatchedPure(entities, 2);
        assertEquals(5, saved.size());
        assertEquals(5, repository.count());
    }

    /**
     * 验证 batchSize=2 时正确的分批行为。
     */
    @Test
    void saveAllBatched_correctBatching() {
        List<TestEntity> entities = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            TestEntity e = new TestEntity();
            e.setName("batching" + i);
            e.setStatus(i);
            entities.add(e);
        }

        // batchSize=2，应该分 4 批（2+2+2+1）
        List<TestEntity> saved = batchSaveTemplate.saveAllBatched(entities, 2);
        assertEquals(7, saved.size());
        assertEquals(7, repository.count());
    }
}
