package com.zsubera.jpa.spec;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;

/**
 * 查询聚合函数的静态工具方法。
 *
 * <p>
 * 提供 {@code count}、{@code sum}、{@code avg}、{@code max}、{@code min} 等聚合表达式工厂方法，
 * 主要用于 {@link QuerySpec#having(BiFunction)} 子句中的类型安全聚合操作。
 *
 * <p>
 * 这些方法也可独立使用，无需通过 {@link QuerySpec} 调用：
 *
 * <pre>{@code
 * Expression<Long> countExpr = QueryAggregates.count(root, User::getDepartment, cb);
 * }</pre>
 *

 * @see QuerySpec#havingCount(SFunction, ConditionNode.Op, long)
 * @see QuerySpec#havingSum(SFunction, ConditionNode.Op, Number)
 */
public final class QueryAggregates {

    private QueryAggregates() {}

    /**
     * 创建 COUNT 聚合表达式。
     *
     * @param root 查询根路径
     * @param cb CriteriaBuilder 实例
     * @return COUNT 聚合表达式
     * @param <T> 实体类型
     */
    public static <T> Expression<Long> count(Path<T> root, CriteriaBuilder cb) {
        return AggregateHelper.count(root, cb);
    }

    /**
     * 创建指定字段的 COUNT 聚合表达式。
     *
     * @param root 查询根路径
     * @param field 要计数的字段方法引用
     * @param cb CriteriaBuilder 实例
     * @return COUNT 聚合表达式
     * @param <T> 实体类型
     */
    public static <T> Expression<Long> count(Path<T> root, SFunction<T, ?> field, CriteriaBuilder cb) {
        return AggregateHelper.count(root, field, cb);
    }

    /**
     * 创建 COUNT DISTINCT 聚合表达式。
     *
     * @param root 查询根路径
     * @param field 要计数的字段方法引用
     * @param cb CriteriaBuilder 实例
     * @return COUNT DISTINCT 聚合表达式
     * @param <T> 实体类型
     */
    public static <T> Expression<Long> countDistinct(Path<T> root, SFunction<T, ?> field, CriteriaBuilder cb) {
        return AggregateHelper.countDistinct(root, field, cb);
    }

    /**
     * 创建 SUM 聚合表达式。
     *
     * @param root 查询根路径
     * @param field 要求和的字段方法引用
     * @param cb CriteriaBuilder 实例
     * @return SUM 聚合表达式
     * @param <T> 实体类型
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T> Expression<? extends Number> sum(Path<T> root, SFunction<T, ?> field, CriteriaBuilder cb) {
        return AggregateHelper.sum(root, field, cb);
    }

    /**
     * 创建 AVG 聚合表达式。
     *
     * @param root 查询根路径
     * @param field 要求平均值的字段方法引用
     * @param cb CriteriaBuilder 实例
     * @return AVG 聚合表达式
     * @param <T> 实体类型
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T> Expression<Double> avg(Path<T> root, SFunction<T, ?> field, CriteriaBuilder cb) {
        return AggregateHelper.avg(root, field, cb);
    }

    /**
     * 创建 MAX 聚合表达式。
     *
     * @param root 查询根路径
     * @param field 要求最大值的字段方法引用
     * @param cb CriteriaBuilder 实例
     * @return MAX 聚合表达式
     * @param <T> 实体类型
     * @param <Y> 可比较类型
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T, Y extends Comparable<? super Y>> Expression<Y> max(Path<T> root, SFunction<T, ?> field,
        CriteriaBuilder cb) {
        return AggregateHelper.max(root, field, cb);
    }

    /**
     * 创建 MIN 聚合表达式。
     *
     * @param root 查询根路径
     * @param field 要求最小值的字段方法引用
     * @param cb CriteriaBuilder 实例
     * @return MIN 聚合表达式
     * @param <T> 实体类型
     * @param <Y> 可比较类型
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T, Y extends Comparable<? super Y>> Expression<Y> min(Path<T> root, SFunction<T, ?> field,
        CriteriaBuilder cb) {
        return AggregateHelper.min(root, field, cb);
    }
}
