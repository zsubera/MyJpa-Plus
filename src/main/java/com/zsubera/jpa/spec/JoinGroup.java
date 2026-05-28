package com.zsubera.jpa.spec;

import com.zsubera.jpa.util.LambdaUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import java.util.function.Consumer;

/**
 * JOIN 条件组构建器，用于在 {@link QuerySpec} 中构建 JOIN 条件。
 *
 * <p>
 * <strong>嵌套 JOIN 类型推断限制：</strong>由于 Java 泛型通配符限制，嵌套 JOIN 的返回类型 {@code J2} 无法自动推断。 链式调用会导致类型丢失，必须使用显式变量声明：
 *
 * <pre>{@code
 * // 编译失败：Dept 被推断为 Object
 * roleJoin.join(Role::getDepts).eq(Dept::getPath, "...");
 *
 * // 正确：显式声明变量类型
 * JoinGroup<User, Dept> deptJoin = roleJoin.join(Role::getDepts);
 * deptJoin.eq(Dept::getPath, "...");
 *
 * // 或使用 Consumer 模式（推荐）
 * roleJoin.join(Role::getDepts, dj -> dj.eq(Dept::getPath, "..."));
 * }</pre>
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

    /**
     * 添加嵌套 INNER JOIN，通过显式指定实体类辅助类型推断。
     *
     * <p>
     * 当链式调用导致类型丢失时，使用此方法显式指定关联实体类型：
     *
     * <pre>{@code
     * // 类型安全的链式调用
     * JoinGroup<User, Dept> deptJoin = roleJoin.join(Role::getDepts, Dept.class);
     * deptJoin.eq(Dept::getPath, "...");
     * }</pre>
     *
     * @param field 关联字段的方法引用
     * @param joinEntityClass 关联实体类（仅用于类型推断，不影响运行时行为）
     * @param <J2> 关联实体类型
     * @return 新的 JoinGroup 实例
     */
    public <J2> JoinGroup<T, J2> join(SFunction<J, ?> field, Class<J2> joinEntityClass) {
        ConditionNode.JoinNode nestedJoin =
            new ConditionNode.JoinNode(LambdaUtils.getPropertyName(field), ConditionNode.JoinType.INNER);
        joinNode.innerConditions.add(nestedJoin);
        return new JoinGroup<>(root, nestedJoin);
    }

    /**
     * 添加嵌套 LEFT JOIN，通过显式指定实体类辅助类型推断。
     *
     * @param field 关联字段的方法引用
     * @param joinEntityClass 关联实体类（仅用于类型推断，不影响运行时行为）
     * @param <J2> 关联实体类型
     * @return 新的 JoinGroup 实例
     */
    public <J2> JoinGroup<T, J2> leftJoin(SFunction<J, ?> field, Class<J2> joinEntityClass) {
        ConditionNode.JoinNode nestedJoin =
            new ConditionNode.JoinNode(LambdaUtils.getPropertyName(field), ConditionNode.JoinType.LEFT);
        joinNode.innerConditions.add(nestedJoin);
        return new JoinGroup<>(root, nestedJoin);
    }

    /**
     * 使用消费者构建嵌套 INNER JOIN，自动关闭关联组。
     *
     * <p>
     * Consumer 模式避免了手动调用 {@code endJoin()}，也解决了嵌套 JOIN 的类型推断问题：
     *
     * <pre>{@code
     * qs.join(User::getRoles, roleJoin -> roleJoin.join(Role::getDepts, deptJoin -> deptJoin.eq(Dept::getPath, "..."))
     *     .eq(Role::getName, "ADMIN"));
     * }</pre>
     *
     * @param field 关联字段的方法引用
     * @param config JoinGroup 配置消费者
     * @param <J2> 关联实体类型
     * @return 当前 JoinGroup 实例，支持链式调用
     */
    public <J2> JoinGroup<T, J> join(SFunction<J, ?> field, Consumer<JoinGroup<T, J2>> config) {
        ConditionNode.JoinNode nestedJoin =
            new ConditionNode.JoinNode(LambdaUtils.getPropertyName(field), ConditionNode.JoinType.INNER);
        joinNode.innerConditions.add(nestedJoin);
        config.accept(new JoinGroup<>(root, nestedJoin));
        return this;
    }

    /**
     * 使用消费者构建嵌套 LEFT JOIN，自动关闭关联组。
     *
     * @param field 关联字段的方法引用
     * @param config JoinGroup 配置消费者
     * @param <J2> 关联实体类型
     * @return 当前 JoinGroup 实例，支持链式调用
     */
    public <J2> JoinGroup<T, J> leftJoin(SFunction<J, ?> field, Consumer<JoinGroup<T, J2>> config) {
        ConditionNode.JoinNode nestedJoin =
            new ConditionNode.JoinNode(LambdaUtils.getPropertyName(field), ConditionNode.JoinType.LEFT);
        joinNode.innerConditions.add(nestedJoin);
        config.accept(new JoinGroup<>(root, nestedJoin));
        return this;
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
