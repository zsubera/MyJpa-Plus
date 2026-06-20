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

/**
 * Tests for {@link OptimisticLockRetryAdvisor}.
 */
@SpringBootTest(classes = OptimisticLockRetryAdvisorTest.TestConfig.class)
class OptimisticLockRetryAdvisorTest {

    @SpringBootApplication
    static class TestConfig {

        @Bean
        public TestState testState() {
            return new TestState();
        }

        @Bean
        public RetryableService retryableService(TestState state) {
            return new RetryableService(state);
        }
    }

    static class TestState {
        final AtomicInteger callCount = new AtomicInteger(0);
        volatile int succeedAfter = 0;
        volatile Object result = "success";

        void reset() {
            callCount.set(0);
            succeedAfter = 0;
            result = "success";
        }
    }

    static class RetryableService {

        private final TestState state;

        RetryableService(TestState state) {
            this.state = state;
        }

        @RetryOnOptimisticLock(maxRetries = 3, backoffMs = 10)
        public String performWithRetry() {
            int current = state.callCount.incrementAndGet();
            if (current <= state.succeedAfter) {
                throw new OptimisticLockException("Concurrent modification");
            }
            return (String)state.result;
        }

        @RetryOnOptimisticLock(maxRetries = 2, backoffMs = 10)
        public String alwaysFail() {
            state.callCount.incrementAndGet();
            throw new OptimisticLockException("Always fails");
        }

        public String noAnnotation() {
            state.callCount.incrementAndGet();
            throw new OptimisticLockException("No annotation");
        }

        @RetryOnOptimisticLock(maxRetries = 3, backoffMs = 10)
        public String throwWrappedPersistenceException() {
            int current = state.callCount.incrementAndGet();
            if (current <= state.succeedAfter) {
                throw new PersistenceException("wrapped", new OptimisticLockException("cause"));
            }
            return (String)state.result;
        }

        @RetryOnOptimisticLock(maxRetries = 3, backoffMs = 10)
        public String throwNonOptimisticPersistenceException() {
            state.callCount.incrementAndGet();
            throw new PersistenceException("non-optimistic");
        }

        @RetryOnOptimisticLock(maxRetries = 3, backoffMs = 10)
        public String throwInterruptedException() throws InterruptedException {
            state.callCount.incrementAndGet();
            if (state.callCount.get() == 1) {
                throw new OptimisticLockException("trigger retry");
            }
            Thread.currentThread().interrupt();
            throw new InterruptedException("interrupted during retry");
        }

        @RetryOnOptimisticLock(maxRetries = 0, backoffMs = 10)
        public String zeroRetries() {
            state.callCount.incrementAndGet();
            throw new OptimisticLockException("fail with zero retries");
        }
    }

    @Autowired
    private RetryableService service;

    @Autowired
    private TestState state;

    @Test
    void retry_succeedsOnFirstAttempt() {
        state.reset();
        state.succeedAfter = 0;

        String result = service.performWithRetry();

        assertEquals("success", result);
        assertEquals(1, state.callCount.get());
    }

    @Test
    void retry_succeedsAfterRetries() {
        state.reset();
        state.succeedAfter = 2;

        String result = service.performWithRetry();

        assertEquals("success", result);
        assertEquals(3, state.callCount.get());
    }

    @Test
    void retry_throwsAfterMaxRetriesExhausted() {
        state.reset();

        OptimisticLockException ex = assertThrows(OptimisticLockException.class, () -> service.alwaysFail());

        assertNotNull(ex);
        assertEquals(3, state.callCount.get());
    }

    @Test
    void retry_noAnnotation_doesNotRetry() {
        state.reset();

        assertThrows(OptimisticLockException.class, () -> service.noAnnotation());

        assertEquals(1, state.callCount.get());
    }

    @Test
    void retry_resultFromSuccessfulRetry() {
        state.reset();
        state.succeedAfter = 1;
        state.result = "custom-result";

        String result = service.performWithRetry();

        assertEquals("custom-result", result);
        assertEquals(2, state.callCount.get());
    }

    @Test
    void retry_wrappedPersistenceException_retries() {
        state.reset();
        state.succeedAfter = 1;

        String result = service.throwWrappedPersistenceException();

        assertEquals("success", result);
        assertEquals(2, state.callCount.get());
    }

    @Test
    void retry_nonOptimisticPersistenceException_doesNotRetry() {
        state.reset();

        PersistenceException ex =
            assertThrows(PersistenceException.class, () -> service.throwNonOptimisticPersistenceException());

        assertNotNull(ex);
        assertEquals(1, state.callCount.get());
    }

    @Test
    void retry_zeroMaxRetries_throwsImmediately() {
        state.reset();

        OptimisticLockException ex = assertThrows(OptimisticLockException.class, () -> service.zeroRetries());

        assertNotNull(ex);
        assertEquals(1, state.callCount.get());
    }
}
