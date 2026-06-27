package com.zsubera.jpa.spec;

import com.zsubera.jpa.util.LambdaUtils;
import java.util.List;
import java.util.function.Consumer;

/**
 * OR 条件组构建器，用于在 {@link QuerySpec} 中构建 OR 条件。
 *
 * @param <T> 实体类型
 */
public class OrGroup<T> implements ConditionBuilder<T, OrGroup<T>> {

    private final QuerySpec<T> root;

    OrGroup(QuerySpec<T> root) {
        if (root == null) {
            throw new IllegalArgumentException("root must not be null");
        }
        this.root = root;
    }

    @Override
    public List<ConditionNode> conditions() {
        return root.currentGroup();
    }

    private <J> JoinGroup<T, J> internalJoin(SFunction<T, ?> field, ConditionNode.JoinType joinType) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        ConditionNode.JoinNode joinNode = new ConditionNode.JoinNode(LambdaUtils.getPropertyName(field), joinType);
        root.currentGroup().add(joinNode);
        return new JoinGroup<>(root, joinNode);
    }

    // ---- 基于 Consumer 的 API（自动关闭） ----

    /**
     * 使用消费者构建嵌套 OR 组，自动关闭组。
     *
     * @param config OR 组配置消费者
     * @return 当前 OrGroup 实例，支持链式调用
     * @throws IllegalArgumentException 如果 config 为 null
     */
    public OrGroup<T> or(Consumer<OrGroup<T>> config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        OrGroup<T> nested = root.pushOrGroup();
        try {
            config.accept(nested);
        } finally {
            root.endOr();
        }
        return this;
    }

    /**
     * 使用消费者构建 OR 组内的 INNER JOIN，自动关闭关联组。
     *
     * @param field 关联字段的方法引用
     * @param config JoinGroup 配置消费者
     * @param <J> 关联实体类型
     * @return 当前 OrGroup 实例，支持链式调用
     * @throws IllegalArgumentException 如果 field 或 config 为 null
     */
    public <J> OrGroup<T> join(SFunction<T, ?> field, Consumer<JoinGroup<T, J>> config) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        JoinGroup<T, J> joinGroup = internalJoin(field, ConditionNode.JoinType.INNER);
        config.accept(joinGroup);
        return this;
    }

    /**
     * 使用消费者构建 OR 组内的 LEFT JOIN，自动关闭关联组。
     *
     * @param field 关联字段的方法引用
     * @param config JoinGroup 配置消费者
     * @param <J> 关联实体类型
     * @return 当前 OrGroup 实例，支持链式调用
     * @throws IllegalArgumentException 如果 field 或 config 为 null
     */
    public <J> OrGroup<T> leftJoin(SFunction<T, ?> field, Consumer<JoinGroup<T, J>> config) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        JoinGroup<T, J> joinGroup = internalJoin(field, ConditionNode.JoinType.LEFT);
        config.accept(joinGroup);
        return this;
    }
}
