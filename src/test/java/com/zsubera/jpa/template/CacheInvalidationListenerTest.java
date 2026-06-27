package com.zsubera.jpa.template;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class CacheInvalidationListenerTest {

    @Test
    void constructor_createsListener() {
        QueryCacheManager cacheManager = new QueryCacheManager();
        CacheInvalidationListener listener = new CacheInvalidationListener(cacheManager);
        assertNotNull(listener);
    }

    @Test
    void onEntityModified_evictsByPrefix() {
        QueryCacheManager cacheManager = new QueryCacheManager();
        cacheManager.put("User:query1", "result1", 60);
        cacheManager.put("User:query2", "result2", 60);
        cacheManager.put("Order:query1", "result3", 60);

        CacheInvalidationListener listener = new CacheInvalidationListener(cacheManager);
        EntityModifiedEvent event = new EntityModifiedEvent("User", 1);
        listener.onEntityModified(event);

        assertNull(cacheManager.get("User:query1"));
        assertNull(cacheManager.get("User:query2"));
        assertNotNull(cacheManager.get("Order:query1"));
    }

    @Test
    void onTransactionCommit_evictsByPrefix() {
        QueryCacheManager cacheManager = new QueryCacheManager();
        cacheManager.put("User:query1", "result1", 60);

        CacheInvalidationListener listener = new CacheInvalidationListener(cacheManager);
        EntityModifiedEvent event = new EntityModifiedEvent("User", 1);
        listener.onTransactionCommit(event);

        assertNull(cacheManager.get("User:query1"));
    }

    @Test
    void entityModifiedEvent_getters() {
        EntityModifiedEvent event = new EntityModifiedEvent("User", 5);
        assertEquals("User", event.getEntityName());
        assertEquals(5, event.getAffectedRows());
    }

    @Test
    void entityModifiedEvent_fromClass() {
        EntityModifiedEvent event = new EntityModifiedEvent(String.class, 10);
        assertEquals("java.lang.String", event.getEntityName());
        assertEquals(10, event.getAffectedRows());
    }

    @Test
    void onEntityModified_evictsByFqcnPrefix() {
        QueryCacheManager cacheManager = new QueryCacheManager();
        String fqcn = "com.example.MyEntity";
        cacheManager.put(fqcn + ":query1", "result1", 60);
        cacheManager.put(fqcn + ":query2", "result2", 60);
        cacheManager.put("com.other.Entity:query1", "result3", 60);

        CacheInvalidationListener listener = new CacheInvalidationListener(cacheManager);
        EntityModifiedEvent event = new EntityModifiedEvent("com.example.MyEntity", 1);
        listener.onEntityModified(event);

        assertNull(cacheManager.get(fqcn + ":query1"));
        assertNull(cacheManager.get(fqcn + ":query2"));
        assertNotNull(cacheManager.get("com.other.Entity:query1"));
    }
}
