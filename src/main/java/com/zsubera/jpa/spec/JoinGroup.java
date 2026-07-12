package com.zsubera.jpa.spec;

import com.zsubera.jpa.util.LambdaUtils;
import java.util.List;
import java.util.function.Consumer;

/**
 * JOIN 条件组构建器，用于在 {@link QuerySpec} 中构建 JOIN 条件。
 *
 * <p>
 * 使用 Consumer 模式自动关闭关联组，避免手动调用 {@code endJoin()}：
 *
 * <pre>{@code
 * // Consumer 模式（推荐）
 * qs.join(User::getRoles, roleJoin -> roleJoin.eq(Role::getName, "ADMIN"));
 *
 * // 嵌套 JOIN
 * qs.join(User::getRoles, roleJoin -> {
 *     roleJoin.eq(Role::getName, "ADMIN");
 *     roleJoin.join(Role::getDepts, deptJoin -> deptJoin.eq(Dept::getPath, "..."));
 * });
 * }</pre>
 *
 * @param <T> 根实体类型
 * @param <J> JOIN 实体类型
 */
public class JoinGroup<T, J> implements ConditionBuilder<J, JoinGroup<T, J>> {

    private final QuerySpec<T> root;
    private final ConditionNode.JoinNode joinNode;

    JoinGroup(QuerySpec<T> root, ConditionNode.JoinNode joinNode) {
        if (root == null) {
            throw new IllegalArgumentException("root must not be null");
        }
        if (joinNode == null) {
            throw new IllegalArgumentException("joinNode must not be null");
        }
        this.root = root;
        this.joinNode = joinNode;
    }

    static <T, J> JoinGroup<T, J> createJoin(QuerySpec<T> root, SFunction<T, ?> field,
        ConditionNode.JoinType joinType) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        ConditionNode.JoinNode joinNode = new ConditionNode.JoinNode(LambdaUtils.getPropertyName(field), joinType);
        root.currentGroup().add(joinNode);
        return new JoinGroup<>(root, joinNode);
    }

    @Override
    public List<ConditionNode> conditions() {
        return joinNode.innerConditions;
    }

    // ---- 内部方法 ----

    /**
     * 在 JOIN 条件组中创建 OR 子组（内部使用）。
     *
     * @return 新的 OrJoinGroup 实例
     */
    OrJoinGroup<T, J> pushOrJoinGroup() {
        ConditionNode.OrNode orNode = new ConditionNode.OrNode();
        joinNode.innerConditions.add(orNode);
        return new OrJoinGroup<>(root, joinNode, orNode);
    }

    // ---- 基于 Consumer 的 API（自动关闭） ----

    /**
     * 添加嵌套 INNER JOIN，并配置 JOIN 条件。
     *
     * @param field 关联字段的方法引用
     * @param config JOIN 条件配置消费者
     * @param <J2> 关联实体类型
     * @return 当前 JoinGroup 实例，支持链式调用
     * @throws IllegalArgumentException 如果 field 或 config 为 null
     */
    public <J2> JoinGroup<T, J2> join(SFunction<J, ?> field, Consumer<JoinGroup<T, J2>> config) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        ConditionNode.JoinNode nestedJoin =
            new ConditionNode.JoinNode(LambdaUtils.getPropertyName(field), ConditionNode.JoinType.INNER);
        joinNode.innerConditions.add(nestedJoin);
        config.accept(new JoinGroup<>(root, nestedJoin));
        @SuppressWarnings("unchecked")
        JoinGroup<T, J2> result = (JoinGroup<T, J2>)this;
        return result;
    }

    /**
     * 添加嵌套 LEFT JOIN，并配置 JOIN 条件。
     *
     * @param field 关联字段的方法引用
     * @param config JOIN 条件配置消费者
     * @param <J2> 关联实体类型
     * @return 当前 JoinGroup 实例，支持链式调用
     * @throws IllegalArgumentException 如果 field 或 config 为 null
     */
    public <J2> JoinGroup<T, J2> leftJoin(SFunction<J, ?> field, Consumer<JoinGroup<T, J2>> config) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        ConditionNode.JoinNode nestedJoin =
            new ConditionNode.JoinNode(LambdaUtils.getPropertyName(field), ConditionNode.JoinType.LEFT);
        joinNode.innerConditions.add(nestedJoin);
        config.accept(new JoinGroup<>(root, nestedJoin));
        @SuppressWarnings("unchecked")
        JoinGroup<T, J2> result = (JoinGroup<T, J2>)this;
        return result;
    }

    /**
     * 添加嵌套 FETCH JOIN（INNER），通过 {@code JOIN FETCH} 预加载关联实体。
     *
     * @param field 关联字段的方法引用
     * @param config JOIN 条件配置消费者
     * @param <J2> 关联实体类型
     * @return 当前 JoinGroup 实例，支持链式调用
     * @throws IllegalArgumentException 如果 field 或 config 为 null
     */
    public <J2> JoinGroup<T, J2> fetchJoin(SFunction<J, ?> field, Consumer<JoinGroup<T, J2>> config) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        ConditionNode.JoinNode nestedJoin =
            new ConditionNode.JoinNode(LambdaUtils.getPropertyName(field), ConditionNode.JoinType.FETCH);
        joinNode.innerConditions.add(nestedJoin);
        config.accept(new JoinGroup<>(root, nestedJoin));
        @SuppressWarnings("unchecked")
        JoinGroup<T, J2> result = (JoinGroup<T, J2>)this;
        return result;
    }

    /**
     * 添加嵌套 LEFT FETCH JOIN，通过 {@code LEFT JOIN FETCH} 预加载关联实体。
     *
     * @param field 关联字段的方法引用
     * @param config JOIN 条件配置消费者
     * @param <J2> 关联实体类型
     * @return 当前 JoinGroup 实例，支持链式调用
     * @throws IllegalArgumentException 如果 field 或 config 为 null
     */
    public <J2> JoinGroup<T, J2> leftFetchJoin(SFunction<J, ?> field, Consumer<JoinGroup<T, J2>> config) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        ConditionNode.JoinNode nestedJoin =
            new ConditionNode.JoinNode(LambdaUtils.getPropertyName(field), ConditionNode.JoinType.LEFT_FETCH);
        joinNode.innerConditions.add(nestedJoin);
        config.accept(new JoinGroup<>(root, nestedJoin));
        @SuppressWarnings("unchecked")
        JoinGroup<T, J2> result = (JoinGroup<T, J2>)this;
        return result;
    }

    /**
     * 使用消费者构建 OR 组内的 INNER JOIN，自动关闭关联组。
     *
     * @param config OrJoinGroup 配置消费者
     * @return 当前 JoinGroup 实例，支持链式调用
     * @throws IllegalArgumentException 如果 config 为 null
     */
    public JoinGroup<T, J> or(Consumer<OrJoinGroup<T, J>> config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        OrJoinGroup<T, J> orJoinGroup = pushOrJoinGroup();
        config.accept(orJoinGroup);
        return this;
    }
}
