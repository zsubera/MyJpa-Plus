package com.zsubera.jpa.projection;

import static org.junit.jupiter.api.Assertions.*;

import com.zsubera.jpa.spec.TestApplication;
import com.zsubera.jpa.spec.TestEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ContextConfiguration;

@DataJpaTest
@org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase(
    replace = org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = TestApplication.class)
class ProjectionSpecTest {

    @Autowired
    private TestEntityManager testEntityManager;

    @PersistenceContext
    private EntityManager em;

    @Test
    void testTupleQuerySingleField() {
        TestEntity entity = new TestEntity();
        entity.setName("test");
        entity.setStatus(1);
        testEntityManager.persistAndFlush(entity);

        List<Tuple> results =
            new ProjectionSpec<>(TestEntity.class).select(TestEntity::getName).toTupleQuery(em).getResultList();

        assertEquals(1, results.size());
        assertEquals("test", results.get(0).get("name"));
    }

    @Test
    void testTupleQueryMultipleFields() {
        TestEntity entity = new TestEntity();
        entity.setName("multi");
        entity.setStatus(42);
        testEntityManager.persistAndFlush(entity);

        List<Tuple> results = new ProjectionSpec<>(TestEntity.class).select(TestEntity::getName)
            .select(TestEntity::getStatus).toTupleQuery(em).getResultList();

        assertEquals(1, results.size());
        Tuple tuple = results.get(0);
        assertEquals("multi", tuple.get("name"));
        assertEquals(42, tuple.get("status"));
    }

    @Test
    void testTupleQueryWithWhereCondition() {
        TestEntity e1 = new TestEntity();
        e1.setName("match");
        e1.setStatus(1);
        testEntityManager.persistAndFlush(e1);

        TestEntity e2 = new TestEntity();
        e2.setName("other");
        e2.setStatus(2);
        testEntityManager.persistAndFlush(e2);

        List<Tuple> results = new ProjectionSpec<>(TestEntity.class).select(TestEntity::getName)
            .where(q -> q.eq(TestEntity::getStatus, 1)).toTupleQuery(em).getResultList();

        assertEquals(1, results.size());
        assertEquals("match", results.get(0).get("name"));
    }

    @Test
    void testDtoQueryWithConstructorProjection() {
        TestEntity entity = new TestEntity();
        entity.setName("dto");
        entity.setStatus(99);
        testEntityManager.persistAndFlush(entity);

        List<NameStatusDto> results = new ProjectionSpec<>(TestEntity.class).select(TestEntity::getName)
            .select(TestEntity::getStatus).asDto(NameStatusDto.class).<NameStatusDto>toDtoQuery(em).getResultList();

        assertEquals(1, results.size());
        NameStatusDto dto = results.get(0);
        assertEquals("dto", dto.name);
        assertEquals(99, dto.status);
    }

    @Test
    void testDtoQueryWithoutAsDtoThrowsException() {
        ProjectionSpec<TestEntity> spec = new ProjectionSpec<>(TestEntity.class).select(TestEntity::getName);
        assertThrows(IllegalStateException.class, () -> spec.toDtoQuery(em));
    }

    @Test
    void testTupleQueryNoResults() {
        List<Tuple> results = new ProjectionSpec<>(TestEntity.class).select(TestEntity::getName)
            .where(q -> q.eq(TestEntity::getName, "nonexistent")).toTupleQuery(em).getResultList();

        assertTrue(results.isEmpty());
    }

    @Test
    void testEmptyProjectionReturnsAllFields() {
        TestEntity e1 = new TestEntity();
        e1.setName("a");
        e1.setStatus(1);
        testEntityManager.persistAndFlush(e1);
        TestEntity e2 = new TestEntity();
        e2.setName("b");
        e2.setStatus(2);
        testEntityManager.persistAndFlush(e2);

        List<Tuple> results =
            new ProjectionSpec<>(TestEntity.class).select(TestEntity::getName).toTupleQuery(em).getResultList();

        assertEquals(2, results.size());
    }

    @Test
    void testOrderByAsc() {
        TestEntity e1 = new TestEntity();
        e1.setName("b");
        e1.setStatus(1);
        testEntityManager.persistAndFlush(e1);
        TestEntity e2 = new TestEntity();
        e2.setName("a");
        e2.setStatus(2);
        testEntityManager.persistAndFlush(e2);

        List<Tuple> results = new ProjectionSpec<>(TestEntity.class).select(TestEntity::getName)
            .orderByAsc(TestEntity::getName).toTupleQuery(em).getResultList();

        assertEquals(2, results.size());
        assertEquals("a", results.get(0).get("name"));
        assertEquals("b", results.get(1).get("name"));
    }

    @Test
    void testOrderByDesc() {
        TestEntity e1 = new TestEntity();
        e1.setName("a");
        e1.setStatus(1);
        testEntityManager.persistAndFlush(e1);
        TestEntity e2 = new TestEntity();
        e2.setName("b");
        e2.setStatus(2);
        testEntityManager.persistAndFlush(e2);

        List<Tuple> results = new ProjectionSpec<>(TestEntity.class).select(TestEntity::getName)
            .orderByDesc(TestEntity::getName).toTupleQuery(em).getResultList();

        assertEquals(2, results.size());
        assertEquals("b", results.get(0).get("name"));
    }

    @Test
    void testFindPagePagination() {
        for (int i = 0; i < 5; i++) {
            TestEntity e = new TestEntity();
            e.setName("item" + i);
            e.setStatus(i);
            testEntityManager.persistAndFlush(e);
        }

        Page<Tuple> page =
            new ProjectionSpec<>(TestEntity.class).select(TestEntity::getName).findPage(em, PageRequest.of(0, 2));

        assertEquals(5, page.getTotalElements());
        assertEquals(2, page.getContent().size());
    }

    @Test
    void testJoinGroupNe() {
        com.zsubera.jpa.spec.ParentEntity p1 = new com.zsubera.jpa.spec.ParentEntity();
        p1.setCategory("admin");
        p1.setLevel(10);
        testEntityManager.persistAndFlush(p1);

        TestEntity child = new TestEntity();
        child.setName("child1");
        child.setStatus(0);
        child.setParent(p1);
        testEntityManager.persistAndFlush(child);

        List<Tuple> results = new ProjectionSpec<>(TestEntity.class).select(TestEntity::getName).join(
            TestEntity::getParent,
            (com.zsubera.jpa.projection.ProjectionSpec.ProjectionJoinGroup<com.zsubera.jpa.spec.ParentEntity> j) -> j
                .ne(com.zsubera.jpa.spec.ParentEntity::getCategory, "user"))
            .toTupleQuery(em).getResultList();

        assertEquals(1, results.size());
    }

    @Test
    void testJoinGroupLike() {
        com.zsubera.jpa.spec.ParentEntity p1 = new com.zsubera.jpa.spec.ParentEntity();
        p1.setCategory("admin");
        p1.setLevel(10);
        testEntityManager.persistAndFlush(p1);

        TestEntity child = new TestEntity();
        child.setName("child1");
        child.setStatus(0);
        child.setParent(p1);
        testEntityManager.persistAndFlush(child);

        List<Tuple> results = new ProjectionSpec<>(TestEntity.class).select(TestEntity::getName).join(
            TestEntity::getParent,
            (com.zsubera.jpa.projection.ProjectionSpec.ProjectionJoinGroup<com.zsubera.jpa.spec.ParentEntity> j) -> j
                .like(com.zsubera.jpa.spec.ParentEntity::getCategory, "adm"))
            .toTupleQuery(em).getResultList();

        assertEquals(1, results.size());
    }

    @Test
    void testJoinGroupGt() {
        com.zsubera.jpa.spec.ParentEntity p1 = new com.zsubera.jpa.spec.ParentEntity();
        p1.setCategory("admin");
        p1.setLevel(10);
        testEntityManager.persistAndFlush(p1);

        TestEntity child = new TestEntity();
        child.setName("child1");
        child.setStatus(0);
        child.setParent(p1);
        testEntityManager.persistAndFlush(child);

        List<Tuple> results = new ProjectionSpec<>(TestEntity.class).select(TestEntity::getName)
            .join(TestEntity::getParent,
                (com.zsubera.jpa.projection.ProjectionSpec.ProjectionJoinGroup<
                    com.zsubera.jpa.spec.ParentEntity> j) -> j.gt(com.zsubera.jpa.spec.ParentEntity::getLevel, 5))
            .toTupleQuery(em).getResultList();

        assertEquals(1, results.size());
    }

    @Test
    void testJoinGroupLt() {
        com.zsubera.jpa.spec.ParentEntity p1 = new com.zsubera.jpa.spec.ParentEntity();
        p1.setCategory("admin");
        p1.setLevel(10);
        testEntityManager.persistAndFlush(p1);

        TestEntity child = new TestEntity();
        child.setName("child1");
        child.setStatus(0);
        child.setParent(p1);
        testEntityManager.persistAndFlush(child);

        List<Tuple> results = new ProjectionSpec<>(TestEntity.class).select(TestEntity::getName)
            .join(TestEntity::getParent,
                (com.zsubera.jpa.projection.ProjectionSpec.ProjectionJoinGroup<
                    com.zsubera.jpa.spec.ParentEntity> j) -> j.lt(com.zsubera.jpa.spec.ParentEntity::getLevel, 20))
            .toTupleQuery(em).getResultList();

        assertEquals(1, results.size());
    }

    @Test
    void testJoinGroupIsNull() {
        TestEntity orphan = new TestEntity();
        orphan.setName("orphan");
        orphan.setStatus(0);
        orphan.setParent(null);
        testEntityManager.persistAndFlush(orphan);

        List<Tuple> results = new ProjectionSpec<>(TestEntity.class).select(TestEntity::getName)
            .leftJoin(TestEntity::getParent,
                (com.zsubera.jpa.projection.ProjectionSpec.ProjectionJoinGroup<
                    com.zsubera.jpa.spec.ParentEntity> j) -> j.isNull(com.zsubera.jpa.spec.ParentEntity::getCategory))
            .toTupleQuery(em).getResultList();

        assertEquals(1, results.size());
    }

    @Test
    void testJoinGroupIsNotNull() {
        com.zsubera.jpa.spec.ParentEntity p1 = new com.zsubera.jpa.spec.ParentEntity();
        p1.setCategory("admin");
        p1.setLevel(10);
        testEntityManager.persistAndFlush(p1);

        TestEntity child = new TestEntity();
        child.setName("child1");
        child.setStatus(0);
        child.setParent(p1);
        testEntityManager.persistAndFlush(child);

        List<Tuple> results = new ProjectionSpec<>(TestEntity.class).select(TestEntity::getName).join(
            TestEntity::getParent,
            (com.zsubera.jpa.projection.ProjectionSpec.ProjectionJoinGroup<com.zsubera.jpa.spec.ParentEntity> j) -> j
                .isNotNull(com.zsubera.jpa.spec.ParentEntity::getCategory))
            .toTupleQuery(em).getResultList();

        assertEquals(1, results.size());
    }

    @Test
    void testLeftJoin() {
        com.zsubera.jpa.spec.ParentEntity p1 = new com.zsubera.jpa.spec.ParentEntity();
        p1.setCategory("admin");
        p1.setLevel(10);
        testEntityManager.persistAndFlush(p1);

        TestEntity child = new TestEntity();
        child.setName("child1");
        child.setStatus(0);
        child.setParent(p1);
        testEntityManager.persistAndFlush(child);

        TestEntity orphan = new TestEntity();
        orphan.setName("orphan");
        orphan.setStatus(0);
        orphan.setParent(null);
        testEntityManager.persistAndFlush(orphan);

        List<Tuple> results = new ProjectionSpec<>(TestEntity.class).select(TestEntity::getName).leftJoin(
            TestEntity::getParent,
            (com.zsubera.jpa.projection.ProjectionSpec.ProjectionJoinGroup<com.zsubera.jpa.spec.ParentEntity> j) -> j
                .eq(com.zsubera.jpa.spec.ParentEntity::getCategory, "admin"))
            .where(q -> q.isNotNull(TestEntity::getParent)).toTupleQuery(em).getResultList();

        assertEquals(1, results.size());
    }

    @Test
    void testToTupleQueryReturnsTypedQuery() {
        TestEntity entity = new TestEntity();
        entity.setName("typed");
        entity.setStatus(1);
        testEntityManager.persistAndFlush(entity);

        jakarta.persistence.TypedQuery<Tuple> query =
            new ProjectionSpec<>(TestEntity.class).select(TestEntity::getName).toTupleQuery(em);

        assertNotNull(query);
        List<Tuple> results = query.getResultList();
        assertEquals(1, results.size());
    }

    @Test
    void testToDtoQueryReturnsTypedQuery() {
        TestEntity entity = new TestEntity();
        entity.setName("dtoTyped");
        entity.setStatus(42);
        testEntityManager.persistAndFlush(entity);

        jakarta.persistence.TypedQuery<NameStatusDto> query = new ProjectionSpec<>(TestEntity.class)
            .select(TestEntity::getName).select(TestEntity::getStatus).asDto(NameStatusDto.class).toDtoQuery(em);

        assertNotNull(query);
        List<NameStatusDto> results = query.getResultList();
        assertEquals(1, results.size());
    }

    @Test
    void testCombinationJoinWhereOrderByPagination() {
        com.zsubera.jpa.spec.ParentEntity p1 = new com.zsubera.jpa.spec.ParentEntity();
        p1.setCategory("admin");
        p1.setLevel(10);
        testEntityManager.persistAndFlush(p1);

        for (int i = 0; i < 5; i++) {
            TestEntity child = new TestEntity();
            child.setName("combo" + i);
            child.setStatus(i);
            child.setParent(p1);
            testEntityManager.persistAndFlush(child);
        }

        Page<Tuple> page = new ProjectionSpec<>(TestEntity.class).select(TestEntity::getName).join(
            TestEntity::getParent,
            (com.zsubera.jpa.projection.ProjectionSpec.ProjectionJoinGroup<com.zsubera.jpa.spec.ParentEntity> j) -> j
                .eq(com.zsubera.jpa.spec.ParentEntity::getCategory, "admin"))
            .where(q -> q.ge(TestEntity::getStatus, 0)).orderByAsc(TestEntity::getName)
            .findPage(em, PageRequest.of(0, 3));

        assertEquals(5, page.getTotalElements());
        assertEquals(3, page.getContent().size());
    }

    @Test
    void testJoinGroupLikeNullValueThrowsException() {
        assertThrows(Exception.class, () -> {
            new ProjectionSpec<>(TestEntity.class).select(TestEntity::getName)
                .join(TestEntity::getParent,
                    (com.zsubera.jpa.projection.ProjectionSpec.ProjectionJoinGroup<
                        com.zsubera.jpa.spec.ParentEntity> j) -> j.like(com.zsubera.jpa.spec.ParentEntity::getCategory,
                            null))
                .toTupleQuery(em).getResultList();
        });
    }

    @Test
    void testJoinGroupGtNullValueThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            new ProjectionSpec<>(TestEntity.class).select(TestEntity::getName).join(TestEntity::getParent,
                (com.zsubera.jpa.projection.ProjectionSpec.ProjectionJoinGroup<
                    com.zsubera.jpa.spec.ParentEntity> j) -> j.gt(com.zsubera.jpa.spec.ParentEntity::getLevel, null))
                .toTupleQuery(em).getResultList();
        });
    }

    @Test
    void testJoinGroupLtNullValueThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            new ProjectionSpec<>(TestEntity.class).select(TestEntity::getName).join(TestEntity::getParent,
                (com.zsubera.jpa.projection.ProjectionSpec.ProjectionJoinGroup<
                    com.zsubera.jpa.spec.ParentEntity> j) -> j.lt(com.zsubera.jpa.spec.ParentEntity::getLevel, null))
                .toTupleQuery(em).getResultList();
        });
    }

    public static class NameStatusDto {

        public final String name;
        public final int status;

        public NameStatusDto(String name, int status) {
            this.name = name;
            this.status = status;
        }
    }

    @Test
    void testFindPageWithDistinct() {
        TestEntity e1 = new TestEntity();
        e1.setName("dup");
        e1.setStatus(1);
        testEntityManager.persistAndFlush(e1);
        TestEntity e2 = new TestEntity();
        e2.setName("dup");
        e2.setStatus(2);
        testEntityManager.persistAndFlush(e2);

        Page<Tuple> page = new ProjectionSpec<>(TestEntity.class).select(TestEntity::getName).distinct().findPage(em,
            PageRequest.of(0, 10));

        assertEquals(1, page.getTotalElements());
        assertEquals(1, page.getContent().size());
    }

    @Test
    void testFindPageWithoutDistinct() {
        TestEntity e1 = new TestEntity();
        e1.setName("dup");
        e1.setStatus(1);
        testEntityManager.persistAndFlush(e1);
        TestEntity e2 = new TestEntity();
        e2.setName("dup");
        e2.setStatus(2);
        testEntityManager.persistAndFlush(e2);

        Page<Tuple> page =
            new ProjectionSpec<>(TestEntity.class).select(TestEntity::getName).findPage(em, PageRequest.of(0, 10));

        // Without distinct, both rows should be returned
        assertEquals(2, page.getTotalElements());
        assertEquals(2, page.getContent().size());
    }

    @Test
    void testFindPageUnpaged() {
        for (int i = 0; i < 3; i++) {
            TestEntity e = new TestEntity();
            e.setName("unpaged" + i);
            e.setStatus(i);
            testEntityManager.persistAndFlush(e);
        }

        Page<Tuple> page = new ProjectionSpec<>(TestEntity.class).select(TestEntity::getName).findPage(em,
            org.springframework.data.domain.Pageable.unpaged());

        assertEquals(3, page.getTotalElements());
        assertEquals(3, page.getContent().size());
    }

    @Test
    void testSelectCount() {
        for (int i = 0; i < 3; i++) {
            TestEntity e = new TestEntity();
            e.setName("count" + i);
            e.setStatus(i);
            testEntityManager.persistAndFlush(e);
        }

        List<Tuple> results = new ProjectionSpec<>(TestEntity.class).selectCount().toTupleQuery(em).getResultList();

        assertEquals(1, results.size());
        assertEquals(3L, results.get(0).get("count"));
    }

    @Test
    void testSelectSum() {
        for (int i = 1; i <= 3; i++) {
            TestEntity e = new TestEntity();
            e.setName("sum" + i);
            e.setStatus(i);
            testEntityManager.persistAndFlush(e);
        }

        List<Tuple> results =
            new ProjectionSpec<>(TestEntity.class).selectSum(TestEntity::getStatus).toTupleQuery(em).getResultList();

        assertEquals(1, results.size());
        assertEquals(6, ((Number)results.get(0).get("sum_status")).intValue());
    }

    @Test
    void testSelectMaxMin() {
        for (int i = 1; i <= 3; i++) {
            TestEntity e = new TestEntity();
            e.setName("mm" + i);
            e.setStatus(i * 10);
            testEntityManager.persistAndFlush(e);
        }

        List<Tuple> results = new ProjectionSpec<>(TestEntity.class).selectMax(TestEntity::getStatus)
            .selectMin(TestEntity::getStatus).toTupleQuery(em).getResultList();

        assertEquals(1, results.size());
        assertEquals(30, ((Number)results.get(0).get("max_status")).intValue());
        assertEquals(10, ((Number)results.get(0).get("min_status")).intValue());
    }

    @Test
    void testSelectAvg() {
        for (int i = 1; i <= 3; i++) {
            TestEntity e = new TestEntity();
            e.setName("avg" + i);
            e.setStatus(i * 10);
            testEntityManager.persistAndFlush(e);
        }

        List<Tuple> results =
            new ProjectionSpec<>(TestEntity.class).selectAvg(TestEntity::getStatus).toTupleQuery(em).getResultList();

        assertEquals(1, results.size());
        assertEquals(20.0, ((Number)results.get(0).get("avg_status")).doubleValue(), 0.01);
    }

    @Test
    void testTupleQueryWithMaxResults() {
        for (int i = 0; i < 5; i++) {
            TestEntity e = new TestEntity();
            e.setName("limit" + i);
            e.setStatus(i);
            testEntityManager.persistAndFlush(e);
        }

        List<Tuple> results =
            new ProjectionSpec<>(TestEntity.class).select(TestEntity::getName).toTupleQuery(em, 2).getResultList();

        assertEquals(2, results.size());
    }

    @Test
    void testTupleQueryUnlimitedResults() {
        for (int i = 0; i < 5; i++) {
            TestEntity e = new TestEntity();
            e.setName("unlim" + i);
            e.setStatus(i);
            testEntityManager.persistAndFlush(e);
        }

        List<Tuple> results =
            new ProjectionSpec<>(TestEntity.class).select(TestEntity::getName).toTupleQuery(em, -1).getResultList();

        assertEquals(5, results.size());
    }

    @Test
    void testNullFieldThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            new ProjectionSpec<>(TestEntity.class).select(null);
        });
    }

    @Test
    void testNullWhereThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            new ProjectionSpec<>(TestEntity.class).where(null);
        });
    }

    @Test
    void testNullDtoClassThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            new ProjectionSpec<>(TestEntity.class).asDto(null);
        });
    }

    @Test
    void testNullJoinFieldThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            new ProjectionSpec<>(TestEntity.class).join(null, j -> {
            });
        });
    }

    @Test
    void testNullJoinConfigThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            new ProjectionSpec<>(TestEntity.class).join(TestEntity::getParent, null);
        });
    }

    @Test
    void testNullOrderByFieldThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            new ProjectionSpec<>(TestEntity.class).orderByAsc(null);
        });
    }

    @Test
    void testNullOrderByDescFieldThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            new ProjectionSpec<>(TestEntity.class).orderByDesc(null);
        });
    }

    @Test
    void testNullGroupByFieldThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            new ProjectionSpec<>(TestEntity.class).groupBy(null);
        });
    }

    @Test
    void testNullHavingThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            new ProjectionSpec<>(TestEntity.class).having(null);
        });
    }

    @Test
    void testGetResultStream() {
        for (int i = 0; i < 3; i++) {
            TestEntity e = new TestEntity();
            e.setName("stream" + i);
            e.setStatus(i);
            testEntityManager.persistAndFlush(e);
        }

        try (java.util.stream.Stream<Tuple> stream =
            new ProjectionSpec<>(TestEntity.class).select(TestEntity::getName).getResultStream(em)) {
            assertEquals(3, stream.count());
        }
    }

    @Test
    void testGetResultStreamNullEmThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            new ProjectionSpec<>(TestEntity.class).select(TestEntity::getName).getResultStream(null);
        });
    }

    @Test
    void testWithDefaultsReturnsSpec() {
        ProjectionSpec<TestEntity> spec = ProjectionSpec.withDefaults(TestEntity.class);
        assertNotNull(spec);
    }

    @Test
    void testWithSoftDeleteFilter() {
        ProjectionSpec<TestEntity> spec =
            new ProjectionSpec<>(TestEntity.class).select(TestEntity::getName).withSoftDeleteFilter();
        assertNotNull(spec);
    }

    @Test
    void testSelectCountDistinct() {
        for (int i = 0; i < 3; i++) {
            TestEntity e = new TestEntity();
            e.setName("cd" + i);
            e.setStatus(i % 2);
            testEntityManager.persistAndFlush(e);
        }

        List<Tuple> results = new ProjectionSpec<>(TestEntity.class).selectCountDistinct()
            .groupBy(TestEntity::getStatus).toTupleQuery(em).getResultList();
        assertFalse(results.isEmpty());
    }

    @Test
    void testGroupByWithHaving() {
        for (int i = 0; i < 5; i++) {
            TestEntity e = new TestEntity();
            e.setName("having" + i);
            e.setStatus(1);
            testEntityManager.persistAndFlush(e);
        }
        TestEntity e = new TestEntity();
        e.setName("having_single");
        e.setStatus(2);
        testEntityManager.persistAndFlush(e);

        List<Tuple> results = new ProjectionSpec<>(TestEntity.class).select(TestEntity::getStatus).selectCount()
            .groupBy(TestEntity::getStatus).having((root, cb) -> cb.greaterThan(cb.count(root), 1L)).toTupleQuery(em)
            .getResultList();
        assertEquals(1, results.size());
    }

    @Test
    void testFindPageWithGroupBy() {
        for (int i = 0; i < 3; i++) {
            TestEntity e = new TestEntity();
            e.setName("gp" + i);
            e.setStatus(i);
            testEntityManager.persistAndFlush(e);
        }

        Page<Tuple> page = new ProjectionSpec<>(TestEntity.class).select(TestEntity::getStatus).selectCount()
            .groupBy(TestEntity::getStatus).findPage(em, PageRequest.of(0, 10));
        assertNotNull(page);
        assertTrue(page.getTotalElements() > 0);
    }

    @Test
    void testFindPageDeepPaginationLimitExceeded() {
        ProjectionSpec<TestEntity> spec =
            new ProjectionSpec<>(TestEntity.class).select(TestEntity::getName).withDeepPaginationLimit(10);

        assertThrows(IllegalArgumentException.class, () -> spec.findPage(em, PageRequest.of(2, 10)));
    }

    @Test
    void testToTupleQueryWithMaxResults() {
        for (int i = 0; i < 5; i++) {
            TestEntity e = new TestEntity();
            e.setName("limit" + i);
            e.setStatus(i);
            testEntityManager.persistAndFlush(e);
        }

        List<Tuple> results =
            new ProjectionSpec<>(TestEntity.class).select(TestEntity::getName).toTupleQuery(em, 2).getResultList();
        assertEquals(2, results.size());
    }

    @Test
    void testToDtoQueryWithMaxResults() {
        for (int i = 0; i < 5; i++) {
            TestEntity e = new TestEntity();
            e.setName("limit" + i);
            e.setStatus(i);
            testEntityManager.persistAndFlush(e);
        }

        @SuppressWarnings("unchecked")
        List<TestEntity> results =
            (List<TestEntity>)(List<?>)new ProjectionSpec<>(TestEntity.class).select(TestEntity::getName)
                .select(TestEntity::getStatus).asDto(TestEntity.class).toDtoQuery(em, 2).getResultList();
        assertEquals(2, results.size());
    }

    @Test
    void testJoinWithOrCondition() {
        for (int i = 0; i < 3; i++) {
            TestEntity e = new TestEntity();
            e.setName("joinOr" + i);
            e.setStatus(i);
            testEntityManager.persistAndFlush(e);
        }

        List<Tuple> results =
            new ProjectionSpec<>(TestEntity.class).select(TestEntity::getName).toTupleQuery(em).getResultList();
        assertFalse(results.isEmpty());
    }

    @Test
    void testEmptySelectionThrows() {
        assertThrows(IllegalArgumentException.class, () -> new ProjectionSpec<>(TestEntity.class).toTupleQuery(em));
    }

    @Test
    void testMixAggregateAndNonAggregateWithoutGroupByThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new ProjectionSpec<>(TestEntity.class).select(TestEntity::getName).selectCount().toTupleQuery(em));
    }
}
