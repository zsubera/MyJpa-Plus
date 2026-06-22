package com.zsubera.jpa.util;

import com.zsubera.jpa.exception.MyJpaPlusException;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
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
 * <strong>配置优先级：</strong>Spring Boot 配置 &gt; 系统属性 &gt; 默认值
 *
 * <ul>
 * <li>Spring Boot: {@code myjpa-plus.in-clause.max-size} / {@code myjpa-plus.in-clause.hard-limit}
 * <li>系统属性: {@code -Dmyjpa-plus.in-clause-max-size} / {@code -Dmyjpa-plus.in-clause-hard-limit}
 * <li>默认值: max-size=1000, hard-limit=5000
 * </ul>
 *
 * <p>
 * <strong>线程安全：</strong>配置通过 {@link AtomicReference} 持有不可变 {@link Config} 原子切换。
 *
 * <p>
 * <strong>NOT IN 与 NULL 的语义差异：</strong>
 * <ul>
 *   <li>SQL 语义: {@code field NOT IN (NULL)} 对每行返回 UNKNOWN（永不匹配任何行）</li>
 *   <li>Java 语义: {@code list.contains(null)} 返回 true → NOT IN 应不排除任何行</li>
 *   <li>本实现采用 Java 语义: NOT IN 全为 NULL 时返回 TRUE（conjunction），不排除任何行</li>
 *   <li>如果需要 SQL 语义（NOT IN NULL 返回空结果），请手动添加 {@code IS NOT NULL} 条件</li>
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
     * 不可变配置记录，两个值同时原子切换，消除之前两个独立 volatile 字段的竞态条件。
     */
    public record Config(int maxInClauseSize, int hardLimit) {

        private static final Config DEFAULT = new Config(1000, 5000);

        public Config {
            if (maxInClauseSize <= 0 || maxInClauseSize > MAX_ALLOWED_VALUE) {
                throw new IllegalArgumentException(
                    "maxInClauseSize must be between 1 and " + MAX_ALLOWED_VALUE + ", got: " + maxInClauseSize);
            }
            if (hardLimit <= 0 || hardLimit > MAX_ALLOWED_VALUE) {
                throw new IllegalArgumentException(
                    "hardLimit must be between 1 and " + MAX_ALLOWED_VALUE + ", got: " + hardLimit);
            }
            if (hardLimit < maxInClauseSize) {
                throw new IllegalArgumentException(
                    "hardLimit (" + hardLimit + ") must be >= maxInClauseSize (" + maxInClauseSize + ")");
            }
        }

        static Config loadFromSystemProperties() {
            int maxSize = DEFAULT.maxInClauseSize;
            String prop = System.getProperty("myjpa-plus.in-clause-max-size");
            if (prop != null) {
                try {
                    int val = Integer.parseInt(prop);
                    if (val > 0 && val <= MAX_ALLOWED_VALUE) {
                        maxSize = val;
                    } else if (val > MAX_ALLOWED_VALUE) {
                        log.warn("myjpa-plus.in-clause-max-size value ({}) exceeds upper limit ({}). Using {}.", val,
                            MAX_ALLOWED_VALUE, MAX_ALLOWED_VALUE);
                        maxSize = MAX_ALLOWED_VALUE;
                    }
                } catch (NumberFormatException ignored) {
                    // 使用默认值
                }
            }
            int hardLimit = DEFAULT.hardLimit;
            String hardProp = System.getProperty("myjpa-plus.in-clause-hard-limit");
            if (hardProp != null) {
                try {
                    int val = Integer.parseInt(hardProp);
                    if (val > 0 && val <= MAX_ALLOWED_VALUE) {
                        hardLimit = val;
                    } else if (val > MAX_ALLOWED_VALUE) {
                        log.warn("myjpa-plus.in-clause-hard-limit value ({}) exceeds upper limit ({}). Using {}.", val,
                            MAX_ALLOWED_VALUE, MAX_ALLOWED_VALUE);
                        hardLimit = MAX_ALLOWED_VALUE;
                    } else {
                        log.warn("myjpa-plus.in-clause-hard-limit value ({}) is not positive. Using default {}.", val,
                            hardLimit);
                    }
                } catch (NumberFormatException ignored) {
                    log.warn("myjpa-plus.in-clause-hard-limit value '{}' is not a valid integer. Using default {}.",
                        hardProp, hardLimit);
                }
            }

            if (hardLimit < maxSize) {
                log.warn("hardLimit ({}) < maxInClauseSize ({}). Adjusting hardLimit to {}.", hardLimit, maxSize,
                    maxSize);
                hardLimit = maxSize;
            }
            return new Config(maxSize, hardLimit);
        }
    }

    /** 持有不可变配置，原子切换，消除多线程读写不一致问题。 */
    private static final AtomicReference<Config> CONFIG_REF = new AtomicReference<>(Config.loadFromSystemProperties());

    /**
     * 原子替换当前配置。由 Spring Boot 自动配置调用。
     */
    public static void setConfig(Config config) {
        CONFIG_REF.set(config);
        log.info("IN clause config updated: maxSize={}, hardLimit={}", config.maxInClauseSize(), config.hardLimit());
    }

    /**
     * 重置配置为系统属性或默认值。用于测试隔离。
     */
    public static void reset() {
        CONFIG_REF.set(Config.loadFromSystemProperties());
    }

    /**
     * 获取单个 IN 子句中的最大参数数量。
     */
    public static int getMaxInClauseSize() {
        return CONFIG_REF.get().maxInClauseSize();
    }

    /**
     * 获取 IN 子句的硬限制。
     */
    public static int getHardLimit() {
        return CONFIG_REF.get().hardLimit();
    }

    /**
     * 获取当前配置。
     *
     * @return 当前配置
     */
    public static Config getConfig() {
        return CONFIG_REF.get();
    }

    private InClauseBuilder() {}

    private static Config cfg() {
        return CONFIG_REF.get();
    }

    private record NullFilterResult(List<Object> nonNullValues, boolean hasNull) {
    }

    private static NullFilterResult filterNulls(Object[] values) {
        return filterNulls(Arrays.asList(values));
    }

    private static NullFilterResult filterNulls(Collection<?> values) {
        List<Object> nonNullValues = new ArrayList<>(values.size());
        boolean hasNull = false;
        for (Object v : values) {
            if (v == null) {
                hasNull = true;
            } else {
                nonNullValues.add(v);
            }
        }
        return new NullFilterResult(nonNullValues, hasNull);
    }

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
        NullFilterResult filtered = filterNulls(values);
        List<Object> nonNullValues = filtered.nonNullValues();
        boolean hasNull = filtered.hasNull();
        if (nonNullValues.isEmpty()) {
            return cb.isNull(path);
        }
        Config config = cfg();
        Predicate inPredicate;
        if (nonNullValues.size() <= config.maxInClauseSize()) {
            inPredicate = buildSingleIn(cb, path, nonNullValues.toArray());
        } else {
            inPredicate = buildBatchedIn(cb, path, nonNullValues, config);
        }
        if (hasNull) {
            return cb.or(inPredicate, cb.isNull(path));
        }
        return inPredicate;
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
        NullFilterResult filtered = filterNulls(values);
        List<Object> nonNullValues = filtered.nonNullValues();
        boolean hasNull = filtered.hasNull();
        if (nonNullValues.isEmpty()) {
            return cb.isNull(path);
        }
        Config config = cfg();
        Predicate inPredicate;
        if (nonNullValues.size() <= config.maxInClauseSize()) {
            inPredicate = buildSingleIn(cb, path, nonNullValues);
        } else {
            inPredicate = buildBatchedIn(cb, path, nonNullValues, config);
        }
        if (hasNull) {
            return cb.or(inPredicate, cb.isNull(path));
        }
        return inPredicate;
    }

    /**
     * 构建 {@code NOT IN} 谓词：{@code field NOT IN (values)}。
     *
     * <p>
     * 如果值的数量超过 {@link #getMaxInClauseSize()}，NOT IN 子句会自动拆分为多个 AND NOT 连接的批次。
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
        NullFilterResult filtered = filterNulls(values);
        List<Object> nonNullValues = filtered.nonNullValues();
        boolean hasNull = filtered.hasNull();
        if (nonNullValues.isEmpty()) {
            log.warn("NOT IN clause has only NULL values. Returning TRUE (no rows filtered). "
                + "This differs from SQL semantics where NOT IN (NULL) returns no rows. "
                + "If you need SQL semantics, add an explicit IS NOT NULL condition.");
            return cb.conjunction();
        }
        Config config = cfg();
        Predicate notInPredicate;
        if (nonNullValues.size() <= config.maxInClauseSize()) {
            notInPredicate = cb.not(buildSingleIn(cb, path, nonNullValues));
        } else {
            notInPredicate = buildBatchedNotIn(cb, path, nonNullValues, config);
        }
        if (hasNull) {
            return cb.and(notInPredicate, cb.isNotNull(path));
        }
        return notInPredicate;
    }

    /**
     * 构建 {@code NOT IN} 谓词：{@code field NOT IN (values)}。
     *
     * <p>
     * 如果值的数量超过 {@link #getMaxInClauseSize()}，NOT IN 子句会自动拆分为多个 AND NOT 连接的批次。
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
        NullFilterResult filtered = filterNulls(values);
        List<Object> nonNullValues = filtered.nonNullValues();
        boolean hasNull = filtered.hasNull();
        if (nonNullValues.isEmpty()) {
            log.warn("NOT IN clause has only NULL values. Returning TRUE (no rows filtered). "
                + "This differs from SQL semantics where NOT IN (NULL) returns no rows. "
                + "If you need SQL semantics, add an explicit IS NOT NULL condition.");
            return cb.conjunction();
        }
        Config config = cfg();
        Predicate notInPredicate;
        if (nonNullValues.size() <= config.maxInClauseSize()) {
            notInPredicate = cb.not(buildSingleIn(cb, path, nonNullValues));
        } else {
            notInPredicate = buildBatchedNotIn(cb, path, nonNullValues, config);
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

    private static Predicate buildBatchedIn(CriteriaBuilder cb, Path<?> path, Collection<?> values, Config config) {
        if (values.size() > config.hardLimit()) {
            throw new MyJpaPlusException("IN clause size " + values.size() + " exceeds hard limit " + config.hardLimit()
                + ". Consider using temporary tables or subqueries for better performance. "
                + "You can adjust the limit via -Dmyjpa-plus.in-clause-hard-limit=<value>.");
        }
        int warningThreshold = (int)(config.hardLimit() * 0.8);
        if (values.size() > warningThreshold) {
            log.warn("IN clause has {} values (hard limit: {}), which may cause database performance degradation. "
                + "Consider using temporary tables or subqueries.", values.size(), config.hardLimit());
        } else if (values.size() > 10_000) {
            log.warn("IN clause has {} values, which may cause performance issues. "
                + "Consider using temporary tables or subqueries for better performance.", values.size());
        }
        if (log.isDebugEnabled()) {
            log.debug("IN clause has {} values, exceeding limit of {}. Splitting into batches.", values.size(),
                config.maxInClauseSize());
        }
        List<?> valueList = values instanceof List<?> l ? l : new ArrayList<>(values);
        int estimatedBatches = (valueList.size() + config.maxInClauseSize() - 1) / config.maxInClauseSize();
        List<Predicate> batchPredicates = new ArrayList<>(estimatedBatches);
        for (int i = 0; i < valueList.size(); i += config.maxInClauseSize()) {
            int end = Math.min(i + config.maxInClauseSize(), valueList.size());
            batchPredicates.add(buildSingleIn(cb, path, valueList.subList(i, end)));
        }
        if (batchPredicates.size() == 1) {
            return batchPredicates.get(0);
        }
        return cb.or(batchPredicates.toArray(new Predicate[0]));
    }

    private static Predicate buildBatchedNotIn(CriteriaBuilder cb, Path<?> path, Collection<?> values, Config config) {
        if (values.size() > config.hardLimit()) {
            throw new MyJpaPlusException("NOT IN clause size " + values.size() + " exceeds hard limit "
                + config.hardLimit() + ". Consider using temporary tables or subqueries for better performance. "
                + "You can adjust the limit via -Dmyjpa-plus.in-clause-hard-limit=<value>.");
        }
        int warningThreshold = config.hardLimit() / 2;
        if (values.size() > warningThreshold) {
            log.warn(
                "NOT IN clause has {} values (hard limit: {}), which may cause severe database performance degradation. "
                    + "Consider using temporary tables or subqueries.",
                values.size(), config.hardLimit());
        } else if (values.size() > 10_000) {
            log.warn("NOT IN clause has {} values, which may cause performance issues. "
                + "Consider using temporary tables or subqueries for better performance.", values.size());
        }
        if (log.isDebugEnabled()) {
            log.debug("NOT IN clause has {} values, exceeding limit of {}. Splitting into batches.", values.size(),
                config.maxInClauseSize());
        }
        List<?> valueList = values instanceof List<?> l ? l : new ArrayList<>(values);
        int estimatedBatches = (valueList.size() + config.maxInClauseSize() - 1) / config.maxInClauseSize();
        List<Predicate> batchPredicates = new ArrayList<>(estimatedBatches);
        for (int i = 0; i < valueList.size(); i += config.maxInClauseSize()) {
            int end = Math.min(i + config.maxInClauseSize(), valueList.size());
            batchPredicates.add(cb.not(buildSingleIn(cb, path, valueList.subList(i, end))));
        }
        if (batchPredicates.size() == 1) {
            return batchPredicates.get(0);
        }
        return cb.and(batchPredicates.toArray(new Predicate[0]));
    }
}
