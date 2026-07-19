package com.zsubera.jpa.template;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.Nullable;

/**
 * 共享的 JPA 查询构建工具类，消除 {@link MyJpaTemplate} 和
 * 查询构建逻辑之间的重复。
 *
 * <p>
 * 内部工具类——不建议应用代码直接使用。
 */
final class QueryBuildHelper {

    private QueryBuildHelper() {}

    /**
     * 构建基于 Specification 的 TypedQuery。
     *
     * @param entityManager EntityManager 实例
     * @param entityClass 实体类
     * @param spec 查询规范（可为 null）
     * @param sort 排序规则（可为 null）
     * @param maxResults 最大结果数（null 表示不限制）
     * @param <T> 实体类型
     * @return 构建的 TypedQuery
     */
    static <T> TypedQuery<T> buildSpecificationQuery(EntityManager entityManager, Class<T> entityClass,
        @Nullable Specification<T> spec, @Nullable Sort sort, @Nullable Integer maxResults) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> cq = cb.createQuery(entityClass);
        Root<T> root = cq.from(entityClass);
        if (spec != null) {
            jakarta.persistence.criteria.Predicate predicate = spec.toPredicate(root, cq, cb);
            if (predicate != null) {
                cq.where(predicate);
            }
        }
        applySort(cq, root, cb, sort);
        TypedQuery<T> query = entityManager.createQuery(cq);
        if (maxResults != null && maxResults >= 0) {
            query.setMaxResults(maxResults);
        }
        return query;
    }

    /**
     * 将 Spring Data {@link Sort} 应用到 JPA CriteriaQuery。
     */
    static <T> void applySort(CriteriaQuery<T> query, Root<T> root, CriteriaBuilder cb, @Nullable Sort sort) {
        if (sort != null && sort.isSorted()) {
            query.orderBy(sort.stream().map(order -> order.isAscending() ? cb.asc(root.get(order.getProperty()))
                : cb.desc(root.get(order.getProperty()))).toList());
        }
    }

    /**
     * 执行计数查询。
     *
     * <p>
     * 自动合并软删除过滤条件，确保计数结果与数据查询一致。
     */
    static <T> long executeCountQuery(EntityManager entityManager, Class<T> entityClass, Specification<T> spec) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> countCq = cb.createQuery(Long.class);
        Root<T> countRoot = countCq.from(entityClass);
        countCq.select(cb.count(countRoot));

        // 合并软删除过滤条件（与 MyJpaTemplate.shouldApplySoftDeleteFilter() 保持一致）
        Specification<T> combinedSpec = spec;
        String softDeleteFieldName = com.zsubera.jpa.softdelete.SoftDeleteHelper.findSoftDeleteField(entityClass);
        // ponytail: 检查 ThreadLocal 覆盖值，与 MyJpaTemplate.shouldApplySoftDeleteFilter() 保持一致。
        // 此前遗漏了 getAutoFilterOverride() 检查，导致 withAutoFilterOverride(false) 时
        // count 查询仍应用软删除过滤，与数据查询不一致（totalElements 偏小）。
        Boolean override = com.zsubera.jpa.repository.DefaultMyJpaRepository.getAutoFilterOverride();
        boolean autoFilterEnabled;
        if (override != null) {
            autoFilterEnabled = override;
        } else {
            com.zsubera.jpa.autoconfigure.MyJpaPlusGlobalConfig cfg =
                com.zsubera.jpa.autoconfigure.GlobalConfigHolder.getConfig();
            autoFilterEnabled = cfg == null || cfg.isSoftDeleteAutoFilter();
        }
        boolean applySoftDelete = softDeleteFieldName != null && autoFilterEnabled
            && !com.zsubera.jpa.repository.SoftDeleteContext.isIgnoreSoftDelete();
        if (applySoftDelete) {
            Specification<T> softDeleteSpec = com.zsubera.jpa.softdelete.SoftDeleteHelper.isNotDeleted(entityClass);
            if (spec != null) {
                combinedSpec = spec.and(softDeleteSpec);
            } else {
                combinedSpec = softDeleteSpec;
            }
        }

        if (combinedSpec != null) {
            // ponytail: 传入一个临时 CriteriaQuery 而非 null，以避免 Specification 实现中
            // 对 query 参数的方法调用（如 query.distinct() / query.groupBy()）抛出 NPE。
            // 传入 null 违反 JPA 契约——Specification.toPredicate() 的 CriteriaQuery 参数
            // 可能在任何实现中被访问，NPE 会导致整个 count 查询崩溃。
            // 使用独立的 countCq 进行计数查询，tempCq 仅用于满足 toPredicate() 签名。
            // COUNT 查询不受 tempCq 的 DISTINCT/GROUP BY 副作用影响。
            jakarta.persistence.criteria.CriteriaQuery<?> tempCq = cb.createQuery();
            jakarta.persistence.criteria.Predicate countPredicate = combinedSpec.toPredicate(countRoot, tempCq, cb);
            if (countPredicate != null) {
                countCq.where(countPredicate);
            }
        }
        TypedQuery<Long> countQuery = entityManager.createQuery(countCq);
        return countQuery.getSingleResult();
    }
}
