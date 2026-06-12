package com.zsubera.jpa.spec;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;

@DataJpaTest
@org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase(
    replace = org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = TestApplication.class)
class PredicateHelperTest {

    @PersistenceContext
    private EntityManager em;

    @Test
    void escapeLikeWildcards_null_returnsNull() {
        assertNull(PredicateHelper.escapeLikeWildcards(null));
    }

    @Test
    void escapeLikeWildcards_empty_returnsEmpty() {
        assertEquals("", PredicateHelper.escapeLikeWildcards(""));
    }

    @Test
    void escapeLikeWildcards_percent_escaped() {
        assertEquals("\\%", PredicateHelper.escapeLikeWildcards("%"));
    }

    @Test
    void escapeLikeWildcards_underscore_escaped() {
        assertEquals("\\_", PredicateHelper.escapeLikeWildcards("_"));
    }

    @Test
    void escapeLikeWildcards_backslash_escaped() {
        assertEquals("\\\\", PredicateHelper.escapeLikeWildcards("\\"));
    }

    @Test
    void escapeLikeWildcards_mixed() {
        assertEquals("hello\\%world\\_test\\\\", PredicateHelper.escapeLikeWildcards("hello%world_test\\"));
    }

    @Test
    void eq_normalValue() {
        persistEntity("test1", 1);
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        Predicate predicate = PredicateHelper.eq(root, "name", "test1", cb);
        cq.where(predicate);
        List<TestEntity> result = em.createQuery(cq).getResultList();
        assertEquals(1, result.size());
        assertEquals("test1", result.get(0).getName());
    }

    @Test
    void eq_nullValue_isNull() {
        persistEntity(null, 1);
        persistEntity("test", 2);
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        Predicate predicate = PredicateHelper.eq(root, "name", null, cb);
        cq.where(predicate);
        List<TestEntity> result = em.createQuery(cq).getResultList();
        assertEquals(1, result.size());
        assertNull(result.get(0).getName());
    }

    @Test
    void ne_normalValue() {
        persistEntity("a", 1);
        persistEntity("b", 2);
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        Predicate predicate = PredicateHelper.ne(root, "name", "a", cb);
        cq.where(predicate);
        List<TestEntity> result = em.createQuery(cq).getResultList();
        assertEquals(1, result.size());
        assertEquals("b", result.get(0).getName());
    }

    @Test
    void ne_nullValue_isNotNull() {
        persistEntity(null, 1);
        persistEntity("test", 2);
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        Predicate predicate = PredicateHelper.ne(root, "name", null, cb);
        cq.where(predicate);
        List<TestEntity> result = em.createQuery(cq).getResultList();
        assertEquals(1, result.size());
        assertEquals("test", result.get(0).getName());
    }

    @Test
    void gt_normalValue() {
        persistEntity("a", 1);
        persistEntity("b", 5);
        persistEntity("c", 10);
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        Predicate predicate = PredicateHelper.gt(root, "status", 5, cb);
        cq.where(predicate);
        List<TestEntity> result = em.createQuery(cq).getResultList();
        assertEquals(1, result.size());
        assertEquals(10, result.get(0).getStatus());
    }

    @Test
    void ge_normalValue() {
        persistEntity("a", 1);
        persistEntity("b", 5);
        persistEntity("c", 10);
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        Predicate predicate = PredicateHelper.ge(root, "status", 5, cb);
        cq.where(predicate);
        List<TestEntity> result = em.createQuery(cq).getResultList();
        assertEquals(2, result.size());
    }

    @Test
    void lt_normalValue() {
        persistEntity("a", 1);
        persistEntity("b", 5);
        persistEntity("c", 10);
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        Predicate predicate = PredicateHelper.lt(root, "status", 5, cb);
        cq.where(predicate);
        List<TestEntity> result = em.createQuery(cq).getResultList();
        assertEquals(1, result.size());
        assertEquals(1, result.get(0).getStatus());
    }

    @Test
    void le_normalValue() {
        persistEntity("a", 1);
        persistEntity("b", 5);
        persistEntity("c", 10);
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        Predicate predicate = PredicateHelper.le(root, "status", 5, cb);
        cq.where(predicate);
        List<TestEntity> result = em.createQuery(cq).getResultList();
        assertEquals(2, result.size());
    }

    @Test
    void like_normalPattern() {
        persistEntity("hello", 1);
        persistEntity("world", 2);
        persistEntity("hello world", 3);
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        Predicate predicate = PredicateHelper.like(root, "name", "%hello%", cb);
        cq.where(predicate);
        List<TestEntity> result = em.createQuery(cq).getResultList();
        assertEquals(2, result.size());
    }

    @Test
    void like_withEscapeChar() {
        persistEntity("test%value", 1);
        persistEntity("testXvalue", 2);
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        Predicate predicate = PredicateHelper.like(root, "name", "test\\%value", cb, '\\');
        cq.where(predicate);
        List<TestEntity> result = em.createQuery(cq).getResultList();
        assertEquals(1, result.size());
        assertEquals("test%value", result.get(0).getName());
    }

    @Test
    void notLike_normalPattern() {
        persistEntity("hello", 1);
        persistEntity("world", 2);
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        Predicate predicate = PredicateHelper.notLike(root, "name", "%hello%", cb);
        cq.where(predicate);
        List<TestEntity> result = em.createQuery(cq).getResultList();
        assertEquals(1, result.size());
        assertEquals("world", result.get(0).getName());
    }

    @Test
    void notLike_withEscapeChar() {
        persistEntity("test%value", 1);
        persistEntity("testXvalue", 2);
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        Predicate predicate = PredicateHelper.notLike(root, "name", "test\\%value", cb, '\\');
        cq.where(predicate);
        List<TestEntity> result = em.createQuery(cq).getResultList();
        assertEquals(1, result.size());
        assertEquals("testXvalue", result.get(0).getName());
    }

    @Test
    void startsWith_normalValue() {
        persistEntity("hello", 1);
        persistEntity("help", 2);
        persistEntity("world", 3);
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        Predicate predicate = PredicateHelper.startsWith(root, "name", "hel", cb);
        cq.where(predicate);
        List<TestEntity> result = em.createQuery(cq).getResultList();
        assertEquals(2, result.size());
    }

    @Test
    void startsWith_withWildcards() {
        persistEntity("he%llo", 1);
        persistEntity("help", 2);
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        Predicate predicate = PredicateHelper.startsWith(root, "name", "he%l", cb);
        cq.where(predicate);
        List<TestEntity> result = em.createQuery(cq).getResultList();
        assertEquals(1, result.size());
        assertEquals("he%llo", result.get(0).getName());
    }

    @Test
    void endsWith_normalValue() {
        persistEntity("ending", 1);
        persistEntity("pending", 2);
        persistEntity("start", 3);
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        Predicate predicate = PredicateHelper.endsWith(root, "name", "ing", cb);
        cq.where(predicate);
        List<TestEntity> result = em.createQuery(cq).getResultList();
        assertEquals(2, result.size());
    }

    @Test
    void endsWith_withWildcards() {
        persistEntity("test%end", 1);
        persistEntity("testXend", 2);
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        Predicate predicate = PredicateHelper.endsWith(root, "name", "%end", cb);
        cq.where(predicate);
        List<TestEntity> result = em.createQuery(cq).getResultList();
        assertEquals(1, result.size());
        assertEquals("test%end", result.get(0).getName());
    }

    @Test
    void contains_normalValue() {
        persistEntity("abc", 1);
        persistEntity("xabcx", 2);
        persistEntity("xyz", 3);
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        Predicate predicate = PredicateHelper.contains(root, "name", "ab", cb);
        cq.where(predicate);
        List<TestEntity> result = em.createQuery(cq).getResultList();
        assertEquals(2, result.size());
    }

    @Test
    void contains_withWildcards() {
        persistEntity("test%value", 1);
        persistEntity("testXvalue", 2);
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        Predicate predicate = PredicateHelper.contains(root, "name", "%val", cb);
        cq.where(predicate);
        List<TestEntity> result = em.createQuery(cq).getResultList();
        assertEquals(1, result.size());
        assertEquals("test%value", result.get(0).getName());
    }

    @Test
    void eqIgnoreCase_normalValue() {
        persistEntity("Hello", 1);
        persistEntity("hello", 2);
        persistEntity("WORLD", 3);
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        Predicate predicate = PredicateHelper.eqIgnoreCase(root, "name", "HELLO", cb);
        cq.where(predicate);
        List<TestEntity> result = em.createQuery(cq).getResultList();
        assertEquals(2, result.size());
    }

    @Test
    void eqIgnoreCase_null_isNull() {
        persistEntity(null, 1);
        persistEntity("hello", 2);
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        Predicate predicate = PredicateHelper.eqIgnoreCase(root, "name", null, cb);
        cq.where(predicate);
        List<TestEntity> result = em.createQuery(cq).getResultList();
        assertEquals(1, result.size());
        assertNull(result.get(0).getName());
    }

    @Test
    void likeIgnoreCase_normalValue() {
        persistEntity("HelloWorld", 1);
        persistEntity("HELLO", 2);
        persistEntity("xyz", 3);
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        Predicate predicate = PredicateHelper.likeIgnoreCase(root, "name", "%hello%", cb);
        cq.where(predicate);
        List<TestEntity> result = em.createQuery(cq).getResultList();
        assertEquals(2, result.size());
    }

    @Test
    void in_array_normalValues() {
        persistEntity("a", 1);
        persistEntity("b", 2);
        persistEntity("c", 3);
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        Predicate predicate = PredicateHelper.in(root, "status", new Object[] {1, 3}, cb);
        cq.where(predicate);
        List<TestEntity> result = em.createQuery(cq).getResultList();
        assertEquals(2, result.size());
    }

    @Test
    void notIn_array_normalValues() {
        persistEntity("a", 1);
        persistEntity("b", 2);
        persistEntity("c", 3);
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        Predicate predicate = PredicateHelper.notIn(root, "status", new Object[] {1, 3}, cb);
        cq.where(predicate);
        List<TestEntity> result = em.createQuery(cq).getResultList();
        assertEquals(1, result.size());
        assertEquals(2, result.get(0).getStatus());
    }

    @Test
    void in_collection_normalValues() {
        persistEntity("a", 1);
        persistEntity("b", 2);
        persistEntity("c", 3);
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        Collection<Integer> values = Arrays.asList(1, 3);
        Predicate predicate = PredicateHelper.in(root, "status", values, cb);
        cq.where(predicate);
        List<TestEntity> result = em.createQuery(cq).getResultList();
        assertEquals(2, result.size());
    }

    @Test
    void notIn_collection_normalValues() {
        persistEntity("a", 1);
        persistEntity("b", 2);
        persistEntity("c", 3);
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        Collection<Integer> values = Arrays.asList(1, 3);
        Predicate predicate = PredicateHelper.notIn(root, "status", values, cb);
        cq.where(predicate);
        List<TestEntity> result = em.createQuery(cq).getResultList();
        assertEquals(1, result.size());
        assertEquals(2, result.get(0).getStatus());
    }

    @Test
    void between_normalRange() {
        for (int i = 1; i <= 10; i++) {
            persistEntity("item" + i, i);
        }
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        Predicate predicate = PredicateHelper.between(root, "status", 3, 7, cb);
        cq.where(predicate);
        List<TestEntity> result = em.createQuery(cq).getResultList();
        assertEquals(5, result.size());
    }

    @Test
    void notBetween_normalRange() {
        for (int i = 1; i <= 10; i++) {
            persistEntity("item" + i, i);
        }
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        Predicate predicate = PredicateHelper.notBetween(root, "status", 3, 7, cb);
        cq.where(predicate);
        List<TestEntity> result = em.createQuery(cq).getResultList();
        assertEquals(5, result.size());
    }

    @Test
    void isNull_fieldIsNull() {
        persistEntity(null, 1);
        persistEntity("test", 2);
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        Predicate predicate = PredicateHelper.isNull(root, "name", cb);
        cq.where(predicate);
        List<TestEntity> result = em.createQuery(cq).getResultList();
        assertEquals(1, result.size());
        assertNull(result.get(0).getName());
    }

    @Test
    void isNotNull_fieldIsNotNull() {
        persistEntity(null, 1);
        persistEntity("test", 2);
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        Predicate predicate = PredicateHelper.isNotNull(root, "name", cb);
        cq.where(predicate);
        List<TestEntity> result = em.createQuery(cq).getResultList();
        assertEquals(1, result.size());
        assertEquals("test", result.get(0).getName());
    }

    @Test
    void isEmpty_collectionEmpty() {
        ParentEntity parent = new ParentEntity();
        parent.setCategory("cat");
        parent.setLevel(1);
        em.persist(parent);
        em.flush();

        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<ParentEntity> cq = cb.createQuery(ParentEntity.class);
        Root<ParentEntity> root = cq.from(ParentEntity.class);
        Predicate predicate = PredicateHelper.isEmpty(root, "children", cb);
        cq.where(predicate);
        List<ParentEntity> result = em.createQuery(cq).getResultList();
        assertEquals(1, result.size());
    }

    @Test
    void isNotEmpty_collectionNotEmpty() {
        ParentEntity parent = new ParentEntity();
        parent.setCategory("cat");
        parent.setLevel(1);
        em.persist(parent);

        TestEntity child = new TestEntity();
        child.setName("child");
        child.setStatus(0);
        child.setParent(parent);
        em.persist(child);
        em.flush();

        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<ParentEntity> cq = cb.createQuery(ParentEntity.class);
        Root<ParentEntity> root = cq.from(ParentEntity.class);
        Predicate predicate = PredicateHelper.isNotEmpty(root, "children", cb);
        cq.where(predicate);
        List<ParentEntity> result = em.createQuery(cq).getResultList();
        assertEquals(1, result.size());
    }

    private void persistEntity(String name, int status) {
        TestEntity entity = new TestEntity();
        entity.setName(name);
        entity.setStatus(status);
        em.persist(entity);
    }
}
