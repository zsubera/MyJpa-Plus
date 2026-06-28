package com.zsubera.jpa.update;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import org.junit.jupiter.api.Test;

class EntityFieldExtractorInvocationTargetTest {

    @Test
    void checkedExceptionThroughInvocationTarget_preservesCauseChain() {
        EntityFieldExtractor<EntityWithCheckedException> extractor =
            new EntityFieldExtractor<>(EntityWithCheckedException.class);
        EntityWithCheckedException entity = new EntityWithCheckedException();

        var ex = assertThrows(com.zsubera.jpa.exception.MyJpaPlusException.class,
            () -> extractor.extractFieldValues(entity));
        assertInstanceOf(RuntimeException.class, ex.getCause());
        assertInstanceOf(IOException.class, ex.getCause().getCause());
    }

    @Test
    void runtimeExceptionThroughInvocationTarget_rethrowsAsIs() {
        EntityFieldExtractor<EntityWithRuntimeException> extractor =
            new EntityFieldExtractor<>(EntityWithRuntimeException.class);
        EntityWithRuntimeException entity = new EntityWithRuntimeException();

        var ex = assertThrows(com.zsubera.jpa.exception.MyJpaPlusException.class,
            () -> extractor.extractFieldValues(entity));
        assertInstanceOf(IllegalArgumentException.class, ex.getCause());
    }

    @Test
    void errorThroughInvocationTarget_preservesOriginalError() {
        EntityFieldExtractor<EntityWithError> extractor =
            new EntityFieldExtractor<>(EntityWithError.class);
        EntityWithError entity = new EntityWithError();

        assertThrows(AssertionError.class, () -> extractor.extractFieldValues(entity));
    }

    static class EntityWithCheckedException {
        @SuppressWarnings("unused")
        private String name;

        public String getName() throws IOException {
            throw new IOException("io failure");
        }
    }

    static class EntityWithRuntimeException {
        @SuppressWarnings("unused")
        private String name;

        public String getName() {
            throw new IllegalArgumentException("bad arg");
        }
    }

    static class EntityWithError {
        @SuppressWarnings("unused")
        private String name;

        public String getName() {
            throw new AssertionError("assertion failed");
        }
    }
}
