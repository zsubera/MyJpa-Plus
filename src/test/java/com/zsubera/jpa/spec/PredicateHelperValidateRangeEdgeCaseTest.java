package com.zsubera.jpa.spec;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Edge-case tests for {@link PredicateHelper#validateRange(Comparable, Comparable)}.
 *
 * <p>Covers the cross-Number-type BigDecimal path, NaN handling, null parameters,
 * and incompatible type combinations that the basic tests don't exercise.
 */
class PredicateHelperValidateRangeEdgeCaseTest {

    // ===== null parameter paths =====

    @Test
    void validateRange_nullStart_throws() {
        assertThrows(IllegalArgumentException.class, () -> PredicateHelper.validateRange(null, 10));
    }

    @Test
    void validateRange_nullEnd_throws() {
        assertThrows(IllegalArgumentException.class, () -> PredicateHelper.validateRange(1, null));
    }

    @Test
    void validateRange_bothNull_throws() {
        assertThrows(IllegalArgumentException.class, () -> PredicateHelper.validateRange(null, null));
    }

    // ===== cross Number type path (BigDecimal fallback) =====

    @Test
    void validateRange_integerVsLong_validRange() {
        assertDoesNotThrow(() -> PredicateHelper.validateRange(1, 2L));
    }

    @Test
    void validateRange_integerVsLong_equalValues() {
        assertDoesNotThrow(() -> PredicateHelper.validateRange(5, 5L));
    }

    @Test
    void validateRange_integerVsLong_startGreaterThanEnd_throws() {
        assertThrows(IllegalArgumentException.class, () -> PredicateHelper.validateRange(10, 5L));
    }

    @Test
    void validateRange_longVsInteger_validRange() {
        assertDoesNotThrow(() -> PredicateHelper.validateRange(1L, 2));
    }

    @Test
    void validateRange_longVsInteger_startGreaterThanEnd_throws() {
        assertThrows(IllegalArgumentException.class, () -> PredicateHelper.validateRange(100L, 50));
    }

    @Test
    void validateRange_integerVsDouble_validRange() {
        assertDoesNotThrow(() -> PredicateHelper.validateRange(1, 2.5));
    }

    @Test
    void validateRange_integerVsDouble_startGreaterThanEnd_throws() {
        assertThrows(IllegalArgumentException.class, () -> PredicateHelper.validateRange(10, 5.5));
    }

    @Test
    void validateRange_longMaxValue_validRange() {
        assertDoesNotThrow(() -> PredicateHelper.validateRange(0, Long.MAX_VALUE));
    }

    @Test
    void validateRange_longMaxValue_startGreaterThanEnd_throws() {
        assertThrows(IllegalArgumentException.class, () -> PredicateHelper.validateRange(Long.MAX_VALUE, 0));
    }

    // ===== incompatible types (non-Number) =====

    @Test
    void validateRange_incompatibleTypes_throws() {
        assertThrows(IllegalArgumentException.class, () -> PredicateHelper.validateRange("abc", 123));
    }

    @Test
    void validateRange_incompatibleTypesReversed_throws() {
        assertThrows(IllegalArgumentException.class, () -> PredicateHelper.validateRange(123, "abc"));
    }

    // ===== same Comparable type paths =====

    @Test
    void validateRange_strings_validRange() {
        assertDoesNotThrow(() -> PredicateHelper.validateRange("apple", "banana"));
    }

    @Test
    void validateRange_strings_startGreaterThanEnd_throws() {
        assertThrows(IllegalArgumentException.class, () -> PredicateHelper.validateRange("zebra", "apple"));
    }

    @Test
    void validateRange_strings_equalValues() {
        assertDoesNotThrow(() -> PredicateHelper.validateRange("same", "same"));
    }

    // ===== Float/Double edge cases =====

    @Test
    void validateRange_doubleVsFloat_validRange() {
        assertDoesNotThrow(() -> PredicateHelper.validateRange(1.0, 2.0f));
    }

    @Test
    void validateRange_doubleVsFloat_startGreaterThanEnd_throws() {
        assertThrows(IllegalArgumentException.class, () -> PredicateHelper.validateRange(10.0, 5.0f));
    }

    // ===== Infinity values (cross-Number path) =====

    @Test
    void validateRange_positiveInfinity_throws() {
        assertThrows(IllegalArgumentException.class, () -> PredicateHelper.validateRange(0, Double.POSITIVE_INFINITY));
    }

    @Test
    void validateRange_negativeInfinity_throws() {
        assertThrows(IllegalArgumentException.class, () -> PredicateHelper.validateRange(Double.NEGATIVE_INFINITY, 0));
    }

    @Test
    void validateRange_bothInfinity_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> PredicateHelper.validateRange(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY));
    }

    // ===== escapeLikeWildcards additional coverage =====

    @Test
    void escapeLikeWildcards_multipleSpecialChars() {
        assertEquals("\\%\\_\\\\test", PredicateHelper.escapeLikeWildcards("%_\\test"));
    }

    @Test
    void escapeLikeWildcards_noSpecialChars_unchanged() {
        assertEquals("hello", PredicateHelper.escapeLikeWildcards("hello"));
    }

    @Test
    void LIKE_ESCAPE_CHAR_isBackslash() {
        assertEquals('\\', PredicateHelper.LIKE_ESCAPE_CHAR);
    }
}
