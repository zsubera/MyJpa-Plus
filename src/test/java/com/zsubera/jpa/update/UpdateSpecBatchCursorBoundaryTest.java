package com.zsubera.jpa.update;

import static org.junit.jupiter.api.Assertions.*;

import com.zsubera.jpa.spec.TestApplication;
import com.zsubera.jpa.spec.TestEntity;
import com.zsubera.jpa.spec.TestEntityRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;

/**
 * UpdateSpec 游标分页边界 case 测试。
 *
 * <p>验证：
 * 1. 空表游标分页返回 0
 * 2. 单批正好等于 batchSize 时的边界行为
 * 3. 游标分页的 cursor 正确前进
 * 4. 无条件更新需要 allowUnconditional
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = TestApplication.class)
class UpdateSpecBatchCursorBoundaryTest {

    @Autowired
    private TestEntityRepository repository;

    @PersistenceContext
    private EntityManager em;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        repository.flush();
    }

    @Test
    void executeLimitedCursor_emptyTable_returnsZero() {
        UpdateSpec<TestEntity> spec = new UpdateSpec<>(TestEntity.class);
        spec.set(TestEntity::getStatus, 1).eq(TestEntity::getStatus, 0);

        UpdateSpec.BatchCursor cursor = spec.executeLimitedCursor(em, 10, null);
        assertEquals(0, cursor.affected());
        assertNull(cursor.lastId());
    }

    @Test
    void executeLimitedCursor_singleBatchExactSize() {
        // 创建恰好 batchSize 条记录
        for (int i = 0; i < 5; i++) {
            TestEntity e = new TestEntity();
            e.setName("exact" + i);
            e.setStatus(0);
            repository.save(e);
        }
        repository.flush();

        UpdateSpec<TestEntity> spec = new UpdateSpec<>(TestEntity.class);
        spec.set(TestEntity::getStatus, 1).eq(TestEntity::getStatus, 0);

        // batchSize = 5，正好处理所有行
        UpdateSpec.BatchCursor cursor = spec.executeLimitedCursor(em, 5, null);
        assertEquals(5, cursor.affected());
        assertNotNull(cursor.lastId());

        // 第二批应该返回 0
        UpdateSpec.BatchCursor cursor2 = spec.executeLimitedCursor(em, 5, cursor.lastId());
        assertEquals(0, cursor2.affected());
    }

    @Test
    void executeLimitedCursor_cursorAdvancesCorrectly() {
        // 创建 10 条记录
        for (int i = 0; i < 10; i++) {
            TestEntity e = new TestEntity();
            e.setName("cursor" + i);
            e.setStatus(0);
            repository.save(e);
        }
        repository.flush();

        UpdateSpec<TestEntity> spec = new UpdateSpec<>(TestEntity.class);
        spec.set(TestEntity::getStatus, 1).eq(TestEntity::getStatus, 0);

        // 第一批：处理 3 条
        UpdateSpec.BatchCursor c1 = spec.executeLimitedCursor(em, 3, null);
        assertEquals(3, c1.affected());
        assertNotNull(c1.lastId());

        // 第二批：处理下 3 条
        UpdateSpec.BatchCursor c2 = spec.executeLimitedCursor(em, 3, c1.lastId());
        assertEquals(3, c2.affected());
        assertTrue(((Comparable)c2.lastId()).compareTo(c1.lastId()) > 0);

        // 第三批：处理下 3 条
        UpdateSpec.BatchCursor c3 = spec.executeLimitedCursor(em, 3, c2.lastId());
        assertEquals(3, c3.affected());

        // 第四批：处理最后 1 条
        UpdateSpec.BatchCursor c4 = spec.executeLimitedCursor(em, 3, c3.lastId());
        assertEquals(1, c4.affected());

        // 第五批：无更多行
        UpdateSpec.BatchCursor c5 = spec.executeLimitedCursor(em, 3, c4.lastId());
        assertEquals(0, c5.affected());
    }

    @Test
    void executeLimitedCursor_noConditions_requiresAllowUnconditional() {
        UpdateSpec<TestEntity> spec = new UpdateSpec<>(TestEntity.class);
        spec.set(TestEntity::getStatus, 1);

        // 没有 WHERE 条件且未设置 allowUnconditional 应抛异常
        assertThrows(IllegalStateException.class, () -> spec.executeLimitedCursor(em, 10, null));
    }

    @Test
    void executeLimitedCursor_withAllowUnconditional_succeeds() {
        for (int i = 0; i < 3; i++) {
            TestEntity e = new TestEntity();
            e.setName("uncond" + i);
            e.setStatus(0);
            repository.save(e);
        }
        repository.flush();

        UpdateSpec<TestEntity> spec = new UpdateSpec<>(TestEntity.class);
        spec.set(TestEntity::getStatus, 1).allowUnconditional(true);

        UpdateSpec.BatchCursor cursor = spec.executeLimitedCursor(em, 10, null);
        assertEquals(3, cursor.affected());
    }

    /**
     * 验证游标分页循环的完整性。
     */
    @Test
    void executeLimitedCursor_fullCycleProcessesAllRows() {
        for (int i = 0; i < 8; i++) {
            TestEntity e = new TestEntity();
            e.setName("cycle" + i);
            e.setStatus(0);
            repository.save(e);
        }
        repository.flush();

        UpdateSpec<TestEntity> spec = new UpdateSpec<>(TestEntity.class);
        spec.set(TestEntity::getStatus, 1).eq(TestEntity::getStatus, 0);

        int totalUpdated = 0;
        Object lastId = null;
        while (true) {
            UpdateSpec.BatchCursor cursor = spec.executeLimitedCursor(em, 3, lastId);
            if (cursor.affected() == 0)
                break;
            totalUpdated += cursor.affected();
            lastId = cursor.lastId();
        }

        assertEquals(8, totalUpdated);
    }
}
