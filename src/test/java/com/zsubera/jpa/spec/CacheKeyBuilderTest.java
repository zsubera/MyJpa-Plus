package com.zsubera.jpa.spec;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link CacheKeyBuilder} — cache key generation logic extracted from QuerySpec.
 */
class CacheKeyBuilderTest {

    private List<ConditionNode> emptyConditions() {
        return new ArrayList<>();
    }

    private Deque<List<ConditionNode>> emptyGroupStack() {
        return new ArrayDeque<>();
    }

    @Test
    void emptyQuery_startsWithQPrefix() {
        String key = CacheKeyBuilder.buildCacheKey(emptyConditions(), emptyConditions(), emptyGroupStack(), false,
            List.of(), List.of(), List.of(), null, null);
        assertTrue(key.startsWith("Q:"), "Cache key should start with Q:");
    }

    @Test
    void simpleNode_eqGeneratesValidKey() {
        List<ConditionNode> conditions = new ArrayList<>();
        conditions.add(new ConditionNode.SimpleNode("status", 1, ConditionNode.Op.EQ));
        String key = CacheKeyBuilder.buildCacheKey(conditions, conditions, emptyGroupStack(), false, List.of(),
            List.of(), List.of(), null, null);
        assertTrue(key.contains("status"), "Key should contain field name");
        assertTrue(key.contains("EQ"), "Key should contain operator");
    }

    @Test
    void distinctFlag_appendedToKey() {
        String withDistinct = CacheKeyBuilder.buildCacheKey(emptyConditions(), emptyConditions(), emptyGroupStack(),
            true, List.of(), List.of(), List.of(), null, null);
        String withoutDistinct = CacheKeyBuilder.buildCacheKey(emptyConditions(), emptyConditions(), emptyGroupStack(),
            false, List.of(), List.of(), List.of(), null, null);
        assertTrue(withDistinct.contains("#DISTINCT"));
        assertFalse(withoutDistinct.contains("#DISTINCT"));
    }

    @Test
    void groupByFields_appendedToKey() {
        String key = CacheKeyBuilder.buildCacheKey(emptyConditions(), emptyConditions(), emptyGroupStack(), false,
            List.of("department", "status"), List.of(), List.of(), null, null);
        assertTrue(key.contains("#GROUPBY(department,status)"));
    }

    @Test
    void orderByNodes_appendedToKey() {
        List<ConditionNode.OrderNode> orders =
            List.of(new ConditionNode.OrderNode("name", true), new ConditionNode.OrderNode("id", false));
        String key = CacheKeyBuilder.buildCacheKey(emptyConditions(), emptyConditions(), emptyGroupStack(), false,
            List.of(), List.of(), orders, null, null);
        assertTrue(key.contains("#ORDERBY(nameASC,idDESC)"));
    }

    @Test
    void timeout_appendedToKey() {
        String key = CacheKeyBuilder.buildCacheKey(emptyConditions(), emptyConditions(), emptyGroupStack(), false,
            List.of(), List.of(), List.of(), 30, null);
        assertTrue(key.contains("#TIMEOUT(30)"));
    }

    @Test
    void lockMode_appendedToKey() {
        String key = CacheKeyBuilder.buildCacheKey(emptyConditions(), emptyConditions(), emptyGroupStack(), false,
            List.of(), List.of(), List.of(), null, LockModeType.PESSIMISTIC_WRITE);
        assertTrue(key.contains("#LOCK(PESSIMISTIC_WRITE)"));
    }

    @Test
    void nestedGroup_showsRootAndNested() {
        List<ConditionNode> rootConditions = new ArrayList<>();
        rootConditions.add(new ConditionNode.SimpleNode("id", 1, ConditionNode.Op.EQ));
        List<ConditionNode> nestedConditions = new ArrayList<>();
        nestedConditions.add(new ConditionNode.SimpleNode("name", "test", ConditionNode.Op.EQ));
        Deque<List<ConditionNode>> stack = new ArrayDeque<>();
        stack.push(nestedConditions);

        String key = CacheKeyBuilder.buildCacheKey(rootConditions, nestedConditions, stack, false, List.of(), List.of(),
            List.of(), null, null);
        assertTrue(key.contains("ROOT("));
        assertTrue(key.contains("#NESTED("));
    }

    @Test
    void hashedValue_stringIncludesLength() {
        List<ConditionNode> conditions = new ArrayList<>();
        conditions.add(new ConditionNode.SimpleNode("name", "hello", ConditionNode.Op.EQ));
        String key = CacheKeyBuilder.buildCacheKey(conditions, conditions, emptyGroupStack(), false, List.of(),
            List.of(), List.of(), null, null);
        assertTrue(key.contains("S[5:"), "String value should include length");
    }

    @Test
    void hashedValue_nullValue() {
        List<ConditionNode> conditions = new ArrayList<>();
        conditions.add(new ConditionNode.SimpleNode("name", null, ConditionNode.Op.IS_NULL));
        String key = CacheKeyBuilder.buildCacheKey(conditions, conditions, emptyGroupStack(), false, List.of(),
            List.of(), List.of(), null, null);
        assertTrue(key.contains("null"), "Null value should be literal 'null'");
    }

    @Test
    void orNode_cacheKeyIncludesORPrefix() {
        ConditionNode.OrNode orNode = new ConditionNode.OrNode();
        orNode.nodes.add(new ConditionNode.SimpleNode("a", 1, ConditionNode.Op.EQ));
        orNode.nodes.add(new ConditionNode.SimpleNode("b", 2, ConditionNode.Op.EQ));
        List<ConditionNode> conditions = new ArrayList<>();
        conditions.add(orNode);

        StringBuilder sb = new StringBuilder();
        CacheKeyBuilder.appendCacheKey(sb, orNode);
        assertTrue(sb.toString().startsWith("OR("));
        assertTrue(sb.toString().contains("a"));
        assertTrue(sb.toString().contains("b"));
    }

    @Test
    void andNode_cacheKeyIncludesANDPrefix() {
        ConditionNode.AndNode andNode = new ConditionNode.AndNode();
        andNode.nodes.add(new ConditionNode.SimpleNode("x", 1, ConditionNode.Op.EQ));
        andNode.nodes.add(new ConditionNode.SimpleNode("y", 2, ConditionNode.Op.EQ));

        StringBuilder sb = new StringBuilder();
        CacheKeyBuilder.appendCacheKey(sb, andNode);
        assertTrue(sb.toString().startsWith("AND("));
    }

    @Test
    void negateNode_cacheKeyIncludesNOTPrefix() {
        ConditionNode.NegateNode negate =
            new ConditionNode.NegateNode(new ConditionNode.SimpleNode("flag", true, ConditionNode.Op.EQ));

        StringBuilder sb = new StringBuilder();
        CacheKeyBuilder.appendCacheKey(sb, negate);
        assertTrue(sb.toString().startsWith("NOT("));
    }

    @Test
    void multiLikeNode_cacheKeyIncludesMULTILIKEPrefix() {
        ConditionNode.MultiLikeNode mln = new ConditionNode.MultiLikeNode("keyword", new String[] {"name", "email"});

        StringBuilder sb = new StringBuilder();
        CacheKeyBuilder.appendCacheKey(sb, mln);
        String key = sb.toString();
        assertTrue(key.startsWith("MULTILIKE("));
        assertTrue(key.contains(",name,email)"));
        assertFalse(key.contains("keyword"), "keyword should be hashed, not plaintext");
    }

    @Test
    void collectionNode_cacheKeyIncludesCOLLECTIONPrefix() {
        ConditionNode.CollectionNode cn =
            new ConditionNode.CollectionNode("items", ConditionNode.CollectionOp.IS_EMPTY);

        StringBuilder sb = new StringBuilder();
        CacheKeyBuilder.appendCacheKey(sb, cn);
        assertTrue(sb.toString().contains("COLLECTION(items,IS_EMPTY)"));
    }

    @Test
    void funcNode_cacheKeyIncludesFUNC() {
        ConditionNode.FuncNode fn = ConditionNode.FuncNode.of("COALESCE", new Object[] {"name"});
        StringBuilder sb = new StringBuilder();
        CacheKeyBuilder.appendCacheKey(sb, fn);
        assertTrue(sb.toString().contains("FUNC(COALESCE,"));
    }

    @Test
    void identicalConditions_produceSameKey() {
        List<ConditionNode> c1 = new ArrayList<>();
        c1.add(new ConditionNode.SimpleNode("status", 1, ConditionNode.Op.EQ));
        List<ConditionNode> c2 = new ArrayList<>();
        c2.add(new ConditionNode.SimpleNode("status", 1, ConditionNode.Op.EQ));

        String key1 = CacheKeyBuilder.buildCacheKey(c1, c1, emptyGroupStack(), false, List.of(), List.of(), List.of(),
            null, null);
        String key2 = CacheKeyBuilder.buildCacheKey(c2, c2, emptyGroupStack(), false, List.of(), List.of(), List.of(),
            null, null);
        assertEquals(key1, key2);
    }

    @Test
    void differentOperators_produceDifferentKeys() {
        List<ConditionNode> eq = new ArrayList<>();
        eq.add(new ConditionNode.SimpleNode("status", 1, ConditionNode.Op.EQ));
        List<ConditionNode> ne = new ArrayList<>();
        ne.add(new ConditionNode.SimpleNode("status", 1, ConditionNode.Op.NE));

        String key1 = CacheKeyBuilder.buildCacheKey(eq, eq, emptyGroupStack(), false, List.of(), List.of(), List.of(),
            null, null);
        String key2 = CacheKeyBuilder.buildCacheKey(ne, ne, emptyGroupStack(), false, List.of(), List.of(), List.of(),
            null, null);
        assertNotEquals(key1, key2);
    }

    @Test
    void multiLikeNode_keywordWithEscapingChars_producesDifferentKeys() {
        ConditionNode.MultiLikeNode mln1 = new ConditionNode.MultiLikeNode("a,b", new String[] {"name"});
        ConditionNode.MultiLikeNode mln2 = new ConditionNode.MultiLikeNode("a\\b", new String[] {"name"});
        ConditionNode.MultiLikeNode mln3 = new ConditionNode.MultiLikeNode("a(b", new String[] {"name"});
        ConditionNode.MultiLikeNode mln4 = new ConditionNode.MultiLikeNode("a)b", new String[] {"name"});

        StringBuilder sb1 = new StringBuilder();
        CacheKeyBuilder.appendCacheKey(sb1, mln1);
        StringBuilder sb2 = new StringBuilder();
        CacheKeyBuilder.appendCacheKey(sb2, mln2);
        StringBuilder sb3 = new StringBuilder();
        CacheKeyBuilder.appendCacheKey(sb3, mln3);
        StringBuilder sb4 = new StringBuilder();
        CacheKeyBuilder.appendCacheKey(sb4, mln4);

        String key1 = sb1.toString();
        String key2 = sb2.toString();
        String key3 = sb3.toString();
        String key4 = sb4.toString();
        assertNotEquals(key1, key2, "Comma and backslash keywords must differ");
        assertNotEquals(key2, key3, "Backslash and open-paren keywords must differ");
        assertNotEquals(key3, key4, "Open-paren and close-paren keywords must differ");
        assertNotEquals(key1, key3, "Comma and open-paren keywords must differ");
    }

    @Test
    void stringHashCodeCollision_differentStringsProduceDifferentKeys() {
        List<ConditionNode> c1 = new ArrayList<>();
        c1.add(new ConditionNode.SimpleNode("name", "Aa", ConditionNode.Op.EQ));
        List<ConditionNode> c2 = new ArrayList<>();
        c2.add(new ConditionNode.SimpleNode("name", "BB", ConditionNode.Op.EQ));

        String key1 = CacheKeyBuilder.buildCacheKey(c1, c1, emptyGroupStack(), false, List.of(), List.of(), List.of(),
            null, null);
        String key2 = CacheKeyBuilder.buildCacheKey(c2, c2, emptyGroupStack(), false, List.of(), List.of(), List.of(),
            null, null);
        assertNotEquals(key1, key2,
            "Different strings with same hashCode (Aa vs BB) must produce different cache keys");
    }

    @Test
    void nonStringHashCodeCollision_differentValuesProduceDifferentKeys() {
        List<ConditionNode> c1 = new ArrayList<>();
        c1.add(new ConditionNode.SimpleNode("val", 12, ConditionNode.Op.EQ));
        List<ConditionNode> c2 = new ArrayList<>();
        c2.add(new ConditionNode.SimpleNode("val", 21, ConditionNode.Op.EQ));

        String key1 = CacheKeyBuilder.buildCacheKey(c1, c1, emptyGroupStack(), false, List.of(), List.of(), List.of(),
            null, null);
        String key2 = CacheKeyBuilder.buildCacheKey(c2, c2, emptyGroupStack(), false, List.of(), List.of(), List.of(),
            null, null);
        assertNotEquals(key1, key2,
            "Different non-String values with same String.valueOf length must produce different cache keys");
    }

    @Test
    void nullGroupByFields_doesNotThrowNPE() {
        String key = CacheKeyBuilder.buildCacheKey(emptyConditions(), emptyConditions(), emptyGroupStack(), false, null,
            List.of(), List.of(), null, null);
        assertNotNull(key);
        assertFalse(key.contains("#GROUPBY"), "null groupByFields should not produce GROUPBY section");
    }

    @Test
    void nullHavingConditions_doesNotThrowNPE() {
        String key = CacheKeyBuilder.buildCacheKey(emptyConditions(), emptyConditions(), emptyGroupStack(), false,
            List.of(), null, List.of(), null, null);
        assertNotNull(key);
        assertFalse(key.contains("#HAVING"), "null havingConditions should not produce HAVING section");
    }

    @Test
    void havingConditions_differentContent_producesDifferentKeys() {
        List<ConditionNode> having1 = List.of(new ConditionNode.SimpleNode("cnt", 5, ConditionNode.Op.GT));
        List<ConditionNode> having2 = List.of(new ConditionNode.SimpleNode("total", 10, ConditionNode.Op.GT));

        String key1 = CacheKeyBuilder.buildCacheKey(emptyConditions(), emptyConditions(), emptyGroupStack(), false,
            List.of(), having1, List.of(), null, null);
        String key2 = CacheKeyBuilder.buildCacheKey(emptyConditions(), emptyConditions(), emptyGroupStack(), false,
            List.of(), having2, List.of(), null, null);
        assertNotEquals(key1, key2, "Different HAVING conditions must produce different cache keys");
    }
}
