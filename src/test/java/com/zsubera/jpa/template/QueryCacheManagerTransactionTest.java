package com.zsubera.jpa.template;

import static org.junit.jupiter.api.Assertions.*;

import com.zsubera.jpa.spec.TestApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;

@DataJpaTest
@org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase(
    replace = org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = {TestApplication.class, QueryCacheManagerTransactionTest.TestConfig.class})
class QueryCacheManagerTransactionTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        public QueryCacheManager queryCacheManager() {
            return new QueryCacheManager();
        }
    }

    @Autowired
    private QueryCacheManager cacheManager;

    @BeforeEach
    void setUp() {
        cacheManager.clear();
    }

    @Test
    void evictByPrefixAfterTransactionCommit_inTransaction_registersSynchronization() {
        cacheManager.put("User:q1", "v1", 60);
        cacheManager.put("Order:q1", "v2", 60);

        cacheManager.evictByPrefixAfterTransactionCommit("User:");

        assertNotNull(cacheManager.get("User:q1"));
        assertNotNull(cacheManager.get("Order:q1"));
    }

    @Test
    void clearAfterTransactionCommit_inTransaction_registersSynchronization() {
        cacheManager.put("key1", "v1", 60);
        cacheManager.put("key2", "v2", 60);

        cacheManager.clearAfterTransactionCommit();

        assertEquals(2, cacheManager.size());
    }
}
