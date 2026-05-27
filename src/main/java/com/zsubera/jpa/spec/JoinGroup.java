package com.zsubera.jpa.spec;

import com.zsubera.jpa.util.LambdaUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import java.util.function.Consumer;

/**
 * JOIN 条件组构建器，用于在 {@link QuerySpec} 中构建 JOIN 条件。
 *
 * @param <T> 根实体类型
 * @param <J> JOIN 实体类型
 */
public class JoinGroup<T, J> implements ConditionBuilder<J, JoinGroup<T, J>> {

    private final QuerySpec<T> root;
    private final ConditionNode.JoinNode joinNode;

    JoinGroup(QuerySpec<T> root, ConditionNode.JoinNode joinNode) {
        this.root = root;
        this.joinNode = joinNode;
    }

    @Override
    public List<ConditionNode> conditions() {
        return joinNode.innerConditions;
    }

    public OrJoinGroup<T, J> or() {
        ConditionNode.OrNode orNode = new ConditionNode.OrNode();
        joinNode.innerConditions.add(orNode);
        return new OrJoinGroup<>(root, joinNode, orNode);
    }

    public <J2> JoinGroup<T, J2> join(SFunction<J, ?> field) {
        ConditionNode.JoinNode nestedJoin =
            new ConditionNode.JoinNode(LambdaUtils.getPropertyName(field), ConditionNode.JoinType.INNER);
        joinNode.innerConditions.add(nestedJoin);
        return new JoinGroup<>(root, nestedJoin);
    }

    public <J2> JoinGroup<T, J2> leftJoin(SFunction<J, ?> field) {
        ConditionNode.JoinNode nestedJoin =
            new ConditionNode.JoinNode(LambdaUtils.getPropertyName(field), ConditionNode.JoinType.LEFT);
        joinNode.innerConditions.add(nestedJoin);
        return new JoinGroup<>(root, nestedJoin);
    }

    /** JOIN 内自动关闭的 OR：在 JOIN 实体上构建 OR 组，然后返回到当前 JoinGroup。 */
    public JoinGroup<T, J> or(Consumer<OrJoinGroup<T, J>> config) {
        ConditionNode.OrNode orNode = new ConditionNode.OrNode();
        joinNode.innerConditions.add(orNode);
        config.accept(new OrJoinGroup<>(root, joinNode, orNode));
        return this;
    }

    @SuppressFBWarnings("EI_EXPOSE_REP")
    public QuerySpec<T> endJoin() {
        return root;
    }
}
