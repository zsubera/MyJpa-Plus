package com.zsubera.jpa.util;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 用于构建 JPA {@code IN} 和 {@code NOT IN} 子句的工具类，消除了重复的 {@code CriteriaBuilder.In} 构建模式。
 *
 * <p>
 * 被 {@link com.zsubera.jpa.spec.ConditionBuilder}、{@link com.zsubera.jpa.spec.SubQuerySpec} 和
 * {@link com.zsubera.jpa.update.AbstractBulkOperationSpec} 内部使用，以避免在三个实现中重复相同的 IN 构建逻辑。
 *
 * <p>
 * <strong>大型 IN 子句处理：</strong>大多数数据库对 IN 子句中的参数数量有限制 （Oracle: 1000, SQL Server: 2100）。此类会自动将大型 IN 子句拆分为多个 OR 连接的批次，
 * 以避免超出这些限制。
 */
public final class InClauseBuilder {

    private static final Logger log = LoggerFactory.getLogger(InClauseBuilder.class);

    /** 单个 IN 子句中的最大参数数量。此值为保守值，支持所有主流数据库（Oracle 限制为 1000）。 */
    public static final int MAX_IN_CLAUSE_SIZE = 1000;

    private InClauseBuilder() {}

    /**
     * 构建 {@code IN} 谓词：{@code field IN (values)}。
     *
     * <p>
     * 如果值的数量超过 {@link #MAX_IN_CLAUSE_SIZE}，IN 子句会自动拆分为多个 OR 连接的批次： {@code field IN (1..1000) OR
     * field IN (1001..2000) OR ...}
     *
     * @param cb CriteriaBuilder 实例
     * @param path 字段路径
     * @param values 要匹配的值（数组形式）
     * @return IN 谓词
     * @throws IllegalArgumentException 如果 values 为 null 或空
     */
    public static Predicate in(CriteriaBuilder cb, Path<?> path, Object... values) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("values must not be empty");
        }
        if (values.length <= MAX_IN_CLAUSE_SIZE) {
            return buildSingleIn(cb, path, values);
        }
        return buildBatchedIn(cb, path, Arrays.asList(values));
    }

    /**
     * 构建 {@code IN} 谓词：{@code field IN (values)}。
     *
     * <p>
     * 如果值的数量超过 {@link #MAX_IN_CLAUSE_SIZE}，IN 子句会自动拆分为多个 OR 连接的批次。
     *
     * @param cb CriteriaBuilder 实例
     * @param path 字段路径
     * @param values 要匹配的值（集合形式）
     * @return IN 谓词
     * @throws IllegalArgumentException 如果 values 为 null 或空
     */
    public static Predicate in(CriteriaBuilder cb, Path<?> path, Collection<?> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("values must not be empty");
        }
        if (values.size() <= MAX_IN_CLAUSE_SIZE) {
            return buildSingleIn(cb, path, values);
        }
        return buildBatchedIn(cb, path, values);
    }

    /**
     * 构建 {@code NOT IN} 谓词：{@code field NOT IN (values)}。
     *
     * <p>
     * 如果值的数量超过 {@link #MAX_IN_CLAUSE_SIZE}，NOT IN 子句会自动拆分为多个 AND NOT 连接的批次。
     *
     * @param cb CriteriaBuilder 实例
     * @param path 字段路径
     * @param values 要匹配的值（数组形式）
     * @return NOT IN 谓词
     * @throws IllegalArgumentException 如果 values 为 null 或空
     */
    public static Predicate notIn(CriteriaBuilder cb, Path<?> path, Object... values) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("values must not be empty");
        }
        if (values.length <= MAX_IN_CLAUSE_SIZE) {
            return cb.not(buildSingleIn(cb, path, values));
        }
        return buildBatchedNotIn(cb, path, Arrays.asList(values));
    }

    /**
     * 构建 {@code NOT IN} 谓词：{@code field NOT IN (values)}。
     *
     * <p>
     * 如果值的数量超过 {@link #MAX_IN_CLAUSE_SIZE}，NOT IN 子句会自动拆分为多个 AND NOT 连接的批次。
     *
     * @param cb CriteriaBuilder 实例
     * @param path 字段路径
     * @param values 要匹配的值（集合形式）
     * @return NOT IN 谓词
     * @throws IllegalArgumentException 如果 values 为 null 或空
     */
    public static Predicate notIn(CriteriaBuilder cb, Path<?> path, Collection<?> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("values must not be empty");
        }
        if (values.size() <= MAX_IN_CLAUSE_SIZE) {
            return cb.not(buildSingleIn(cb, path, values));
        }
        return buildBatchedNotIn(cb, path, values);
    }

    private static Predicate buildSingleIn(CriteriaBuilder cb, Path<?> path, Object[] values) {
        CriteriaBuilder.In<Object> in = cb.in(path);
        for (Object v : values) {
            in.value(v);
        }
        return in;
    }

    private static Predicate buildSingleIn(CriteriaBuilder cb, Path<?> path, Collection<?> values) {
        CriteriaBuilder.In<Object> in = cb.in(path);
        for (Object v : values) {
            in.value(v);
        }
        return in;
    }

    private static Predicate buildBatchedIn(CriteriaBuilder cb, Path<?> path, Collection<?> values) {
        if (log.isDebugEnabled()) {
            log.debug("IN clause has {} values, exceeding limit of {}. Splitting into batches.", values.size(),
                MAX_IN_CLAUSE_SIZE);
        }
        List<Predicate> batchPredicates = new ArrayList<>();
        List<Object> batch = new ArrayList<>();
        for (Object v : values) {
            batch.add(v);
            if (batch.size() >= MAX_IN_CLAUSE_SIZE) {
                batchPredicates.add(buildSingleIn(cb, path, batch));
                batch = new ArrayList<>();
            }
        }
        if (!batch.isEmpty()) {
            batchPredicates.add(buildSingleIn(cb, path, batch));
        }
        return cb.or(batchPredicates.toArray(new Predicate[0]));
    }

    private static Predicate buildBatchedNotIn(CriteriaBuilder cb, Path<?> path, Collection<?> values) {
        if (log.isDebugEnabled()) {
            log.debug("NOT IN clause has {} values, exceeding limit of {}. Splitting into batches.", values.size(),
                MAX_IN_CLAUSE_SIZE);
        }
        List<Predicate> batchPredicates = new ArrayList<>();
        List<Object> batch = new ArrayList<>();
        for (Object v : values) {
            batch.add(v);
            if (batch.size() >= MAX_IN_CLAUSE_SIZE) {
                batchPredicates.add(cb.not(buildSingleIn(cb, path, batch)));
                batch = new ArrayList<>();
            }
        }
        if (!batch.isEmpty()) {
            batchPredicates.add(cb.not(buildSingleIn(cb, path, batch)));
        }
        return cb.and(batchPredicates.toArray(new Predicate[0]));
    }
}
