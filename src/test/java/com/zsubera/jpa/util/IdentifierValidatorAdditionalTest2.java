package com.zsubera.jpa.util;

import static org.junit.jupiter.api.Assertions.*;

import com.zsubera.jpa.exception.MyJpaPlusException;
import com.zsubera.jpa.spec.TestEntity;
import org.junit.jupiter.api.Test;

class IdentifierValidatorAdditionalTest2 {

    @Test
    void resolveTableName_withTableAnnotation() {
        String name = IdentifierValidator.resolveTableName(TestEntity.class);
        assertNotNull(name);
        assertFalse(name.isEmpty());
    }

    @Test
    void validate_unicodeDisabled_rejectsUnicode() {
        boolean original = IdentifierValidator.isUnicodeIdentifiersEnabled();
        try {
            IdentifierValidator.setUnicodeIdentifiers(false);
            assertDoesNotThrow(() -> IdentifierValidator.validate("users"));
        } finally {
            IdentifierValidator.setUnicodeIdentifiers(original);
        }
    }

    @Test
    void validate_unicodeEnabled_acceptsUnicode() {
        boolean original = IdentifierValidator.isUnicodeIdentifiersEnabled();
        try {
            IdentifierValidator.setUnicodeIdentifiers(true);
            assertDoesNotThrow(() -> IdentifierValidator.validate("\u00e9l\u00e8ve"));
        } finally {
            IdentifierValidator.setUnicodeIdentifiers(original);
        }
    }

    @Test
    void validateColumnName_unicodeDisabled_rejectsUnicode() {
        boolean original = IdentifierValidator.isUnicodeIdentifiersEnabled();
        try {
            IdentifierValidator.setUnicodeIdentifiers(false);
            assertDoesNotThrow(() -> IdentifierValidator.validateColumnName("col_name"));
        } finally {
            IdentifierValidator.setUnicodeIdentifiers(original);
        }
    }

    @Test
    void validateTableName_null_throws() {
        assertThrows(MyJpaPlusException.class, () -> IdentifierValidator.validateTableName(null));
    }

    @Test
    void validateTableName_empty_throws() {
        assertThrows(MyJpaPlusException.class, () -> IdentifierValidator.validateTableName(""));
    }

    @Test
    void validatePart_invalidSegment_throws() {
        assertThrows(MyJpaPlusException.class, () -> IdentifierValidator.validate("schema@table"));
    }

    @Test
    void validate_homoglyphWithStrictMode_throws() {
        boolean original = IdentifierValidator.isUnicodeIdentifiersEnabled();
        String origStrict = System.getProperty("myjpa-plus.merge.strict-mode");
        try {
            IdentifierValidator.setUnicodeIdentifiers(true);
            System.setProperty("myjpa-plus.merge.strict-mode", "true");
            String homoglyph = "\u0430bc";
            assertThrows(MyJpaPlusException.class, () -> IdentifierValidator.validate(homoglyph));
        } finally {
            IdentifierValidator.setUnicodeIdentifiers(original);
            if (origStrict != null) {
                System.setProperty("myjpa-plus.merge.strict-mode", origStrict);
            } else {
                System.clearProperty("myjpa-plus.merge.strict-mode");
            }
        }
    }
}
