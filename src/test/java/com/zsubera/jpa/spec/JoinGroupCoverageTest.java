package com.zsubera.jpa.spec;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
@org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase(
    replace = org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE)
class JoinGroupCoverageTest {

    @Autowired
    private TestEntityRepository repository;

    @Autowired
    private ParentEntityRepository parentRepository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        repository.flush();
        parentRepository.deleteAll();
        parentRepository.flush();
    }

    private TestEntity newEntity(String name, int status) {
        TestEntity e = new TestEntity();
        e.setName(name);
        e.setStatus(status);
        return e;
    }

    private ParentEntity newParent(String category, int level) {
        ParentEntity p = new ParentEntity();
        p.setCategory(category);
        p.setLevel(level);
        return p;
    }

    @Test
    void join_withMultiConditions_executes() {
        ParentEntity parent = newParent("admin", 10);
        parentRepository.save(parent);
        TestEntity child = newEntity("child", 0);
        child.setParent(parent);
        repository.save(child);

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.join(TestEntity::getParent, (JoinGroup<TestEntity, ParentEntity> j) -> j
            .eq(ParentEntity::getCategory, "admin").gt(ParentEntity::getLevel, 5));
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void join_withIn_executes() {
        ParentEntity parent = newParent("admin", 10);
        parentRepository.save(parent);
        TestEntity child = newEntity("child", 0);
        child.setParent(parent);
        repository.save(child);

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.join(TestEntity::getParent,
            (JoinGroup<TestEntity, ParentEntity> j) -> j.in(ParentEntity::getLevel, 10, 20, 30));
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void join_withBetween_executes() {
        ParentEntity parent = newParent("admin", 10);
        parentRepository.save(parent);
        TestEntity child = newEntity("child", 0);
        child.setParent(parent);
        repository.save(child);

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.join(TestEntity::getParent,
            (JoinGroup<TestEntity, ParentEntity> j) -> j.between(ParentEntity::getLevel, 5, 15));
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void join_withLike_executes() {
        ParentEntity parent = newParent("admin", 10);
        parentRepository.save(parent);
        TestEntity child = newEntity("child", 0);
        child.setParent(parent);
        repository.save(child);

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.join(TestEntity::getParent,
            (JoinGroup<TestEntity, ParentEntity> j) -> j.like(ParentEntity::getCategory, "admin"));
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void join_withNotLike_executes() {
        ParentEntity parent = newParent("admin", 10);
        parentRepository.save(parent);
        TestEntity child = newEntity("child", 0);
        child.setParent(parent);
        repository.save(child);

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.join(TestEntity::getParent,
            (JoinGroup<TestEntity, ParentEntity> j) -> j.notLike(ParentEntity::getCategory, "user%"));
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void join_withIsNull_executes() {
        ParentEntity parent = newParent(null, 10);
        parentRepository.save(parent);
        TestEntity child = newEntity("child", 0);
        child.setParent(parent);
        repository.save(child);

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.join(TestEntity::getParent, (JoinGroup<TestEntity, ParentEntity> j) -> j.isNull(ParentEntity::getCategory));
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void join_withIsNotNull_executes() {
        ParentEntity parent = newParent("admin", 10);
        parentRepository.save(parent);
        TestEntity child = newEntity("child", 0);
        child.setParent(parent);
        repository.save(child);

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.join(TestEntity::getParent,
            (JoinGroup<TestEntity, ParentEntity> j) -> j.isNotNull(ParentEntity::getCategory));
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void join_withGtLt_executes() {
        ParentEntity parent = newParent("admin", 10);
        parentRepository.save(parent);
        TestEntity child = newEntity("child", 0);
        child.setParent(parent);
        repository.save(child);

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.join(TestEntity::getParent,
            (JoinGroup<TestEntity, ParentEntity> j) -> j.gt(ParentEntity::getLevel, 5).lt(ParentEntity::getLevel, 15));
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void join_withNotIn_executes() {
        ParentEntity parent = newParent("admin", 10);
        parentRepository.save(parent);
        TestEntity child = newEntity("child", 0);
        child.setParent(parent);
        repository.save(child);

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.join(TestEntity::getParent,
            (JoinGroup<TestEntity, ParentEntity> j) -> j.notIn(ParentEntity::getLevel, 99, 100));
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void join_withMultiConditionAndOr_executes() {
        ParentEntity parent = newParent("admin", 10);
        parentRepository.save(parent);
        TestEntity child = newEntity("child", 0);
        child.setParent(parent);
        repository.save(child);

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.join(TestEntity::getParent,
            (JoinGroup<TestEntity, ParentEntity> j) -> j.eq(ParentEntity::getCategory, "admin")
                .or(o -> o.eq(ParentEntity::getLevel, 10).eq(ParentEntity::getLevel, 20)));
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void join_withOr_executes() {
        ParentEntity parent1 = newParent("admin", 10);
        parentRepository.save(parent1);
        ParentEntity parent2 = newParent("user", 5);
        parentRepository.save(parent2);
        TestEntity child1 = newEntity("child1", 0);
        child1.setParent(parent1);
        repository.save(child1);
        TestEntity child2 = newEntity("child2", 0);
        child2.setParent(parent2);
        repository.save(child2);

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.join(TestEntity::getParent, (JoinGroup<TestEntity, ParentEntity> j) -> j
            .or(o -> o.eq(ParentEntity::getCategory, "admin").eq(ParentEntity::getCategory, "user")));
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(2, result.size());
    }

    @Test
    void join_nullField_throws() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class, () -> qs.join(null, (JoinGroup<TestEntity, ParentEntity> j) -> {
        }));
    }

    @Test
    void join_nullConfig_throws() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class, () -> qs.join(TestEntity::getParent,
            (java.util.function.Consumer<JoinGroup<TestEntity, ParentEntity>>)null));
    }

    @Test
    void leftJoin_withMultiConditions_executes() {
        ParentEntity parent = newParent("admin", 10);
        parentRepository.save(parent);
        TestEntity child = newEntity("child", 0);
        child.setParent(parent);
        repository.save(child);

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.leftJoin(TestEntity::getParent, (JoinGroup<TestEntity, ParentEntity> j) -> j
            .eq(ParentEntity::getCategory, "admin").isNotNull(ParentEntity::getCategory));
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void fetchJoin_withConditions_executes() {
        ParentEntity parent = newParent("admin", 10);
        parentRepository.save(parent);
        TestEntity child = newEntity("child", 0);
        child.setParent(parent);
        repository.save(child);

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.fetchJoin(TestEntity::getParent,
            (JoinGroup<TestEntity, ParentEntity> j) -> j.eq(ParentEntity::getCategory, "admin"));
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void leftFetchJoin_withConditions_throwsQueryBuildException() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.leftFetchJoin(TestEntity::getParent,
            (JoinGroup<TestEntity, ParentEntity> j) -> j.eq(ParentEntity::getCategory, "admin"));
        assertThrows(com.zsubera.jpa.exception.QueryBuildException.class,
            () -> repository.findAll(qs.toSpecification()));
    }

    @Test
    void join_withMultiLike_executes() {
        ParentEntity parent = newParent("admin", 10);
        parentRepository.save(parent);
        TestEntity child = newEntity("child", 0);
        child.setParent(parent);
        repository.save(child);

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.join(TestEntity::getParent,
            (JoinGroup<TestEntity, ParentEntity> j) -> j.multiLike("admin", ParentEntity::getCategory));
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    // ===== Nested fetchJoin/leftFetchJoin within JoinGroup =====

    @Test
    void join_withNestedJoinOnCollection_executes() {
        ParentEntity parent = newParent("admin", 10);
        parentRepository.save(parent);
        TestEntity child = newEntity("child", 0);
        child.setParent(parent);
        repository.save(child);

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.join(TestEntity::getParent, (JoinGroup<TestEntity, ParentEntity> jg) -> {
            jg.eq(ParentEntity::getCategory, "admin");
            jg.join(ParentEntity::getChildren, (JoinGroup<TestEntity, TestEntity> j2) -> {
                j2.eq(TestEntity::getName, "child");
            });
        });
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void join_withNestedLeftJoinOnCollection_executes() {
        ParentEntity parent = newParent("admin", 10);
        parentRepository.save(parent);
        TestEntity child = newEntity("child", 0);
        child.setParent(parent);
        repository.save(child);

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.join(TestEntity::getParent, (JoinGroup<TestEntity, ParentEntity> jg) -> {
            jg.eq(ParentEntity::getCategory, "admin");
            jg.leftJoin(ParentEntity::getChildren, (JoinGroup<TestEntity, TestEntity> j2) -> {
                j2.eq(TestEntity::getName, "child");
            });
        });
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void join_withNestedFetchJoinOnVisibleField_executes() {
        ParentEntity parent = newParent("admin", 10);
        parentRepository.save(parent);
        TestEntity child = newEntity("child", 0);
        child.setParent(parent);
        repository.save(child);

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.join(TestEntity::getParent, (JoinGroup<TestEntity, ParentEntity> jg) -> {
            jg.eq(ParentEntity::getCategory, "admin");
            jg.fetchJoin(ParentEntity::getChildren, (JoinGroup<TestEntity, TestEntity> j2) -> {
            });
        });
        assertFalse(qs.conditions().isEmpty());
    }

    @Test
    void join_withNestedLeftFetchJoinOnCollection_executes() {
        ParentEntity parent = newParent("admin", 10);
        parentRepository.save(parent);
        TestEntity child = newEntity("child", 0);
        child.setParent(parent);
        repository.save(child);

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.join(TestEntity::getParent, (JoinGroup<TestEntity, ParentEntity> jg) -> {
            jg.eq(ParentEntity::getCategory, "admin");
            jg.leftFetchJoin(ParentEntity::getChildren, (JoinGroup<TestEntity, TestEntity> j2) -> {
            });
        });
        assertFalse(qs.conditions().isEmpty());
    }

    // ===== JoinGroup null validation =====

    @Test
    void joinGroup_join_nullField_throws() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.join(TestEntity::getParent, (JoinGroup<TestEntity, ParentEntity> jg) -> {
            assertThrows(IllegalArgumentException.class, () -> jg.join(null, j2 -> {
            }));
        });
    }

    @Test
    void joinGroup_join_nullConfig_throws() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.join(TestEntity::getParent, (JoinGroup<TestEntity, ParentEntity> jg) -> {
            assertThrows(IllegalArgumentException.class, () -> jg.join(ParentEntity::getChildren, null));
        });
    }

    @Test
    void joinGroup_leftJoin_nullField_throws() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.join(TestEntity::getParent, (JoinGroup<TestEntity, ParentEntity> jg) -> {
            assertThrows(IllegalArgumentException.class, () -> jg.leftJoin(null, j2 -> {
            }));
        });
    }

    @Test
    void joinGroup_leftJoin_nullConfig_throws() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.join(TestEntity::getParent, (JoinGroup<TestEntity, ParentEntity> jg) -> {
            assertThrows(IllegalArgumentException.class, () -> jg.leftJoin(ParentEntity::getChildren, null));
        });
    }

    @Test
    void joinGroup_fetchJoin_nullField_throws() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.join(TestEntity::getParent, (JoinGroup<TestEntity, ParentEntity> jg) -> {
            assertThrows(IllegalArgumentException.class, () -> jg.fetchJoin(null, j2 -> {
            }));
        });
    }

    @Test
    void joinGroup_fetchJoin_nullConfig_throws() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.join(TestEntity::getParent, (JoinGroup<TestEntity, ParentEntity> jg) -> {
            assertThrows(IllegalArgumentException.class, () -> jg.fetchJoin(ParentEntity::getChildren, null));
        });
    }

    @Test
    void joinGroup_leftFetchJoin_nullField_throws() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.join(TestEntity::getParent, (JoinGroup<TestEntity, ParentEntity> jg) -> {
            assertThrows(IllegalArgumentException.class, () -> jg.leftFetchJoin(null, j2 -> {
            }));
        });
    }

    @Test
    void joinGroup_leftFetchJoin_nullConfig_throws() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.join(TestEntity::getParent, (JoinGroup<TestEntity, ParentEntity> jg) -> {
            assertThrows(IllegalArgumentException.class, () -> jg.leftFetchJoin(ParentEntity::getChildren, null));
        });
    }

    @Test
    void joinGroup_or_nullConfig_throws() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.join(TestEntity::getParent, (JoinGroup<TestEntity, ParentEntity> jg) -> {
            assertThrows(IllegalArgumentException.class, () -> jg.or(null));
        });
    }

    @Test
    void joinGroup_join_fetchJoin_nullField_throws() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.fetchJoin(TestEntity::getParent, (JoinGroup<TestEntity, ParentEntity> jg) -> {
            assertThrows(IllegalArgumentException.class, () -> jg.join(null, j2 -> {
            }));
        });
    }

    @Test
    void joinGroup_fetchJoin_nullField_root_throws() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.leftFetchJoin(TestEntity::getParent, (JoinGroup<TestEntity, ParentEntity> jg) -> {
            assertThrows(IllegalArgumentException.class, () -> jg.leftFetchJoin(null, j2 -> {
            }));
        });
    }

    // ===== leftJoin with nested join =====

    @Test
    void leftJoin_withNestedJoin_executes() {
        ParentEntity parent = newParent("admin", 10);
        parentRepository.save(parent);
        TestEntity child = newEntity("child", 0);
        child.setParent(parent);
        repository.save(child);

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.leftJoin(TestEntity::getParent, (JoinGroup<TestEntity, ParentEntity> jg) -> {
            jg.eq(ParentEntity::getCategory, "admin");
            jg.join(ParentEntity::getChildren, (JoinGroup<TestEntity, TestEntity> j2) -> {
                j2.eq(TestEntity::getName, "child");
            });
        });
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void leftJoin_withNestedLeftJoin_executes() {
        ParentEntity parent = newParent("admin", 10);
        parentRepository.save(parent);
        TestEntity child = newEntity("child", 0);
        child.setParent(parent);
        repository.save(child);

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.leftJoin(TestEntity::getParent, (JoinGroup<TestEntity, ParentEntity> jg) -> {
            jg.eq(ParentEntity::getCategory, "admin");
            jg.leftJoin(ParentEntity::getChildren, (JoinGroup<TestEntity, TestEntity> j2) -> {
                j2.eq(TestEntity::getName, "child");
            });
        });
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }
}
