package com.zsubera.jpa.spec;

import com.zsubera.jpa.util.LambdaUtils;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Path;

/**
 * 聚合函数工具类，提供 COUNT/SUM/AVG/MAX/MIN 表达式构建和 HAVING 条件比较的静态方法。
 *
 * <p>
 * 从 {@link QuerySpec} 提取的纯工具方法，不依赖任何实例状态。
 *
 * @see QuerySpec#havingCount(SFunction, ConditionNode.Op, long)
 * @see QuerySpec#havingSum(SFunction, ConditionNode.Op, Number)
 */
final class AggregateHelper {

    private AggregateHelper() {}

    // ---- 静态聚合表达式方法 ----

    /**
     * 创建 COUNT 聚合表达式。
     *
     * @param root 查询根路径
     * @param cb CriteriaBuilder 实例
     * @return COUNT 聚合表达式
     * @param <T> 实体类型
     */
    static <T> Expression<Long> count(Path<T> root, CriteriaBuilder cb) {
        requireNonNull(root, "root");
        requireNonNull(cb, "cb");
        return cb.count(root);
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
    static <T> Expression<Long> count(Path<T> root, SFunction<T, ?> field, CriteriaBuilder cb) {
        requireNonNull(root, "root");
        requireNonNull(field, "field");
        requireNonNull(cb, "cb");
        return cb.count(root.get(LambdaUtils.getPropertyName(field)));
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
    static <T> Expression<Long> countDistinct(Path<T> root, SFunction<T, ?> field, CriteriaBuilder cb) {
        requireNonNull(root, "root");
        requireNonNull(field, "field");
        requireNonNull(cb, "cb");
        return cb.countDistinct(root.get(LambdaUtils.getPropertyName(field)));
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
    static <T> Expression<? extends Number> sum(Path<T> root, SFunction<T, ?> field, CriteriaBuilder cb) {
        requireNonNull(root, "root");
        requireNonNull(field, "field");
        requireNonNull(cb, "cb");
        return cb.sum((Expression)root.get(LambdaUtils.getPropertyName(field)));
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
    static <T> Expression<Double> avg(Path<T> root, SFunction<T, ?> field, CriteriaBuilder cb) {
        requireNonNull(root, "root");
        requireNonNull(field, "field");
        requireNonNull(cb, "cb");
        return cb.avg((Expression)root.get(LambdaUtils.getPropertyName(field)));
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
    static <T, Y extends Comparable<? super Y>> Expression<Y> max(Path<T> root, SFunction<T, ?> field,
        CriteriaBuilder cb) {
        requireNonNull(root, "root");
        requireNonNull(field, "field");
        requireNonNull(cb, "cb");
        return cb.max((Expression)root.get(LambdaUtils.getPropertyName(field)));
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
    static <T, Y extends Comparable<? super Y>> Expression<Y> min(Path<T> root, SFunction<T, ?> field,
        CriteriaBuilder cb) {
        requireNonNull(root, "root");
        requireNonNull(field, "field");
        requireNonNull(cb, "cb");
        return cb.min((Expression)root.get(LambdaUtils.getPropertyName(field)));
    }

    // ---- HAVING 条件辅助方法 ----

    /**
     * 验证 HAVING 子句支持的运算符。
     *
     * @param op 运算符
     * @throws IllegalArgumentException 如果运算符不支持
     */
    static void validateHavingOperator(ConditionNode.Op op) {
        requireNonNull(op, "op");
        switch (op) {
            case GT, GE, LT, LE, EQ, NE -> {
                /* supported */ }
            default -> throw new IllegalArgumentException(
                "Unsupported operator for HAVING: " + op + ". Supported operators: GT, GE, LT, LE, EQ, NE");
        }
    }

    /**
     * 比较数值表达式与给定值。
     *
     * @param cb CriteriaBuilder 实例
     * @param expr 数值表达式
     * @param op 比较运算符
     * @param value 比较值
     * @return 比较谓词
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static Predicate compareExpression(CriteriaBuilder cb, Expression<? extends Number> expr, ConditionNode.Op op,
        Number value) {
        return switch (op) {
            case GT -> cb.greaterThan((Expression)expr, (Comparable)value);
            case GE -> cb.greaterThanOrEqualTo((Expression)expr, (Comparable)value);
            case LT -> cb.lessThan((Expression)expr, (Comparable)value);
            case LE -> cb.lessThanOrEqualTo((Expression)expr, (Comparable)value);
            case EQ -> cb.equal(expr, value);
            case NE -> cb.notEqual(expr, value);
            default -> throw new IllegalArgumentException("Unsupported operator for HAVING: " + op);
        };
    }

    /**
     * 比较可比较表达式与给定值。
     *
     * @param cb CriteriaBuilder 实例
     * @param expr 可比较表达式
     * @param op 比较运算符
     * @param value 比较值
     * @return 比较谓词
     * @param <Y> 可比较类型
     */
    static <Y extends Comparable<? super Y>> Predicate compareComparable(CriteriaBuilder cb, Expression<Y> expr,
        ConditionNode.Op op, Y value) {
        return switch (op) {
            case GT -> cb.greaterThan(expr, value);
            case GE -> cb.greaterThanOrEqualTo(expr, value);
            case LT -> cb.lessThan(expr, value);
            case LE -> cb.lessThanOrEqualTo(expr, value);
            case EQ -> cb.equal(expr, value);
            case NE -> cb.notEqual(expr, value);
            default -> throw new IllegalArgumentException("Unsupported operator for HAVING: " + op);
        };
    }

    private static void requireNonNull(Object value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
    }
}
