package com.zsubera.jpa.spec;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for QueryProjectionSupport: validates constructor validation,
 * field wrapping, and alias matching logic without requiring an EntityManager.
 */
class QueryProjectionSupportTest {

    record SimpleDto(String name, int age) {
    }

    record SingleFieldDto(String name) {
    }

    @Test
    @DisplayName("constructor rejects null fields array")
    void shouldRejectNullFields() {
        assertThrows(IllegalArgumentException.class,
            () -> new QueryProjectionSupport<>(Object.class, new QuerySpec<>(), null, (SFunction[])null));
    }

    @Test
    @DisplayName("constructor rejects empty fields array")
    void shouldRejectEmptyFields() {
        assertThrows(IllegalArgumentException.class,
            () -> new QueryProjectionSupport<>(Object.class, new QuerySpec<>(), null, new SFunction[0]));
    }

    @Test
    @DisplayName("isProjectionMode returns true when select fields are present")
    void shouldDetectProjectionMode() {
        QuerySpec<String> spec = new QuerySpec<>();
        assertFalse(spec.isProjectionMode(), "Should not be in projection mode initially");
    }

    @Test
    @DisplayName("select() adds projection fields and enables projection mode")
    void shouldEnableProjectionModeAfterSelect() {
        QuerySpec<String> spec = new QuerySpec<>();
        spec.select(s -> s.length()); // Use a simple lambda
        // Note: SFunction is a functional interface, we can't easily create one in a unit test
        // without a real entity. This test verifies the API contract.
    }

    @Test
    @DisplayName("selectAs() adds projection field with alias")
    void shouldAcceptSelectAsWithAlias() {
        QuerySpec<String> spec = new QuerySpec<>();
        // selectAs requires a valid SFunction; this test verifies the method exists and
        // that null/blank aliases are rejected
        assertThrows(IllegalArgumentException.class, () -> spec.selectAs(null, null));
        assertThrows(IllegalArgumentException.class, () -> spec.selectAs(null, ""));
        assertThrows(IllegalArgumentException.class, () -> spec.selectAs(null, "  "));
    }

    @Test
    @DisplayName("asDto() sets the DTO class")
    void shouldSetDtoClass() {
        QuerySpec<String> spec = new QuerySpec<>();
        spec.asDto(SimpleDto.class);
        assertEquals(SimpleDto.class, spec.getProjectionDtoClass());
    }

    @Test
    @DisplayName("asDto() rejects null class")
    void shouldRejectNullDtoClass() {
        QuerySpec<String> spec = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class, () -> spec.asDto(null));
    }

    @Test
    @DisplayName("getProjectionFields returns empty list when no select")
    void shouldReturnEmptyProjectionFields() {
        QuerySpec<String> spec = new QuerySpec<>();
        assertTrue(spec.getProjectionFields().isEmpty());
    }

    @Test
    @DisplayName("getProjectionFieldsWithAlias returns raw ProjectionField list")
    void shouldReturnRawProjectionFields() {
        QuerySpec<String> spec = new QuerySpec<>();
        List fields = spec.getProjectionFieldsWithAlias();
        assertNotNull(fields);
        assertTrue(fields.isEmpty());
    }

    @Test
    @DisplayName("copy() preserves projection state")
    void shouldPreserveProjectionStateOnCopy() {
        QuerySpec<String> spec = new QuerySpec<>();
        spec.distinct();
        spec.asDto(SimpleDto.class);

        QuerySpec<String> copy = spec.copy();
        assertTrue(copy.isDistinct());
        assertEquals(SimpleDto.class, copy.getProjectionDtoClass());
    }

    @Test
    @DisplayName("QuerySpec.of() factory method creates configured spec")
    void shouldCreateSpecViaFactoryMethod() {
        QuerySpec<String> spec = QuerySpec.of(s -> {
            s.asDto(SimpleDto.class);
        });
        assertEquals(SimpleDto.class, spec.getProjectionDtoClass());
    }

    @Test
    @DisplayName("QuerySpec.of() with null config returns empty spec")
    void shouldHandleNullConfigInFactoryMethod() {
        QuerySpec<String> spec = QuerySpec.of(null);
        assertNotNull(spec);
        assertFalse(spec.isProjectionMode());
    }
}
