package com.zsubera.jpa.spec;

import com.zsubera.jpa.util.LambdaUtils;

import java.util.List;

public class JoinGroup<T, J> implements ConditionBuilder<J, JoinGroup<T, J>> {

    private final QuerySpec<T> root;
    private final QuerySpec.JoinNode joinNode;

    JoinGroup(QuerySpec<T> root, QuerySpec.JoinNode joinNode) {
        this.root = root;
        this.joinNode = joinNode;
    }

    @Override
    public List<QuerySpec.ConditionNode> conditions() {
        return joinNode.innerConditions;
    }

    public OrJoinGroup<T, J> or() {
        QuerySpec.OrNode orNode = new QuerySpec.OrNode();
        joinNode.innerConditions.add(orNode);
        return new OrJoinGroup<>(root, joinNode, orNode);
    }

    public <J2> JoinGroup<T, J2> join(SFunction<J, ?> field) {
        QuerySpec.JoinNode nestedJoin = new QuerySpec.JoinNode(LambdaUtils.getPropertyName(field), QuerySpec.JoinType.INNER);
        joinNode.innerConditions.add(nestedJoin);
        return new JoinGroup<>(root, nestedJoin);
    }

    public <J2> JoinGroup<T, J2> leftJoin(SFunction<J, ?> field) {
        QuerySpec.JoinNode nestedJoin = new QuerySpec.JoinNode(LambdaUtils.getPropertyName(field), QuerySpec.JoinType.LEFT);
        joinNode.innerConditions.add(nestedJoin);
        return new JoinGroup<>(root, nestedJoin);
    }

    public QuerySpec<T> endJoin() {
        return root;
    }
}
