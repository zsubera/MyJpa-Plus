package com.zsubera.jpa.util;

import com.zsubera.jpa.spec.QuerySpec;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

/**
 * 用于在 {@link QuerySpec} / {@link Specification} 和 Spring Data 的 {@link Pageable} 之间实现无缝集成的工具类。
 *
 * <p>
 * 默认情况下，使用 {@code findAll(Specification, Pageable)} 时，Spring Data 会使用 {@link Pageable} 中的排序顺序 覆盖通过
 * {@link QuerySpec#orderByAsc} / {@link QuerySpec#orderByDesc} 设置的任何排序。此帮助类提供了解决此冲突的方法。
 *
 * <p>
 * 示例：
 *
 * <pre>{@code
 * // 不使用 PageableHelper — Pageable 排序覆盖 QuerySpec 排序：
 * Page<User> page = repository.findAll(new QuerySpec<User>().eq(User::getStatus, "ACTIVE").orderByAsc(User::getName),
 *     PageRequest.of(0, 20));
 *
 * // 使用 PageableHelper — 明确控制优先级：
 * Page<User> page = repository.findAll(spec, PageableHelper.unsorted(0, 20));
 * // 保留 QuerySpec 排序
 *
 * // 或者将 Pageable 排序与 QuerySpec 合并：
 * Pageable merged = PageableHelper.merge(PageRequest.of(0, 20, Sort.by("id")), spec);
 * }</pre>
 */
public final class PageableHelper {

    private PageableHelper() {}

    /**
     * 创建一个没有排序的 {@link PageRequest}，保留 {@link Specification} 上设置的任何排序 （例如来自 {@link QuerySpec#orderByAsc}）。
     *
     * @param page 从零开始的页码索引
     * @param size 每页大小
     * @return 没有排序的 Pageable
     */
    public static Pageable unsorted(int page, int size) {
        return PageRequest.of(page, size, Sort.unsorted());
    }

    /**
     * 将 {@link Pageable} 的排序与 {@link QuerySpec} 的排序合并。QuerySpec 排序优先级更高， 然后追加 Pageable 排序。这允许将 QuerySpec
     * 的内置排序与动态分页排序相结合。
     *
     * <p>
     * 如果 QuerySpec 没有排序，则使用 Pageable 排序。
     *
     * @param pageable 带有潜在排序的 pageable
     * @param querySpec 带有潜在内置排序的 QuerySpec
     * @return 一个组合了两种排序的新 Pageable（QuerySpec 排序在前，Pageable 排序在后）
     */
    public static Pageable merge(Pageable pageable, QuerySpec<?> querySpec) {
        if (pageable == null) {
            return Pageable.unpaged();
        }
        // 对 querySpec 参数添加空值检查
        if (querySpec == null) {
            return pageable;
        }
        Sort querySpecSort = querySpec.getSort();
        Sort pageableSort = pageable.getSort();
        Sort combined;
        if (querySpecSort.isSorted()) {
            // QuerySpec 排序优先，然后追加 Pageable 排序
            if (pageableSort.isSorted()) {
                combined = querySpecSort.and(pageableSort);
            } else {
                combined = querySpecSort;
            }
        } else {
            combined = pageableSort;
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), combined);
    }

    /**
     * 返回一个带有显式排序的 {@link Pageable}，以覆盖任何 QuerySpec 排序。当与 {@code findAll(spec, pageable)} 一起使用时，将应用此 pageable 的排序，而不是
     * {@link QuerySpec} 中定义的任何排序。
     *
     * @param page 从零开始的页码索引
     * @param size 每页大小
     * @param sort 要应用的排序
     * @return 带有给定排序的 Pageable
     */
    public static Pageable sorted(int page, int size, Sort sort) {
        return PageRequest.of(page, size, sort);
    }

    /**
     * 根据数据库方言确定流式查询的 fetchSize。
     *
     * <p>
     * PostgreSQL 需要 fetchSize > 0 以启用服务端游标进行流式查询。MySQL 使用 {@code Integer.MIN_VALUE} 以启用流式模式。其他数据库使用默认值（不设置提示）。
     *
     * @param em EntityManager 实例
     * @return fetchSize 值，0 表示不设置提示
     */
    public static int determineFetchSize(jakarta.persistence.EntityManager em) {
        try {
            Object urlObj = em.getEntityManagerFactory().getProperties().get("jakarta.persistence.jdbc.url");
            if (urlObj == null) {
                urlObj = em.getEntityManagerFactory().getProperties().get("hibernate.connection.url");
            }
            if (urlObj != null) {
                String lower = urlObj.toString().toLowerCase();
                if (lower.contains("postgresql")) {
                    return 100;
                }
                if (lower.contains("mysql")) {
                    return Integer.MIN_VALUE;
                }
            }
        } catch (SecurityException e) {
            org.slf4j.LoggerFactory.getLogger(PageableHelper.class)
                .warn("SecurityException while determining fetch size for JDBC URL: {}. "
                    + "Add --add-opens java.sql/java.sql=ALL-UNNAMED if needed.", e.getMessage());
        } catch (Exception e) {
            // 无法从 JDBC URL 确定 fetchSize
        }
        return 0;
    }
}
