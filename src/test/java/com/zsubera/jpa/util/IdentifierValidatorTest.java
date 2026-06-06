package com.zsubera.jpa.util;

import static org.junit.jupiter.api.Assertions.*;

import com.zsubera.jpa.exception.MyJpaPlusException;
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
    void validate_null_throwsException() {
        assertThrows(MyJpaPlusException.class, () -> IdentifierValidator.validate(null));
    }

    @Test
    void validate_empty_throwsException() {
        assertThrows(MyJpaPlusException.class, () -> IdentifierValidator.validate(""));
    }

    @Test
    void validate_identifierWithSpaces_throwsException() {
        assertThrows(MyJpaPlusException.class, () -> IdentifierValidator.validate("user name"));
    }

    @Test
    void validate_identifierWithSpecialChars_throwsException() {
        assertThrows(MyJpaPlusException.class, () -> IdentifierValidator.validate("user@name"));
    }

    @Test
    void validate_identifierWithSemicolon_throwsException() {
        assertThrows(MyJpaPlusException.class, () -> IdentifierValidator.validate("users; DROP TABLE"));
    }

    @Test
    void validate_identifierWithSingleQuote_throwsException() {
        assertThrows(MyJpaPlusException.class, () -> IdentifierValidator.validate("user's"));
    }

    @Test
    void validate_identifierTooLong_throwsException() {
        String longIdentifier = "a".repeat(129);
        assertThrows(MyJpaPlusException.class, () -> IdentifierValidator.validate(longIdentifier));
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
}
