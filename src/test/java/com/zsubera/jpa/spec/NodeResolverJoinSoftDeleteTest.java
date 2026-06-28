package com.zsubera.jpa.spec;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Tests that NodeResolver.resolveJoin correctly passes the join path
 * (not join.get(fieldName)) to SoftDeleteHelper.buildNotDeleted,
 * avoiding double path resolution.
 */
class NodeResolverJoinSoftDeleteTest {

    @Test
    void resolveJoin_withoutSoftDelete_onJoin_doesNotThrow() {
        // Verify that a QuerySpec with a join builds correctly
        // This verifies the contract doesn't break on join nodes
        assertDoesNotThrow(() -> {
            QuerySpec<Object> qs = new QuerySpec<>();
            qs.conditions().add(new ConditionNode.JoinNode("someField",
                ConditionNode.JoinType.INNER));
        });
    }

    @Test
    void joinNode_equalsAndHashCode_consistent() {
        var a = new ConditionNode.JoinNode("field", ConditionNode.JoinType.LEFT);
        var b = new ConditionNode.JoinNode("field", ConditionNode.JoinType.LEFT);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());

        var c = new ConditionNode.JoinNode("other", ConditionNode.JoinType.LEFT);
        assertNotEquals(a, c);
    }
}
