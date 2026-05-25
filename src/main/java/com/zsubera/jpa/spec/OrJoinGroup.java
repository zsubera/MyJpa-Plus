package com.zsubera.jpa.spec;

import java.util.List;

public class OrJoinGroup<T, J> implements ConditionBuilder<J, OrJoinGroup<T, J>> {

    private final QuerySpec<T> root;
    private final QuerySpec.JoinNode joinNode;
    private final QuerySpec.OrNode orNode;

    OrJoinGroup(QuerySpec<T> root, QuerySpec.JoinNode joinNode, QuerySpec.OrNode orNode) {
        this.root = root;
        this.joinNode = joinNode;
        this.orNode = orNode;
    }

    @Override
    public List<QuerySpec.ConditionNode> conditions() {
        return orNode.nodes;
    }

    public JoinGroup<T, J> endOr() {
        return new JoinGroup<>(root, joinNode);
    }
}
