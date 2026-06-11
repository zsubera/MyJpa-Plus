package com.zsubera.jpa.spec;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * QuerySpec.cacheKey() 哈希碰撞测试。
 *
 * <p>测试 P1-1 修复：减少哈希碰撞风险
 */
class QuerySpecCacheKeyTest {

    /**
     * 测试不同查询应该有不同的缓存键。
     *
     * <p>P1-1 修复验证：确保缓存键唯一性
     */
    @Test
    void differentQueriesShouldHaveDifferentCacheKeys() {
        QuerySpec<TestEntity> spec1 = new QuerySpec<TestEntity>().eq(TestEntity::getStatus, 1);
        QuerySpec<TestEntity> spec2 = new QuerySpec<TestEntity>().eq(TestEntity::getStatus, 2);

        String key1 = spec1.cacheKey();
        String key2 = spec2.cacheKey();

        assertNotEquals(key1, key2, "Different queries should have different cache keys");
        assertTrue(key1.startsWith("Q:"), "Cache key should start with Q: prefix");
        assertTrue(key2.startsWith("Q:"), "Cache key should start with Q: prefix");
    }

    /**
     * 测试嵌套查询应该有不同的缓存键。
     */
    @Test
    void nestedQueriesShouldHaveDifferentCacheKeys() {
        QuerySpec<TestEntity> spec1 =
            new QuerySpec<TestEntity>().or(or -> or.eq(TestEntity::getStatus, 1)).eq(TestEntity::getName, "test");
        QuerySpec<TestEntity> spec2 =
            new QuerySpec<TestEntity>().or(or -> or.eq(TestEntity::getStatus, 2)).eq(TestEntity::getName, "test");

        String key1 = spec1.cacheKey();
        String key2 = spec2.cacheKey();

        assertNotEquals(key1, key2, "Nested queries with different conditions should have different cache keys");
    }

    /**
     * 测试相同查询应该有相同的缓存键。
     */
    @Test
    void identicalQueriesShouldHaveSameCacheKeys() {
        QuerySpec<TestEntity> spec1 =
            new QuerySpec<TestEntity>().eq(TestEntity::getStatus, 1).eq(TestEntity::getName, "test");
        QuerySpec<TestEntity> spec2 =
            new QuerySpec<TestEntity>().eq(TestEntity::getStatus, 1).eq(TestEntity::getName, "test");

        assertEquals(spec1.cacheKey(), spec2.cacheKey(), "Identical queries should have same cache keys");
    }

    /**
     * 测试 DISTINCT 标志影响缓存键。
     */
    @Test
    void distinctFlagShouldAffectCacheKey() {
        QuerySpec<TestEntity> spec1 = new QuerySpec<TestEntity>().eq(TestEntity::getStatus, 1);
        QuerySpec<TestEntity> spec2 = new QuerySpec<TestEntity>().eq(TestEntity::getStatus, 1).distinct();

        assertNotEquals(spec1.cacheKey(), spec2.cacheKey(), "DISTINCT flag should affect cache key");
    }

    /**
     * 测试 ORDER BY 影响缓存键。
     */
    @Test
    void orderByShouldAffectCacheKey() {
        QuerySpec<TestEntity> spec1 = new QuerySpec<TestEntity>().eq(TestEntity::getStatus, 1);
        QuerySpec<TestEntity> spec2 =
            new QuerySpec<TestEntity>().eq(TestEntity::getStatus, 1).orderByAsc(TestEntity::getName);

        assertNotEquals(spec1.cacheKey(), spec2.cacheKey(), "ORDER BY should affect cache key");
    }

    /**
     * 测试 GROUP BY 影响缓存键。
     */
    @Test
    void groupByShouldAffectCacheKey() {
        QuerySpec<TestEntity> spec1 = new QuerySpec<TestEntity>().eq(TestEntity::getStatus, 1);
        QuerySpec<TestEntity> spec2 =
            new QuerySpec<TestEntity>().eq(TestEntity::getStatus, 1).groupBy(TestEntity::getStatus);

        assertNotEquals(spec1.cacheKey(), spec2.cacheKey(), "GROUP BY should affect cache key");
    }

    /**
     * 测试缓存键格式正确性。
     */
    @Test
    void cacheKeyFormatShouldBeCorrect() {
        QuerySpec<TestEntity> spec =
            new QuerySpec<TestEntity>().eq(TestEntity::getStatus, 1).distinct().orderByAsc(TestEntity::getName);

        String key = spec.cacheKey();

        assertTrue(key.startsWith("Q:"), "Cache key should start with Q:");
        assertTrue(key.contains("#DISTINCT"), "Cache key should contain DISTINCT");
        assertTrue(key.contains("#ORDERBY("), "Cache key should contain ORDERBY");
    }

    /**
     * 测试空查询的缓存键。
     */
    @Test
    void emptyQueryShouldHaveValidCacheKey() {
        QuerySpec<TestEntity> spec = new QuerySpec<>();
        String key = spec.cacheKey();

        assertNotNull(key, "Cache key should not be null");
        assertTrue(key.startsWith("Q:"), "Cache key should start with Q:");
    }
}
