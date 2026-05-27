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
        this.root = root;
        this.joinNode = joinNode;
        this.orNode = orNode;
    }

    @Override
    public List<ConditionNode> conditions() {
        return orNode.nodes;
    }

    public JoinGroup<T, J> endOr() {
        return new JoinGroup<>(root, joinNode);
    }
}
