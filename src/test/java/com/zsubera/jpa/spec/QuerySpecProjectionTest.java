package com.zsubera.jpa.spec;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class QuerySpecProjectionTest {

    static class TestEntity {
        private String name;
        private Integer status;

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

    // ==================== isProjectionMode ====================

    @Test
    void isProjectionMode_defaultFalse() {
        assertFalse(new QuerySpec<TestEntity>().isProjectionMode());
    }

    @Test
    void isProjectionMode_afterSelect() {
        QuerySpec<TestEntity> spec = new QuerySpec<TestEntity>().select(TestEntity::getName);
        assertTrue(spec.isProjectionMode());
    }

    @Test
    void isProjectionMode_afterMultipleSelect() {
        QuerySpec<TestEntity> spec = new QuerySpec<TestEntity>().select(TestEntity::getName, TestEntity::getStatus);
        assertTrue(spec.isProjectionMode());
    }

    @Test
    void isProjectionMode_onlyConditions_noProjection() {
        QuerySpec<TestEntity> spec = new QuerySpec<TestEntity>().eq(TestEntity::getName, "Alice");
        assertFalse(spec.isProjectionMode());
    }

    // ==================== select() ====================

    @Test
    void select_addsFields() {
        QuerySpec<TestEntity> spec = new QuerySpec<TestEntity>().select(TestEntity::getName, TestEntity::getStatus);

        assertEquals(2, spec.getProjectionFields().size());
    }

    @Test
    void select_withAggregation_mixedFields() {
        QuerySpec<TestEntity> spec = new QuerySpec<TestEntity>().select(TestEntity::getName, QuerySpec.count());

        assertEquals(2, spec.getProjectionFields().size());
    }

    // ==================== asDto ====================

    @Test
    void asDto_setsDtoClass() {
        QuerySpec<TestEntity> spec = new QuerySpec<TestEntity>().select(TestEntity::getName).asDto(String.class);

        assertEquals(String.class, spec.getProjectionDtoClass());
    }

    @Test
    void asDto_nullIfNotSet() {
        QuerySpec<TestEntity> spec = new QuerySpec<TestEntity>().select(TestEntity::getName);

        assertNull(spec.getProjectionDtoClass());
    }

    // ==================== 链式调用兼容性 ====================

    @Test
    void select_chainedWithConditions() {
        QuerySpec<TestEntity> spec = new QuerySpec<TestEntity>().select(TestEntity::getName)
            .eq(TestEntity::getStatus, 1).orderByAsc(TestEntity::getName);

        assertTrue(spec.isProjectionMode());
        assertEquals(1, spec.getProjectionFields().size());
    }

    @Test
    void conditions_thenSelect() {
        QuerySpec<TestEntity> spec =
            new QuerySpec<TestEntity>().eq(TestEntity::getStatus, 1).select(TestEntity::getName);

        assertTrue(spec.isProjectionMode());
    }

    // ==================== 聚合静态方法 ====================

    @Test
    void count_static() {
        AggregateSFunction<TestEntity, Long> c = QuerySpec.count();
        assertEquals(AggregateSFunction.AggregateType.COUNT, c.getAggregateType());
        assertEquals("count", c.getAlias());
        assertTrue(c.isCountAll());
    }

    @Test
    void sum_static() {
        AggregateSFunction<TestEntity, ?> s = QuerySpec.sum(TestEntity::getStatus);
        assertEquals(AggregateSFunction.AggregateType.SUM, s.getAggregateType());
        assertEquals("status_sum", s.getAlias());
    }

    @Test
    void avg_static() {
        AggregateSFunction<TestEntity, Double> a = QuerySpec.avg(TestEntity::getStatus);
        assertEquals(AggregateSFunction.AggregateType.AVG, a.getAggregateType());
        assertEquals("status_avg", a.getAlias());
    }

    @Test
    void max_static() {
        AggregateSFunction<TestEntity, ?> m = QuerySpec.max(TestEntity::getStatus);
        assertEquals(AggregateSFunction.AggregateType.MAX, m.getAggregateType());
        assertEquals("status_max", m.getAlias());
    }

    @Test
    void min_static() {
        AggregateSFunction<TestEntity, ?> m = QuerySpec.min(TestEntity::getStatus);
        assertEquals(AggregateSFunction.AggregateType.MIN, m.getAggregateType());
        assertEquals("status_min", m.getAlias());
    }

    // ==================== selectAs() ====================

    @Test
    void selectAs_customAlias() {
        QuerySpec<TestEntity> spec = new QuerySpec<TestEntity>().selectAs(TestEntity::getName, "user_name");

        assertEquals(1, spec.getProjectionFields().size());
        assertEquals(1, spec.getProjectionFieldsWithAlias().size());
    }

    @Test
    void selectAs_nullAlias_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> new QuerySpec<TestEntity>().selectAs(TestEntity::getName, null));
    }

    @Test
    void selectAs_blankAlias_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> new QuerySpec<TestEntity>().selectAs(TestEntity::getName, "  "));
    }

    @Test
    void selectAs_mixedWithSelect_defaultAndCustomAliases() {
        QuerySpec<TestEntity> spec = new QuerySpec<TestEntity>().select(TestEntity::getName) // default alias
            .selectAs(TestEntity::getStatus, "status_code"); // custom alias

        assertEquals(2, spec.getProjectionFields().size());
    }

    @Test
    void selectAs_aggregationCustomAlias() {
        QuerySpec<TestEntity> spec = new QuerySpec<TestEntity>().selectAs(QuerySpec.count(), "total_count")
            .selectAs(QuerySpec.sum(TestEntity::getStatus), "sum_status");

        assertEquals(2, spec.getProjectionFields().size());
    }

    // ==================== copy() 保留投影状态 ====================

    @Test
    void copy_preservesProjectionState() {
        QuerySpec<TestEntity> original =
            new QuerySpec<TestEntity>().select(TestEntity::getName, TestEntity::getStatus).asDto(String.class);

        QuerySpec<TestEntity> copy = original.copy();

        assertTrue(copy.isProjectionMode());
        assertEquals(2, copy.getProjectionFields().size());
        assertEquals(String.class, copy.getProjectionDtoClass());
    }

    @Test
    void copy_emptySpec_preservesProjection() {
        QuerySpec<TestEntity> original = new QuerySpec<TestEntity>().select(TestEntity::getName);

        QuerySpec<TestEntity> copy = original.copy();

        assertTrue(copy.isProjectionMode());
        assertEquals(1, copy.getProjectionFields().size());
    }

    // ==================== asDto 验证 ====================

    @Test
    void asDto_null_throws() {
        assertThrows(IllegalArgumentException.class, () -> new QuerySpec<TestEntity>().asDto(null));
    }

    // ==================== getProjectionFieldsWithAlias ====================

    @Test
    void getProjectionFieldsWithAlias_emptyWhenNoSelect() {
        QuerySpec<TestEntity> spec = new QuerySpec<TestEntity>();
        assertTrue(spec.getProjectionFieldsWithAlias().isEmpty());
    }

    @Test
    void getProjectionFieldsWithAlias_returnsAliasedFields() {
        QuerySpec<TestEntity> spec = new QuerySpec<TestEntity>().selectAs(TestEntity::getName, "name_alias");
        assertEquals(1, spec.getProjectionFieldsWithAlias().size());
    }

    // ==================== AggregateSFunction.apply ====================

    @Test
    void aggregateSFunction_apply_throwsUnsupported() {
        AggregateSFunction<TestEntity, Long> count = QuerySpec.count();
        assertThrows(UnsupportedOperationException.class, () -> count.apply(new TestEntity()));
    }
}
