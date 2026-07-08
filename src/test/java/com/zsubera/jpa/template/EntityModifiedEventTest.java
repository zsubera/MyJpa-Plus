package com.zsubera.jpa.template;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link EntityModifiedEvent} — constructor variants and accessors.
 */
class EntityModifiedEventTest {

    @Test
    void classConstructorSetsEntityNameAndRows() {
        EntityModifiedEvent event = new EntityModifiedEvent(String.class, 5);
        assertEquals("java.lang.String", event.getEntityName());
        assertEquals(5, event.getAffectedRows());
    }

    @Test
    void stringConstructorSetsEntityNameAndRows() {
        EntityModifiedEvent event = new EntityModifiedEvent("Order", 10);
        assertEquals("Order", event.getEntityName());
        assertEquals(10, event.getAffectedRows());
    }

    @Test
    void classConstructorSetsSource() {
        EntityModifiedEvent event = new EntityModifiedEvent(String.class, 3);
        // ponytail: 两个构造函数统一使用 String 作为 source，确保类型一致性
        assertEquals(String.class.getName(), event.getSource());
    }

    @Test
    void stringConstructorSetsSource() {
        EntityModifiedEvent event = new EntityModifiedEvent("User", 2);
        assertEquals("User", event.getSource());
    }

    @Test
    void zeroAffectedRows() {
        EntityModifiedEvent event = new EntityModifiedEvent("Entity", 0);
        assertEquals(0, event.getAffectedRows());
    }

    @Test
    void largeAffectedRows() {
        EntityModifiedEvent event = new EntityModifiedEvent("Entity", Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, event.getAffectedRows());
    }
}
