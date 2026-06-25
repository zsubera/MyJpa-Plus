package com.zsubera.jpa.spec;

import com.zsubera.jpa.util.LambdaUtils;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;

/**
 * QuerySpec 的 ORDER BY 辅助类，提供排序字段管理和查询排序应用逻辑。
 *
 * <p>
 * 从 {@link QuerySpec} 中提取，将排序字段的验证、解析和 CriteriaQuery 应用逻辑集中在此类中，
 * 降低 QuerySpec 的复杂度。所有方法返回 {@link QuerySpec} 以支持链式调用。
 *
 * <p>
 * 内部工具类——不建议应用代码直接使用。
 *
 * @param <T> 实体类型
 */
final class QueryOrderBySupport<T> {

    private static final Logger log = LoggerFactory.getLogger(QueryOrderBySupport.class);

    private final QuerySpec<T> parent;
    private final List<ConditionNode.OrderNode> orderNodes;

    QueryOrderBySupport(QuerySpec<T> parent, List<ConditionNode.OrderNode> orderNodes) {
        this.parent = parent;
        this.orderNodes = orderNodes;
    }

    /**
     * 添加升序 ORDER BY 排序。
     *
     * @param fields 要排序的字段方法引用
     * @return 当前 QuerySpec 实例
     */
    @SafeVarargs
    final QuerySpec<T> orderByAsc(SFunction<T, ?>... fields) {
        if (fields == null) {
            throw new IllegalArgumentException("fields must not be null");
        }
        java.util.List<String> names = new java.util.ArrayList<>(fields.length);
        for (SFunction<T, ?> f : fields) {
            if (f == null) {
                throw new IllegalArgumentException("fields must not contain null elements");
            }
            String name = LambdaUtils.getPropertyName(f);
            orderNodes.add(new ConditionNode.OrderNode(name, true));
            names.add(name);
        }
        if (log.isDebugEnabled()) {
            log.debug("QuerySpec: ORDER BY ASC {}", String.join(", ", names));
        }
        return parent;
    }

    /**
     * 添加降序 ORDER BY 排序。
     *
     * @param fields 要排序的字段方法引用
     * @return 当前 QuerySpec 实例
     */
    @SafeVarargs
    final QuerySpec<T> orderByDesc(SFunction<T, ?>... fields) {
        if (fields == null) {
            throw new IllegalArgumentException("fields must not be null");
        }
        java.util.List<String> names = new java.util.ArrayList<>(fields.length);
        for (SFunction<T, ?> f : fields) {
            if (f == null) {
                throw new IllegalArgumentException("fields must not contain null elements");
            }
            String name = LambdaUtils.getPropertyName(f);
            orderNodes.add(new ConditionNode.OrderNode(name, false));
            names.add(name);
        }
        if (log.isDebugEnabled()) {
            log.debug("QuerySpec: ORDER BY DESC {}", String.join(", ", names));
        }
        return parent;
    }

    /**
     * 将排序定义转换为 Spring Data {@link Sort} 对象。
     *
     * @return 排序对象，如果未设置排序则返回 {@link Sort#unsorted()}
     */
    Sort getSort() {
        if (orderNodes.isEmpty()) {
            return Sort.unsorted();
        }
        List<Sort.Order> orders = new ArrayList<>();
        for (ConditionNode.OrderNode node : orderNodes) {
            orders.add(node.asc ? Sort.Order.asc(node.fieldName) : Sort.Order.desc(node.fieldName));
        }
        return Sort.by(orders);
    }

    /**
     * 将 ORDER BY 子句应用到 CriteriaQuery。
     *
     * @param root 根实体路径
     * @param query Criteria 查询对象
     * @param cb Criteria 构建器
     */
    void applyOrderBy(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        if (!orderNodes.isEmpty()) {
            List<jakarta.persistence.criteria.Order> orders = new ArrayList<>();
            for (ConditionNode.OrderNode node : orderNodes) {
                if (node.asc) {
                    orders.add(cb.asc(root.get(node.fieldName)));
                } else {
                    orders.add(cb.desc(root.get(node.fieldName)));
                }
            }
            query.orderBy(orders);
        }
    }

    /**
     * 检查是否有排序条件。
     *
     * @return 如果有排序条件返回 true
     */
    boolean isEmpty() {
        return orderNodes.isEmpty();
    }

    /**
     * 将另一个排序列表合并到当前列表。
     *
     * @param other 要合并的排序节点列表
     */
    void addAll(List<ConditionNode.OrderNode> other) {
        orderNodes.addAll(other);
    }
}
