package com.zsubera.jpa.template;

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
    private static final java.util.concurrent.ConcurrentMap<String, java.lang.reflect.Method> GETTER_CACHE =
        new java.util.concurrent.ConcurrentHashMap<>(256);

    /** 静态缓存最大条目数，超出时触发采样驱逐防止内存泄漏 */
    private static final int MAX_GETTER_CACHE_SIZE = 4096;

    /** 缓存访问计数器，用于触发周期性采样驱逐 */
    private static final java.util.concurrent.atomic.AtomicLong GETTER_CACHE_ACCESS_COUNT =
        new java.util.concurrent.atomic.AtomicLong(0);

    private final EntityManager entityManager;

    KeysetPaginationHelper(EntityManager entityManager) {
        this.entityManager = entityManager;
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
    <T> MyJpaTemplate.KeysetPage<T> findKeysetPage(Class<T> entityClass, Specification<T> spec, Sort sort, int pageSize,
        @Nullable Object[] lastSortValues) {
        List<Sort.Order> orders = sort.stream().toList();

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> cq = cb.createQuery(entityClass);
        Root<T> root = cq.from(entityClass);

        // 应用用户条件
        jakarta.persistence.criteria.Predicate userPredicate = spec.toPredicate(root, cq, cb);
        if (userPredicate != null) {
            cq.where(userPredicate);
        }

        // 应用 keyset 条件（游标定位）
        if (lastSortValues != null) {
            jakarta.persistence.criteria.Predicate keysetPredicate =
                buildKeysetPredicate(root, cb, orders, lastSortValues);
            if (userPredicate != null) {
                cq.where(cb.and(userPredicate, keysetPredicate));
            } else {
                cq.where(keysetPredicate);
            }
        }

        // 应用排序
        List<jakarta.persistence.criteria.Order> orderList = new ArrayList<>();
        for (Sort.Order order : orders) {
            orderList.add(
                order.isAscending() ? cb.asc(root.get(order.getProperty())) : cb.desc(root.get(order.getProperty())));
        }
        cq.orderBy(orderList);

        // 多查一条以判断是否有下一页
        jakarta.persistence.TypedQuery<T> query = entityManager.createQuery(cq);
        query.setMaxResults(pageSize + 1);
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

        return new MyJpaTemplate.KeysetPage<>(results, hasNext, nextLastSortValues);
    }

    /**
     * 构建 keyset 分页的 WHERE 条件。
     *
     * <p>
     * 对于单字段排序 {@code ORDER BY id ASC}，条件为 {@code id > lastValue}。 对于多字段排序 {@code ORDER BY a ASC, b DESC}，条件为：
     * {@code (a > lastA) OR (a = lastA AND b < lastB)}。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static jakarta.persistence.criteria.Predicate buildKeysetPredicate(Root<?> root, CriteriaBuilder cb,
        List<Sort.Order> orders, Object[] lastSortValues) {
        if (orders.size() == 1) {
            Sort.Order order = orders.get(0);
            jakarta.persistence.criteria.Expression<Comparable> field =
                (jakarta.persistence.criteria.Expression)((jakarta.persistence.criteria.Path)root)
                    .get(order.getProperty());
            Object value = lastSortValues[0];
            if (value == null) {
                if (order.isAscending()) {
                    return cb.isNull(field);
                } else {
                    return cb.or(cb.isNull(field), cb.isNotNull(field));
                }
            }
            if (order.isAscending()) {
                return cb.greaterThan(field, (Comparable)value);
            } else {
                return cb.lessThan(field, (Comparable)value);
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
                if (currentOrder.isAscending()) {
                    andPredicates.add(cb.isNull(currentField));
                } else {
                    andPredicates.add(cb.or(cb.isNull(currentField), cb.isNotNull(currentField)));
                }
            } else if (currentOrder.isAscending()) {
                andPredicates.add(cb.greaterThan(currentField, (Comparable)currentValue));
            } else {
                andPredicates.add(cb.lessThan(currentField, (Comparable)currentValue));
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
            // 每 10000 次访问采样驱逐一次缓存
            long count = GETTER_CACHE_ACCESS_COUNT.incrementAndGet();
            if (count % 10000 == 0 && GETTER_CACHE.size() > MAX_GETTER_CACHE_SIZE / 2) {
                evictGetterCache();
            }
            java.lang.reflect.Method getter = GETTER_CACHE.computeIfAbsent(cacheKey, k -> {
                try {
                    return entity.getClass()
                        .getMethod("get" + Character.toUpperCase(property.charAt(0)) + property.substring(1));
                } catch (NoSuchMethodException e) {
                    try {
                        return entity.getClass()
                            .getMethod("is" + Character.toUpperCase(property.charAt(0)) + property.substring(1));
                    } catch (NoSuchMethodException ex) {
                        return null;
                    }
                }
            });
            if (getter == null) {
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

    /**
     * 采样驱逐 GETTER_CACHE：移除约 25% 的条目，避免全量清空导致的性能抖动。
     */
    private static void evictGetterCache() {
        int targetSize = MAX_GETTER_CACHE_SIZE / 4;
        int toRemove = GETTER_CACHE.size() - targetSize;
        if (toRemove <= 0) {
            return;
        }
        var iterator = GETTER_CACHE.keySet().iterator();
        int removed = 0;
        while (iterator.hasNext() && removed < toRemove) {
            iterator.next();
            iterator.remove();
            removed++;
        }
    }
}
