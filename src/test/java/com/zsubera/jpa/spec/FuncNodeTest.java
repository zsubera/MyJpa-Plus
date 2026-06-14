package com.zsubera.jpa.spec;

import static org.junit.jupiter.api.Assertions.*;

import com.zsubera.jpa.exception.SecurityViolationException;
import org.junit.jupiter.api.Test;

class FuncNodeTest {

    @Test
    void of_validBooleanFunction_createsNode() {
        ConditionNode.FuncNode node = ConditionNode.FuncNode.of("COALESCE", new Object[] {"field", "default"});
        assertNotNull(node);
        assertEquals("COALESCE", node.functionName);
        assertArrayEquals(new Object[] {"field", "default"}, node.params);
    }

    @Test
    void of_caseInsensitive_createsNode() {
        ConditionNode.FuncNode node = ConditionNode.FuncNode.of("coalesce", new Object[] {"field"});
        assertNotNull(node);
        assertEquals("coalesce", node.functionName);
    }

    @Test
    void of_nullFunctionName_throws() {
        assertThrows(IllegalArgumentException.class, () -> ConditionNode.FuncNode.of(null, new Object[] {}));
    }

    @Test
    void of_nullParams_throws() {
        assertThrows(IllegalArgumentException.class, () -> ConditionNode.FuncNode.of("COALESCE", null));
    }

    @Test
    void of_nonWhitelistedFunction_throwsSecurityException() {
        assertThrows(SecurityViolationException.class,
            () -> ConditionNode.FuncNode.of("PG_SLEEP", new Object[] {"field", 1}));
    }

    @Test
    void of_nonBooleanFunction_throwsSecurityException() {
        assertThrows(SecurityViolationException.class,
            () -> ConditionNode.FuncNode.of("LENGTH", new Object[] {"field"}));
    }

    @Test
    void of_allBooleanFunctions_work() {
        for (String fn : ConditionBuilder.BOOLEAN_FUNCTION_NAMES) {
            ConditionNode.FuncNode node = ConditionNode.FuncNode.of(fn, new Object[] {"field"});
            assertNotNull(node, "Function " + fn + " should be accepted");
        }
    }

    @Test
    void constructor_nullFunctionName_throws() {
        assertThrows(IllegalArgumentException.class, () -> new ConditionNode.FuncNode(null, new Object[] {}));
    }

    @Test
    void constructor_nullParams_throws() {
        assertThrows(IllegalArgumentException.class, () -> new ConditionNode.FuncNode("COALESCE", null));
    }

    @Test
    void constructor_clonesParams() {
        Object[] original = new Object[] {"field", "value"};
        ConditionNode.FuncNode node = new ConditionNode.FuncNode("COALESCE", original);
        original[0] = "modified";
        assertEquals("field", node.params[0], "Params should be defensively copied");
    }

    @Test
    void toString_masksStringParams() {
        ConditionNode.FuncNode node = ConditionNode.FuncNode.of("COALESCE", new Object[] {"password", "secret"});
        String str = node.toString();
        assertTrue(str.contains("FuncNode[COALESCE("));
        assertTrue(str.contains("String[***]"));
        assertFalse(str.contains("password"));
        assertFalse(str.contains("secret"));
    }

    @Test
    void toString_masksNullParams() {
        ConditionNode.FuncNode node = ConditionNode.FuncNode.of("COALESCE", new Object[] {null});
        String str = node.toString();
        assertTrue(str.contains("null"));
    }

    @Test
    void toString_masksNonStringParams() {
        ConditionNode.FuncNode node = ConditionNode.FuncNode.of("COALESCE", new Object[] {42, 3.14});
        String str = node.toString();
        assertTrue(str.contains("Integer[***]"));
        assertTrue(str.contains("Double[***]"));
    }

    @Test
    void toString_emptyParams() {
        ConditionNode.FuncNode node = ConditionNode.FuncNode.of("COALESCE", new Object[] {});
        String str = node.toString();
        assertEquals("FuncNode[COALESCE()]", str);
    }
}
