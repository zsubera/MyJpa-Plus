package com.zsubera.jpa.spec;

import com.zsubera.jpa.util.LambdaUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import java.util.function.Consumer;

/**
 * NOT 条件组构建器，用于在 {@link QuerySpec} 中构建 NOT 条件组。
 *
 * <p>
 * 组内多个条件之间为 AND 关系，整体取反。 即 {@code not(b -> b.eq(A).eq(B))} 生成 {@code NOT (A AND B)}，根据德摩根定律等价于
 * {@code NOT A OR NOT B}。
 *
 * @param <T> 实体类型
 */
@SuppressFBWarnings(value = "CT_CONSTRUCTOR_THROW",
    justification = "Factory method validates parameters before constructor call")
public class NotGroup<T> implements ConditionBuilder<T, NotGroup<T>> {

    private final QuerySpec<T> root;

    private NotGroup(QuerySpec<T> root) {
        this.root = root;
    }

    static <T> NotGroup<T> create(QuerySpec<T> root) {
        if (root == null) {
            throw new IllegalArgumentException("root must not be null");
        }
        return new NotGroup<>(root);
    }

    @Override
    public List<ConditionNode> conditions() {
        return root.currentGroup();
    }

    private <J> JoinGroup<T, J> internalJoin(SFunction<T, ?> field, ConditionNode.JoinType joinType) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        ConditionNode.JoinNode joinNode = new ConditionNode.JoinNode(LambdaUtils.getPropertyName(field), joinType);
        root.currentGroup().add(joinNode);
        return new JoinGroup<>(root, joinNode);
    }

    /**
     * 在 NOT 组内添加 INNER JOIN。
     *
     * @param field 关联字段的方法引用
     * @param <J> 关联实体类型
     * @return 新的 JoinGroup 实例，用于添加 JOIN 条件
     * @throws IllegalArgumentException 如果 {@code field} 为 null
     */
    public <J> JoinGroup<T, J> join(SFunction<T, ?> field) {
        return internalJoin(field, ConditionNode.JoinType.INNER);
    }

    /**
     * 在 NOT 组内添加 INNER JOIN，并配置 JOIN 条件。
     *
     * @param field 关联字段的方法引用
     * @param config JOIN 条件配置消费者
     * @param <J> 关联实体类型
     * @return 当前 NotGroup 实例，支持链式调用
     * @throws IllegalArgumentException 如果 {@code field} 或 {@code config} 为 null
     */
    public <J> NotGroup<T> join(SFunction<T, ?> field, Consumer<JoinGroup<T, J>> config) {
        JoinGroup<T, J> joinGroup = internalJoin(field, ConditionNode.JoinType.INNER);
        config.accept(joinGroup);
        return this;
    }

    /**
     * 在 NOT 组内添加 LEFT JOIN。
     *
     * @param field 关联字段的方法引用
     * @param <J> 关联实体类型
     * @return 新的 JoinGroup 实例，用于添加 JOIN 条件
     * @throws IllegalArgumentException 如果 {@code field} 为 null
     */
    public <J> JoinGroup<T, J> leftJoin(SFunction<T, ?> field) {
        return internalJoin(field, ConditionNode.JoinType.LEFT);
    }

    /**
     * 在 NOT 组内添加 LEFT JOIN，并配置 JOIN 条件。
     *
     * @param field 关联字段的方法引用
     * @param config JOIN 条件配置消费者
     * @param <J> 关联实体类型
     * @return 当前 NotGroup 实例，支持链式调用
     * @throws IllegalArgumentException 如果 {@code field} 或 {@code config} 为 null
     */
    public <J> NotGroup<T> leftJoin(SFunction<T, ?> field, Consumer<JoinGroup<T, J>> config) {
        JoinGroup<T, J> joinGroup = internalJoin(field, ConditionNode.JoinType.LEFT);
        config.accept(joinGroup);
        return this;
    }
}
