package com.zsubera.jpa.spec;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Data integrity tests for {@link ConditionNode.SimpleNode} defensive copy and toString masking.
 */
class SimpleNodeDataIntegrityTest {

    @Test
    void simpleNodeNullFieldNameThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new ConditionNode.SimpleNode(null, "value", ConditionNode.Op.EQ));
    }

    @Test
    void simpleNodeNullOpThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new ConditionNode.SimpleNode("field", "value", null));
    }

    @Test
    void simpleNodeDefensiveCopyComparableArray() {
        Comparable<?>[] original = new Comparable[]{1, 5};
        ConditionNode.SimpleNode node = new ConditionNode.SimpleNode("field", original, ConditionNode.Op.BETWEEN);
        original[0] = 999;
        // The internal value should not be affected
        Comparable<?>[] internal = (Comparable<?>[]) node.value;
        assertEquals(1, internal[0]);
    }

    @Test
    void simpleNodeDefensiveCopyObjectArray() {
        Object[] original = new Object[]{"a", "b", "c"};
        ConditionNode.SimpleNode node = new ConditionNode.SimpleNode("field", original, ConditionNode.Op.IN);
        original[0] = "MUTATED";
        Object[] internal = (Object[]) node.value;
        assertEquals("a", internal[0]);
    }

    @Test
    void simpleNodeDefensiveCopyCollection() {
        List<String> original = new ArrayList<>(Arrays.asList("x", "y"));
        ConditionNode.SimpleNode node = new ConditionNode.SimpleNode("field", original, ConditionNode.Op.IN);
        original.add("z");
        Collection<?> internal = (Collection<?>) node.value;
        assertEquals(2, internal.size());
    }

    @Test
    void simpleNodeToStringMasksStringValue() {
        ConditionNode.SimpleNode node =
            new ConditionNode.SimpleNode("password", "secret123", ConditionNode.Op.EQ);
        String str = node.toString();
        assertTrue(str.contains("***("));
        assertTrue(str.contains("chars)"));
        assertFalse(str.contains("secret123"));
    }

    @Test
    void simpleNodeToStringShowsNullLiteral() {
        ConditionNode.SimpleNode node = new ConditionNode.SimpleNode("field", null, ConditionNode.Op.IS_NULL);
        assertTrue(node.toString().contains("null"));
    }

    @Test
    void simpleNodeToStringShowsArraySize() {
        Object[] arr = new Object[]{1, 2, 3};
        ConditionNode.SimpleNode node = new ConditionNode.SimpleNode("field", arr, ConditionNode.Op.IN);
        String str = node.toString();
        assertTrue(str.contains("ARRAY[3 items]"));
    }

    @Test
    void simpleNodeToStringShowsCollectionSize() {
        List<String> list = Arrays.asList("a", "b");
        ConditionNode.SimpleNode node = new ConditionNode.SimpleNode("field", list, ConditionNode.Op.IN);
        String str = node.toString();
        assertTrue(str.contains("IN[2 items]"));
    }

    @Test
    void simpleNodeToStringShowsClassNameForNonString() {
        ConditionNode.SimpleNode node =
            new ConditionNode.SimpleNode("field", Integer.valueOf(42), ConditionNode.Op.EQ);
        String str = node.toString();
        assertTrue(str.contains("Integer[***]"));
    }

    @Test
    void simpleNodeEscapeCharPreserved() {
        ConditionNode.SimpleNode node =
            new ConditionNode.SimpleNode("field", "%test%", ConditionNode.Op.LIKE, '\\');
        assertEquals('\\', node.escapeChar);
    }

    @Test
    void simpleNodeDefaultEscapeCharIsNul() {
        ConditionNode.SimpleNode node = new ConditionNode.SimpleNode("field", "val", ConditionNode.Op.EQ);
        assertEquals('\0', node.escapeChar);
    }

    @Test
    void simpleNodeCopyOnStringDoesNotShareReference() {
        StringBuilder sb = new StringBuilder("original");
        ConditionNode.SimpleNode node = new ConditionNode.SimpleNode("field", sb.toString(), ConditionNode.Op.EQ);
        sb.append("_mutated");
        // String is immutable in Java, but verify the stored value is correct
        assertEquals("original", node.value);
    }
}
