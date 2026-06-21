package com.zsubera.jpa.spec;

import com.zsubera.jpa.util.LambdaUtils;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QuerySpec 的 HAVING 条件辅助类，提供类型安全的聚合 HAVING 方法和通用 HAVING 条件支持。
 *
 * <p>
 * 从 {@link QuerySpec} 中提取，将 HAVING 相关的验证、字段解析、表达式构建和查询应用逻辑集中在此类中，
 * 降低 QuerySpec 的复杂度。所有方法返回 {@link QuerySpec} 以支持链式调用。
 *
 * <p>
 * 内部工具类——不建议应用代码直接使用。
 *
 * @param <T> 实体类型
 */
final class QueryHavingSupport<T> {

    private static final Logger log = LoggerFactory.getLogger(QueryHavingSupport.class);

    private final QuerySpec<T> parent;
    private final List<BiFunction<Path<T>, CriteriaBuilder, Predicate>> havingConditions;

    QueryHavingSupport(QuerySpec<T> parent, List<BiFunction<Path<T>, CriteriaBuilder, Predicate>> havingConditions) {
        this.parent = parent;
        this.havingConditions = havingConditions;
    }

    // ---- 通用 HAVING 条件 ----

    /**
     * 添加 HAVING 条件，使用 {@link BiFunction} 参数。
     *
     * @param condition HAVING 条件函数
     * @return 当前 QuerySpec 实例
     */
    QuerySpec<T> having(BiFunction<Path<T>, CriteriaBuilder, Predicate> condition) {
        if (condition == null) {
            throw new IllegalArgumentException("condition must not be null");
        }
        havingConditions.add(condition);
        log.debug("QuerySpec: HAVING condition added ({} total)", havingConditions.size());
        return parent;
    }

    /**
     * 添加 HAVING 条件，使用 {@link Function} 参数（避免类型推断问题）。
     *
     * @param condition HAVING 条件函数
     * @return 当前 QuerySpec 实例
     */
    QuerySpec<T> having(Function<Path<T>, Predicate> condition) {
        if (condition == null) {
            throw new IllegalArgumentException("condition must not be null");
        }
        havingConditions.add((root, cb) -> condition.apply(root));
        log.debug("QuerySpec: HAVING condition added ({} total)", havingConditions.size());
        return parent;
    }

    // ---- 类型安全的 HAVING 辅助方法 ----

    /**
     * 添加 HAVING 条件的通用入口，统一验证和字段解析逻辑。
     *
     * @param field 聚合字段
     * @param op 比较运算符
     * @param valueName 值参数名（用于错误消息）
     * @param value 比较值（可为 null，由调用方决定是否校验）
     * @param expressionFn 聚合表达式构建函数
     * @return 当前 QuerySpec 实例
     */
    QuerySpec<T> addHavingCondition(SFunction<T, ?> field, ConditionNode.Op op, String valueName, Object value,
        BiFunction<Path<T>, CriteriaBuilder, Predicate> expressionFn) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        if (op == null) {
            throw new IllegalArgumentException("op must not be null");
        }
        if (value == null) {
            throw new IllegalArgumentException(valueName + " must not be null");
        }
        AggregateHelper.validateHavingOperator(op);
        havingConditions.add(expressionFn);
        return parent;
    }

    /**
     * 添加 HAVING COUNT 条件：{@code HAVING COUNT(field) op value}。
     *
     * @param field 要计数的字段方法引用
     * @param op 比较运算符
     * @param value 比较值
     * @return 当前 QuerySpec 实例
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    QuerySpec<T> havingCount(SFunction<T, ?> field, ConditionNode.Op op, long value) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        if (op == null) {
            throw new IllegalArgumentException("op must not be null");
        }
        AggregateHelper.validateHavingOperator(op);
        String fieldName = LambdaUtils.getPropertyName(field);
        havingConditions.add((root, cb) -> {
            Expression<Long> countExpr = cb.count(root.get(fieldName));
            return AggregateHelper.compareExpression(cb, countExpr, op, value);
        });
        return parent;
    }

    /**
     * 添加 HAVING SUM 条件：{@code HAVING SUM(field) op value}。
     *
     * @param field 要求和的字段方法引用
     * @param op 比较运算符
     * @param value 比较值
     * @return 当前 QuerySpec 实例
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    QuerySpec<T> havingSum(SFunction<T, ?> field, ConditionNode.Op op, Number value) {
        String fieldName = LambdaUtils.getPropertyName(field);
        return addHavingCondition(field, op, "value", value, (root, cb) -> {
            Expression<? extends Number> sumExpr = cb.sum((Expression)root.get(fieldName));
            return AggregateHelper.compareExpression(cb, sumExpr, op, value);
        });
    }

    /**
     * 添加 HAVING AVG 条件：{@code HAVING AVG(field) op value}。
     *
     * @param field 要求平均值的字段方法引用
     * @param op 比较运算符
     * @param value 比较值
     * @return 当前 QuerySpec 实例
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    QuerySpec<T> havingAvg(SFunction<T, ?> field, ConditionNode.Op op, Number value) {
        String fieldName = LambdaUtils.getPropertyName(field);
        return addHavingCondition(field, op, "value", value, (root, cb) -> {
            Expression<Double> avgExpr = cb.avg((Expression)root.get(fieldName));
            return AggregateHelper.compareExpression(cb, avgExpr, op, value);
        });
    }

    /**
     * 添加 HAVING MAX 条件：{@code HAVING MAX(field) op value}。
     *
     * @param field 要求最大值的字段方法引用
     * @param op 比较运算符
     * @param value 比较值
     * @return 当前 QuerySpec 实例
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    <Y extends Comparable<? super Y>> QuerySpec<T> havingMax(SFunction<T, ?> field, ConditionNode.Op op, Y value) {
        return addHavingCondition(field, op, "value", value, (root, cb) -> {
            Expression<Y> maxExpr = cb.max((Expression)root.get(LambdaUtils.getPropertyName(field)));
            return AggregateHelper.compareComparable(cb, maxExpr, op, value);
        });
    }

    /**
     * 添加 HAVING MIN 条件：{@code HAVING MIN(field) op value}。
     *
     * @param field 要求最小值的字段方法引用
     * @param op 比较运算符
     * @param value 比较值
     * @return 当前 QuerySpec 实例
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    <Y extends Comparable<? super Y>> QuerySpec<T> havingMin(SFunction<T, ?> field, ConditionNode.Op op, Y value) {
        return addHavingCondition(field, op, "value", value, (root, cb) -> {
            Expression<Y> minExpr = cb.min((Expression)root.get(LambdaUtils.getPropertyName(field)));
            return AggregateHelper.compareComparable(cb, minExpr, op, value);
        });
    }

    // ---- 查询应用 ----

    /**
     * 将 HAVING 条件应用到 CriteriaQuery。
     *
     * @param root 根实体路径
     * @param groupByFields GROUP BY 字段列表
     * @param query Criteria 查询对象
     * @param cb Criteria 构建器
     * @throws IllegalStateException 如果有 HAVING 但没有 GROUP BY
     */
    void applyHaving(Root<T> root, List<String> groupByFields, jakarta.persistence.criteria.CriteriaQuery<?> query,
        CriteriaBuilder cb) {
        if (havingConditions.isEmpty()) {
            return;
        }
        if (groupByFields.isEmpty()) {
            throw new IllegalStateException("HAVING conditions specified without GROUP BY. "
                + "HAVING without GROUP BY is not supported by most databases. "
                + "Add a groupBy() call before having().");
        }
        List<Predicate> havingPredicates = new ArrayList<>();
        for (BiFunction<Path<T>, CriteriaBuilder, Predicate> having : havingConditions) {
            havingPredicates.add(having.apply(root, cb));
        }
        if (havingPredicates.size() == 1) {
            query.having(havingPredicates.get(0));
        } else {
            query.having(cb.and(havingPredicates.toArray(new Predicate[0])));
        }
    }

    /**
     * 检查是否有 HAVING 条件。
     *
     * @return 如果有 HAVING 条件返回 true
     */
    boolean isEmpty() {
        return havingConditions.isEmpty();
    }

    /**
     * 返回 HAVING 条件数量。
     *
     * @return 条件数量
     */
    int size() {
        return havingConditions.size();
    }

    /**
     * 将另一个 HAVING 条件列表合并到当前列表。
     *
     * @param other 要合并的 HAVING 条件列表
     */
    void addAll(List<BiFunction<Path<T>, CriteriaBuilder, Predicate>> other) {
        havingConditions.addAll(other);
    }
}
