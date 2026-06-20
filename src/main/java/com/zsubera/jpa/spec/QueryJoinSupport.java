package com.zsubera.jpa.spec;

import com.zsubera.jpa.util.LambdaUtils;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QuerySpec 的 JOIN 关联辅助类，提供 INNER JOIN、LEFT JOIN、FETCH JOIN 关联的构建方法。
 *
 * <p>
 * 从 {@link QueryConditionSupport} 中提取，将 JOIN 关联的验证、节点创建和 Consumer 模式逻辑集中在此类中，
 * 降低 QueryConditionSupport 的复杂度。所有方法返回 {@link QuerySpec} 以支持链式调用。
 *
 * <p>
 * 内部工具类——不建议应用代码直接使用。
 *
 * @param <T> 实体类型
 */
final class QueryJoinSupport<T> {

    private static final Logger log = LoggerFactory.getLogger(QueryJoinSupport.class);

    private final QuerySpec<T> parent;

    QueryJoinSupport(QuerySpec<T> parent) {
        this.parent = parent;
    }

    /**
     * 使用消费者构建 INNER JOIN 关联，自动关闭关联组。
     *
     * @param field 关联字段的方法引用
     * @param config JoinGroup 配置消费者
     * @param <J> 关联实体类型
     * @return 当前 QuerySpec 实例
     */
    <J> QuerySpec<T> join(SFunction<T, ?> field, Consumer<JoinGroup<T, J>> config) {
        return internalJoinWithConsumer(field, ConditionNode.JoinType.INNER, config);
    }

    /**
     * 使用消费者构建 LEFT JOIN 关联，自动关闭关联组。
     *
     * @param field 关联字段的方法引用
     * @param config JoinGroup 配置消费者
     * @param <J> 关联实体类型
     * @return 当前 QuerySpec 实例
     */
    <J> QuerySpec<T> leftJoin(SFunction<T, ?> field, Consumer<JoinGroup<T, J>> config) {
        return internalJoinWithConsumer(field, ConditionNode.JoinType.LEFT, config);
    }

    /**
     * 使用消费者构建 FETCH JOIN 关联，自动关闭关联组。
     *
     * @param field 关联字段的方法引用
     * @param config JoinGroup 配置消费者
     * @param <J> 关联实体类型
     * @return 当前 QuerySpec 实例
     */
    <J> QuerySpec<T> fetchJoin(SFunction<T, ?> field, Consumer<JoinGroup<T, J>> config) {
        return internalJoinWithConsumer(field, ConditionNode.JoinType.FETCH, config);
    }

    /**
     * 使用消费者构建 LEFT FETCH JOIN 关联，自动关闭关联组。
     *
     * @param field 关联字段的方法引用
     * @param config JoinGroup 配置消费者
     * @param <J> 关联实体类型
     * @return 当前 QuerySpec 实例
     */
    <J> QuerySpec<T> leftFetchJoin(SFunction<T, ?> field, Consumer<JoinGroup<T, J>> config) {
        return internalJoinWithConsumer(field, ConditionNode.JoinType.LEFT_FETCH, config);
    }

    private <J> QuerySpec<T> internalJoinWithConsumer(SFunction<T, ?> field, ConditionNode.JoinType joinType,
        Consumer<JoinGroup<T, J>> config) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        ConditionNode.JoinNode joinNode = new ConditionNode.JoinNode(LambdaUtils.getPropertyName(field), joinType);
        parent.currentGroup().add(joinNode);
        config.accept(new JoinGroup<>(parent, joinNode));
        return parent;
    }
}
