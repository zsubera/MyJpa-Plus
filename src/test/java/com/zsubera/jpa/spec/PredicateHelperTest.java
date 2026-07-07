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

    // ===== LIKE/NOT_LIKE/startsWith/endsWith null 值路径 =====

    @Test
    void like_nullValue_throwsException() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        assertThrows(IllegalArgumentException.class, () -> PredicateHelper.like(root, "name", null, cb));
    }

    @Test
    void notLike_nullValue_throws() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        assertThrows(IllegalArgumentException.class, () -> PredicateHelper.notLike(root, "name", null, cb));
    }

    @Test
    void notLike_withEscapeChar_nullValue_throws() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        assertThrows(IllegalArgumentException.class, () -> PredicateHelper.notLike(root, "name", null, cb, '\\'));
    }

    @Test
    void startsWith_nullValue_returnsConjunction() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        Predicate p = PredicateHelper.startsWith(root, "name", null, cb);
        assertNotNull(p);
    }

    @Test
    void endsWith_nullValue_returnsConjunction() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        Predicate p = PredicateHelper.endsWith(root, "name", null, cb);
        assertNotNull(p);
    }

    @Test
    void contains_nullValue_returnsConjunction() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        Predicate p = PredicateHelper.contains(root, "name", null, cb);
        assertNotNull(p);
    }

    @Test
    void likeIgnoreCase_nullValue_throws() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        assertThrows(IllegalArgumentException.class, () -> PredicateHelper.likeIgnoreCase(root, "name", null, cb));
    }

    @Test
    void likeIgnoreCase_withEscapeChar_nullValue_throws() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        assertThrows(IllegalArgumentException.class,
            () -> PredicateHelper.likeIgnoreCase(root, "name", null, cb, '\\'));
    }

    // ===== validateRange 边界路径 =====

    @Test
    void validateRange_startGreaterThanEnd_throws() {
        assertThrows(IllegalArgumentException.class, () -> PredicateHelper.validateRange(10, 5));
    }

    @Test
    void validateRange_startEqualsEnd_noThrow() {
        assertDoesNotThrow(() -> PredicateHelper.validateRange(5, 5));
    }

    @Test
    void validateRange_doubles_startGreaterThanEnd_throws() {
        assertThrows(IllegalArgumentException.class, () -> PredicateHelper.validateRange(10.5, 5.5));
    }

    @Test
    void validateRange_doubles_startEqualsEnd_noThrow() {
        assertDoesNotThrow(() -> PredicateHelper.validateRange(5.5, 5.5));
    }

    // ===== resolveSimplePredicate null 参数校验 =====

    @Test
    void resolveSimplePredicate_nullPath_throws() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        ConditionNode.SimpleNode node = new ConditionNode.SimpleNode("name", "test", ConditionNode.Op.EQ);
        assertThrows(IllegalArgumentException.class, () -> PredicateHelper.resolveSimplePredicate(null, node, cb));
    }

    @Test
    void resolveSimplePredicate_nullNode_throws() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        assertThrows(IllegalArgumentException.class, () -> PredicateHelper.resolveSimplePredicate(root, null, cb));
    }

    @Test
    void resolveSimplePredicate_nullCb_throws() {
        CriteriaQuery<TestEntity> cq = em.getCriteriaBuilder().createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        ConditionNode.SimpleNode node = new ConditionNode.SimpleNode("name", "test", ConditionNode.Op.EQ);
        assertThrows(IllegalArgumentException.class, () -> PredicateHelper.resolveSimplePredicate(root, node, null));
    }

    // ===== resolveSimplePredicate 各 Op null 值路径 =====

    @Test
    void resolveSimplePredicate_like_nullValue() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        ConditionNode.SimpleNode node = new ConditionNode.SimpleNode("name", null, ConditionNode.Op.LIKE);
        assertThrows(IllegalArgumentException.class, () -> PredicateHelper.resolveSimplePredicate(root, node, cb));
    }

    @Test
    void resolveSimplePredicate_notLike_nullValue() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        ConditionNode.SimpleNode node = new ConditionNode.SimpleNode("name", null, ConditionNode.Op.NOT_LIKE);
        assertThrows(IllegalArgumentException.class, () -> PredicateHelper.resolveSimplePredicate(root, node, cb));
    }

    @Test
    void resolveSimplePredicate_eqIgnoreCase_nullValue() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        ConditionNode.SimpleNode node = new ConditionNode.SimpleNode("name", null, ConditionNode.Op.EQ_IGNORE_CASE);
        Predicate p = PredicateHelper.resolveSimplePredicate(root, node, cb);
        assertNotNull(p);
    }

    @Test
    void resolveSimplePredicate_neIgnoreCase_nullValue() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        ConditionNode.SimpleNode node = new ConditionNode.SimpleNode("name", null, ConditionNode.Op.NE_IGNORE_CASE);
        Predicate p = PredicateHelper.resolveSimplePredicate(root, node, cb);
        assertNotNull(p);
    }

    @Test
    void resolveSimplePredicate_likeIgnoreCase_nullValue() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        ConditionNode.SimpleNode node = new ConditionNode.SimpleNode("name", null, ConditionNode.Op.LIKE_IGNORE_CASE);
        assertThrows(IllegalArgumentException.class, () -> PredicateHelper.resolveSimplePredicate(root, node, cb));
    }

    // ===== resolveSimplePredicate IN/NOT_IN 边界 =====

    @Test
    void resolveSimplePredicate_in_nullValue_throws() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        ConditionNode.SimpleNode node = new ConditionNode.SimpleNode("status", null, ConditionNode.Op.IN);
        assertThrows(IllegalArgumentException.class, () -> PredicateHelper.resolveSimplePredicate(root, node, cb));
    }

    @Test
    void resolveSimplePredicate_in_emptyCollection_returnsDisjunction() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        ConditionNode.SimpleNode node = new ConditionNode.SimpleNode("status", List.of(), ConditionNode.Op.IN);
        Predicate p = PredicateHelper.resolveSimplePredicate(root, node, cb);
        assertNotNull(p);
    }

    @Test
    void resolveSimplePredicate_notIn_nullValue_throws() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        ConditionNode.SimpleNode node = new ConditionNode.SimpleNode("status", null, ConditionNode.Op.NOT_IN);
        assertThrows(IllegalArgumentException.class, () -> PredicateHelper.resolveSimplePredicate(root, node, cb));
    }

    @Test
    void resolveSimplePredicate_notIn_emptyCollection_returnsConjunction() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        ConditionNode.SimpleNode node = new ConditionNode.SimpleNode("status", List.of(), ConditionNode.Op.NOT_IN);
        Predicate p = PredicateHelper.resolveSimplePredicate(root, node, cb);
        assertNotNull(p);
    }

    // ===== resolveSimplePredicate BETWEEN/NOT_BETWEEN 异常 =====

    @Test
    void resolveSimplePredicate_between_invalidLength_throws() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        ConditionNode.SimpleNode node =
            new ConditionNode.SimpleNode("status", new Comparable[] {1}, ConditionNode.Op.BETWEEN);
        assertThrows(IllegalArgumentException.class, () -> PredicateHelper.resolveSimplePredicate(root, node, cb));
    }

    @Test
    void resolveSimplePredicate_notBetween_invalidLength_throws() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        ConditionNode.SimpleNode node =
            new ConditionNode.SimpleNode("status", new Comparable[] {1}, ConditionNode.Op.NOT_BETWEEN);
        assertThrows(IllegalArgumentException.class, () -> PredicateHelper.resolveSimplePredicate(root, node, cb));
    }

    // ---- validateRange edge cases ----

    @Test
    void validateRange_nanValues_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> PredicateHelper.validateRange(Double.NaN, Double.valueOf(1.0)));
    }

    @Test
    void validateRange_crossTypeNan_throws() {
        // When types differ (Integer vs Double), the BigDecimal fallback path is taken
        // BigDecimal("NaN") throws NumberFormatException → NaN check triggers
        assertThrows(IllegalArgumentException.class,
            () -> PredicateHelper.validateRange(Integer.valueOf(1), Double.NaN));
    }

    @Test
    void validateRange_equalBounds_succeeds() {
        assertDoesNotThrow(() -> PredicateHelper.validateRange(5, 5));
    }

    @Test
    void validateRange_crossNumeric_succeeds() {
        assertDoesNotThrow(() -> PredicateHelper.validateRange(1, 2L));
    }

    // ---- escapeLikeWildcards edge cases ----

    @Test
    void escapeLikeWildcards_unicodeInput() {
        String result = PredicateHelper.escapeLikeWildcards("日本語_test%");
        assertEquals("日本語\\_test\\%", result);
    }

    @Test
    void escapeLikeWildcards_emptyString() {
        assertEquals("", PredicateHelper.escapeLikeWildcards(""));
    }

    @Test
    void escapeLikeWildcards_onlyBackslashes() {
        assertEquals("\\\\\\\\", PredicateHelper.escapeLikeWildcards("\\\\"));
    }

    // ---- startsWith/endsWith/contains with null ----

    @Test
    void startsWith_nullValue_returnsDisjunction() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        Predicate p = PredicateHelper.startsWith(root, "name", null, cb);
        assertNotNull(p);
        // disjunction matches zero rows
        cq.where(p);
        List<TestEntity> result = em.createQuery(cq).getResultList();
        assertTrue(result.isEmpty());
    }

    @Test
    void endsWith_nullValue_returnsDisjunction() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        Predicate p = PredicateHelper.endsWith(root, "name", null, cb);
        assertNotNull(p);
        cq.where(p);
        List<TestEntity> result = em.createQuery(cq).getResultList();
        assertTrue(result.isEmpty());
    }

    @Test
    void contains_nullValue_returnsDisjunction() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        Predicate p = PredicateHelper.contains(root, "name", null, cb);
        assertNotNull(p);
        cq.where(p);
        List<TestEntity> result = em.createQuery(cq).getResultList();
        assertTrue(result.isEmpty());
    }

    // ---- neIgnoreCase direct call ----

    @Test
    void neIgnoreCase_directCall_succeeds() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        Predicate p = PredicateHelper.neIgnoreCase(root, "name", "test", cb);
        assertNotNull(p);
    }

    @Test
    void neIgnoreCase_nullValue_returnsIsNotNull() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        Predicate p = PredicateHelper.neIgnoreCase(root, "name", null, cb);
        assertNotNull(p);
    }

    // ---- like/notLike with empty string ----

    @Test
    void like_emptyString_succeeds() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        Predicate p = PredicateHelper.like(root, "name", "", cb);
        assertNotNull(p);
    }

    @Test
    void notLike_emptyString_succeeds() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        Predicate p = PredicateHelper.notLike(root, "name", "", cb);
        assertNotNull(p);
    }

    // ---- in/notIn with single element ----

    @Test
    void in_singleElement_succeeds() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        Predicate p = PredicateHelper.in(root, "status", new Object[] {1}, cb);
        assertNotNull(p);
    }

    @Test
    void notIn_singleElement_succeeds() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        Predicate p = PredicateHelper.notIn(root, "status", new Object[] {1}, cb);
        assertNotNull(p);
    }

    // ---- contains with empty string ----

    @Test
    void contains_emptyString_succeeds() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        Predicate p = PredicateHelper.contains(root, "name", "", cb);
        assertNotNull(p);
    }
}
