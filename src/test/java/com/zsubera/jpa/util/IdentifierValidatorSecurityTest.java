package com.zsubera.jpa.util;

import static org.junit.jupiter.api.Assertions.*;

import com.zsubera.jpa.exception.SecurityViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Security-focused tests for IdentifierValidator:
 * - Unicode homoglyph attack detection
 * - Max length enforcement
 * - Multi-part identifier (schema.table) validation
 * - Column name validation (no dots)
 */
class IdentifierValidatorSecurityTest {

    @BeforeEach
    void setUp() {
        IdentifierValidator.setUnicodeIdentifiers(true);
    }

    @AfterEach
    void tearDown() {
        IdentifierValidator.setUnicodeIdentifiers(false);
    }

    @Test
    @DisplayName("Cyrillic characters in identifier are rejected")
    void shouldRejectCyrillicHomoglyphs() {
        // Cyrillic 'а' (U+0430) looks like Latin 'a'
        assertThrows(SecurityViolationException.class, () -> IdentifierValidator.validate("us\u0430ers"), // "usаers"
                                                                                                          // with
                                                                                                          // Cyrillic а
            "Cyrillic homoglyph should be rejected");
    }

    @Test
    @DisplayName("Greek characters in identifier are rejected")
    void shouldRejectGreekHomoglyphs() {
        // Greek 'α' (U+03B1)
        assertThrows(SecurityViolationException.class, () -> IdentifierValidator.validate("t\u03B1ble"),
            "Greek homoglyph should be rejected");
    }

    @Test
    @DisplayName("Armenian characters in identifier are rejected")
    void shouldRejectArmenianHomoglyphs() {
        // Armenian 'ա' (U+0561)
        assertThrows(SecurityViolationException.class, () -> IdentifierValidator.validate("t\u0561ble"),
            "Armenian homoglyph should be rejected");
    }

    @Test
    @DisplayName("fullwidth Latin characters are rejected")
    void shouldRejectFullwidthHomoglyphs() {
        // Fullwidth 'Ａ' (U+FF21)
        assertThrows(SecurityViolationException.class, () -> IdentifierValidator.validate("\uFF21table"),
            "Fullwidth homoglyph should be rejected");
    }

    @Test
    @DisplayName("identifier exceeding max length is rejected")
    void shouldRejectLongIdentifier() {
        String longName = "a".repeat(129); // MAX_IDENTIFIER_LENGTH is 128
        assertThrows(SecurityViolationException.class, () -> IdentifierValidator.validate(longName));
    }

    @Test
    @DisplayName("identifier at max length is accepted")
    void shouldAcceptMaxLengthIdentifier() {
        String maxName = "a".repeat(128);
        assertDoesNotThrow(() -> IdentifierValidator.validate(maxName));
    }

    @Test
    @DisplayName("identifier ending with dot is rejected")
    void shouldRejectTrailingDot() {
        assertThrows(SecurityViolationException.class, () -> IdentifierValidator.validate("table."));
    }

    @Test
    @DisplayName("multi-part identifier validates each part")
    void shouldValidateEachPartOfMultiPartIdentifier() {
        assertDoesNotThrow(() -> IdentifierValidator.validate("schema.table"));
        assertDoesNotThrow(() -> IdentifierValidator.validate("catalog.schema.table"));
    }

    @Test
    @DisplayName("column name rejects dot")
    void shouldRejectDotInColumnName() {
        assertThrows(SecurityViolationException.class, () -> IdentifierValidator.validateColumnName("schema.column"),
            "Column name should not contain dots");
    }

    @Test
    @DisplayName("column name accepts valid identifier")
    void shouldAcceptValidColumnName() {
        assertDoesNotThrow(() -> IdentifierValidator.validateColumnName("user_name"));
        assertDoesNotThrow(() -> IdentifierValidator.validateColumnName("_private"));
        assertDoesNotThrow(() -> IdentifierValidator.validateColumnName("col123"));
    }

    @Test
    @DisplayName("column name rejects null and empty")
    void shouldRejectNullAndEmptyColumnName() {
        assertThrows(SecurityViolationException.class, () -> IdentifierValidator.validateColumnName(null));
        assertThrows(SecurityViolationException.class, () -> IdentifierValidator.validateColumnName(""));
    }

    @Test
    @DisplayName("identifier with special characters is rejected")
    void shouldRejectSpecialCharacters() {
        assertThrows(SecurityViolationException.class, () -> IdentifierValidator.validate("table-name"));
        assertThrows(SecurityViolationException.class, () -> IdentifierValidator.validate("table name"));
        assertThrows(SecurityViolationException.class, () -> IdentifierValidator.validate("table;DROP"));
    }

    @Test
    @DisplayName("identifier starting with digit is rejected")
    void shouldRejectDigitStart() {
        assertThrows(SecurityViolationException.class, () -> IdentifierValidator.validate("1table"));
    }

    @Test
    @DisplayName("resolveTableName from @Table annotation")
    void shouldResolveTableNameFromAnnotation() {
        // Use an entity class with @Table annotation
        String name = IdentifierValidator.resolveTableName(TableAnnotatedEntity.class);
        assertEquals("custom_table", name);
    }

    @Test
    @DisplayName("resolveTableName from @Entity annotation")
    void shouldResolveTableNameFromEntityAnnotation() {
        String name = IdentifierValidator.resolveTableName(EntityAnnotatedEntity.class);
        assertEquals("entity_named_table", name);
    }

    @Test
    @DisplayName("resolveTableName falls back to camelCase conversion")
    void shouldResolveTableNameFromClassName() {
        String name = IdentifierValidator.resolveTableName(SimpleEntity.class);
        assertEquals("simple_entity", name);
    }

    // ---- Test entity classes ----

    @jakarta.persistence.Table(name = "custom_table")
    @jakarta.persistence.Entity
    static class TableAnnotatedEntity {
        @jakarta.persistence.Id
        private Long id;
    }

    @jakarta.persistence.Entity(name = "entity_named_table")
    static class EntityAnnotatedEntity {
        @jakarta.persistence.Id
        private Long id;
    }

    @jakarta.persistence.Entity
    static class SimpleEntity {
        @jakarta.persistence.Id
        private Long id;
    }
}
