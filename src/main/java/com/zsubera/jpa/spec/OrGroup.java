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
        this.root = root;
    }

    @Override
    public List<ConditionNode> conditions() {
        return root.currentGroup();
    }

    @SuppressWarnings("unchecked")
    private <J> JoinGroup<T, J> internalJoin(SFunction<T, ?> field, ConditionNode.JoinType joinType) {
        ConditionNode.JoinNode joinNode = new ConditionNode.JoinNode(LambdaUtils.getPropertyName(field), joinType);
        root.currentGroup().add(joinNode);
        return new JoinGroup<>(root, joinNode);
    }

    public <J> JoinGroup<T, J> join(SFunction<T, ?> field) {
        return internalJoin(field, ConditionNode.JoinType.INNER);
    }

    public <J> JoinGroup<T, J> leftJoin(SFunction<T, ?> field) {
        return internalJoin(field, ConditionNode.JoinType.LEFT);
    }

    public OrGroup<T> or() {
        ConditionNode.OrNode nested = new ConditionNode.OrNode();
        root.currentGroup().add(nested);
        root.pushGroupStack(nested.nodes);
        return new OrGroup<>(root);
    }

    @SuppressFBWarnings("EI_EXPOSE_REP")
    public QuerySpec<T> endOr() {
        root.endOr();
        return root;
    }

    /** 自动关闭的 OR：构建嵌套 OR 组，然后返回到当前 OrGroup。 */
    public OrGroup<T> or(Consumer<OrGroup<T>> config) {
        ConditionNode.OrNode nested = new ConditionNode.OrNode();
        root.currentGroup().add(nested);
        root.pushGroupStack(nested.nodes);
        config.accept(new OrGroup<>(root));
        root.endOr();
        return this;
    }

    /** OR 组内自动关闭的 JOIN。 */
    public <J> OrGroup<T> join(SFunction<T, ?> field, Consumer<JoinGroup<T, J>> config) {
        ConditionNode.JoinNode joinNode =
            new ConditionNode.JoinNode(LambdaUtils.getPropertyName(field), ConditionNode.JoinType.INNER);
        root.currentGroup().add(joinNode);
        config.accept(new JoinGroup<>(root, joinNode));
        return this;
    }

    /** OR 组内自动关闭的 LEFT JOIN。 */
    public <J> OrGroup<T> leftJoin(SFunction<T, ?> field, Consumer<JoinGroup<T, J>> config) {
        ConditionNode.JoinNode joinNode =
            new ConditionNode.JoinNode(LambdaUtils.getPropertyName(field), ConditionNode.JoinType.LEFT);
        root.currentGroup().add(joinNode);
        config.accept(new JoinGroup<>(root, joinNode));
        return this;
    }
}
