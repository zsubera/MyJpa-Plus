package com.zsubera.jpa.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class BaseEntityTest {

    private static class ConcreteEntity extends BaseEntity {
        private String name;

        public void assignId(Long id) {
            setId(id);
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    @Test
    void equals_sameObject_returnsTrue() {
        ConcreteEntity entity = new ConcreteEntity();
        entity.assignId(1L);
        assertTrue(entity.equals(entity));
    }

    @Test
    void equals_bothHaveSameId_returnsTrue() {
        ConcreteEntity entity1 = new ConcreteEntity();
        entity1.assignId(1L);
        ConcreteEntity entity2 = new ConcreteEntity();
        entity2.assignId(1L);
        assertTrue(entity1.equals(entity2));
    }

    @Test
    void equals_bothHaveDifferentId_returnsFalse() {
        ConcreteEntity entity1 = new ConcreteEntity();
        entity1.assignId(1L);
        ConcreteEntity entity2 = new ConcreteEntity();
        entity2.assignId(2L);
        assertFalse(entity1.equals(entity2));
    }

    @Test
    void equals_oneIdNull_returnsFalse() {
        ConcreteEntity entity1 = new ConcreteEntity();
        entity1.assignId(1L);
        ConcreteEntity entity2 = new ConcreteEntity();
        assertFalse(entity1.equals(entity2));
    }

    @Test
    void equals_nonBaseEntity_returnsFalse() {
        ConcreteEntity entity = new ConcreteEntity();
        entity.assignId(1L);
        assertFalse(entity.equals("not a BaseEntity"));
    }

    @Test
    void equals_null_returnsFalse() {
        ConcreteEntity entity = new ConcreteEntity();
        entity.assignId(1L);
        assertFalse(entity.equals(null));
    }

    @Test
    void hashCode_withId_basedOnId() {
        ConcreteEntity entity1 = new ConcreteEntity();
        entity1.assignId(1L);
        ConcreteEntity entity2 = new ConcreteEntity();
        entity2.assignId(1L);
        assertEquals(entity1.hashCode(), entity2.hashCode());
    }

    @Test
    void hashCode_withoutId_usesSuperHashCode() {
        ConcreteEntity entity = new ConcreteEntity();
        assertEquals(System.identityHashCode(entity), entity.hashCode());
    }

    @Test
    void getterSetter_id() {
        ConcreteEntity entity = new ConcreteEntity();
        entity.assignId(1L);
        assertEquals(1L, entity.getId());
    }

    @Test
    void getterSetter_createdAt() {
        ConcreteEntity entity = new ConcreteEntity();
        Instant now = Instant.now();
        entity.setCreatedAt(now);
        assertEquals(now, entity.getCreatedAt());
    }

    @Test
    void getterSetter_updatedAt() {
        ConcreteEntity entity = new ConcreteEntity();
        Instant now = Instant.now();
        entity.setUpdatedAt(now);
        assertEquals(now, entity.getUpdatedAt());
    }
}
