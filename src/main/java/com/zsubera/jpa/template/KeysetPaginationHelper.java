package com.zsubera.jpa.template;

import com.zsubera.jpa.util.SampledEvictionCache;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.Nullable;

/**
 * Keyset 分页（游标分页）的实现辅助类。
 *
 * <p>
 * 相比传统 offset 分页，游标分页在大数据量场景下性能更优（O(log n) vs O(n)）， 因为它使用 WHERE 条件而非 OFFSET 跳过已读记录。
 *
 * <p>
 * 此类封装了 keyset 分页的核心逻辑：游标条件构建、排序值提取和缓存管理。
 *
 * @see MyJpaTemplate#findKeysetPage(Class, Specification, Sort, int, Object[])
 */
final class KeysetPaginationHelper {

    private static final Logger log = LoggerFactory.getLogger(KeysetPaginationHelper.class);

    /** Getter 方法缓存，避免 extractSortValues 每次反射查找。key = className#propertyName */
    private static final SampledEvictionCache<String, java.lang.reflect.Method> GETTER_CACHE =
        new SampledEvictionCache<>(4096, 0.25, 10000, 256);

    /** 哨兵值：表示 getter 方法不存在，用于替代 ConcurrentHashMap 不允许的 null value */
    private static final java.lang.reflect.Method NO_GETTER_SENTINEL;

    static {
        try {
            NO_GETTER_SENTINEL = Object.class.getDeclaredMethod("toString");
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    private final EntityManager entityManager;

    /** 是否假设数据库使用 NULLS FIRST 语义。Oracle/SQL Server 默认 NULLS LAST，PostgreSQL 默认 NULLS LAST，MySQL NULLs 作为最小值。 */
    private final boolean nullsFirst;

    /**
     * 创建使用默认 NULLS LAST 语义的分页助手。
     *
     * <p>
     * 自动检测数据库类型并设置默认 NULLS FIRST/LAST 语义：
     * <ul>
     * <li>MySQL/MariaDB: NULLS FIRST（MySQL 将 NULL 视为最小值）</li>
     * <li>PostgreSQL/Oracle/SQL Server: NULLS LAST（标准 SQL 默认）</li>
     * </ul>
     * 对于需要覆盖默认语义的场景，请使用 {@link #KeysetPaginationHelper(EntityManager, boolean)}。
     */
    KeysetPaginationHelper(EntityManager entityManager) {
        this(entityManager, detectNullsFirst(entityManager));
    }

    /**
     * 自动检测数据库类型，返回正确的 NULLS FIRST 默认值。
     * MySQL 将 NULL 视为最小值（ASC 时等效于 NULLS FIRST），其他数据库默认 NULLS LAST。
     */
    private static boolean detectNullsFirst(EntityManager entityManager) {
        try {
            java.sql.Connection conn = entityManager.unwrap(java.sql.Connection.class);
            // ponytail: 处理连接池代理，递归 unwrap 到物理连接，设置迭代上限防止死循环
            int unwrapAttempts = 0;
            while (conn.isWrapperFor(java.sql.Connection.class) && unwrapAttempts < 5) {
                conn = conn.unwrap(java.sql.Connection.class);
                unwrapAttempts++;
            }
            String productName = conn.getMetaData().getDatabaseProductName().toLowerCase();
            return productName.contains("mysql") || productName.contains("mariadb");
        } catch (Exception e) {
            log.warn("Failed to detect database type for NULLS handling, assuming NULLS LAST: {}", e.getMessage());
            return false;
        }
    }

    KeysetPaginationHelper(EntityManager entityManager, boolean nullsFirst) {
        this.entityManager = entityManager;
        this.nullsFirst = nullsFirst;
    }

    /**
     * 执行 keyset 分页查询。
     *
     * @param entityClass 实体类
     * @param spec 查询规范
     * @param sort 排序规则
     * @param pageSize 每页大小
     * @param lastSortValues 上一页最后记录的排序值（null 表示第一页）
     * @param <T> 实体类型
     * @return 分页结果
     */
    <T> MyJpaTemplateOperations.KeysetPage<T> findKeysetPage(Class<T> entityClass, Specification<T> spec, Sort sort,
        int pageSize, @Nullable Object[] lastSortValues) {
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize must be positive, got: " + pageSize);
        }
        List<Sort.Order> orders = sort.stream().toList();

        // First page: no cursor values → skip keyset predicate
        boolean isFirstPage = lastSortValues == null || lastSortValues.length == 0;

        if (!isFirstPage && lastSortValues.length != orders.size()) {
            throw new IllegalArgumentException("Cursor array length (" + lastSortValues.length
                + ") must match sort order count (" + orders.size() + "). Sort: " + sort);
        }

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> cq = cb.createQuery(entityClass);
        Root<T> root = cq.from(entityClass);

        // 应用用户条件
        jakarta.persistence.criteria.Predicate userPredicate = spec.toPredicate(root, cq, cb);
        if (userPredicate != null) {
            cq.where(userPredicate);
        }

        // 应用 keyset 条件（游标定位）
        if (!isFirstPage) {
            jakarta.persistence.criteria.Predicate keysetPredicate =
                buildKeysetPredicate(root, cb, orders, lastSortValues, nullsFirst);
            if (userPredicate != null) {
                cq.where(cb.and(userPredicate, keysetPredicate));
            } else {
                cq.where(keysetPredicate);
            }
        }

        // 应用排序（包含 NULLS FIRST/LAST 以匹配 keyset predicate 的 NULL 语义）
        // ponytail: JPA 3.1 Order doesn't have nullsFirst/nullsLast. Use Hibernate's sort() with NullPrecedence
        // when available; fall back to standard JPA asc()/desc() for other providers (EclipseLink, OpenJPA, etc.).
        List<jakarta.persistence.criteria.Order> orderList = new ArrayList<>();
        if (cb instanceof org.hibernate.query.criteria.HibernateCriteriaBuilder hcb) {
            org.hibernate.query.NullPrecedence nullPrecedence =
                nullsFirst ? org.hibernate.query.NullPrecedence.FIRST : org.hibernate.query.NullPrecedence.LAST;
            for (Sort.Order order : orders) {
                jakarta.persistence.criteria.Expression<?> expr = root.get(order.getProperty());
                jakarta.persistence.criteria.Order sorted;
                if (order.isAscending()) {
                    sorted = hcb.sort((org.hibernate.query.criteria.JpaExpression<?>)expr,
                        org.hibernate.query.SortDirection.ASCENDING, nullPrecedence);
                } else {
                    sorted = hcb.sort((org.hibernate.query.criteria.JpaExpression<?>)expr,
                        org.hibernate.query.SortDirection.DESCENDING, nullPrecedence);
                }
                orderList.add(sorted);
            }
        } else {
            for (Sort.Order order : orders) {
                jakarta.persistence.criteria.Expression<?> expr = root.get(order.getProperty());
                orderList.add(order.isAscending() ? cb.asc(expr) : cb.desc(expr));
            }
        }
        cq.orderBy(orderList);

        // 多查一条以判断是否有下一页
        // pageSize + 1 在 pageSize == Integer.MAX_VALUE 时溢出，使用饱和运算
        int fetchLimit = pageSize == Integer.MAX_VALUE ? Integer.MAX_VALUE : pageSize + 1;
        jakarta.persistence.TypedQuery<T> query = entityManager.createQuery(cq);
        query.setMaxResults(fetchLimit);
        List<T> results = query.getResultList();
        boolean hasNext = results.size() > pageSize;
        if (hasNext) {
            results = results.subList(0, pageSize);
        }

        // 提取最后一条记录的排序字段值
        Object[] nextLastSortValues = null;
        if (hasNext && !results.isEmpty()) {
            T lastRecord = results.get(results.size() - 1);
            nextLastSortValues = extractSortValues(lastRecord, orders);
        }

        return new MyJpaTemplateOperations.KeysetPage<>(results, hasNext, nextLastSortValues);
    }

    /**
     * 构建 keyset 分页的 WHERE 条件。
     *
     * <p>
     * 对于单字段排序 {@code ORDER BY id ASC}，条件为 {@code id > lastValue}。 对于多字段排序 {@code ORDER BY a ASC, b DESC}，条件为：
     * {@code (a > lastA) OR (a = lastA AND b < lastB)}。
     *
     * <p>
     * <strong>NULL 排序说明：</strong>此方法根据 {@code nullsFirst} 参数决定 NULL 排序语义。
     * MySQL/PostgreSQL 默认 NULLS FIRST，Oracle/SQL Server 默认 NULLS LAST。
     * 可通过构造函数 {@link #KeysetPaginationHelper(EntityManager, boolean)} 配置。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static jakarta.persistence.criteria.Predicate buildKeysetPredicate(Root<?> root, CriteriaBuilder cb,
        List<Sort.Order> orders, Object[] lastSortValues, boolean nullsFirst) {
        if (orders.size() == 1) {
            Sort.Order order = orders.get(0);
            jakarta.persistence.criteria.Expression<Comparable> field =
                (jakarta.persistence.criteria.Expression)((jakarta.persistence.criteria.Path)root)
                    .get(order.getProperty());
            Object value = lastSortValues[0];
            if (value == null) {
                // NULLS FIRST: nulls are at the start → after passing nulls, next page = non-null values
                // NULLS LAST: nulls are at the end → after seeing null, we're at the end → match nothing
                if (nullsFirst) {
                    return cb.isNotNull(field);
                } else {
                    // NULLS LAST + null cursor = at end of sort, no next page
                    return cb.disjunction();
                }
            }
            if (order.isAscending()) {
                return nullsFirst ? cb.greaterThan(field, (Comparable)value)
                    : cb.or(cb.greaterThan(field, (Comparable)value), cb.isNull(field));
            } else {
                return nullsFirst ? cb.lessThan(field, (Comparable)value)
                    : cb.or(cb.lessThan(field, (Comparable)value), cb.isNull(field));
            }
        }

        // 多字段排序：行值比较
        List<jakarta.persistence.criteria.Predicate> orPredicates = new ArrayList<>();
        for (int i = 0; i < orders.size(); i++) {
            List<jakarta.persistence.criteria.Predicate> andPredicates = new ArrayList<>();
            for (int j = 0; j < i; j++) {
                Object eqValue = lastSortValues[j];
                if (eqValue == null) {
                    andPredicates.add(cb.isNull(root.get(orders.get(j).getProperty())));
                } else {
                    andPredicates.add(cb.equal(root.get(orders.get(j).getProperty()), eqValue));
                }
            }
            Sort.Order currentOrder = orders.get(i);
            jakarta.persistence.criteria.Expression<Comparable> currentField =
                (jakarta.persistence.criteria.Expression)((jakarta.persistence.criteria.Path)root)
                    .get(currentOrder.getProperty());
            Object currentValue = lastSortValues[i];
            if (currentValue == null) {
                if (nullsFirst) {
                    // NULLS FIRST: nulls at start → after passing nulls, next page = non-null values
                    andPredicates.add(cb.isNotNull(currentField));
                } else {
                    // NULLS LAST: nulls at end → null cursor means at end of sort
                    andPredicates.add(cb.disjunction());
                }
            } else if (currentOrder.isAscending()) {
                andPredicates.add(nullsFirst ? cb.greaterThan(currentField, (Comparable)currentValue)
                    : cb.or(cb.greaterThan(currentField, (Comparable)currentValue), cb.isNull(currentField)));
            } else {
                andPredicates.add(nullsFirst ? cb.lessThan(currentField, (Comparable)currentValue)
                    : cb.or(cb.lessThan(currentField, (Comparable)currentValue), cb.isNull(currentField)));
            }
            orPredicates.add(cb.and(andPredicates.toArray(new jakarta.persistence.criteria.Predicate[0])));
        }
        return cb.or(orPredicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
    }

    /**
     * 从实体中提取排序字段的值。
     *
     * @param entity 实体对象
     * @param orders 排序规则列表
     * @return 排序字段值数组
     */
    private static Object[] extractSortValues(Object entity, List<Sort.Order> orders) {
        Object[] values = new Object[orders.size()];
        for (int i = 0; i < orders.size(); i++) {
            String property = orders.get(i).getProperty();
            String cacheKey = entity.getClass().getName() + "#" + property;
            java.lang.reflect.Method getter = GETTER_CACHE.computeIfAbsent(cacheKey, k -> {
                try {
                    return entity.getClass()
                        .getMethod("get" + Character.toUpperCase(property.charAt(0)) + property.substring(1));
                } catch (NoSuchMethodException e) {
                    try {
                        return entity.getClass()
                            .getMethod("is" + Character.toUpperCase(property.charAt(0)) + property.substring(1));
                    } catch (NoSuchMethodException ex) {
                        // Handle properties that already start with "is" (e.g., "isValid")
                        if (property.length() > 2 && property.startsWith("is")
                            && Character.isUpperCase(property.charAt(2))) {
                            try {
                                return entity.getClass().getMethod(property);
                            } catch (NoSuchMethodException ignored) {
                            }
                        }
                        return NO_GETTER_SENTINEL;
                    }
                }
            });
            if (getter == NO_GETTER_SENTINEL) {
                throw new IllegalArgumentException("Cannot extract sort value for property '" + property + "' from "
                    + entity.getClass().getName() + ": no getter method found (tried get"
                    + Character.toUpperCase(property.charAt(0)) + property.substring(1) + " and is"
                    + Character.toUpperCase(property.charAt(0)) + property.substring(1) + ")");
            }
            try {
                values[i] = getter.invoke(entity);
            } catch (ReflectiveOperationException ex) {
                throw new IllegalArgumentException(
                    "Cannot extract sort value for property '" + property + "' from " + entity.getClass().getName(),
                    ex);
            }
        }
        return values;
    }
}
