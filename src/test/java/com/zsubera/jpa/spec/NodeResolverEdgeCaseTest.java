package com.zsubera.jpa.spec;

import static org.junit.jupiter.api.Assertions.*;

import com.zsubera.jpa.exception.QueryBuildException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;

/**
 * NodeResolver 边界条件和错误路径测试。
 *
 * <p>覆盖 NegateNode 内部返回 null 的 disjunction 路径、Or/And 单子节点优化、
 * Or/And 空列表边界、FuncNode 解析时字段不存在的错误路径、RawNode 路径。
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class NodeResolverEdgeCaseTest {

    @Autowired
    private TestEntityRepository repository;

    @PersistenceContext
    private EntityManager em;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        repository.flush();
    }

    // ---- NegateNode: empty NOT group resolves to NOT(conjunction) = false ----

    @Test
    void notWithEmptyGroup_filtersAllRows() {
        repository.save(newEntity("test", 1));

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.not(n -> {
            // empty - inner AND resolves to conjunction(true), NOT(true) = false
        });
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(0, result.size(), "Empty NOT group resolves to false, filtering all rows");
    }

    // ---- OrNode: single child optimization ----

    @Test
    void orWithSingleChild_works() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 2));

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.or(o -> o.eq(TestEntity::getStatus, 1));
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    // ---- AndNode: single child optimization ----

    @Test
    void andWithSingleChild_works() {
        repository.save(newEntity("a", 1));

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.eq(TestEntity::getName, "a");
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    // ---- Two empty NOT groups: NOT(true) AND NOT(true) = false AND false = false ----

    @Test
    void twoEmptyNotGroups_filtersAllRows() {
        repository.save(newEntity("a", 1));

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.not(n -> {
            /* empty - resolves to NOT(true) = false */ });
        qs.not(n -> {
            /* empty - resolves to NOT(true) = false */ });
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(0, result.size());
    }

    // ---- FuncNode with non-existent field (added directly) ----

    @Test
    void funcNode_withNonExistentField_throwsQueryBuildException() {
        repository.save(newEntity("test", 1));

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        // FuncNode.of puts SFunction-resolved name as params[0] (the field),
        // so we test by directly adding a FuncNode with a bad field name
        qs.conditions().add(ConditionNode.FuncNode.of("COALESCE", new Object[] {"nonexistent_field"}));
        assertThrows(QueryBuildException.class, () -> repository.findAll(qs.toSpecification()));
    }

    // ---- FuncNode with null literal param ----

    @Test
    void funcNode_withNullLiteralParam_works() {
        repository.save(newEntity("test", 1));

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.func(TestEntity::getName, "COALESCE", (Object)null);
        assertNotNull(qs.toSpecification());
    }

    // ---- MultiLike node ----

    @Test
    void multiLike_withMultipleFields_orCombines() {
        repository.save(newEntity("hello", 1));
        repository.save(newEntity("world", 2));

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.multiLike("hello", TestEntity::getName);
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    // ---- RawNode path (added via conditions().add) ----

    @Test
    void rawPredicate_isApplied() {
        repository.save(newEntity("test", 1));
        repository.save(newEntity("other", 2));

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.conditions().add(new ConditionNode.RawNode((path, cb) -> cb.equal(path.get("status"), 1)));
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    // ---- Deep nested Or with nested OrGroup ----

    @Test
    void deeplyNestedOr_works() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 2));
        repository.save(newEntity("c", 3));

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.or(o -> {
            o.eq(TestEntity::getStatus, 1);
            o.or(inner -> {
                inner.eq(TestEntity::getStatus, 2);
                inner.eq(TestEntity::getName, "b");
            });
        });
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(2, result.size());
    }

    // ---- NOT with real predicate inside ----

    @Test
    void notWithRealPredicate_filtersCorrectly() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 2));

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.not(n -> n.eq(TestEntity::getStatus, 1));
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
        assertEquals("b", result.get(0).getName());
    }

    // ---- helpers ----

    private TestEntity newEntity(String name, int status) {
        TestEntity entity = new TestEntity();
        entity.setName(name);
        entity.setStatus(status);
        return entity;
    }
}
