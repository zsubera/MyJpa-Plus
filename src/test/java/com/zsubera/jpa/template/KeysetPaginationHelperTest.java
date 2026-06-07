package com.zsubera.jpa.template;

import static org.junit.jupiter.api.Assertions.*;

import com.zsubera.jpa.spec.TestApplication;
import com.zsubera.jpa.spec.TestEntity;
import com.zsubera.jpa.spec.TestEntityRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ContextConfiguration;

@DataJpaTest
@ContextConfiguration(classes = {TestApplication.class, KeysetPaginationHelperTest.TestConfig.class})
class KeysetPaginationHelperTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        public KeysetPaginationHelper keysetPaginationHelper(jakarta.persistence.EntityManager entityManager) {
            return new KeysetPaginationHelper(entityManager);
        }
    }

    @Autowired
    private KeysetPaginationHelper keysetPaginationHelper;

    @Autowired
    private TestEntityRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    // ---- 第一页查询（lastSortValues = null）----

    @Test
    void testFirstPage() {
        for (int i = 0; i < 5; i++) {
            TestEntity e = new TestEntity();
            e.setName("ksPage" + i);
            e.setStatus(i);
            repository.save(e);
        }
        repository.flush();

        Specification<TestEntity> spec = (root, query, cb) -> cb.conjunction();
        MyJpaTemplate.KeysetPage<TestEntity> page =
            keysetPaginationHelper.findKeysetPage(TestEntity.class, spec, Sort.by("id"), 3, null);

        assertEquals(3, page.content().size());
        assertTrue(page.hasNext());
        assertNotNull(page.lastSortValues());
        assertEquals(1, page.lastSortValues().length);
    }

    @Test
    void testFirstPageNoMoreData() {
        for (int i = 0; i < 2; i++) {
            TestEntity e = new TestEntity();
            e.setName("ksSmall" + i);
            e.setStatus(i);
            repository.save(e);
        }
        repository.flush();

        Specification<TestEntity> spec = (root, query, cb) -> cb.conjunction();
        MyJpaTemplate.KeysetPage<TestEntity> page =
            keysetPaginationHelper.findKeysetPage(TestEntity.class, spec, Sort.by("id"), 10, null);

        assertEquals(2, page.content().size());
        assertFalse(page.hasNext());
        assertNull(page.lastSortValues());
    }

    @Test
    void testFirstPageEmpty() {
        Specification<TestEntity> spec = (root, query, cb) -> cb.conjunction();
        MyJpaTemplate.KeysetPage<TestEntity> page =
            keysetPaginationHelper.findKeysetPage(TestEntity.class, spec, Sort.by("id"), 10, null);

        assertEquals(0, page.content().size());
        assertFalse(page.hasNext());
        assertNull(page.lastSortValues());
    }

    // ---- 翻页测试 ----

    @Test
    void testSecondPage() {
        for (int i = 0; i < 6; i++) {
            TestEntity e = new TestEntity();
            e.setName("ksPaged" + i);
            e.setStatus(i);
            repository.save(e);
        }
        repository.flush();

        Specification<TestEntity> spec = (root, query, cb) -> cb.conjunction();

        // 第一页
        MyJpaTemplate.KeysetPage<TestEntity> page1 =
            keysetPaginationHelper.findKeysetPage(TestEntity.class, spec, Sort.by("id"), 3, null);
        assertEquals(3, page1.content().size());
        assertTrue(page1.hasNext());

        // 第二页
        MyJpaTemplate.KeysetPage<TestEntity> page2 =
            keysetPaginationHelper.findKeysetPage(TestEntity.class, spec, Sort.by("id"), 3, page1.lastSortValues());
        assertEquals(3, page2.content().size());
        assertFalse(page2.hasNext());

        // 验证两页数据不重复
        List<Long> page1Ids = page1.content().stream().map(TestEntity::getId).toList();
        List<Long> page2Ids = page2.content().stream().map(TestEntity::getId).toList();
        assertTrue(page1Ids.stream().noneMatch(page2Ids::contains), "Pages should not have overlapping IDs");
    }

    // ---- 多字段排序 ----

    @Test
    void testMultiFieldSort() {
        String[] names = {"alice", "bob", "alice", "bob"};
        Integer[] statuses = {1, 1, 2, 2};
        for (int i = 0; i < 4; i++) {
            TestEntity e = new TestEntity();
            e.setName(names[i]);
            e.setStatus(statuses[i]);
            repository.save(e);
        }
        repository.flush();

        Specification<TestEntity> spec = (root, query, cb) -> cb.conjunction();
        Sort sort = Sort.by(Sort.Order.asc("name"), Sort.Order.desc("status"));

        MyJpaTemplate.KeysetPage<TestEntity> page =
            keysetPaginationHelper.findKeysetPage(TestEntity.class, spec, sort, 2, null);
        assertEquals(2, page.content().size());
        assertTrue(page.hasNext());
        assertEquals(2, page.lastSortValues().length);
    }

    // ---- 降序排序 ----

    @Test
    void testDescendingSort() {
        for (int i = 0; i < 5; i++) {
            TestEntity e = new TestEntity();
            e.setName("ksDesc" + i);
            e.setStatus(i);
            repository.save(e);
        }
        repository.flush();

        Specification<TestEntity> spec = (root, query, cb) -> cb.conjunction();
        MyJpaTemplate.KeysetPage<TestEntity> page =
            keysetPaginationHelper.findKeysetPage(TestEntity.class, spec, Sort.by(Sort.Direction.DESC, "id"), 3, null);

        assertEquals(3, page.content().size());
        assertTrue(page.hasNext());

        // 验证降序：第一条应该有最大的 id
        Long firstId = page.content().get(0).getId();
        Long lastId = page.content().get(2).getId();
        assertTrue(firstId > lastId, "Descending sort should have larger IDs first");
    }

    // ---- 带过滤条件的查询 ----

    @Test
    void testWithFilterCondition() {
        for (int i = 0; i < 5; i++) {
            TestEntity e = new TestEntity();
            e.setName("ksFilter" + i);
            e.setStatus(i % 2);
            repository.save(e);
        }
        repository.flush();

        Specification<TestEntity> spec = (root, query, cb) -> cb.equal(root.get("status"), 1);
        MyJpaTemplate.KeysetPage<TestEntity> page =
            keysetPaginationHelper.findKeysetPage(TestEntity.class, spec, Sort.by("id"), 10, null);

        // 只有 status=1 的记录
        assertTrue(page.content().size() <= 3);
        page.content().forEach(e -> assertEquals(1, e.getStatus()));
    }

    // ---- null 游标值处理 ----

    @Test
    void testNullSortValueInCursor() {
        TestEntity e1 = new TestEntity();
        e1.setName(null);
        e1.setStatus(1);
        repository.save(e1);

        TestEntity e2 = new TestEntity();
        e2.setName("nonNull");
        e2.setStatus(2);
        repository.save(e2);
        repository.flush();

        Specification<TestEntity> spec = (root, query, cb) -> cb.conjunction();
        MyJpaTemplate.KeysetPage<TestEntity> page =
            keysetPaginationHelper.findKeysetPage(TestEntity.class, spec, Sort.by("name"), 10, null);

        assertEquals(2, page.content().size());
    }

    // ---- 边界：恰好等于 pageSize ----

    @Test
    void testExactPageSizeMatch() {
        for (int i = 0; i < 3; i++) {
            TestEntity e = new TestEntity();
            e.setName("ksExact" + i);
            e.setStatus(i);
            repository.save(e);
        }
        repository.flush();

        Specification<TestEntity> spec = (root, query, cb) -> cb.conjunction();
        MyJpaTemplate.KeysetPage<TestEntity> page =
            keysetPaginationHelper.findKeysetPage(TestEntity.class, spec, Sort.by("id"), 3, null);

        assertEquals(3, page.content().size());
        assertFalse(page.hasNext(), "Exact match should not have next page");
        assertNull(page.lastSortValues());
    }

    // ---- 边界：pageSize=1 逐条翻页 ----

    @Test
    void testSingleRecordPerPage() {
        for (int i = 0; i < 3; i++) {
            TestEntity e = new TestEntity();
            e.setName("ksSingle" + i);
            e.setStatus(i);
            repository.save(e);
        }
        repository.flush();

        Specification<TestEntity> spec = (root, query, cb) -> cb.conjunction();

        MyJpaTemplate.KeysetPage<TestEntity> page1 =
            keysetPaginationHelper.findKeysetPage(TestEntity.class, spec, Sort.by("id"), 1, null);
        assertEquals(1, page1.content().size());
        assertTrue(page1.hasNext());

        MyJpaTemplate.KeysetPage<TestEntity> page2 =
            keysetPaginationHelper.findKeysetPage(TestEntity.class, spec, Sort.by("id"), 1, page1.lastSortValues());
        assertEquals(1, page2.content().size());
        assertTrue(page2.hasNext());

        MyJpaTemplate.KeysetPage<TestEntity> page3 =
            keysetPaginationHelper.findKeysetPage(TestEntity.class, spec, Sort.by("id"), 1, page2.lastSortValues());
        assertEquals(1, page3.content().size());
        assertFalse(page3.hasNext());
    }
}
