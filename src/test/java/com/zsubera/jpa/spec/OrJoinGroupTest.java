package com.zsubera.jpa.spec;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class OrJoinGroupTest {

    @Test
    void testConstructorNullRoot() {
        assertThrows(IllegalArgumentException.class, () -> new OrJoinGroup<>(null,
            new ConditionNode.JoinNode("x", ConditionNode.JoinType.INNER), new ConditionNode.OrNode()));
    }

    @Test
    void testConstructorNullJoinNode() {
        assertThrows(IllegalArgumentException.class,
            () -> new OrJoinGroup<>(new QuerySpec<>(), null, new ConditionNode.OrNode()));
    }

    @Test
    void testConstructorNullOrNode() {
        assertThrows(IllegalArgumentException.class, () -> new OrJoinGroup<>(new QuerySpec<>(),
            new ConditionNode.JoinNode("x", ConditionNode.JoinType.INNER), null));
    }

    @Test
    void testConditions() {
        QuerySpec<Object> qs = new QuerySpec<>();
        ConditionNode.JoinNode jn = new ConditionNode.JoinNode("parent", ConditionNode.JoinType.INNER);
        ConditionNode.OrNode on = new ConditionNode.OrNode();
        OrJoinGroup<Object, Object> og = new OrJoinGroup<>(qs, jn, on);
        assertNotNull(og.conditions());
    }
}
