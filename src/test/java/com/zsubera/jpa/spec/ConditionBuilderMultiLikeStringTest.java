package com.zsubera.jpa.spec;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pure unit tests for {@link ConditionBuilder#multiLike(String, String...)} — string field name variant.
 * No database required; validates input sanitization and SQL injection prevention.
 */
class ConditionBuilderMultiLikeStringTest {

    @Test
    void nullKeywordThrows() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class, () -> qs.multiLike(null, "name"));
    }

    @Test
    void nullFieldNamesArrayThrows() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class, () -> qs.multiLike("test", (String[])null));
    }

    @Test
    void emptyFieldNamesArrayThrows() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class, () -> qs.multiLike("test", new String[] {}));
    }

    @Test
    void sqlInjectionSemicolonThrows() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class, () -> qs.multiLike("test", "name;DROP TABLE"));
    }

    @Test
    void sqlInjectionUnionThrows() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class, () -> qs.multiLike("test", "name UNION SELECT"));
    }

    @Test
    void sqlInjectionSingleQuoteThrows() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class, () -> qs.multiLike("test", "name' OR '1'='1"));
    }

    @Test
    void nullElementInFieldNamesThrows() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class, () -> qs.multiLike("test", "name", null));
    }

    @Test
    void emptyFieldNamesElementThrows() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class, () -> qs.multiLike("test", ""));
    }

    @Test
    void validSingleFieldNameAddsNode() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.multiLike("keyword", "name");
        List<ConditionNode> conditions = qs.conditions();
        assertEquals(1, conditions.size());
        assertInstanceOf(ConditionNode.MultiLikeNode.class, conditions.get(0));
    }

    @Test
    void validMultipleFieldNamesAddsNode() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.multiLike("keyword", "name", "email");
        List<ConditionNode> conditions = qs.conditions();
        assertEquals(1, conditions.size());
        ConditionNode.MultiLikeNode node = (ConditionNode.MultiLikeNode)conditions.get(0);
        assertEquals(2, node.fieldNames.length);
        assertEquals("name", node.fieldNames[0]);
        assertEquals("email", node.fieldNames[1]);
    }

    @Test
    void emptyKeywordDoesNotAddNode() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.multiLike("", "name");
        assertTrue(qs.conditions().isEmpty());
    }

    @Test
    void nestedDotFieldValidatesEachSegment() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertDoesNotThrow(() -> qs.multiLike("test", "address.city"));
    }

    @Test
    void nestedFieldWithInvalidSegmentThrows() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class, () -> qs.multiLike("test", "address;DROP.city"));
    }

    @Test
    void conditionalTrueAddsNode() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.multiLike(true, "hel", "name");
        assertEquals(1, qs.conditions().size());
    }

    @Test
    void conditionalFalseSkipsNode() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.multiLike(false, "hel", "name");
        assertTrue(qs.conditions().isEmpty());
    }

    @Test
    void multiLikeNodeToStringMasksKeyword() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.multiLike("secret", "name");
        ConditionNode.MultiLikeNode node = (ConditionNode.MultiLikeNode)qs.conditions().get(0);
        String str = node.toString();
        assertTrue(str.contains("***("));
        assertFalse(str.contains("secret"));
    }

    @Test
    void multiLikeNodeDefensiveCopy() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.multiLike("kw", "a", "b");
        ConditionNode.MultiLikeNode node = (ConditionNode.MultiLikeNode)qs.conditions().get(0);
        // Verify fieldNames are stored (defensive copy tested via NodeInnerClassTest)
        assertEquals(2, node.fieldNames.length);
    }
}
