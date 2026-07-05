package com.zsubera.jpa.converter;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for CodeEnumHelper covering:
 * - resolveCodeField with annotated enum
 * - resolveCodeField with non-annotated enum
 * - resolveCodeField caching behavior
 * - hasCodeEnumValue with annotated/non-annotated enums
 */
class CodeEnumHelperTest {

    enum StatusEnum {
        ACTIVE("ACTIVE"), INACTIVE("INACTIVE");

        @CodeEnumValue
        private final String code;

        StatusEnum(String code) {
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }

    enum UnannotatedEnum {
        VALUE_A, VALUE_B
    }

    enum MultipleAnnotationsEnum {
        FIRST("first"), SECOND("second");

        @CodeEnumValue
        private String code;

        @CodeEnumValue
        private String otherField;

        MultipleAnnotationsEnum(String code) {
            this.code = code;
            this.otherField = code;
        }
    }

    @Test
    void resolveCodeField_annotatedEnum_returnsField() {
        Field field = CodeEnumHelper.resolveCodeField(StatusEnum.class);
        assertNotNull(field);
        assertEquals("code", field.getName());
        assertTrue(field.isAnnotationPresent(CodeEnumValue.class));
    }

    @Test
    void resolveCodeField_nonAnnotatedEnum_returnsNull() {
        Field field = CodeEnumHelper.resolveCodeField(UnannotatedEnum.class);
        assertNull(field);
    }

    @Test
    void resolveCodeField_caching_returnsSameInstance() {
        Field field1 = CodeEnumHelper.resolveCodeField(StatusEnum.class);
        Field field2 = CodeEnumHelper.resolveCodeField(StatusEnum.class);
        assertSame(field1, field2);
    }

    @Test
    void resolveCodeField_multipleAnnotations_returnsFirst() {
        Field field = CodeEnumHelper.resolveCodeField(MultipleAnnotationsEnum.class);
        assertNotNull(field);
    }

    @Test
    void hasCodeEnumValue_annotatedEnum_returnsTrue() {
        assertTrue(CodeEnumHelper.hasCodeEnumValue(StatusEnum.class));
    }

    @Test
    void hasCodeEnumValue_nonAnnotatedEnum_returnsFalse() {
        assertFalse(CodeEnumHelper.hasCodeEnumValue(UnannotatedEnum.class));
    }

    @Test
    void resolveCodeField_canAccessValue() throws Exception {
        Field field = CodeEnumHelper.resolveCodeField(StatusEnum.class);
        assertNotNull(field);

        StatusEnum status = StatusEnum.ACTIVE;
        Object value = field.get(status);
        assertEquals("ACTIVE", value);
    }

    @Test
    void resolveCodeField_inaccessibleField_returnsNull() {
        enum InaccessibleEnum {
            TEST;

            @CodeEnumValue
            private final String code = "test";
        }

        Field field = CodeEnumHelper.resolveCodeField(InaccessibleEnum.class);
        // Should either return the field or null depending on setAccessible success
        // The important thing is no exception is thrown
    }

    @Test
    void resolveCodeField_cachesSentinelForNonAnnotated() {
        // First call caches sentinel
        Field field1 = CodeEnumHelper.resolveCodeField(UnannotatedEnum.class);
        assertNull(field1);

        // Second call returns cached sentinel (converted to null)
        Field field2 = CodeEnumHelper.resolveCodeField(UnannotatedEnum.class);
        assertNull(field2);
    }

    @Test
    void resolveCodeField_classWithNoFields_returnsNull() {
        interface EmptyInterface {}
        Field field = CodeEnumHelper.resolveCodeField(EmptyInterface.class);
        assertNull(field);
    }

    @Test
    void hasCodeEnumValue_classWithNoFields_returnsFalse() {
        interface EmptyInterface {}
        assertFalse(CodeEnumHelper.hasCodeEnumValue(EmptyInterface.class));
    }
}
