package com.zsubera.jpa.util;

import static org.junit.jupiter.api.Assertions.*;

import com.zsubera.jpa.spec.QuerySpec;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

class PageableHelperTest {

    @Test
    void testUnsortedReturnsPageRequestWithNoSort() {
        Pageable pageable = PageableHelper.unsorted(0, 10);
        assertEquals(0, pageable.getPageNumber());
        assertEquals(10, pageable.getPageSize());
        assertFalse(pageable.getSort().isSorted());
    }

    @Test
    void testSortedReturnsPageRequestWithGivenSort() {
        Sort sort = Sort.by(Sort.Order.desc("name"), Sort.Order.asc("id"));
        Pageable pageable = PageableHelper.sorted(2, 20, sort);
        assertEquals(2, pageable.getPageNumber());
        assertEquals(20, pageable.getPageSize());
        assertEquals(sort, pageable.getSort());
    }

    @Test
    void testMergeNullPageableReturnsUnpaged() {
        Pageable result = PageableHelper.merge(null, new QuerySpec<>());
        assertTrue(result.isUnpaged());
    }

    @Test
    void testMergeWithOnlyQuerySpecSort() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.orderByAsc(TestEntity::getName);
        qs.orderByDesc(TestEntity::getValue);

        Pageable result = PageableHelper.merge(PageRequest.of(1, 5), qs);
        assertEquals(1, result.getPageNumber());
        assertEquals(5, result.getPageSize());
        assertTrue(result.getSort().isSorted());
        assertEquals(2, result.getSort().stream().count());
    }

    @Test
    void testMergeWithOnlyPageableSort() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        Pageable pageable = PageRequest.of(0, 10, Sort.by("id"));

        Pageable result = PageableHelper.merge(pageable, qs);
        assertEquals(Sort.by("id"), result.getSort());
    }

    @Test
    void testMergeCombinesBothSorts() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.orderByAsc(TestEntity::getName);

        Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Order.desc("id")));

        Pageable result = PageableHelper.merge(pageable, qs);
        assertTrue(result.getSort().isSorted());
        assertEquals(2, result.getSort().stream().count());
        assertEquals("name", result.getSort().stream().findFirst().get().getProperty());
        assertTrue(result.getSort().stream().findFirst().get().isAscending());
    }

    @Test
    void testMergeWithNeitherSorted() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        Pageable pageable = PageRequest.of(0, 10);

        Pageable result = PageableHelper.merge(pageable, qs);
        assertFalse(result.getSort().isSorted());
    }

    // Minimal entity for LambdaUtils
    static class TestEntity {
        private String name;
        private int value;

        public String getName() {
            return name;
        }

        public int getValue() {
            return value;
        }
    }
}
