package com.zsubera.jpa.spec;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;

/**
 * SubQuerySpec 未覆盖错误路径测试。
 *
 * <p>覆盖 select() 在 or()/not() 内调用、重复 select、create() 参数校验、
 * conditional or/not 的 false 路径，以及内部状态方法。
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SubQuerySpecErrorPathTest {

    @Autowired
    private TestEntityRepository repository;

    @Autowired
    private ParentEntityRepository parentRepository;

    @PersistenceContext
    private EntityManager em;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        repository.flush();
    }

    // ---- select() inside or()/not() ----

    @Test
    void selectInsideOrGroup_throwsIllegalState() {
        ParentEntity p = saveParent("cat", 10);

        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.or(o -> {
            o.select(TestEntity::getName);
        }));
        assertThrows(Exception.class, () -> parentRepository.findAll(qs.toSpecification()));
    }

    @Test
    void selectInsideNotGroup_throwsIllegalState() {
        ParentEntity p = saveParent("cat", 10);

        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.not(n -> {
            n.select(TestEntity::getName);
        }));
        assertThrows(Exception.class, () -> parentRepository.findAll(qs.toSpecification()));
    }

    // ---- select() called twice ----

    @Test
    void selectCalledTwice_throwsIllegalState() {
        ParentEntity p = saveParent("cat", 10);

        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> {
            sub.select(TestEntity::getName);
            sub.select(TestEntity::getStatus);
        });
        assertThrows(Exception.class, () -> parentRepository.findAll(qs.toSpecification()));
    }

    // ---- conditional or/not with false ----

    @Test
    void conditionalOrFalse_skips() {
        ParentEntity p = saveParent("cat", 10);
        TestEntity c = saveChild(p, "c1", 5);

        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.or(false, o -> o.eq(TestEntity::getStatus, 5)));
        List<ParentEntity> result = parentRepository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void conditionalNotFalse_skips() {
        ParentEntity p = saveParent("cat", 10);
        TestEntity c = saveChild(p, "c1", 5);

        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.not(false, n -> n.eq(TestEntity::getName, "c1")));
        List<ParentEntity> result = parentRepository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void conditionalOrTrue_executes() {
        ParentEntity p = saveParent("cat", 10);
        TestEntity c = saveChild(p, "c1", 5);

        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.or(true, o -> o.eq(TestEntity::getStatus, 5)));
        List<ParentEntity> result = parentRepository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    // ---- create() null parameter validation ----

    @Test
    void create_nullSubquery_throws() {
        assertThrows(IllegalArgumentException.class, () -> SubQuerySpec.create(null, null, null, null));
    }

    // ---- isSelectSet / getSelectFieldName / getSelectType ----

    @Test
    void selectSetsStateCorrectly() {
        ParentEntity p = saveParent("cat", 10);
        TestEntity c = saveChild(p, "c1", 5);

        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        final boolean[] isSelectSet = {false};
        qs.exists(TestEntity.class, sub -> {
            sub.select(TestEntity::getName);
            isSelectSet[0] = sub.isSelectSet();
        });
        parentRepository.findAll(qs.toSpecification());
        assertTrue(isSelectSet[0]);
    }

    // ---- or() / not() with empty child produces no predicate ----

    @Test
    void orWithEmptyChild_noExtraFilter() {
        ParentEntity p = saveParent("cat", 10);
        saveChild(p, "c1", 1);
        repository.flush();

        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.or(o -> {
            // empty consumer, no predicates added
        }));
        List<ParentEntity> result = parentRepository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void notWithEmptyChild_noExtraFilter() {
        ParentEntity p = saveParent("cat", 10);
        saveChild(p, "c1", 1);
        repository.flush();

        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.not(n -> {
            // empty consumer, no predicates added
        }));
        List<ParentEntity> result = parentRepository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    // ---- correlated() returns correlated root ----

    @Test
    void correlatedReturnsNonNull() {
        ParentEntity p = saveParent("cat", 10);
        saveChild(p, "c1", 1);

        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        final boolean[] gotRoot = {false};
        qs.exists(TestEntity.class, sub -> {
            var root = sub.correlated();
            gotRoot[0] = root != null;
        });
        parentRepository.findAll(qs.toSpecification());
        assertTrue(gotRoot[0]);
    }

    // ---- multiLike with invalid nested field name ----

    @Test
    void multiLikeStringFieldNames_invalidChars_throws() {
        ParentEntity p = saveParent("cat", 10);

        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.multiLike("test", "name;DROP TABLE"));
        RuntimeException ex =
            assertThrows(RuntimeException.class, () -> parentRepository.findAll(qs.toSpecification()));
        assertTrue(ex.getCause() instanceof IllegalArgumentException || ex instanceof IllegalArgumentException);
    }

    @Test
    void multiLikeStringFieldNames_validNestedField() {
        ParentEntity p = saveParent("cat", 10);
        TestEntity c = saveChild(p, "hello", 1);
        repository.flush();

        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.multiLike("hello", "name"));
        List<ParentEntity> result = parentRepository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    // ---- eqStrict / neStrict with non-null values ----

    @Test
    void eqStrictWithValue_works() {
        ParentEntity p = saveParent("cat", 10);
        saveChild(p, "c1", 1);
        repository.flush();

        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.eqStrict(TestEntity::getName, "c1"));
        List<ParentEntity> result = parentRepository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void neStrictWithValue_works() {
        ParentEntity p = saveParent("cat", 10);
        saveChild(p, "c1", 1);
        repository.flush();

        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.neStrict(TestEntity::getName, "other"));
        List<ParentEntity> result = parentRepository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    // ---- notStartsWith / notEndsWith ----

    @Test
    void notStartsWith_works() {
        ParentEntity p = saveParent("cat", 10);
        saveChild(p, "hello", 0);
        saveChild(p, "world", 0);
        repository.flush();

        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.notStartsWith(TestEntity::getName, "hel"));
        List<ParentEntity> result = parentRepository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void notEndsWith_works() {
        ParentEntity p = saveParent("cat", 10);
        saveChild(p, "hello", 0);
        saveChild(p, "world", 0);
        repository.flush();

        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.notEndsWith(TestEntity::getName, "lo"));
        List<ParentEntity> result = parentRepository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    // ---- neIgnoreCase ----

    @Test
    void neIgnoreCase_works() {
        ParentEntity p = saveParent("cat", 10);
        saveChild(p, "Hello", 0);
        saveChild(p, "World", 0);
        repository.flush();

        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.neIgnoreCase(TestEntity::getName, "hello"));
        List<ParentEntity> result = parentRepository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    // ---- helpers ----

    private ParentEntity saveParent(String category, int level) {
        ParentEntity p = new ParentEntity();
        p.setCategory(category);
        p.setLevel(level);
        em.persist(p);
        return p;
    }

    private TestEntity saveChild(ParentEntity parent, String name, int status) {
        TestEntity c = new TestEntity();
        c.setName(name);
        c.setStatus(status);
        c.setParent(parent);
        return repository.save(c);
    }
}
