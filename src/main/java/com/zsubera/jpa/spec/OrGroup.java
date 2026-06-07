package com.zsubera.jpa.spec;

import com.zsubera.jpa.util.LambdaUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
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

    /**
     * 在 OR 组内添加 INNER JOIN。
     *
     * @param field 关联字段的方法引用
     * @param <J> 关联实体类型
     * @return 新的 JoinGroup 实例，用于添加 JOIN 条件
     * @throws IllegalArgumentException 如果 {@code field} 为 null
     */
    public <J> JoinGroup<T, J> join(SFunction<T, ?> field) {
        return internalJoin(field, ConditionNode.JoinType.INNER);
    }

    /**
     * 在 OR 组内添加 LEFT JOIN。
     *
     * @param field 关联字段的方法引用
     * @param <J> 关联实体类型
     * @return 新的 JoinGroup 实例，用于添加 JOIN 条件
     * @throws IllegalArgumentException 如果 {@code field} 为 null
     */
    public <J> JoinGroup<T, J> leftJoin(SFunction<T, ?> field) {
        return internalJoin(field, ConditionNode.JoinType.LEFT);
    }

    /**
     * 在 OR 组内创建嵌套 OR 子组。
     *
     * @return 新的 OrGroup 实例，用于添加嵌套 OR 条件
     */
    public OrGroup<T> or() {
        ConditionNode.OrNode nested = new ConditionNode.OrNode();
        root.currentGroup().add(nested);
        root.pushGroupStack(nested.nodes);
        return new OrGroup<>(root);
    }

    /**
     * 结束当前 OR 组，返回父级 {@link QuerySpec}。
     *
     * @return 父级 QuerySpec 实例
     */
    @SuppressFBWarnings("EI_EXPOSE_REP")
    public QuerySpec<T> endOr() {
        root.endOr();
        return root;
    }

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
        ConditionNode.OrNode nested = new ConditionNode.OrNode();
        root.currentGroup().add(nested);
        root.pushGroupStack(nested.nodes);
        try {
            config.accept(new OrGroup<>(root));
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
        ConditionNode.JoinNode joinNode =
            new ConditionNode.JoinNode(LambdaUtils.getPropertyName(field), ConditionNode.JoinType.INNER);
        root.currentGroup().add(joinNode);
        config.accept(new JoinGroup<>(root, joinNode));
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
        ConditionNode.JoinNode joinNode =
            new ConditionNode.JoinNode(LambdaUtils.getPropertyName(field), ConditionNode.JoinType.LEFT);
        root.currentGroup().add(joinNode);
        config.accept(new JoinGroup<>(root, joinNode));
        return this;
    }
}
