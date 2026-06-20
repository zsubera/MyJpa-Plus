package com.zsubera.jpa.spec;

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
     * 使用消费者构建 OR 条件组，自动关闭组。
     *
     * @param config OR 组配置消费者
     * @return 当前 QuerySpec 实例
     */
    QuerySpec<T> or(Consumer<OrGroup<T>> config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        ConditionNode.OrNode orNode = new ConditionNode.OrNode();
        parent.currentGroup().add(orNode);
        parent.getGroupStack().push(orNode.nodes);
        try {
            config.accept(new OrGroup<>(parent));
        } finally {
            parent.getGroupStack().pop();
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
        parent.currentGroup().add(negate);
        parent.getGroupStack().push(andNode.nodes);
        try {
            config.accept(NotGroup.create(parent));
        } finally {
            parent.getGroupStack().pop();
        }
        return parent;
    }

    // ---- 条件组合方法 ----

    /**
     * 验证所有条件组已正确关闭。
     *
     * @throws IllegalStateException 如果存在未关闭的 or() 组
     */
    void validateCleanState() {
        if (!parent.getGroupStack().isEmpty()) {
            throw new IllegalStateException("Not all or() groups were closed with endOr() before building the query");
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
    Specification<T> or(QuerySpec<T> other) {
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
