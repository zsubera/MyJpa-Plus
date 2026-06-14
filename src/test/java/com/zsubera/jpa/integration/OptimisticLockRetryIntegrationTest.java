package com.zsubera.jpa.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.zsubera.jpa.annotation.RetryOnOptimisticLock;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

@Tag("integration")
@SpringBootTest(classes = {PgTestApplication.class, OptimisticLockRetryIntegrationTest.TestService.class})
@TestPropertySource(properties = {"spring.jpa.hibernate.ddl-auto=create"})
class OptimisticLockRetryIntegrationTest {

    @Autowired
    private OptimisticLockEntityRepository repository;

    @Autowired
    private TestService testService;

    @PersistenceContext
    private EntityManager em;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        repository.flush();
    }

    @Test
    void testVersionIsIncrementedOnPersist() {
        OptimisticLockEntity entity = new OptimisticLockEntity();
        entity.setName("test");
        entity.setValue("initial");
        repository.save(entity);
        repository.flush();

        assertNotNull(entity.getVersion());
        assertEquals(0L, entity.getVersion());
    }

    @Test
    void testVersionIsIncrementedOnUpdate() {
        OptimisticLockEntity entity = new OptimisticLockEntity();
        entity.setName("test");
        entity.setValue("initial");
        repository.save(entity);
        repository.flush();

        entity.setValue("updated");
        entity = repository.save(entity);
        repository.flush();

        assertEquals(1L, entity.getVersion());
    }

    @Test
    void testOptimisticLockExceptionOnConcurrentUpdate() {
        OptimisticLockEntity entity = new OptimisticLockEntity();
        entity.setName("test");
        entity.setValue("initial");
        repository.save(entity);
        repository.flush();
        Long entityId = entity.getId();

        em.clear();

        OptimisticLockEntity copy1 = repository.findById(entityId).orElseThrow();
        OptimisticLockEntity copy2 = repository.findById(entityId).orElseThrow();

        copy1.setValue("update1");
        repository.save(copy1);
        repository.flush();

        assertThrows(ObjectOptimisticLockingFailureException.class, () -> {
            copy2.setValue("update2");
            repository.save(copy2);
            repository.flush();
        });
    }

    @Test
    void testRetryOnOptimisticLockSucceedsAfterRetry() {
        OptimisticLockEntity entity = new OptimisticLockEntity();
        entity.setName("test");
        entity.setValue("initial");
        repository.save(entity);
        repository.flush();
        Long entityId = entity.getId();

        String result = testService.updateWithRetry(entityId, "updated");
        assertEquals("updated", result);

        em.clear();
        OptimisticLockEntity found = repository.findById(entityId).orElseThrow();
        assertEquals("updated", found.getValue());
    }

    @Test
    void testConcurrentUpdatesWithRetry() throws Exception {
        OptimisticLockEntity entity = new OptimisticLockEntity();
        entity.setName("test");
        entity.setValue("initial");
        repository.save(entity);
        repository.flush();
        Long entityId = entity.getId();

        ExecutorService executor = Executors.newFixedThreadPool(4);
        AtomicInteger successCount = new AtomicInteger(0);

        List<Future<?>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < 4; i++) {
            final int val = i;
            futures.add(executor.submit(() -> {
                try {
                    testService.concurrentUpdate(entityId, "value_" + val);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // Expected for some threads
                }
            }));
        }

        for (Future<?> f : futures) {
            f.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();

        em.clear();
        OptimisticLockEntity found = repository.findById(entityId).orElseThrow();
        assertNotNull(found.getValue());
        assertTrue(successCount.get() >= 1, "At least one update should succeed");
    }

    @Service
    static class TestService {

        @Autowired
        private OptimisticLockEntityRepository repository;

        @RetryOnOptimisticLock(maxRetries = 3, backoffMs = 10)
        @Transactional
        public String updateWithRetry(Long id, String newValue) {
            OptimisticLockEntity entity = repository.findById(id).orElseThrow();
            entity.setValue(newValue);
            repository.save(entity);
            repository.flush();
            return newValue;
        }

        @RetryOnOptimisticLock(maxRetries = 5, backoffMs = 10)
        @Transactional
        public String concurrentUpdate(Long id, String newValue) {
            OptimisticLockEntity entity = repository.findById(id).orElseThrow();
            entity.setValue(newValue);
            repository.save(entity);
            repository.flush();
            return newValue;
        }
    }
}
