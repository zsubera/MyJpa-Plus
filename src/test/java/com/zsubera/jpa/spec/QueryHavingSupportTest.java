package com.zsubera.jpa.spec;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QueryHavingSupportTest {

    private List<BiFunction<Path<Object>, CriteriaBuilder, Predicate>> havingConditions;
    private QuerySpec<Object> parentSpec;
    private QueryHavingSupport<Object> havingSupport;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        havingConditions = new ArrayList<>();
        parentSpec = new QuerySpec<>();
        havingSupport = new QueryHavingSupport<>(parentSpec, havingConditions);
    }

    // --- having(BiFunction) ---

    @Test
    void having_nullBiFunction_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
            () -> havingSupport.having((BiFunction<Path<Object>, CriteriaBuilder, Predicate>)null));
    }

    @Test
    void having_biFunction_addsCondition() {
        BiFunction<Path<Object>, CriteriaBuilder, Predicate> condition = (root, cb) -> null;
        QuerySpec<Object> result = havingSupport.having(condition);
        assertSame(parentSpec, result);
        assertEquals(1, havingSupport.size());
        assertFalse(havingSupport.isEmpty());
    }

    @Test
    void having_biFunction_multipleConditions() {
        havingSupport.having((root, cb) -> null);
        havingSupport.having((root, cb) -> null);
        assertEquals(2, havingSupport.size());
    }

    // --- having(Function) ---

    @Test
    void having_nullFunction_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
            () -> havingSupport.having((Function<Path<Object>, Predicate>)null));
    }

    @Test
    void having_function_addsCondition() {
        Function<Path<Object>, Predicate> condition = root -> null;
        QuerySpec<Object> result = havingSupport.having(condition);
        assertSame(parentSpec, result);
        assertEquals(1, havingSupport.size());
    }

    @Test
    void having_mixedBiFunctionAndFunction() {
        havingSupport.having((BiFunction<Path<Object>, CriteriaBuilder, Predicate>)(root, cb) -> null);
        havingSupport.having((Function<Path<Object>, Predicate>)root -> null);
        assertEquals(2, havingSupport.size());
    }

    // --- addHavingCondition ---

    @Test
    void addHavingCondition_nullField_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
            () -> havingSupport.addHavingCondition(null, ConditionNode.Op.GT, "value", 10, (r, cb) -> null));
    }

    @Test
    void addHavingCondition_nullOp_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
            () -> havingSupport.addHavingCondition(root -> "age", null, "value", 10, (r, cb) -> null));
    }

    @Test
    void addHavingCondition_nullValue_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
            () -> havingSupport.addHavingCondition(root -> "age", ConditionNode.Op.GT, "value", null, (r, cb) -> null));
    }

    @Test
    void addHavingCondition_unsupportedOp_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
            () -> havingSupport.addHavingCondition(root -> "age", ConditionNode.Op.LIKE, "value", 10, (r, cb) -> null));
    }

    @Test
    void addHavingCondition_validParams_addsCondition() {
        QuerySpec<Object> result =
            havingSupport.addHavingCondition(root -> "age", ConditionNode.Op.GT, "value", 10, (r, cb) -> null);
        assertSame(parentSpec, result);
        assertEquals(1, havingSupport.size());
    }

    @Test
    void addHavingCondition_allSupportedOps() {
        for (ConditionNode.Op op : new ConditionNode.Op[] {ConditionNode.Op.GT, ConditionNode.Op.GE,
            ConditionNode.Op.LT, ConditionNode.Op.LE, ConditionNode.Op.EQ, ConditionNode.Op.NE}) {
            havingSupport.addHavingCondition(root -> "field", op, "value", 10, (r, cb) -> null);
        }
        assertEquals(6, havingSupport.size());
    }

    @Test
    void addHavingCondition_unsupportedOps_throws() {
        for (ConditionNode.Op op : new ConditionNode.Op[] {ConditionNode.Op.LIKE, ConditionNode.Op.NOT_LIKE,
            ConditionNode.Op.IN, ConditionNode.Op.NOT_IN, ConditionNode.Op.IS_NULL, ConditionNode.Op.IS_NOT_NULL}) {
            assertThrows(IllegalArgumentException.class,
                () -> havingSupport.addHavingCondition(root -> "field", op, "value", 10, (r, cb) -> null));
        }
    }

    // --- havingCount validation (null checks only, no LambdaUtils) ---

    @Test
    void havingCount_nullField_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> havingSupport.havingCount(null, ConditionNode.Op.GT, 5));
    }

    @Test
    void havingCount_nullOp_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> havingSupport.havingCount(root -> "count", null, 5));
    }

    @Test
    void havingCount_unsupportedOp_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
            () -> havingSupport.havingCount(root -> "count", ConditionNode.Op.LIKE, 5));
    }

    @Test
    void havingCount_inOp_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
            () -> havingSupport.havingCount(root -> "count", ConditionNode.Op.IN, 5));
    }

    @Test
    void havingCount_isNullOp_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
            () -> havingSupport.havingCount(root -> "count", ConditionNode.Op.IS_NULL, 5));
    }

    @Test
    void havingCount_isNotNullOp_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
            () -> havingSupport.havingCount(root -> "count", ConditionNode.Op.IS_NOT_NULL, 5));
    }

    @Test
    void havingCount_notLikeOp_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
            () -> havingSupport.havingCount(root -> "count", ConditionNode.Op.NOT_LIKE, 5));
    }

    @Test
    void havingCount_notInOp_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
            () -> havingSupport.havingCount(root -> "count", ConditionNode.Op.NOT_IN, 5));
    }

    // --- havingMax validation ---

    @Test
    void havingMax_nullField_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
            () -> havingSupport.havingMax(null, ConditionNode.Op.GT, "2024-01-01"));
    }

    @Test
    void havingMax_nullOp_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> havingSupport.havingMax(root -> "date", null, "2024-01-01"));
    }

    // --- havingMin validation ---

    @Test
    void havingMin_nullField_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> havingSupport.havingMin(null, ConditionNode.Op.GT, 10));
    }

    @Test
    void havingMin_nullOp_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> havingSupport.havingMin(root -> "price", null, 10));
    }

    // --- applyHaving ---

    @Test
    void applyHaving_emptyConditions_doesNothing() {
        havingSupport.applyHaving(null, new ArrayList<>(), null, null);
        assertTrue(havingSupport.isEmpty());
    }

    @Test
    void applyHaving_conditionsButNoGroupBy_throwsIllegalState() {
        havingSupport.having((root, cb) -> null);
        List<String> emptyGroupBy = new ArrayList<>();
        assertThrows(IllegalStateException.class, () -> havingSupport.applyHaving(null, emptyGroupBy, null, null));
    }

    // --- size/isEmpty ---

    @Test
    void size_returnsConditionCount() {
        assertEquals(0, havingSupport.size());
        havingSupport.having((BiFunction<Path<Object>, CriteriaBuilder, Predicate>)(root, cb) -> null);
        assertEquals(1, havingSupport.size());
        havingSupport.having((BiFunction<Path<Object>, CriteriaBuilder, Predicate>)(root, cb) -> null);
        assertEquals(2, havingSupport.size());
    }

    @Test
    void isEmpty_initiallyTrue() {
        assertTrue(havingSupport.isEmpty());
    }

    @Test
    void isEmpty_falseAfterAddingCondition() {
        havingSupport.having((BiFunction<Path<Object>, CriteriaBuilder, Predicate>)(root, cb) -> null);
        assertFalse(havingSupport.isEmpty());
    }

    // --- addAll ---

    @Test
    void addAll_mergesConditions() {
        havingSupport.having((BiFunction<Path<Object>, CriteriaBuilder, Predicate>)(root, cb) -> null);
        assertEquals(1, havingSupport.size());

        List<BiFunction<Path<Object>, CriteriaBuilder, Predicate>> other = new ArrayList<>();
        other.add((root, cb) -> null);
        other.add((root, cb) -> null);
        havingSupport.addAll(other);

        assertEquals(3, havingSupport.size());
    }

    @Test
    void addAll_emptyList_noChange() {
        havingSupport.having((BiFunction<Path<Object>, CriteriaBuilder, Predicate>)(root, cb) -> null);
        havingSupport.addAll(new ArrayList<>());
        assertEquals(1, havingSupport.size());
    }
}
