package com.zsubera.jpa.update;

import com.zsubera.jpa.spec.BulkConditionSupport;
import com.zsubera.jpa.spec.SFunction;
import com.zsubera.jpa.update.AbstractBulkOperationSpec.BulkConditionNode;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.List;
import java.util.function.BiFunction;

/**
 * 批量操作（{@link UpdateSpec} 和 {@link DeleteSpec}）中 OR 条件组的构建器。
 *
 * <p>
 * 通过此构建器添加的所有条件将以 OR 方式组合。由 {@link AbstractBulkOperationSpec#or(java.util.function.Consumer)} 隐式创建。
 *
 * @param <T> 实体类型
 * @param <SELF> 父构建器类型
 */
public class OrConditionBuilder<T, SELF extends AbstractBulkOperationSpec<T, SELF>>
    implements BulkConditionSupport<T, OrConditionBuilder<T, SELF>> {

    private final SELF parent;
    private final List<BulkConditionNode> nodes;

    /**
     * 构造函数。
     *
     * @param parent 父构建器
     * @param nodes 条件节点列表
     */
    OrConditionBuilder(SELF parent, List<BulkConditionNode> nodes) {
        this.parent = parent;
        this.nodes = nodes;
    }

    @Override
    public OrConditionBuilder<T, SELF> self() {
        return this;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public OrConditionBuilder<T, SELF> addCondition(BiFunction<Root<?>, CriteriaBuilder, Predicate> predicateFn) {
        nodes.add(new BulkConditionNode.LeafNode((BiFunction)predicateFn));
        return this;
    }

    @Override
    public String property(SFunction<T, ?> field) {
        return parent.property(field);
    }

    // ---- 条件方法由 BulkConditionSupport 默认实现提供 ----

    /**
     * 添加多字段 LIKE 搜索条件。关键字被包装为 {@code %keyword%} 并与每个给定字段匹配，使用 OR 连接。
     *
     * <p>
     * 值中的 {@code %} 或 {@code _} 字符会被转义，作为字面量处理，防止 LIKE 注入。
     *
     * @param keyword 搜索关键字
     * @param fields 一个或多个字符串属性的方法引用
     * @return 当前构建器实例
     * @throws IllegalArgumentException 如果 keyword 为 null，或 fields 为 null，或 fields 包含 null 元素
     */
    @SafeVarargs
    public final OrConditionBuilder<T, SELF> multiLike(String keyword, SFunction<T, ?>... fields) {
        AbstractBulkOperationSpec.BulkMultiLikeResult result =
            AbstractBulkOperationSpec.resolveMultiLike(keyword, fields, parent::property);
        if (result != null) {
            nodes.add(new BulkConditionNode.LeafNode(
                AbstractBulkOperationSpec.buildMultiLikeFn(result.fieldNames, result.pattern)));
        }
        return this;
    }
}
