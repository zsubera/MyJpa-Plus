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
 * 测试 softDeleteAll（原生 SQL 路径）的持久化上下文行为。
 *
 * <p>与 softDeleteAllUsingCriteriaUpdate（CriteriaUpdate 路径）对比，
 * 原生 SQL 路径不清空持久化上下文，导致后续 em.find() 返回旧数据。
 * 这是已知的设计行为，此测试验证该行为差异。
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = TestApplication.class)
class SoftDeleteBulkExecutorNativeSqlStaleContextTest {

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
     * 验证 softDeleteAll（原生 SQL）后持久化上下文未清空，em.clear() 后能读到最新数据。
     */
    @Test
    void softDeleteAll_nativeSql_withClear_readsFromDb() {
        SoftDeleteTestEntity entity = new SoftDeleteTestEntity();
        entity.setName("clear-test");
        repository.saveAndFlush(entity);
        Long id = entity.getId();
        em.clear();

        SoftDeleteTestEntity loaded = em.find(SoftDeleteTestEntity.class, id);
        assertFalse(loaded.getDeleted());

        SoftDeleteBulkExecutor.softDeleteAll(em, SoftDeleteTestEntity.class, true);

        // em.clear() 后从数据库重新加载
        em.clear();
        SoftDeleteTestEntity found = em.find(SoftDeleteTestEntity.class, id);
        assertTrue(found.getDeleted());
    }

    /**
     * 验证 softDeleteByIds（原生 SQL）后 em.clear() 能读到最新数据。
     */
    @Test
    void softDeleteByIds_nativeSql_withClear_readsFromDb() {
        SoftDeleteTestEntity entity = new SoftDeleteTestEntity();
        entity.setName("ids-clear-test");
        repository.saveAndFlush(entity);
        Long id = entity.getId();
        em.clear();

        SoftDeleteTestEntity loaded = em.find(SoftDeleteTestEntity.class, id);
        assertFalse(loaded.getDeleted());

        SoftDeleteBulkExecutor.softDeleteByIds(em, SoftDeleteTestEntity.class, java.util.List.of(id));

        em.clear();
        SoftDeleteTestEntity found = em.find(SoftDeleteTestEntity.class, id);
        assertTrue(found.getDeleted());
    }

    /**
     * 验证 softDeleteAllUsingCriteriaUpdate 后持久化上下文已清空，em.find() 返回最新数据。
     * 与原生 SQL 路径形成对比。
     */
    @Test
    void softDeleteAllUsingCriteriaUpdate_persistenceContextCleared() {
        SoftDeleteTestEntity entity = new SoftDeleteTestEntity();
        entity.setName("criteria-clear-test");
        repository.saveAndFlush(entity);
        Long id = entity.getId();
        em.clear();

        SoftDeleteTestEntity loaded = em.find(SoftDeleteTestEntity.class, id);
        assertFalse(loaded.getDeleted());

        SoftDeleteBulkExecutor.softDeleteAllUsingCriteriaUpdate(em, SoftDeleteTestEntity.class, true);

        // CriteriaUpdate 路径清空了持久化上下文，em.find() 直接从数据库加载
        SoftDeleteTestEntity found = em.find(SoftDeleteTestEntity.class, id);
        assertNotNull(found);
        assertTrue(found.getDeleted());
    }

    /**
     * 验证 softDeleteAll（原生 SQL）后通过 repository 查询能读到最新数据。
     * repository.findAll() 会创建新的查询，不依赖持久化上下文。
     */
    @Test
    void softDeleteAll_nativeSql_repositoryQueryReadsLatest() {
        SoftDeleteTestEntity entity = new SoftDeleteTestEntity();
        entity.setName("repo-query-test");
        repository.saveAndFlush(entity);
        Long id = entity.getId();

        SoftDeleteBulkExecutor.softDeleteAll(em, SoftDeleteTestEntity.class, true);

        // repository.findAll() 创建新查询，从数据库读取
        var allEntities = repository.findAll();
        for (SoftDeleteTestEntity e : allEntities) {
            assertTrue(e.getDeleted());
        }
    }
}
