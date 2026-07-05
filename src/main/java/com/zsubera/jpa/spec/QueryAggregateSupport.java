package com.zsubera.jpa.spec;

import com.zsubera.jpa.util.LambdaUtils;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QuerySpec 的聚合查询辅助类，提供 GROUP BY 字段管理和 DISTINCT/GROUP BY/HAVING 的查询应用逻辑。
 *
 * <p>
 * 从 {@link QuerySpec} 中提取，将 GROUP BY 字段的验证、解析和 CriteriaQuery 应用逻辑集中在此类中，
 * 降低 QuerySpec 的复杂度。所有方法返回 {@link QuerySpec} 以支持链式调用。
 *
 * <p>
 * 内部工具类——不建议应用代码直接使用。
 *
 * @param <T> 实体类型
 */
final class QueryAggregateSupport<T> {

    private static final Logger log = LoggerFactory.getLogger(QueryAggregateSupport.class);

    private final QuerySpec<T> parent;
    private final List<String> groupByFields;
    private final QueryHavingSupport<T> havingSupport;

    QueryAggregateSupport(QuerySpec<T> parent, List<String> groupByFields, QueryHavingSupport<T> havingSupport) {
        this.parent = parent;
        this.groupByFields = groupByFields;
        this.havingSupport = havingSupport;
    }

    /**
     * 添加 GROUP BY 子句，按给定字段进行分组。
     *
     * @param fields 要分组的字段方法引用
     * @return 当前 QuerySpec 实例
     */
    @SafeVarargs
    final QuerySpec<T> groupBy(SFunction<T, ?>... fields) {
        if (fields == null) {
            throw new IllegalArgumentException("fields must not be null");
        }
        for (SFunction<T, ?> f : fields) {
            if (f == null) {
                throw new IllegalArgumentException("fields must not contain null elements");
            }
            groupByFields.add(LambdaUtils.getPropertyName(f));
        }
        log.debug("QuerySpec: GROUP BY {}", groupByFields);
        return parent;
    }

    /**
     * 将 DISTINCT、GROUP BY 和 HAVING 子句应用到 CriteriaQuery。
     *
     * @param distinct 是否启用 DISTINCT
     * @param root 根实体路径
     * @param query Criteria 查询对象
     * @param cb Criteria 构建器
     */
    void applyDistinctAndGroupBy(boolean distinct, Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        if (distinct) {
            query.distinct(true);
        }
        if (!groupByFields.isEmpty()) {
            List<Path<?>> paths = new ArrayList<>();
            for (String field : groupByFields) {
                paths.add(root.get(field));
            }
            query.groupBy(paths.toArray(new Expression[0]));
        }
        havingSupport.applyHaving(root, groupByFields, query, cb);
    }

    /**
     * 检查是否有 GROUP BY 字段。
     *
     * @return 如果有 GROUP BY 字段返回 true
     */
    boolean hasGroupBy() {
        return !groupByFields.isEmpty();
    }

    /**
     * 返回 GROUP BY 字段列表。
     *
     * @return GROUP BY 字段列表
     */
    List<String> getGroupByFields() {
        return groupByFields;
    }
}
