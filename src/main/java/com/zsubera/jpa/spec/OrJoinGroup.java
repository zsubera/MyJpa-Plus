package com.zsubera.jpa.spec;

import java.util.List;
import java.util.Objects;

/**
 * JOIN 内的 OR 条件组构建器，用于在 {@link JoinGroup} 中构建 OR 条件。
 *
 * <p>
 * 适用场景：当需要在 JOIN 条件中使用 OR 逻辑组合多个条件时使用。例如，查找关联实体满足条件 A 或条件 B 的记录：
 *
 * <pre>{@code
 * qs.join(User::getRoles, roleJoin -> roleJoin.or(or -> or.eq(Role::getName, "ADMIN").eq(Role::getName, "MODERATOR"))
 *     .eq(Role::getActive, true));
 * // 生成: JOIN roles r ON (r.name = 'ADMIN' OR r.name = 'MODERATOR') AND r.active = true
 * }</pre>
 *
 * <p>
 * 使用 {@link JoinGroup#or(java.util.function.Consumer)} 方法创建自动关闭的 OR 组。
 *
 * @param <T> 根实体类型
 * @param <J> JOIN 实体类型
 */
public class OrJoinGroup<T, J> implements ConditionBuilder<J, OrJoinGroup<T, J>> {

    private final ConditionNode.OrNode orNode;

    OrJoinGroup(QuerySpec<T> root, ConditionNode.JoinNode joinNode, ConditionNode.OrNode orNode) {
        Objects.requireNonNull(root, "root must not be null");
        Objects.requireNonNull(joinNode, "joinNode must not be null");
        Objects.requireNonNull(orNode, "orNode must not be null");
        this.orNode = orNode;
    }

    @Override
    public List<ConditionNode> conditions() {
        return orNode.nodes;
    }
}
