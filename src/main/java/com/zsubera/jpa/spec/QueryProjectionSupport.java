package com.zsubera.jpa.spec;

import com.zsubera.jpa.template.MyJpaTemplate;
import com.zsubera.jpa.util.LambdaUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.Nullable;

/**
 * QuerySpec 投影查询的支持类，处理 Tuple/DTO 投影的构建和执行。
 *
 * @param <T> 实体类型
 */
public final class QueryProjectionSupport<T> {

    private static final Logger log = LoggerFactory.getLogger(QueryProjectionSupport.class);

    private final Class<T> entityClass;
    private final QuerySpec<T> spec;
    private final Specification<T> softDeleteSpec;
    private final List<QuerySpec.ProjectionField> fields;

    @SafeVarargs
    public QueryProjectionSupport(Class<T> entityClass, QuerySpec<T> spec, SFunction<T, ?>... fields) {
        this(entityClass, spec, null, fields);
    }

    @SafeVarargs
    public QueryProjectionSupport(Class<T> entityClass, QuerySpec<T> spec, @Nullable Specification<T> softDeleteSpec,
        SFunction<T, ?>... fields) {
        if (fields == null || fields.length == 0) {
            throw new IllegalArgumentException("Projection fields must not be null or empty. "
                + "Use QuerySpec.select() to specify fields, then pass them to the projection method.");
        }
        this.entityClass = entityClass;
        this.spec = spec;
        this.softDeleteSpec = softDeleteSpec;
        this.fields = wrapFields(fields);
    }

    @SuppressWarnings("rawtypes")
    public QueryProjectionSupport(Class<T> entityClass, QuerySpec<T> spec, @Nullable Specification<T> softDeleteSpec,
        List fields) {
        if (fields == null || fields.isEmpty()) {
            throw new IllegalArgumentException("Projection fields must not be null or empty. "
                + "Use QuerySpec.select() to specify fields, then pass them to the projection method.");
        }
        this.entityClass = entityClass;
        this.spec = spec;
        this.softDeleteSpec = softDeleteSpec;
        this.fields = fields;
    }

    private static <T> List<QuerySpec.ProjectionField> wrapFields(SFunction<T, ?>[] fields) {
        List<QuerySpec.ProjectionField> list = new ArrayList<>();
        for (SFunction<T, ?> f : fields) {
            list.add(new QuerySpec.ProjectionField(f, null));
        }
        return list;
    }

    public List<Tuple> toTupleList(EntityManager em, int maxResults) {
        TypedQuery<Tuple> query = buildTupleQuery(em, maxResults);
        return query.getResultList();
    }

    public TypedQuery<Tuple> buildTupleQuery(EntityManager em, int maxResults) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<T> root = cq.from(entityClass);

        List<Selection<?>> selections = buildSelectionList(root, cb);
        cq.multiselect(selections);

        cq.where(getCombinedPredicate(root, cq, cb));
        spec.applyDistinctAndGroupBy(root, cq, cb);
        spec.applyOrderBy(root, cq, cb);

        TypedQuery<Tuple> query = em.createQuery(cq);
        if (maxResults > 0) {
            query.setMaxResults(maxResults);
        }
        return query;
    }

    public <R> List<R> toDtoList(EntityManager em, Class<R> dtoClass, int maxResults) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<R> cq = cb.createQuery(dtoClass);
        Root<T> root = cq.from(entityClass);

        List<Selection<?>> selections = buildSelectionList(root, cb);
        Selection<?>[] ordered = alignSelectionsToConstructor(selections, dtoClass);
        @SuppressWarnings("unchecked")
        Selection<R> construct = (Selection<R>)cb.construct(dtoClass, ordered);
        cq.select(construct);

        cq.where(getCombinedPredicate(root, cq, cb));
        spec.applyDistinctAndGroupBy(root, cq, cb);
        spec.applyOrderBy(root, cq, cb);

        TypedQuery<R> query = em.createQuery(cq);
        if (maxResults > 0) {
            query.setMaxResults(maxResults);
        }
        return query.getResultList();
    }

    /**
     * 将选择列表按 DTO 构造函数的参数名重新排序，实现按名称匹配而非位置匹配。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Selection<?>[] alignSelectionsToConstructor(List<Selection<?>> selections, Class<?> dtoClass) {
        String[] paramNames = getConstructorParamNames(dtoClass, selections.size());
        java.util.Map<String, Selection<?>> aliasMap = new java.util.LinkedHashMap<>();
        for (Selection<?> sel : selections) {
            String alias = sel.getAlias();
            aliasMap.put(alias, sel);
        }

        Selection[] ordered = new Selection[paramNames.length];
        for (int i = 0; i < paramNames.length; i++) {
            Selection<?> sel = aliasMap.get(paramNames[i]);
            if (sel == null) {
                throw new IllegalArgumentException("DTO constructor parameter '" + paramNames[i]
                    + "' does not match any select() field. Available aliases: " + aliasMap.keySet()
                    + ". Use selectAs(field, \"" + paramNames[i] + "\") to match by name.");
            }
            ordered[i] = sel;
        }
        return ordered;
    }

    /**
     * 获取 DTO 类的构造函数参数名。优先使用 Record 组件名，其次使用 Java 8+ 的 Parameter.getName()。
     */
    private static String[] getConstructorParamNames(Class<?> dtoClass, int expectedCount) {
        // try record components first
        if (dtoClass.isRecord()) {
            java.lang.reflect.RecordComponent[] components = dtoClass.getRecordComponents();
            String[] names = new String[components.length];
            for (int i = 0; i < components.length; i++) {
                names[i] = components[i].getName();
            }
            return names;
        }
        // try constructor with matching parameter count
        for (java.lang.reflect.Constructor<?> ctor : dtoClass.getConstructors()) {
            if (ctor.getParameterCount() == expectedCount) {
                java.lang.reflect.Parameter[] params = ctor.getParameters();
                String[] names = new String[params.length];
                for (int i = 0; i < params.length; i++) {
                    names[i] = params[i].getName();
                    // Java 8 default synthetic names (arg0, arg1, ...) mean -parameters not used
                    if (names[i].startsWith("arg")) {
                        throw new IllegalArgumentException("DTO class " + dtoClass.getName()
                            + " must be compiled with -parameters flag, or use a record. "
                            + "Add to pom.xml: <maven.compiler.parameters>true</maven.compiler.parameters>");
                    }
                }
                return names;
            }
        }
        throw new IllegalArgumentException("DTO class " + dtoClass.getName() + " must have a public constructor with "
            + expectedCount + " parameter(s).");
    }

    public Page<Tuple> toTuplePage(EntityManager em, Pageable pageable) {
        return toTuplePage(em, pageable, MyJpaTemplate.DEFAULT_MAX_RESULTS);
    }

    public Page<Tuple> toTuplePage(EntityManager em, Pageable pageable, int maxResults) {
        if (pageable.isUnpaged()) {
            List<Tuple> all = toTupleList(em, maxResults);
            return new PageImpl<>(all);
        }

        CriteriaBuilder cb = em.getCriteriaBuilder();
        long total = executeCountQuery(cb, em);

        CriteriaQuery<Tuple> dataQuery = cb.createTupleQuery();
        Root<T> dataRoot = dataQuery.from(entityClass);

        List<Selection<?>> selections = buildSelectionList(dataRoot, cb);
        dataQuery.multiselect(selections);

        dataQuery.where(getCombinedPredicate(dataRoot, dataQuery, cb));
        spec.applyDistinctAndGroupBy(dataRoot, dataQuery, cb);
        spec.applyOrderBy(dataRoot, dataQuery, cb);

        TypedQuery<Tuple> dataTypedQuery = em.createQuery(dataQuery);
        try {
            dataTypedQuery.setFirstResult(Math.toIntExact(pageable.getOffset()));
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Page offset exceeds Integer.MAX_VALUE.", e);
        }
        dataTypedQuery.setMaxResults(pageable.getPageSize());
        List<Tuple> content = dataTypedQuery.getResultList();

        return new PageImpl<>(content, pageable, total);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<Selection<?>> buildSelectionList(Root<T> root, CriteriaBuilder cb) {
        List<Selection<?>> selections = new ArrayList<>();
        for (QuerySpec.ProjectionField pf : fields) {
            SFunction<T, ?> field = (SFunction<T, ?>)pf.field;
            String alias = pf.alias;
            if (field instanceof AggregateSFunction agg) {
                Expression<?> expr = switch (agg.getAggregateType()) {
                    case COUNT -> agg.isCountAll() ? cb.count(root) : cb.count(root.get(agg.getFieldName()));
                    case COUNT_FIELD -> cb.count(root.get(agg.getFieldName()));
                    case SUM -> cb.sum(root.get(agg.getFieldName()));
                    case AVG -> cb.avg(root.get(agg.getFieldName()));
                    case MAX -> cb.max(root.get(agg.getFieldName()));
                    case MIN -> cb.min(root.get(agg.getFieldName()));
                    default -> throw new IllegalArgumentException("Unsupported aggregate: " + agg.getAggregateType());
                };
                String aggAlias = alias != null ? alias : agg.getAlias();
                selections.add(expr.alias(aggAlias));
            } else {
                String fieldName = LambdaUtils.getPropertyName(field);
                String fieldAlias = alias != null ? alias : fieldName;
                selections.add(root.get(fieldName).alias(fieldAlias));
            }
        }
        return selections;
    }

    private Predicate getCombinedPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        Predicate predicate = spec.toPredicate(root, query, cb);
        if (softDeleteSpec != null) {
            Predicate sdPredicate = softDeleteSpec.toPredicate(root, query, cb);
            if (sdPredicate != null) {
                predicate = predicate != null ? cb.and(predicate, sdPredicate) : sdPredicate;
            }
        }
        return predicate;
    }

    private long executeCountQuery(CriteriaBuilder cb, EntityManager em) {
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<T> countRoot = countQuery.from(entityClass);
        countQuery.select(cb.count(countRoot));
        Predicate predicate = getCombinedPredicate(countRoot, countQuery, cb);
        if (predicate != null) {
            countQuery.where(predicate);
        }
        return em.createQuery(countQuery).getSingleResult();
    }

    /**
     * 验证 DTO 类的构造函数参数数量与投影字段匹配。
     */
    private static void validateDtoConstructor(Class<?> dtoClass, int fieldCount) {
        for (java.lang.reflect.Constructor<?> ctor : dtoClass.getConstructors()) {
            if (ctor.getParameterCount() == fieldCount) {
                return;
            }
        }
        throw new IllegalArgumentException("DTO class " + dtoClass.getName() + " must have a public constructor with "
            + fieldCount + " parameter(s) matching the select() fields. " + "Use: new " + dtoClass.getSimpleName()
            + "(...) with the same number and types as select() fields.");
    }
}
