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
    void testConditionsAccessorReturnsQuerySpec() {
        ProjectionSpec<TestEntity> spec = new ProjectionSpec<>(TestEntity.class);
        assertNotNull(spec.conditions());
        spec.conditions().eq(TestEntity::getName, "value");
        assertNotNull(spec.conditions());
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

    public static class NameStatusDto {
        public final String name;
        public final int status;

        public NameStatusDto(String name, int status) {
            this.name = name;
            this.status = status;
        }
    }
}
