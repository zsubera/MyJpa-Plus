package com.zsubera.jpa.spec;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class NotGroupTest {

    @Test
    void create_withValidRoot_returnsNotGroup() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        NotGroup<TestEntity> notGroup = NotGroup.create(qs);
        assertNotNull(notGroup);
    }

    @Test
    void create_withNullRoot_throws() {
        assertThrows(IllegalArgumentException.class, () -> NotGroup.create(null));
    }

    @Test
    void conditions_returnsRootCurrentGroup() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        NotGroup<TestEntity> notGroup = NotGroup.create(qs);
        assertNotNull(notGroup.conditions());
    }

    @Test
    void eq_addsCondition() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        NotGroup<TestEntity> notGroup = NotGroup.create(qs);
        notGroup.eq(TestEntity::getName, "test");
        assertFalse(notGroup.conditions().isEmpty());
    }

    @Test
    void ne_addsCondition() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        NotGroup<TestEntity> notGroup = NotGroup.create(qs);
        notGroup.ne(TestEntity::getName, "test");
        assertFalse(notGroup.conditions().isEmpty());
    }

    @Test
    void gt_addsCondition() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        NotGroup<TestEntity> notGroup = NotGroup.create(qs);
        notGroup.gt(TestEntity::getStatus, 5);
        assertFalse(notGroup.conditions().isEmpty());
    }

    @Test
    void ge_addsCondition() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        NotGroup<TestEntity> notGroup = NotGroup.create(qs);
        notGroup.ge(TestEntity::getStatus, 5);
        assertFalse(notGroup.conditions().isEmpty());
    }

    @Test
    void lt_addsCondition() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        NotGroup<TestEntity> notGroup = NotGroup.create(qs);
        notGroup.lt(TestEntity::getStatus, 5);
        assertFalse(notGroup.conditions().isEmpty());
    }

    @Test
    void le_addsCondition() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        NotGroup<TestEntity> notGroup = NotGroup.create(qs);
        notGroup.le(TestEntity::getStatus, 5);
        assertFalse(notGroup.conditions().isEmpty());
    }

    @Test
    void isNull_addsCondition() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        NotGroup<TestEntity> notGroup = NotGroup.create(qs);
        notGroup.isNull(TestEntity::getName);
        assertFalse(notGroup.conditions().isEmpty());
    }

    @Test
    void isNotNull_addsCondition() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        NotGroup<TestEntity> notGroup = NotGroup.create(qs);
        notGroup.isNotNull(TestEntity::getName);
        assertFalse(notGroup.conditions().isEmpty());
    }

    @Test
    void like_addsCondition() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        NotGroup<TestEntity> notGroup = NotGroup.create(qs);
        notGroup.like(TestEntity::getName, "test");
        assertFalse(notGroup.conditions().isEmpty());
    }

    @Test
    void notLike_addsCondition() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        NotGroup<TestEntity> notGroup = NotGroup.create(qs);
        notGroup.notLike(TestEntity::getName, "test");
        assertFalse(notGroup.conditions().isEmpty());
    }

    @Test
    void startsWith_addsCondition() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        NotGroup<TestEntity> notGroup = NotGroup.create(qs);
        notGroup.startsWith(TestEntity::getName, "test");
        assertFalse(notGroup.conditions().isEmpty());
    }

    @Test
    void endsWith_addsCondition() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        NotGroup<TestEntity> notGroup = NotGroup.create(qs);
        notGroup.endsWith(TestEntity::getName, "test");
        assertFalse(notGroup.conditions().isEmpty());
    }

    @Test
    void in_addsCondition() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        NotGroup<TestEntity> notGroup = NotGroup.create(qs);
        notGroup.in(TestEntity::getStatus, 1, 2, 3);
        assertFalse(notGroup.conditions().isEmpty());
    }

    @Test
    void notIn_addsCondition() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        NotGroup<TestEntity> notGroup = NotGroup.create(qs);
        notGroup.notIn(TestEntity::getStatus, 1, 2);
        assertFalse(notGroup.conditions().isEmpty());
    }

    @Test
    void between_addsCondition() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        NotGroup<TestEntity> notGroup = NotGroup.create(qs);
        notGroup.between(TestEntity::getStatus, 1, 10);
        assertFalse(notGroup.conditions().isEmpty());
    }

    @Test
    void notBetween_addsCondition() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        NotGroup<TestEntity> notGroup = NotGroup.create(qs);
        notGroup.notBetween(TestEntity::getStatus, 1, 10);
        assertFalse(notGroup.conditions().isEmpty());
    }

    @Test
    void self_returnsNotGroup() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        NotGroup<TestEntity> notGroup = NotGroup.create(qs);
        assertSame(notGroup, notGroup.self());
    }

    @Test
    void multipleConditions_createsMultipleNodes() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        NotGroup<TestEntity> notGroup = NotGroup.create(qs);
        notGroup.eq(TestEntity::getName, "a").ne(TestEntity::getName, "b").gt(TestEntity::getStatus, 5);
        assertEquals(3, notGroup.conditions().size());
    }

    @jakarta.persistence.Entity
    @jakarta.persistence.Table(name = "not_group_test_entity")
    static class TestEntity {
        @jakarta.persistence.Id
        @jakarta.persistence.GeneratedValue
        private Long id;
        private String name;
        private Integer status;
        @jakarta.persistence.ManyToOne
        private TestEntity parent;

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

        public TestEntity getParent() {
            return parent;
        }

        public void setParent(TestEntity parent) {
            this.parent = parent;
        }
    }
}
