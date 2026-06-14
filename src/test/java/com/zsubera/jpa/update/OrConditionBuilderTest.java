package com.zsubera.jpa.update;

import static org.junit.jupiter.api.Assertions.*;

import com.zsubera.jpa.spec.SFunction;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class OrConditionBuilderTest {

    @Test
    void self_returnsThis() {
        UpdateSpec<TestEntity> updateSpec = new UpdateSpec<>(TestEntity.class);
        OrConditionBuilder<TestEntity, UpdateSpec<TestEntity>> builder =
            new OrConditionBuilder<>(updateSpec, new ArrayList<>());
        assertSame(builder, builder.self());
    }

    @Test
    void property_delegatesToParent() {
        UpdateSpec<TestEntity> updateSpec = new UpdateSpec<>(TestEntity.class);
        OrConditionBuilder<TestEntity, UpdateSpec<TestEntity>> builder =
            new OrConditionBuilder<>(updateSpec, new ArrayList<>());
        String property = builder.property(TestEntity::getName);
        assertEquals("name", property);
    }

    @Test
    void addCondition_addsLeafNode() {
        ArrayList<com.zsubera.jpa.update.AbstractBulkOperationSpec.BulkConditionNode> nodes = new ArrayList<>();
        UpdateSpec<TestEntity> updateSpec = new UpdateSpec<>(TestEntity.class);
        OrConditionBuilder<TestEntity, UpdateSpec<TestEntity>> builder = new OrConditionBuilder<>(updateSpec, nodes);
        builder.addCondition((root, cb) -> cb.equal(root.get("name"), "test"));
        assertEquals(1, nodes.size());
    }

    @Test
    void eqStrict_withValue_addsCondition() {
        ArrayList<com.zsubera.jpa.update.AbstractBulkOperationSpec.BulkConditionNode> nodes = new ArrayList<>();
        UpdateSpec<TestEntity> updateSpec = new UpdateSpec<>(TestEntity.class);
        OrConditionBuilder<TestEntity, UpdateSpec<TestEntity>> builder = new OrConditionBuilder<>(updateSpec, nodes);
        builder.eqStrict(TestEntity::getName, "test");
        assertFalse(nodes.isEmpty());
    }

    @Test
    void eqStrict_withNull_throws() {
        UpdateSpec<TestEntity> updateSpec = new UpdateSpec<>(TestEntity.class);
        OrConditionBuilder<TestEntity, UpdateSpec<TestEntity>> builder =
            new OrConditionBuilder<>(updateSpec, new ArrayList<>());
        assertThrows(IllegalArgumentException.class, () -> builder.eqStrict(TestEntity::getName, null));
    }

    @Test
    void neStrict_withValue_addsCondition() {
        ArrayList<com.zsubera.jpa.update.AbstractBulkOperationSpec.BulkConditionNode> nodes = new ArrayList<>();
        UpdateSpec<TestEntity> updateSpec = new UpdateSpec<>(TestEntity.class);
        OrConditionBuilder<TestEntity, UpdateSpec<TestEntity>> builder = new OrConditionBuilder<>(updateSpec, nodes);
        builder.neStrict(TestEntity::getName, "test");
        assertFalse(nodes.isEmpty());
    }

    @Test
    void neStrict_withNull_throws() {
        UpdateSpec<TestEntity> updateSpec = new UpdateSpec<>(TestEntity.class);
        OrConditionBuilder<TestEntity, UpdateSpec<TestEntity>> builder =
            new OrConditionBuilder<>(updateSpec, new ArrayList<>());
        assertThrows(IllegalArgumentException.class, () -> builder.neStrict(TestEntity::getName, null));
    }

    @Test
    void multiLike_withValidKeyword_addsCondition() {
        ArrayList<com.zsubera.jpa.update.AbstractBulkOperationSpec.BulkConditionNode> nodes = new ArrayList<>();
        UpdateSpec<TestEntity> updateSpec = new UpdateSpec<>(TestEntity.class);
        OrConditionBuilder<TestEntity, UpdateSpec<TestEntity>> builder = new OrConditionBuilder<>(updateSpec, nodes);
        builder.multiLike("test", TestEntity::getName);
        assertFalse(nodes.isEmpty());
    }

    @Test
    void multiLike_withNullKeyword_throws() {
        UpdateSpec<TestEntity> updateSpec = new UpdateSpec<>(TestEntity.class);
        OrConditionBuilder<TestEntity, UpdateSpec<TestEntity>> builder =
            new OrConditionBuilder<>(updateSpec, new ArrayList<>());
        assertThrows(IllegalArgumentException.class, () -> builder.multiLike(null, TestEntity::getName));
    }

    @Test
    void multiLike_withNullFields_throws() {
        UpdateSpec<TestEntity> updateSpec = new UpdateSpec<>(TestEntity.class);
        OrConditionBuilder<TestEntity, UpdateSpec<TestEntity>> builder =
            new OrConditionBuilder<>(updateSpec, new ArrayList<>());
        assertThrows(IllegalArgumentException.class, () -> builder.multiLike("test", (SFunction<TestEntity, ?>[])null));
    }

    @Test
    void multiLike_withEmptyFields_doesNotAdd() {
        ArrayList<com.zsubera.jpa.update.AbstractBulkOperationSpec.BulkConditionNode> nodes = new ArrayList<>();
        UpdateSpec<TestEntity> updateSpec = new UpdateSpec<>(TestEntity.class);
        OrConditionBuilder<TestEntity, UpdateSpec<TestEntity>> builder = new OrConditionBuilder<>(updateSpec, nodes);
        builder.multiLike("test");
        assertTrue(nodes.isEmpty());
    }

    @Test
    void multiLike_withEmptyKeyword_doesNotAdd() {
        ArrayList<com.zsubera.jpa.update.AbstractBulkOperationSpec.BulkConditionNode> nodes = new ArrayList<>();
        UpdateSpec<TestEntity> updateSpec = new UpdateSpec<>(TestEntity.class);
        OrConditionBuilder<TestEntity, UpdateSpec<TestEntity>> builder = new OrConditionBuilder<>(updateSpec, nodes);
        builder.multiLike("", TestEntity::getName);
        assertTrue(nodes.isEmpty());
    }

    @Test
    void multiLike_multipleFields_addsSingleNode() {
        ArrayList<com.zsubera.jpa.update.AbstractBulkOperationSpec.BulkConditionNode> nodes = new ArrayList<>();
        UpdateSpec<TestEntity> updateSpec = new UpdateSpec<>(TestEntity.class);
        OrConditionBuilder<TestEntity, UpdateSpec<TestEntity>> builder = new OrConditionBuilder<>(updateSpec, nodes);
        builder.multiLike("test", TestEntity::getName, TestEntity::getStatus);
        assertEquals(1, nodes.size());
    }

    @jakarta.persistence.Entity
    @jakarta.persistence.Table(name = "or_cond_builder_test_entity")
    static class TestEntity {
        @jakarta.persistence.Id
        @jakarta.persistence.GeneratedValue
        private Long id;
        private String name;
        private Integer status;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Integer getStatus() {
            return status;
        }

        public void setStatus(Integer status) {
            this.status = status;
        }
    }
}
