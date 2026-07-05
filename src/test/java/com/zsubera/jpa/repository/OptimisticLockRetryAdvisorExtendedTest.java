package com.zsubera.jpa.repository;

import static org.junit.jupiter.api.Assertions.*;

import com.zsubera.jpa.annotation.RetryOnOptimisticLock;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PersistenceException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

@SpringBootTest(classes = OptimisticLockRetryAdvisorExtendedTest.TestConfig.class)
class OptimisticLockRetryAdvisorExtendedTest {

    @SpringBootApplication
    static class TestConfig {

        @Bean
        public TestState2 testState2() {
            return new TestState2();
        }

        @Bean
        public RetryableService2 retryableService2(TestState2 state) {
            return new RetryableService2(state);
        }
    }

    static class TestState2 {
        final AtomicInteger callCount = new AtomicInteger(0);
        volatile int succeedAfter = 0;
    }

    static class RetryableService2 {

        private final TestState2 state;

        RetryableService2(TestState2 state) {
            this.state = state;
        }

        @RetryOnOptimisticLock(maxRetries = 5, backoffMs = 10)
        public String succeedAfterRetries() {
            int current = state.callCount.incrementAndGet();
            if (current <= state.succeedAfter) {
                throw new OptimisticLockException("Concurrent modification");
            }
            return "success";
        }

        @RetryOnOptimisticLock(maxRetries = 2, backoffMs = 10)
        public String wrappedPersistenceException() {
            int current = state.callCount.incrementAndGet();
            if (current <= state.succeedAfter) {
                throw new PersistenceException("wrapped", new OptimisticLockException("cause"));
            }
            return "success";
        }

        @RetryOnOptimisticLock(maxRetries = 2, backoffMs = 10)
        public String nonOptimisticPersistenceException() {
            state.callCount.incrementAndGet();
            throw new PersistenceException("non-optimistic");
        }

        @RetryOnOptimisticLock(maxRetries = 3, backoffMs = 10)
        public String interruptedException() throws InterruptedException {
            int current = state.callCount.incrementAndGet();
            if (current == 1) {
                throw new OptimisticLockException("trigger retry");
            }
            Thread.currentThread().interrupt();
            throw new InterruptedException("interrupted");
        }

        @RetryOnOptimisticLock(maxRetries = 0, backoffMs = 10)
        public String zeroRetries() {
            state.callCount.incrementAndGet();
            throw new OptimisticLockException("fail");
        }

        public String noAnnotation() {
            state.callCount.incrementAndGet();
            return "no-annotation";
        }

        @RetryOnOptimisticLock(maxRetries = 3, backoffMs = 10)
        public String alwaysFail() {
            state.callCount.incrementAndGet();
            throw new OptimisticLockException("always fails");
        }

        @RetryOnOptimisticLock(maxRetries = 3, backoffMs = 10)
        public String objectOptimisticLockException() {
            int current = state.callCount.incrementAndGet();
            if (current <= state.succeedAfter) {
                throw new org.springframework.orm.ObjectOptimisticLockingFailureException("obj lock", new Object());
            }
            return "success";
        }
    }

    @Autowired
    private RetryableService2 service;

    @Autowired
    private TestState2 state;

    @Test
    void retry_succeedsAfterRetries() {
        state.callCount.set(0);
        state.succeedAfter = 2;
        String result = service.succeedAfterRetries();
        assertEquals("success", result);
        assertEquals(3, state.callCount.get());
    }

    @Test
    void retry_throwsAfterMaxRetriesExhausted() {
        state.callCount.set(0);
        assertThrows(OptimisticLockException.class, () -> service.alwaysFail());
        assertEquals(4, state.callCount.get());
    }

    @Test
    void retry_zeroMaxRetries() {
        state.callCount.set(0);
        assertThrows(OptimisticLockException.class, () -> service.zeroRetries());
        assertEquals(1, state.callCount.get());
    }

    @Test
    void retry_noAnnotation_doesNotRetry() {
        state.callCount.set(0);
        String result = service.noAnnotation();
        assertEquals("no-annotation", result);
        assertEquals(1, state.callCount.get());
    }

    @Test
    void retry_wrappedPersistenceException_retries() {
        state.callCount.set(0);
        state.succeedAfter = 1;
        String result = service.wrappedPersistenceException();
        assertEquals("success", result);
        assertEquals(2, state.callCount.get());
    }

    @Test
    void retry_nonOptimisticPersistenceException_doesNotRetry() {
        state.callCount.set(0);
        assertThrows(PersistenceException.class, () -> service.nonOptimisticPersistenceException());
        assertEquals(1, state.callCount.get());
    }

    @Test
    void retry_interruptedException_throwsAfterRetry() {
        state.callCount.set(0);
        assertThrows(Exception.class, () -> service.interruptedException());
    }

    @Test
    void retry_objectOptimisticLockException_retries() {
        state.callCount.set(0);
        state.succeedAfter = 1;
        String result = service.objectOptimisticLockException();
        assertEquals("success", result);
        assertEquals(2, state.callCount.get());
    }
}
