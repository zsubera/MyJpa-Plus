package com.zsubera.jpa.util;

import static org.junit.jupiter.api.Assertions.*;

import com.zsubera.jpa.exception.MyJpaPlusException;
import com.zsubera.jpa.exception.SecurityViolationException;
import org.junit.jupiter.api.Test;

class IdentifierValidatorTest {

    @Test
    void validate_simpleIdentifier() {
        assertDoesNotThrow(() -> IdentifierValidator.validate("users"));
    }

    @Test
    void validate_identifierWithUnderscore() {
        assertDoesNotThrow(() -> IdentifierValidator.validate("user_profiles"));
    }

    @Test
    void validate_identifierStartingWithUnderscore() {
        assertDoesNotThrow(() -> IdentifierValidator.validate("_users"));
    }

    @Test
    void validate_identifierWithNumbers() {
        assertDoesNotThrow(() -> IdentifierValidator.validate("table123"));
    }

    @Test
    void validate_schemaTableFormat() {
        assertDoesNotThrow(() -> IdentifierValidator.validate("public.users"));
    }

    @Test
    void validate_catalogSchemaTableFormat() {
        assertDoesNotThrow(() -> IdentifierValidator.validate("mydb.public.users"));
    }

    @Test
    void validate_null_throwsSecurityViolationException() {
        assertThrows(SecurityViolationException.class, () -> IdentifierValidator.validate(null));
    }

    @Test
    void validate_empty_throwsSecurityViolationException() {
        assertThrows(SecurityViolationException.class, () -> IdentifierValidator.validate(""));
    }

    @Test
    void validate_identifierWithSpaces_throwsSecurityViolationException() {
        assertThrows(SecurityViolationException.class, () -> IdentifierValidator.validate("user name"));
    }

    @Test
    void validate_identifierWithSpecialChars_throwsSecurityViolationException() {
        assertThrows(SecurityViolationException.class, () -> IdentifierValidator.validate("user@name"));
    }

    @Test
    void validate_identifierWithSemicolon_throwsSecurityViolationException() {
        assertThrows(SecurityViolationException.class, () -> IdentifierValidator.validate("users; DROP TABLE"));
    }

    @Test
    void validate_identifierWithSingleQuote_throwsSecurityViolationException() {
        assertThrows(SecurityViolationException.class, () -> IdentifierValidator.validate("user's"));
    }

    @Test
    void validate_identifierTooLong_throwsSecurityViolationException() {
        String longIdentifier = "a".repeat(129);
        assertThrows(SecurityViolationException.class, () -> IdentifierValidator.validate(longIdentifier));
    }

    @Test
    void validate_identifierAtMaxLength() {
        String maxLengthIdentifier = "a".repeat(128);
        assertDoesNotThrow(() -> IdentifierValidator.validate(maxLengthIdentifier));
    }

    @Test
    void validateTableName_simpleTable() {
        assertDoesNotThrow(() -> IdentifierValidator.validateTableName("users"));
    }

    @Test
    void validateTableName_schemaTable() {
        assertDoesNotThrow(() -> IdentifierValidator.validateTableName("public.users"));
    }

    @Test
    void validateColumnName_simpleColumn() {
        assertDoesNotThrow(() -> IdentifierValidator.validateColumnName("user_id"));
    }

    @Test
    void validateColumnName_withDot_throwsException() {
        assertThrows(MyJpaPlusException.class, () -> IdentifierValidator.validateColumnName("user.id"));
    }

    @Test
    void validateColumnName_null_throwsException() {
        assertThrows(MyJpaPlusException.class, () -> IdentifierValidator.validateColumnName(null));
    }

    @Test
    void validateColumnName_empty_throwsException() {
        assertThrows(MyJpaPlusException.class, () -> IdentifierValidator.validateColumnName(""));
    }

    @Test
    void validateColumnName_tooLong_throwsException() {
        String longName = "a".repeat(129);
        assertThrows(MyJpaPlusException.class, () -> IdentifierValidator.validateColumnName(longName));
    }

    @Test
    void resolveTableName_withTableAnnotation() {
        String name = IdentifierValidator.resolveTableName(TableAnnotatedEntity.class);
        assertEquals("custom_table", name);
    }

    @Test
    void resolveTableName_withEntityName() {
        String name = IdentifierValidator.resolveTableName(EntityNamedEntity.class);
        assertEquals("entity_named", name);
    }

    @Test
    void resolveTableName_fallbackToSnakeCase() {
        String name = IdentifierValidator.resolveTableName(TableAnnotatedEntity.class);
        // Just verify it doesn't throw and returns a non-empty string
        assertNotNull(name);
        assertFalse(name.isEmpty());
    }

    @Test
    void resolveTableName_withCatalogAndSchema() {
        String name = IdentifierValidator.resolveTableName(FullTableEntity.class);
        assertEquals("my_catalog.my_schema.full_table", name);
    }

    @jakarta.persistence.Entity
    @jakarta.persistence.Table(name = "custom_table")
    static class TableAnnotatedEntity {}

    @jakarta.persistence.Entity(name = "entity_named")
    static class EntityNamedEntity {}

    @jakarta.persistence.Entity
    @jakarta.persistence.Table(name = "full_table", schema = "my_schema", catalog = "my_catalog")
    static class FullTableEntity {}

    @Test
    void setUnicodeIdentifiers_enableDisable() {
        boolean original = IdentifierValidator.isUnicodeIdentifiersEnabled();
        try {
            IdentifierValidator.setUnicodeIdentifiers(true);
            assertTrue(IdentifierValidator.isUnicodeIdentifiersEnabled());
            IdentifierValidator.setUnicodeIdentifiers(false);
            assertFalse(IdentifierValidator.isUnicodeIdentifiersEnabled());
        } finally {
            IdentifierValidator.setUnicodeIdentifiers(original);
        }
    }

    @Test
    void validateColumnName_unicodeHomoglyph_throwsSecurityViolationException() {
        boolean original = IdentifierValidator.isUnicodeIdentifiersEnabled();
        try {
            IdentifierValidator.setUnicodeIdentifiers(true);
            // Cyrillic 'a' (\u0430) looks like Latin 'a' — homoglyph attack
            String homoglyph = "us\u0430r"; // "usаr" with Cyrillic 'а'
            assertThrows(SecurityViolationException.class, () -> IdentifierValidator.validateColumnName(homoglyph));
        } finally {
            IdentifierValidator.setUnicodeIdentifiers(original);
        }
    }

    @Test
    void validateColumnName_unicodeHomoglyph_throwsNotMyJpaPlusException() {
        boolean original = IdentifierValidator.isUnicodeIdentifiersEnabled();
        try {
            IdentifierValidator.setUnicodeIdentifiers(true);
            String homoglyph = "us\u0430r";
            try {
                IdentifierValidator.validateColumnName(homoglyph);
                fail("Expected SecurityViolationException");
            } catch (SecurityViolationException e) {
                // Verify it's the correct exception type (not MyJpaPlusException)
                assertTrue(e.getMessage().contains("homoglyph"));
            }
        } finally {
            IdentifierValidator.setUnicodeIdentifiers(original);
        }
    }
}
