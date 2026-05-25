package com.zsubera.jpa.spec;

import com.zsubera.jpa.util.LambdaUtils;

import java.util.List;
import java.util.function.Consumer;

public class OrGroup<T> implements ConditionBuilder<T, OrGroup<T>> {

    private final QuerySpec<T> root;

    OrGroup(QuerySpec<T> root) {
        this.root = root;
    }

    @Override
    public List<QuerySpec.ConditionNode> conditions() {
        return root.currentGroup();
    }

    @SuppressWarnings("unchecked")
    private <J> JoinGroup<T, J> internalJoin(SFunction<T, ?> field, QuerySpec.JoinType joinType) {
        QuerySpec.JoinNode joinNode = new QuerySpec.JoinNode(LambdaUtils.getPropertyName(field), joinType);
        root.currentGroup().add(joinNode);
        return new JoinGroup<>(root, joinNode);
    }

    public <J> JoinGroup<T, J> join(SFunction<T, ?> field) {
        return internalJoin(field, QuerySpec.JoinType.INNER);
    }

    public <J> JoinGroup<T, J> leftJoin(SFunction<T, ?> field) {
        return internalJoin(field, QuerySpec.JoinType.LEFT);
    }

    public OrGroup<T> or() {
        QuerySpec.OrNode nested = new QuerySpec.OrNode();
        root.currentGroup().add(nested);
        root.pushGroupStack(nested.nodes);
        return new OrGroup<>(root);
    }

    public QuerySpec<T> endOr() {
        root.endOr();
        return root;
    }

    /**
     * Self-closing OR: builds a nested OR group with a consumer, then returns to this OrGroup.
     */
    public OrGroup<T> or(Consumer<OrGroup<T>> config) {
        QuerySpec.OrNode nested = new QuerySpec.OrNode();
        root.currentGroup().add(nested);
        root.pushGroupStack(nested.nodes);
        config.accept(new OrGroup<>(root));
        root.endOr();
        return this;
    }

    /**
     * Self-closing JOIN inside OR group.
     */
    public <J> OrGroup<T> join(SFunction<T, ?> field, Consumer<JoinGroup<T, J>> config) {
        QuerySpec.JoinNode joinNode = new QuerySpec.JoinNode(LambdaUtils.getPropertyName(field), QuerySpec.JoinType.INNER);
        root.currentGroup().add(joinNode);
        config.accept(new JoinGroup<>(root, joinNode));
        return this;
    }

    /**
     * Self-closing LEFT JOIN inside OR group.
     */
    public <J> OrGroup<T> leftJoin(SFunction<T, ?> field, Consumer<JoinGroup<T, J>> config) {
        QuerySpec.JoinNode joinNode = new QuerySpec.JoinNode(LambdaUtils.getPropertyName(field), QuerySpec.JoinType.LEFT);
        root.currentGroup().add(joinNode);
        config.accept(new JoinGroup<>(root, joinNode));
        return this;
    }
}
