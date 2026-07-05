package com.zsubera.jpa.spec;

import static org.junit.jupiter.api.Assertions.*;

import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

class QueryCompositionSupportTest {

    interface Dummy {
        String getName();

        String getStatus();
    }

    @SuppressWarnings("unchecked")
    @Test
    void or_nullConsumer_throws() {
        QuerySpec<Dummy> qs = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class, () -> qs.or((Consumer<OrGroup<Dummy>>)null));
    }

    @Test
    void not_nullConsumer_throws() {
        QuerySpec<Dummy> qs = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class, () -> qs.not(null));
    }

    @Test
    void then_nullOther_returnsSameInstance() {
        QuerySpec<Dummy> qs = new QuerySpec<>();
        QuerySpec<Dummy> result = qs.then(null);
        assertSame(qs, result);
    }

    @Test
    void then_mergesConditions() {
        QuerySpec<Dummy> qs1 = new QuerySpec<>();
        qs1.eq(Dummy::getName, "Alice");

        QuerySpec<Dummy> qs2 = new QuerySpec<>();
        qs2.eq(Dummy::getStatus, "ACTIVE");

        qs1.then(qs2);

        assertFalse(qs1.getConditions().isEmpty());
    }

    @Test
    void then_mergesDistinct() {
        QuerySpec<Dummy> qs1 = new QuerySpec<>();
        QuerySpec<Dummy> qs2 = new QuerySpec<>();
        qs2.setDistinct(true);

        qs1.then(qs2);
        assertTrue(qs1.isDistinct());
    }

    @Test
    void then_mergesGroupBy() {
        QuerySpec<Dummy> qs1 = new QuerySpec<>();
        QuerySpec<Dummy> qs2 = new QuerySpec<>();

        qs1.then(qs2);
        assertNotNull(qs1.getGroupByFields());
    }

    @Test
    void toSpecification_withExternal_andComposed() {
        QuerySpec<Dummy> qs = new QuerySpec<>();
        qs.eq(Dummy::getName, "Alice");
        Specification<Dummy> external = (root, query, cb) -> cb.conjunction();

        Specification<Dummy> result = qs.toSpecification(external);
        assertNotNull(result);
    }

    @Test
    void toSpecification_withNullExternal_returnsSelf() {
        QuerySpec<Dummy> qs = new QuerySpec<>();
        Specification<Dummy> result = qs.toSpecification(null);
        assertSame(qs, result);
    }

    @Test
    void orCombine_nullOther_returnsSelf() {
        QuerySpec<Dummy> qs = new QuerySpec<>();
        Specification<Dummy> result = qs.orCombine(null);
        assertSame(qs, result);
    }

    @Test
    void toSpecification_withNullExternal_unclosedOrGroup_throws() {
        QuerySpec<Dummy> qs = new QuerySpec<>();
        // Manually push to groupStack to simulate an unclosed or() group
        java.util.Deque<java.util.List<ConditionNode>> stack = qs.getGroupStack();
        stack.push(new java.util.ArrayList<>());
        try {
            assertThrows(com.zsubera.jpa.exception.QueryBuildException.class,
                () -> qs.toSpecification((Specification<Dummy>)null));
        } finally {
            stack.pop();
        }
    }

    @Test
    void toSpecification_withNullExternal_cleanState_succeeds() {
        QuerySpec<Dummy> qs = new QuerySpec<>();
        // or() uses try-finally to pop the groupStack, so stack is clean after or()
        qs.or(b -> b.eq(Dummy::getName, "Alice"));
        Specification<Dummy> result = qs.toSpecification(null);
        assertNotNull(result);
    }
}
