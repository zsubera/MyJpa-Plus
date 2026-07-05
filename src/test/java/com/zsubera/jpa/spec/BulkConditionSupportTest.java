package com.zsubera.jpa.spec;

import static org.junit.jupiter.api.Assertions.*;

import com.zsubera.jpa.update.DeleteSpec;
import org.junit.jupiter.api.Test;

class BulkConditionSupportTest {

    @Test
    void eqStrict_nullField_throws() {
        DeleteSpec<Object> spec = new DeleteSpec<>(Object.class);
        assertThrows(IllegalArgumentException.class, () -> spec.eqStrict(null, "value"));
    }

    @Test
    void eqStrict_nullValue_throws() {
        DeleteSpec<Object> spec = new DeleteSpec<>(Object.class);
        assertThrows(IllegalArgumentException.class, () -> spec.eqStrict(null, null));
    }

    @Test
    void neStrict_nullValue_throws() {
        DeleteSpec<Object> spec = new DeleteSpec<>(Object.class);
        assertThrows(IllegalArgumentException.class, () -> spec.neStrict(null, null));
    }

    @Test
    void eq_nullField_throws() {
        DeleteSpec<Object> spec = new DeleteSpec<>(Object.class);
        assertThrows(IllegalArgumentException.class, () -> spec.eq(null, "value"));
    }

    @Test
    void ne_nullField_throws() {
        DeleteSpec<Object> spec = new DeleteSpec<>(Object.class);
        assertThrows(IllegalArgumentException.class, () -> spec.ne(null, "value"));
    }

    @Test
    void gt_nullField_throws() {
        DeleteSpec<Object> spec = new DeleteSpec<>(Object.class);
        assertThrows(IllegalArgumentException.class, () -> spec.gt(null, 5));
    }

    @Test
    void gt_nullValue_throws() {
        DeleteSpec<Object> spec = new DeleteSpec<>(Object.class);
        assertThrows(IllegalArgumentException.class, () -> spec.gt(null, null));
    }

    @Test
    void ge_nullField_throws() {
        DeleteSpec<Object> spec = new DeleteSpec<>(Object.class);
        assertThrows(IllegalArgumentException.class, () -> spec.ge(null, 5));
    }

    @Test
    void lt_nullField_throws() {
        DeleteSpec<Object> spec = new DeleteSpec<>(Object.class);
        assertThrows(IllegalArgumentException.class, () -> spec.lt(null, 5));
    }

    @Test
    void le_nullField_throws() {
        DeleteSpec<Object> spec = new DeleteSpec<>(Object.class);
        assertThrows(IllegalArgumentException.class, () -> spec.le(null, 5));
    }

    @Test
    void like_nullField_throws() {
        DeleteSpec<Object> spec = new DeleteSpec<>(Object.class);
        assertThrows(IllegalArgumentException.class, () -> spec.like(null, "%test%"));
    }

    @Test
    void like_nullValue_throws() {
        DeleteSpec<Object> spec = new DeleteSpec<>(Object.class);
        assertThrows(IllegalArgumentException.class, () -> spec.like(null, null));
    }

    @Test
    void notLike_nullField_throws() {
        DeleteSpec<Object> spec = new DeleteSpec<>(Object.class);
        assertThrows(IllegalArgumentException.class, () -> spec.notLike(null, "%test%"));
    }

    @Test
    void startsWith_nullField_throws() {
        DeleteSpec<Object> spec = new DeleteSpec<>(Object.class);
        assertThrows(IllegalArgumentException.class, () -> spec.startsWith(null, "test"));
    }

    @Test
    void endsWith_nullField_throws() {
        DeleteSpec<Object> spec = new DeleteSpec<>(Object.class);
        assertThrows(IllegalArgumentException.class, () -> spec.endsWith(null, "test"));
    }

    @Test
    void notStartsWith_nullField_throws() {
        DeleteSpec<Object> spec = new DeleteSpec<>(Object.class);
        assertThrows(IllegalArgumentException.class, () -> spec.notStartsWith(null, "test"));
    }

    @Test
    void notEndsWith_nullField_throws() {
        DeleteSpec<Object> spec = new DeleteSpec<>(Object.class);
        assertThrows(IllegalArgumentException.class, () -> spec.notEndsWith(null, "test"));
    }

    @Test
    void eqIgnoreCase_nullField_throws() {
        DeleteSpec<Object> spec = new DeleteSpec<>(Object.class);
        assertThrows(IllegalArgumentException.class, () -> spec.eqIgnoreCase(null, "test"));
    }

    @Test
    void neIgnoreCase_nullField_throws() {
        DeleteSpec<Object> spec = new DeleteSpec<>(Object.class);
        assertThrows(IllegalArgumentException.class, () -> spec.neIgnoreCase(null, "test"));
    }

    @Test
    void likeIgnoreCase_nullField_throws() {
        DeleteSpec<Object> spec = new DeleteSpec<>(Object.class);
        assertThrows(IllegalArgumentException.class, () -> spec.likeIgnoreCase(null, "%test%"));
    }

    @Test
    void in_nullField_throws() {
        DeleteSpec<Object> spec = new DeleteSpec<>(Object.class);
        assertThrows(IllegalArgumentException.class, () -> spec.in(null, (Object[])null));
    }

    @Test
    void notIn_nullField_throws() {
        DeleteSpec<Object> spec = new DeleteSpec<>(Object.class);
        assertThrows(IllegalArgumentException.class, () -> spec.notIn(null, (Object[])null));
    }

    @Test
    void isNull_nullField_throws() {
        DeleteSpec<Object> spec = new DeleteSpec<>(Object.class);
        assertThrows(IllegalArgumentException.class, () -> spec.isNull(null));
    }

    @Test
    void isNotNull_nullField_throws() {
        DeleteSpec<Object> spec = new DeleteSpec<>(Object.class);
        assertThrows(IllegalArgumentException.class, () -> spec.isNotNull(null));
    }

    @Test
    void isEmpty_nullField_throws() {
        DeleteSpec<Object> spec = new DeleteSpec<>(Object.class);
        assertThrows(IllegalArgumentException.class, () -> spec.isEmpty(null));
    }

    @Test
    void isNotEmpty_nullField_throws() {
        DeleteSpec<Object> spec = new DeleteSpec<>(Object.class);
        assertThrows(IllegalArgumentException.class, () -> spec.isNotEmpty(null));
    }
}
