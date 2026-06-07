package com.zsubera.jpa.util;

import com.zsubera.jpa.exception.MyJpaPlusException;
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
 *
 * <p>
 * <strong>配置优先级：</strong>Spring Boot 配置 > 系统属性 > 默认值
 *
 * <ul>
 * <li>Spring Boot: {@code myjpa-plus.in-clause.max-size} / {@code myjpa-plus.in-clause.hard-limit}
 * <li>系统属性: {@code -Dmyjpa-plus.in-clause-max-size} / {@code -Dmyjpa-plus.in-clause-hard-limit}
 * <li>默认值: max-size=1000, hard-limit=5000
 * </ul>
 *
 * <p>
 * 不同数据库的限制参考：
 * <ul>
 * <li>Oracle: 1000</li>
 * <li>SQL Server: 2100</li>
 * <li>MySQL: ~65535</li>
 * <li>PostgreSQL: 无限制（建议设置合理值避免 SQL 过长）</li>
 * </ul>
 */
public final class InClauseBuilder {

    private static final Logger log = LoggerFactory.getLogger(InClauseBuilder.class);

    /** IN 子句值数量的上限，超过此限制将抛出异常 */
    private static final int MAX_ALLOWED_VALUE = 100_000;

    /**
     * IN 子句的硬限制。超过此限制时将抛出异常，防止数据库性能问题。
     *
     * <p>
     * 配置优先级：Spring Boot 配置 > 系统属性 {@code myjpa-plus.in-clause-hard-limit} > 默认值 (5000)。
     */
    private static volatile int hardLimit;

    /**
     * 单个 IN 子句中的最大参数数量。
     *
     * <p>
     * 配置优先级：Spring Boot 配置 > 系统属性 {@code myjpa-plus.in-clause-max-size} > 默认值 (1000)。
     */
    private static volatile int maxInClauseSize;

    static {
        int configured = 1000;
        String prop = System.getProperty("myjpa-plus.in-clause-max-size");
        if (prop != null) {
            try {
                int val = Integer.parseInt(prop);
                if (val > 0 && val <= MAX_ALLOWED_VALUE) {
                    configured = val;
                } else if (val > MAX_ALLOWED_VALUE) {
                    log.warn("myjpa-plus.in-clause-max-size value ({}) exceeds upper limit ({}). Using {}.", val,
                        MAX_ALLOWED_VALUE, MAX_ALLOWED_VALUE);
                    configured = MAX_ALLOWED_VALUE;
                }
            } catch (NumberFormatException ignored) {
                // 使用默认值
            }
        }
        maxInClauseSize = configured;

        int hardConfigured = 5000;
        String hardProp = System.getProperty("myjpa-plus.in-clause-hard-limit");
        if (hardProp != null) {
            try {
                int val = Integer.parseInt(hardProp);
                if (val > 0) {
                    hardConfigured = val;
                }
            } catch (NumberFormatException ignored) {
                // 使用默认值
            }
        }
        hardLimit = hardConfigured;
    }

    /**
     * 获取单个 IN 子句中的最大参数数量。
     *
     * @return 最大参数数量
     */
    public static int getMaxInClauseSize() {
        return maxInClauseSize;
    }

    /**
     * 设置单个 IN 子句中的最大参数数量。由 Spring Boot 自动配置调用。
     *
     * <p>
     * 有效范围：1-100000。超出范围的值将被忽略并记录警告。
     *
     * @param size 最大参数数量
     */
    public static void setMaxInClauseSize(int size) {
        if (size > 0 && size <= MAX_ALLOWED_VALUE) {
            maxInClauseSize = size;
            log.info("IN clause max size configured to {}", size);
        } else if (size > MAX_ALLOWED_VALUE) {
            log.warn("IN clause max size ({}) exceeds upper limit ({}). Ignoring.", size, MAX_ALLOWED_VALUE);
        }
    }

    /**
     * 获取 IN 子句的硬限制。
     *
     * @return 硬限制值
     */
    public static int getHardLimit() {
        return hardLimit;
    }

    /**
     * 设置 IN 子句的硬限制。由 Spring Boot 自动配置调用。
     *
     * <p>
     * 有效范围：1-100000。超出范围的值将被忽略并记录警告。
     *
     * @param limit 硬限制值
     */
    public static void setHardLimit(int limit) {
        if (limit > 0 && limit <= MAX_ALLOWED_VALUE) {
            hardLimit = limit;
            log.info("IN clause hard limit configured to {}", limit);
        } else if (limit > MAX_ALLOWED_VALUE) {
            log.warn("IN clause hard limit ({}) exceeds upper limit ({}). Ignoring.", limit, MAX_ALLOWED_VALUE);
        }
    }

    private InClauseBuilder() {}

    /**
     * 构建 {@code IN} 谓词：{@code field IN (values)}。
     *
     * <p>
     * 如果值的数量超过 {@link #getMaxInClauseSize()}，IN 子句会自动拆分为多个 OR 连接的批次： {@code field IN (1..1000) OR
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
        if (values.length <= maxInClauseSize) {
            return buildSingleIn(cb, path, values);
        }
        return buildBatchedIn(cb, path, Arrays.asList(values));
    }

    /**
     * 构建 {@code IN} 谓词：{@code field IN (values)}。
     *
     * <p>
     * 如果值的数量超过 {@link #getMaxInClauseSize()}，IN 子句会自动拆分为多个 OR 连接的批次。
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
        if (values.size() <= maxInClauseSize) {
            return buildSingleIn(cb, path, values);
        }
        return buildBatchedIn(cb, path, values);
    }

    /**
     * 构建 {@code NOT IN} 谓词：{@code field NOT IN (values)}。
     *
     * <p>
     * 如果值的数量超过 {@link #maxInClauseSize}，NOT IN 子句会自动拆分为多个 AND NOT 连接的批次。
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
        // SQL NOT IN 语义：NULL IN (x, y) 返回 UNKNOWN，导致 NOT IN 整体返回空。
        // 过滤 NULL 值并附加 IS NOT NULL 条件，确保结果符合预期。
        List<Object> nonNullValues = new ArrayList<>(values.length);
        boolean hasNull = false;
        for (Object v : values) {
            if (v == null) {
                hasNull = true;
            } else {
                nonNullValues.add(v);
            }
        }
        if (nonNullValues.isEmpty()) {
            // 全部为 NULL：NOT IN (空) 等价于所有行都匹配，但为安全起见返回 IS NOT NULL
            return cb.isNotNull(path);
        }
        // 先构建 NOT IN 谓词（含批拆分），再根据 hasNull 决定是否追加 IS NOT NULL。
        // 之前的实现在 hasNull=true 且 nonNullValues.size() > maxInClauseSize 时
        // 会提前返回未经批拆分的 notInPredicate，绕过了 buildBatchedNotIn。
        Predicate notInPredicate;
        if (nonNullValues.size() <= maxInClauseSize) {
            notInPredicate = cb.not(buildSingleIn(cb, path, nonNullValues));
        } else {
            notInPredicate = buildBatchedNotIn(cb, path, nonNullValues);
        }
        if (hasNull) {
            // 额外添加 IS NOT NULL 条件，排除字段为 NULL 的行
            return cb.and(notInPredicate, cb.isNotNull(path));
        }
        return notInPredicate;
    }

    /**
     * 构建 {@code NOT IN} 谓词：{@code field NOT IN (values)}。
     *
     * <p>
     * 如果值的数量超过 {@link #maxInClauseSize}，NOT IN 子句会自动拆分为多个 AND NOT 连接的批次。
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
        // SQL NOT IN 语义：NULL IN (x, y) 返回 UNKNOWN，导致 NOT IN 整体返回空。
        // 过滤 NULL 值并附加 IS NOT NULL 条件，确保结果符合预期。
        List<Object> nonNullValues = new ArrayList<>(values.size());
        boolean hasNull = false;
        for (Object v : values) {
            if (v == null) {
                hasNull = true;
            } else {
                nonNullValues.add(v);
            }
        }
        if (nonNullValues.isEmpty()) {
            return cb.isNotNull(path);
        }
        // 先构建 NOT IN 谓词（含批拆分），再根据 hasNull 决定是否追加 IS NOT NULL。
        // 与数组版本相同的 bug 修复：hasNull=true 且大列表时不应绕过批拆分。
        Predicate notInPredicate;
        if (nonNullValues.size() <= maxInClauseSize) {
            notInPredicate = cb.not(buildSingleIn(cb, path, nonNullValues));
        } else {
            notInPredicate = buildBatchedNotIn(cb, path, nonNullValues);
        }
        if (hasNull) {
            return cb.and(notInPredicate, cb.isNotNull(path));
        }
        return notInPredicate;
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
        if (values.size() > hardLimit) {
            throw new MyJpaPlusException("IN clause size " + values.size() + " exceeds hard limit " + hardLimit
                + ". Consider using temporary tables or subqueries for better performance. "
                + "You can adjust the limit via -Dmyjpa-plus.in-clause-hard-limit=<value>.");
        }
        int warningThreshold = hardLimit / 2;
        if (values.size() > warningThreshold) {
            log.warn(
                "IN clause has {} values (hard limit: {}), which may cause severe database performance degradation. "
                    + "Consider using temporary tables or subqueries.",
                values.size(), hardLimit);
        } else if (values.size() > 10_000) {
            log.warn("IN clause has {} values, which may cause performance issues. "
                + "Consider using temporary tables or subqueries for better performance.", values.size());
        }
        if (log.isDebugEnabled()) {
            log.debug("IN clause has {} values, exceeding limit of {}. Splitting into batches.", values.size(),
                maxInClauseSize);
        }
        // 一次性转换为List以启用subList()视图，避免临时List复制
        List<?> valueList = values instanceof List<?> l ? l : new ArrayList<>(values);
        int estimatedBatches = (valueList.size() + maxInClauseSize - 1) / maxInClauseSize;
        List<Predicate> batchPredicates = new ArrayList<>(estimatedBatches);
        for (int i = 0; i < valueList.size(); i += maxInClauseSize) {
            int end = Math.min(i + maxInClauseSize, valueList.size());
            batchPredicates.add(buildSingleIn(cb, path, valueList.subList(i, end)));
        }
        return cb.or(batchPredicates.toArray(new Predicate[0]));
    }

    private static Predicate buildBatchedNotIn(CriteriaBuilder cb, Path<?> path, Collection<?> values) {
        if (values.size() > hardLimit) {
            throw new MyJpaPlusException("NOT IN clause size " + values.size() + " exceeds hard limit " + hardLimit
                + ". Consider using temporary tables or subqueries for better performance. "
                + "You can adjust the limit via -Dmyjpa-plus.in-clause-hard-limit=<value>.");
        }
        int warningThreshold = hardLimit / 2;
        if (values.size() > warningThreshold) {
            log.warn(
                "NOT IN clause has {} values (hard limit: {}), which may cause severe database performance degradation. "
                    + "Consider using temporary tables or subqueries.",
                values.size(), hardLimit);
        } else if (values.size() > 10_000) {
            log.warn("NOT IN clause has {} values, which may cause performance issues. "
                + "Consider using temporary tables or subqueries for better performance.", values.size());
        }
        if (log.isDebugEnabled()) {
            log.debug("NOT IN clause has {} values, exceeding limit of {}. Splitting into batches.", values.size(),
                maxInClauseSize);
        }
        // 一次性转换为List以启用subList()视图，避免临时List复制
        List<?> valueList = values instanceof List<?> l ? l : new ArrayList<>(values);
        int estimatedBatches = (valueList.size() + maxInClauseSize - 1) / maxInClauseSize;
        List<Predicate> batchPredicates = new ArrayList<>(estimatedBatches);
        for (int i = 0; i < valueList.size(); i += maxInClauseSize) {
            int end = Math.min(i + maxInClauseSize, valueList.size());
            batchPredicates.add(cb.not(buildSingleIn(cb, path, valueList.subList(i, end))));
        }
        return cb.and(batchPredicates.toArray(new Predicate[0]));
    }
}
