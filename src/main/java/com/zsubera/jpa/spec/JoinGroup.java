package com.zsubera.jpa.spec;

import com.zsubera.jpa.util.LambdaUtils;

import java.util.List;
import java.util.function.Consumer;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

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

    /**
     * Self-closing OR inside join: builds an OR group on the joined entity, then returns to this JoinGroup.
     */
    public JoinGroup<T, J> or(Consumer<OrJoinGroup<T, J>> config) {
        QuerySpec.OrNode orNode = new QuerySpec.OrNode();
        joinNode.innerConditions.add(orNode);
        config.accept(new OrJoinGroup<>(root, joinNode, orNode));
        return this;
    }

    @SuppressFBWarnings("EI_EXPOSE_REP")
    public QuerySpec<T> endJoin() {
        return root;
    }
}
