package com.zsubera.jpa.spec;

import java.util.List;

/**
 * JOIN 内的 OR 条件组构建器，用于在 {@link JoinGroup} 中构建 OR 条件。
 *
 * @param <T> 根实体类型
 * @param <J> JOIN 实体类型
 */
public class OrJoinGroup<T, J> implements ConditionBuilder<J, OrJoinGroup<T, J>> {

    private final QuerySpec<T> root;
    private final ConditionNode.JoinNode joinNode;
    private final ConditionNode.OrNode orNode;

    OrJoinGroup(QuerySpec<T> root, ConditionNode.JoinNode joinNode, ConditionNode.OrNode orNode) {
        if (root == null) {
            throw new IllegalArgumentException("root must not be null");
        }
        if (joinNode == null) {
            throw new IllegalArgumentException("joinNode must not be null");
        }
        if (orNode == null) {
            throw new IllegalArgumentException("orNode must not be null");
        }
        this.root = root;
        this.joinNode = joinNode;
        this.orNode = orNode;
    }

    @Override
    public List<ConditionNode> conditions() {
        return orNode.nodes;
    }

    /**
     * 结束当前 OR 组，返回父级 {@link JoinGroup}。
     *
     * @return 父级 JoinGroup 实例
     */
    public JoinGroup<T, J> endOr() {
        return new JoinGroup<>(root, joinNode);
    }
}
