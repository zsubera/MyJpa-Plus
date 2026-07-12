package com.zsubera.jpa.spec;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for ConditionNode inner class constructors, null validation, and toString.
 * Covers OrNode, AndNode, MultiLikeNode, CollectionNode, ExistsNode, InSubQueryNode,
 * RawNode, NegateNode, and OrderNode.
 */
class NodeInnerClassTest {

    // ---- OrNode ----

    @Test
    void orNodeAddNodeAndAccess() {
        ConditionNode.OrNode or = new ConditionNode.OrNode();
        ConditionNode.SimpleNode n1 = new ConditionNode.SimpleNode("a", 1, ConditionNode.Op.EQ);
        ConditionNode.SimpleNode n2 = new ConditionNode.SimpleNode("b", 2, ConditionNode.Op.EQ);
        or.addNode(n1);
        or.addNode(n2);
        assertEquals(2, or.nodes().size());
        assertTrue(or.nodes().contains(n1));
        assertTrue(or.nodes().contains(n2));
    }

    @Test
    void orNodeNullNodeThrows() {
        ConditionNode.OrNode or = new ConditionNode.OrNode();
        assertThrows(IllegalArgumentException.class, () -> or.addNode(null));
    }

    @Test
    void orNodeNodesReturnsUnmodifiableList() {
        ConditionNode.OrNode or = new ConditionNode.OrNode();
        or.addNode(new ConditionNode.SimpleNode("a", 1, ConditionNode.Op.EQ));
        List<ConditionNode> nodes = or.nodes();
        assertThrows(UnsupportedOperationException.class, () -> nodes.add(null));
    }

    @Test
    void orNodeToStringContainsOrNode() {
        ConditionNode.OrNode or = new ConditionNode.OrNode();
        or.addNode(new ConditionNode.SimpleNode("a", 1, ConditionNode.Op.EQ));
        assertTrue(or.toString().contains("OrNode"));
    }

    // ---- AndNode ----

    @Test
    void andNodeAddNodeAndAccess() {
        ConditionNode.AndNode and = new ConditionNode.AndNode();
        ConditionNode.SimpleNode n1 = new ConditionNode.SimpleNode("a", 1, ConditionNode.Op.EQ);
        and.addNode(n1);
        assertEquals(1, and.nodes().size());
    }

    @Test
    void andNodeNullNodeThrows() {
        ConditionNode.AndNode and = new ConditionNode.AndNode();
        assertThrows(IllegalArgumentException.class, () -> and.addNode(null));
    }

    @Test
    void andNodeNodesReturnsUnmodifiableList() {
        ConditionNode.AndNode and = new ConditionNode.AndNode();
        and.addNode(new ConditionNode.SimpleNode("a", 1, ConditionNode.Op.EQ));
        assertThrows(UnsupportedOperationException.class, () -> and.nodes().add(null));
    }

    // ---- MultiLikeNode ----

    @Test
    void multiLikeNodeValidConstruction() {
        ConditionNode.MultiLikeNode node = new ConditionNode.MultiLikeNode("keyword", new String[] {"name", "email"});
        assertEquals("keyword", node.keyword);
        assertEquals(2, node.fieldNames.length);
    }

    @Test
    void multiLikeNodeNullKeywordThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new ConditionNode.MultiLikeNode(null, new String[] {"name"}));
    }

    @Test
    void multiLikeNodeNullFieldNamesThrows() {
        assertThrows(IllegalArgumentException.class, () -> new ConditionNode.MultiLikeNode("keyword", null));
    }

    @Test
    void multiLikeNodeEmptyFieldNamesThrows() {
        assertThrows(IllegalArgumentException.class, () -> new ConditionNode.MultiLikeNode("keyword", new String[] {}));
    }

    @Test
    void multiLikeNodeDefensiveCopy() {
        String[] original = new String[] {"name"};
        ConditionNode.MultiLikeNode node = new ConditionNode.MultiLikeNode("keyword", original);
        original[0] = "MUTATED";
        assertEquals("name", node.fieldNames[0]);
    }

    @Test
    void multiLikeNodeToStringMasksKeyword() {
        ConditionNode.MultiLikeNode node = new ConditionNode.MultiLikeNode("secret", new String[] {"name"});
        String str = node.toString();
        assertTrue(str.contains("***("));
        assertFalse(str.contains("secret"));
    }

    // ---- CollectionNode ----

    @Test
    void collectionNodeValidConstruction() {
        ConditionNode.CollectionNode node =
            new ConditionNode.CollectionNode("items", ConditionNode.CollectionOp.IS_EMPTY);
        assertEquals("items", node.fieldName());
        assertEquals(ConditionNode.CollectionOp.IS_EMPTY, node.op());
    }

    @Test
    void collectionNodeNullFieldNameThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new ConditionNode.CollectionNode(null, ConditionNode.CollectionOp.IS_EMPTY));
    }

    @Test
    void collectionNodeNullOpThrows() {
        assertThrows(IllegalArgumentException.class, () -> new ConditionNode.CollectionNode("items", null));
    }

    // ---- ExistsNode ----

    @Test
    void existsNodeValidConstruction() {
        ConditionNode.ExistsNode<String> node = new ConditionNode.ExistsNode<>(String.class, sub -> {
        }, false);
        assertEquals(String.class, node.subEntity);
        assertFalse(node.negate);
    }

    @Test
    void existsNodeNullSubEntityThrows() {
        assertThrows(IllegalArgumentException.class, () -> new ConditionNode.ExistsNode<>(null, sub -> {
        }, false));
    }

    @Test
    void existsNodeNullConfigThrows() {
        assertThrows(IllegalArgumentException.class, () -> new ConditionNode.ExistsNode<>(String.class, null, false));
    }

    @Test
    void existsNodeToStringShowsNotWhenNegated() {
        ConditionNode.ExistsNode<String> node = new ConditionNode.ExistsNode<>(String.class, sub -> {
        }, true);
        assertTrue(node.toString().contains("NOT"));
    }

    // ---- InSubQueryNode ----

    @Test
    void inSubQueryNodeValidConstruction() {
        ConditionNode.InSubQueryNode<String> node = new ConditionNode.InSubQueryNode<>("fieldId", String.class, sub -> {
        }, false);
        assertEquals("fieldId", node.outerFieldName);
        assertEquals(String.class, node.subEntity);
        assertFalse(node.negate);
    }

    @Test
    void inSubQueryNodeNullOuterFieldThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new ConditionNode.InSubQueryNode<>(null, String.class, sub -> {
            }, false));
    }

    @Test
    void inSubQueryNodeNullSubEntityThrows() {
        assertThrows(IllegalArgumentException.class, () -> new ConditionNode.InSubQueryNode<>("field", null, sub -> {
        }, false));
    }

    @Test
    void inSubQueryNodeNullConfigThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new ConditionNode.InSubQueryNode<>("field", String.class, null, false));
    }

    // ---- RawNode ----

    @Test
    void rawNodeValidConstruction() {
        ConditionNode node = ConditionNode.ofInternalPredicate((path, cb) -> cb.conjunction());
        assertNotNull(node);
        assertInstanceOf(ConditionNode.RawNode.class, node);
    }

    @Test
    void rawNodeNullFnThrows() {
        assertThrows(IllegalArgumentException.class, () -> ConditionNode.ofInternalPredicate(null));
    }

    // ---- NegateNode ----

    @Test
    void negateNodeValidConstruction() {
        ConditionNode.SimpleNode inner = new ConditionNode.SimpleNode("a", 1, ConditionNode.Op.EQ);
        ConditionNode.NegateNode node = new ConditionNode.NegateNode(inner);
        assertEquals(inner, node.inner());
    }

    @Test
    void negateNodeNullInnerThrows() {
        assertThrows(IllegalArgumentException.class, () -> new ConditionNode.NegateNode(null));
    }

    @Test
    void negateNodeToStringShowsNegatePrefix() {
        ConditionNode.SimpleNode inner = new ConditionNode.SimpleNode("a", 1, ConditionNode.Op.EQ);
        ConditionNode.NegateNode node = new ConditionNode.NegateNode(inner);
        assertTrue(node.toString().contains("NegateNode"));
    }

    // ---- JoinNode ----

    @Test
    void joinNodeValidConstruction() {
        ConditionNode.JoinNode node = new ConditionNode.JoinNode("parent", ConditionNode.JoinType.INNER);
        assertEquals("parent", node.fieldName);
        assertEquals(ConditionNode.JoinType.INNER, node.joinType);
        assertTrue(node.innerConditions.isEmpty());
    }

    @Test
    void joinNodeNullFieldNameThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new ConditionNode.JoinNode(null, ConditionNode.JoinType.INNER));
    }

    @Test
    void joinNodeNullJoinTypeThrows() {
        assertThrows(IllegalArgumentException.class, () -> new ConditionNode.JoinNode("parent", null));
    }

    @Test
    void joinNodeEqualsAndHashCode() {
        ConditionNode.JoinNode n1 = new ConditionNode.JoinNode("parent", ConditionNode.JoinType.INNER);
        ConditionNode.JoinNode n2 = new ConditionNode.JoinNode("parent", ConditionNode.JoinType.INNER);
        ConditionNode.JoinNode n3 = new ConditionNode.JoinNode("other", ConditionNode.JoinType.LEFT);
        assertEquals(n1, n2);
        assertEquals(n1.hashCode(), n2.hashCode());
        assertNotEquals(n1, n3);
    }

    @Test
    void joinNodeNotEqualToOtherType() {
        ConditionNode.JoinNode n = new ConditionNode.JoinNode("parent", ConditionNode.JoinType.INNER);
        assertNotEquals(n, "string");
    }

    @Test
    void joinNodeEqualsSelf() {
        ConditionNode.JoinNode n = new ConditionNode.JoinNode("parent", ConditionNode.JoinType.INNER);
        assertEquals(n, n);
    }

    // ---- OrderNode ----

    @Test
    void orderNodeValidConstruction() {
        ConditionNode.OrderNode node = new ConditionNode.OrderNode("name", true);
        assertEquals("name", node.fieldName);
        assertTrue(node.asc);
    }

    @Test
    void orderNodeNullFieldNameThrows() {
        assertThrows(IllegalArgumentException.class, () -> new ConditionNode.OrderNode(null, true));
    }

    @Test
    void orderNodeToStringAsc() {
        ConditionNode.OrderNode node = new ConditionNode.OrderNode("name", true);
        assertTrue(node.toString().contains("ASC"));
    }

    @Test
    void orderNodeToStringDesc() {
        ConditionNode.OrderNode node = new ConditionNode.OrderNode("name", false);
        assertTrue(node.toString().contains("DESC"));
    }
}
