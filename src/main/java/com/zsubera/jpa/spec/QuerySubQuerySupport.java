package com.zsubera.jpa.spec;

import com.zsubera.jpa.util.LambdaUtils;
import java.util.function.Consumer;

/**
 * QuerySpec 的子查询辅助类，提供 EXISTS/NOT EXISTS 和 IN/NOT IN 子查询的构建方法。
 *
 * <p>
 * 从 {@link QueryConditionSupport} 中提取，将子查询的验证和节点创建逻辑集中在此类中，
 * 降低 QueryConditionSupport 的复杂度。所有方法返回 {@link QuerySpec} 以支持链式调用。
 *
 * <p>
 * 内部工具类——不建议应用代码直接使用。
 *
 * @param <T> 实体类型
 */
final class QuerySubQuerySupport<T> {

    private final QuerySpec<T> parent;

    QuerySubQuerySupport(QuerySpec<T> parent) {
        this.parent = parent;
    }

    /**
     * 添加 EXISTS 子查询条件。
     *
     * @param subEntity 子查询实体类型
     * @param config 子查询配置消费者
     * @param <S> 子查询实体类型
     * @return 当前 QuerySpec 实例
     */
    <S> QuerySpec<T> exists(Class<S> subEntity, Consumer<SubQuerySpec<S>> config) {
        addSubQueryNode(subEntity, config, false);
        return parent;
    }

    /**
     * 添加 NOT EXISTS 子查询条件。
     *
     * @param subEntity 子查询实体类型
     * @param config 子查询配置消费者
     * @param <S> 子查询实体类型
     * @return 当前 QuerySpec 实例
     */
    <S> QuerySpec<T> notExists(Class<S> subEntity, Consumer<SubQuerySpec<S>> config) {
        addSubQueryNode(subEntity, config, true);
        return parent;
    }

    private <S> void addSubQueryNode(Class<S> subEntity, Consumer<SubQuerySpec<S>> config, boolean negate) {
        if (subEntity == null) {
            throw new IllegalArgumentException("subEntity must not be null");
        }
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        parent.currentGroup().add(new ConditionNode.ExistsNode<>(subEntity, config, negate));
    }

    /**
     * 添加 IN 子查询条件：{@code field IN (SELECT ...)}。
     *
     * @param outerField 外部实体的字段方法引用
     * @param subEntity 子查询实体类型
     * @param config 子查询配置消费者
     * @param <S> 子查询实体类型
     * @return 当前 QuerySpec 实例
     */
    <S> QuerySpec<T> inSubQuery(SFunction<T, ?> outerField, Class<S> subEntity,
        java.util.function.Consumer<SubQuerySpec<S>> config) {
        addInSubQueryNode(outerField, subEntity, config, false);
        return parent;
    }

    /**
     * 添加 NOT IN 子查询条件：{@code field NOT IN (SELECT ...)}。
     *
     * @param outerField 外部实体的字段方法引用
     * @param subEntity 子查询实体类型
     * @param config 子查询配置消费者
     * @param <S> 子查询实体类型
     * @return 当前 QuerySpec 实例
     */
    <S> QuerySpec<T> notInSubQuery(SFunction<T, ?> outerField, Class<S> subEntity,
        java.util.function.Consumer<SubQuerySpec<S>> config) {
        addInSubQueryNode(outerField, subEntity, config, true);
        return parent;
    }

    private <S> void addInSubQueryNode(SFunction<T, ?> outerField, Class<S> subEntity,
        java.util.function.Consumer<SubQuerySpec<S>> config, boolean negate) {
        if (outerField == null) {
            throw new IllegalArgumentException("outerField must not be null");
        }
        if (subEntity == null) {
            throw new IllegalArgumentException("subEntity must not be null");
        }
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        parent.currentGroup().add(
            new ConditionNode.InSubQueryNode<>(LambdaUtils.getPropertyName(outerField), subEntity, config, negate));
    }
}
