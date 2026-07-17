package com.zsubera.jpa.spec;

import java.util.List;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.Nullable;

/**
 * QuerySpec 的条件组合辅助类，提供 OR/NOT 条件组构建和 QuerySpec 之间的条件合并/组合逻辑。
 *
 * <p>
 * 从 {@link QueryConditionSupport} 中提取，将 OR/NOT 组的 Consumer 模式、
 * {@link #toSpecification()} 转换、{@link #then(QuerySpec)} 条件合并逻辑集中在此类中。
 *
 * <p>
 * 内部工具类——不建议应用代码直接使用。
 *
 * @param <T> 实体类型
 */
final class QueryCompositionSupport<T> {

    private static final Logger log = LoggerFactory.getLogger(QueryCompositionSupport.class);

    private final QuerySpec<T> parent;

    QueryCompositionSupport(QuerySpec<T> parent) {
        this.parent = parent;
    }

    // ---- OR/NOT 方法 ----

    /**
     * 使用多个消费者构建 OR 条件组，每个消费者代表一个 OR 分支。
     *
     * <p>
     * 每个 lambda 代表一个 OR 分支，lambda 内部的链式调用（如 {@code .eq().eq()}）表示 AND 语义，
     * 与外层保持一致，消除了"相同语法不同含义"的困惑。
     *
     * <p>
     * <strong>示例：</strong>
     *
     * <pre>{@code
     * // 每个 lambda 是一个 OR 分支
     * s.or(
     *     o -> o.eq(User::getRole, "ADMIN"),           // 分支1: role='ADMIN'
     *     o -> o.eq(User::getStatus, "ACTIVE")         // 分支2: status='ACTIVE'
     * );
     * // → role='ADMIN' OR status='ACTIVE'
     *
     * // lambda 内部 .eq().eq() = AND（与外层一致）
     * s.or(
     *     o -> o.eq(User::getRole, "ADMIN").eq(User::getStatus, "ACTIVE"),   // 分支1: (ADMIN AND ACTIVE)
     *     o -> o.eq(User::getRole, "USER")                                    // 分支2: (USER)
     * );
     * // → (role='ADMIN' AND status='ACTIVE') OR (role='USER')
     * }</pre>
     *
     * @param branches 多个 OR 分支消费者
     * @return 当前 QuerySpec 实例
     */
    @SafeVarargs
    final QuerySpec<T> or(Consumer<OrGroup<T>>... branches) {
        if (branches == null || branches.length == 0) {
            throw new IllegalArgumentException("At least one OR branch required");
        }

        List<ConditionNode> group = parent.currentGroup();
        ConditionNode.OrNode orNode;
        boolean isNewOrNode = false;

        // 合并到已存在的 OrNode：如果当前组最后一个节点也是 OrNode，则合并条件而非创建新节点
        if (!group.isEmpty() && group.get(group.size() - 1) instanceof ConditionNode.OrNode) {
            orNode = (ConditionNode.OrNode)group.get(group.size() - 1);
        } else {
            orNode = new ConditionNode.OrNode();
            group.add(orNode);
            isNewOrNode = true;
        }

        int sizeBefore = orNode.nodes.size();

        // 为每个 lambda 创建独立的 OrGroup，lambda 内部的条件会添加到独立的 AndNode 中
        for (Consumer<OrGroup<T>> branch : branches) {
            if (branch == null) {
                throw new IllegalArgumentException("OR branch must not be null");
            }

            ConditionNode.AndNode andNode = new ConditionNode.AndNode();
            orNode.nodes.add(andNode);

            int andSizeBefore = andNode.nodes.size();
            parent.getGroupStack().push(andNode.nodes);
            try {
                branch.accept(new OrGroup<>(parent));
            } catch (RuntimeException e) {
                // 消费者异常时移除本次添加的部分条件
                andNode.nodes.subList(andSizeBefore, andNode.nodes.size()).clear();
                orNode.nodes.remove(andNode);
                throw e;
            } finally {
                parent.getGroupStack().pop();
            }

            // 如果 AndNode 为空（lambda 没有条件），则移除 AndNode
            if (andNode.nodes.isEmpty()) {
                orNode.nodes.remove(andNode);
            }
        }

        // 如果 OrNode 为空（所有分支都失败或为空），则移除 OrNode
        if (isNewOrNode && orNode.nodes.isEmpty()) {
            group.remove(orNode);
        }

        return parent;
    }

    /**
     * 添加 NOT 条件组，对组合条件取反。
     *
     * @param config 条件组配置消费者
     * @return 当前 QuerySpec 实例
     */
    QuerySpec<T> not(Consumer<NotGroup<T>> config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        ConditionNode.AndNode andNode = new ConditionNode.AndNode();
        ConditionNode.NegateNode negate = new ConditionNode.NegateNode(andNode);
        // 保存根组引用——push 到 stack 后 currentGroup() 会返回 andNode.nodes，
        // 因此必须在 push 之前捕获 negate 所在的父组。
        List<ConditionNode> rootGroup = parent.currentGroup();
        rootGroup.add(negate);
        int sizeBefore = andNode.nodes.size();
        parent.getGroupStack().push(andNode.nodes);
        try {
            config.accept(NotGroup.create(parent));
        } catch (RuntimeException e) {
            andNode.nodes.subList(sizeBefore, andNode.nodes.size()).clear();
            rootGroup.remove(negate);
            throw e;
        } finally {
            parent.getGroupStack().pop();
        }
        return parent;
    }

    // ---- 条件组合方法 ----

    /**
     * 验证所有条件组已正确关闭。
     *
     * @throws com.zsubera.jpa.exception.QueryBuildException 如果存在未关闭的 or() 组
     */
    void validateCleanState() {
        if (!parent.getGroupStack().isEmpty()) {
            throw new com.zsubera.jpa.exception.QueryBuildException(
                "Not all or() groups were closed with endOr() before building the query");
        }
    }

    /**
     * 将此 QuerySpec 转换为 Spring Data Specification。
     *
     * @return Specification 实例
     */
    Specification<T> toSpecification() {
        validateCleanState();
        return parent;
    }

    /**
     * 将此 QuerySpec 与另一个 Specification 使用 AND 组合。
     *
     * @param external 要组合的外部 Specification（可以为 null）
     * @return 组合后的 Specification 实例
     */
    Specification<T> toSpecification(@Nullable Specification<T> external) {
        validateCleanState();
        if (external == null) {
            return parent;
        }
        return parent.and(external);
    }

    /**
     * 将此 QuerySpec 与另一个 QuerySpec 使用 OR 组合。
     *
     * @param other 另一个 QuerySpec 实例
     * @return 组合后的 Specification 实例
     */
    Specification<T> orCombine(QuerySpec<T> other) {
        if (other == null) {
            return parent;
        }
        return parent.or(other.toSpecification());
    }

    /**
     * 将另一个 QuerySpec 的条件以 AND 语义合并到当前实例。
     *
     * @param other 另一个 QuerySpec 实例
     * @return 当前 QuerySpec 实例
     */
    QuerySpec<T> then(QuerySpec<T> other) {
        if (other == null) {
            return parent;
        }
        if (!parent.getGroupStack().isEmpty()) {
            throw new IllegalStateException(
                "Cannot merge into a QuerySpec with unclosed or() groups. Close all groups with endOr() before calling then().");
        }
        if (!other.getGroupStack().isEmpty()) {
            throw new IllegalStateException(
                "Cannot merge a QuerySpec with unclosed or() groups. Close all groups with endOr() before calling then().");
        }
        for (ConditionNode node : other.getConditions()) {
            parent.getConditions().add(QuerySpec.deepCopyNode(node));
        }
        if (other.isDistinct()) {
            parent.setDistinct(true);
        }
        parent.getGroupByFields().addAll(other.getGroupByFields());
        parent.getHavingSupport().addAll(other.getHavingConditions());
        parent.getOrderBySupport().addAll(other.getOrderNodes());
        if (other.getQueryTimeout() != null && parent.getQueryTimeout() == null) {
            parent.setQueryTimeout(other.getQueryTimeout());
        }
        if (other.getLockMode() != null && parent.getLockMode() == null) {
            parent.setLockMode(other.getLockMode());
        }
        return parent;
    }
}
