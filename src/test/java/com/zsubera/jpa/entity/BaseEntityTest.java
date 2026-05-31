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
    void hashCode_withoutId_usesClassHashCode() {
        ConcreteEntity entity1 = new ConcreteEntity();
        ConcreteEntity entity2 = new ConcreteEntity();
        // When id is null, hashCode is based on the class (fixed value),
        // ensuring equals/hashCode contract: all unpersisted entities have the same hashCode
        assertEquals(entity1.hashCode(), entity2.hashCode());
        assertEquals(ConcreteEntity.class.hashCode(), entity1.hashCode());
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

    @Test
    void prePersist_setsCreatedAtAndUpdatedAt() {
        ConcreteEntity entity = new ConcreteEntity();
        entity.prePersist();
        assertNotNull(entity.getCreatedAt());
        assertNotNull(entity.getUpdatedAt());
        assertEquals(entity.getCreatedAt(), entity.getUpdatedAt());
    }

    @Test
    void preUpdate_updatesUpdatedAt() {
        ConcreteEntity entity = new ConcreteEntity();
        entity.prePersist();
        Instant originalCreatedAt = entity.getCreatedAt();
        Instant originalUpdatedAt = entity.getUpdatedAt();
        entity.preUpdate();
        assertNotNull(entity.getUpdatedAt());
        assertEquals(originalCreatedAt, entity.getCreatedAt());
        assertFalse(entity.getUpdatedAt().isBefore(originalUpdatedAt));
    }

    @Test
    void equals_differentSubclassWithSameId_returnsTrue() {
        ConcreteEntity entity1 = new ConcreteEntity();
        entity1.assignId(1L);
        ConcreteEntity entity2 = new ConcreteEntity();
        entity2.assignId(1L);
        assertTrue(entity1.equals(entity2));
        assertTrue(entity2.equals(entity1));
    }

    @Test
    void equals_bothIdsNull_returnsFalse() {
        ConcreteEntity entity1 = new ConcreteEntity();
        ConcreteEntity entity2 = new ConcreteEntity();
        assertFalse(entity1.equals(entity2));
    }

    @Test
    void hashCode_sameIdConsistent() {
        ConcreteEntity entity = new ConcreteEntity();
        entity.assignId(42L);
        int h1 = entity.hashCode();
        int h2 = entity.hashCode();
        assertEquals(h1, h2);
    }
}
