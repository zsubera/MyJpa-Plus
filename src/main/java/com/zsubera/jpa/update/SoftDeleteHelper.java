package com.zsubera.jpa.update;

import com.zsubera.jpa.annotation.SoftDelete;
import com.zsubera.jpa.exception.MyJpaPlusException;
import com.zsubera.jpa.spec.QuerySpec;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.ConcurrentReferenceHashMap;

/**
 * 软删除实体辅助工具类。
 *
 * <p>
 * 用于处理带有 {@link SoftDelete @SoftDelete} 注解的实体，提供自动过滤软删除记录的 {@link Specification} 实例。
 *
 * <p>
 * 支持的字段类型：
 * <ul>
 * <li>{@code Boolean} / {@code boolean} — {@code true} 表示"已删除"，{@code false}（或 {@code null}）表示"未删除"</li>
 * <li>{@code Enum} — 通过 {@link SoftDelete#deletedValue()} 指定表示"已删除"的枚举值名称</li>
 * </ul>
 *
 * <p>
 * 缓存策略：所有缓存均使用 {@link ConcurrentHashMap} 实现线程安全访问。缓存无驱逐策略， 因为实体类数量在实际应用中有限且有界。 FIELD_CACHE 在超过 1024 条目时会记录警告日志，
 * 以帮助诊断潜在的类加载器泄漏问题。NOT_DELETED_SPEC_CACHE 和 DELETED_SPEC_CACHE 按实体类缓存 Specification 实例， 避免每次调用创建新的 lambda。
 *
 * <p>
 * 使用示例：
 *
 * <pre>{@code
 * Specification<Product> notDeleted = SoftDeleteHelper.isNotDeleted(Product.class);
 * List<Product> active = repository.findAll(notDeleted.and(otherSpec));
 * }</pre>
 *
 * @author myjpa-plus
 * @see SoftDelete
 * @see Specification
 */
public final class SoftDeleteHelper {

    private static final int MAX_CACHE_SIZE = 1024;
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(SoftDeleteHelper.class);

    // 没有@SoftDelete字段的实体的哨兵值（避免在 中出现空缓存）
    private static final String NO_FIELD_SENTINEL = "\0";

    // 缓存：entityClass ->字段名（或“无字段”的哨兵）
    // 使用ConcurrentHashMap实现线程安全访问;实体类别数量在实际中是有限的
    // Cache: entityClass -> field name (or sentinel for "no field")
    // Uses weak key references to allow class loader GC in OSGi/hot-redeploy scenarios
    private static final ConcurrentMap<Class<?>, String> FIELD_CACHE =
        new ConcurrentReferenceHashMap<>(16, ConcurrentReferenceHashMap.ReferenceType.WEAK);

    // Cache: entityClass -> isNotDeleted Specification
    // Uses weak key references to allow class loader GC in OSGi/hot-redeploy scenarios
    private static final ConcurrentMap<Class<?>, Specification<?>> NOT_DELETED_SPEC_CACHE =
        new ConcurrentReferenceHashMap<>(16, ConcurrentReferenceHashMap.ReferenceType.WEAK);

    // Cache: entityClass -> isDeleted Specification
    // Uses weak key references to allow class loader GC in OSGi/hot-redeploy scenarios
    private static final ConcurrentMap<Class<?>, Specification<?>> DELETED_SPEC_CACHE =
        new ConcurrentReferenceHashMap<>(16, ConcurrentReferenceHashMap.ReferenceType.WEAK);

    private SoftDeleteHelper() {}

    /**
     * 返回排除软删除记录的 {@link Specification}。
     *
     * <p>
     * 对于 {@code Boolean} 类型字段，生成条件为 {@code field = false}；对于引用类型 {@code Boolean} 字段（使用 null 表示"未删除"），生成条件为
     * {@code field IS NULL}。对于 {@code Enum} 类型字段，生成条件为 {@code field != deletedValue}。
     *
     * <p>
     * 结果按实体类缓存，避免每次调用创建新的 lambda 实例。
     *
     * @param entityClass 实体类
     * @param <T> 实体类型
     * @return 排除软删除记录的 Specification
     */
    @SuppressWarnings("unchecked")
    public static <T> Specification<T> isNotDeleted(Class<T> entityClass) {
        return (Specification<T>)NOT_DELETED_SPEC_CACHE.computeIfAbsent(entityClass, cls -> {
            String fieldName = findSoftDeleteField(entityClass);
            if (fieldName == null) {
                return (Specification<T>)(root, query, cb) -> cb.conjunction();
            }
            return (Specification<T>)(root, query, cb) -> buildNotDeleted(cb, root, fieldName, entityClass);
        });
    }

    /**
     * 返回仅匹配软删除记录的 {@link Specification}。
     *
     * <p>
     * 结果按实体类缓存，避免每次调用创建新的 lambda 实例。
     *
     * @param entityClass 实体类
     * @param <T> 实体类型
     * @return 匹配软删除记录的 Specification
     */
    @SuppressWarnings("unchecked")
    public static <T> Specification<T> isDeleted(Class<T> entityClass) {
        return (Specification<T>)DELETED_SPEC_CACHE.computeIfAbsent(entityClass, cls -> {
            String fieldName = findSoftDeleteField(entityClass);
            if (fieldName == null) {
                return (Specification<T>)(root, query, cb) -> cb.disjunction();
            }
            return (Specification<T>)(root, query, cb) -> buildDeleted(cb, root, fieldName, entityClass);
        });
    }

    /**
     * 构建预应用软删除条件的新 {@link QuerySpec}。
     *
     * <p>
     * 注意：与缓存结果的 {@link #isNotDeleted(Class)} 不同，此方法每次调用都会创建新的 {@code QuerySpec} 实例，因为 {@code
     * QuerySpec} 是可变的，不适合共享。
     *
     * @param entityClass 实体类
     * @param <T> 实体类型
     * @return 应用了软删除条件的新 QuerySpec
     */
    public static <T> QuerySpec<T> notDeletedQuery(Class<T> entityClass) {
        String fieldName = findSoftDeleteField(entityClass);
        if (fieldName == null) {
            return new QuerySpec<>();
        }
        QuerySpec<T> qs = new QuerySpec<>();
        qs.where((path, cb) -> buildNotDeleted(cb, path, fieldName, entityClass));
        return qs;
    }

    private static Predicate buildNotDeleted(CriteriaBuilder cb, Path<?> path, String fieldName, Class<?> entityClass) {
        Field field = getField(entityClass, fieldName);
        if (field == null) {
            return cb.equal(path.get(fieldName), false);
        }
        SoftDelete annotation = field.getAnnotation(SoftDelete.class);
        // Boolean 类型
        if (field.getType() == Boolean.class || field.getType() == boolean.class) {
            return cb.or(cb.isNull(path.get(fieldName)), cb.equal(path.get(fieldName), false));
        }
        // 枚举类型
        if (Enum.class.isAssignableFrom(field.getType())) {
            if (annotation == null || annotation.deletedValue().isEmpty()) {
                throw new MyJpaPlusException("@SoftDelete on enum field '" + fieldName + "' in " + entityClass.getName()
                    + " must specify deletedValue");
            }
            Object deletedEnumValue = getEnumConstant(field.getType(), annotation.deletedValue());
            return cb.or(cb.isNull(path.get(fieldName)), cb.notEqual(path.get(fieldName), deletedEnumValue));
        }
        // 默认：按 Boolean false 处理
        return cb.equal(path.get(fieldName), false);
    }

    private static Predicate buildDeleted(CriteriaBuilder cb, Path<?> path, String fieldName, Class<?> entityClass) {
        Field field = getField(entityClass, fieldName);
        if (field == null) {
            return cb.equal(path.get(fieldName), true);
        }
        SoftDelete annotation = field.getAnnotation(SoftDelete.class);
        // 枚举类型
        if (Enum.class.isAssignableFrom(field.getType())) {
            if (annotation == null || annotation.deletedValue().isEmpty()) {
                throw new MyJpaPlusException("@SoftDelete on enum field '" + fieldName + "' in " + entityClass.getName()
                    + " must specify deletedValue");
            }
            Object deletedEnumValue = getEnumConstant(field.getType(), annotation.deletedValue());
            return cb.equal(path.get(fieldName), deletedEnumValue);
        }
        // 默认：按 Boolean true 处理
        return cb.equal(path.get(fieldName), true);
    }

    /**
     * 获取枚举类型的指定常量。
     *
     * @param enumType 枚举类型
     * @param constantName 枚举常量名称
     * @return 枚举常量
     * @throws IllegalStateException 如果枚举常量不存在
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object getEnumConstant(Class<?> enumType, String constantName) {
        try {
            return Enum.valueOf((Class<Enum>)enumType, constantName);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(String.format(
                "Enum constant '%s' not found in %s. " + "Please check the @SoftDelete(deletedValue) configuration.",
                constantName, enumType.getName()), e);
        }
    }

    /**
     * 查找实体类上带有 {@link SoftDelete @SoftDelete} 注解的字段名称。
     *
     * <p>
     * 会遍历类层次结构（包括父类）。结果按实体类缓存。
     *
     * @param entityClass 要扫描的实体类
     * @return 字段名称，如果未找到 {@code @SoftDelete} 字段则返回 {@code null}
     */
    public static String findSoftDeleteField(Class<?> entityClass) {
        if (FIELD_CACHE.size() > MAX_CACHE_SIZE) {
            log.warn(
                "SoftDeleteHelper field cache size ({}) exceeds limit ({}). "
                    + "This may indicate a class loader leak or excessive entity classes.",
                FIELD_CACHE.size(), MAX_CACHE_SIZE);
            // Evict stale entries (null key/value) to prevent unbounded growth
            FIELD_CACHE.entrySet().removeIf(e -> e.getKey() == null || e.getValue() == null);
        }
        String result = FIELD_CACHE.computeIfAbsent(entityClass, cls -> {
            // Try getter-based resolution first (Java 17+ compatible)
            String viaGetter = resolveSoftDeleteFieldNameViaGetter(cls);
            if (viaGetter != null) {
                return viaGetter;
            }
            // Fallback to field-based reflection
            for (Field field : getAllFields(cls)) {
                if (field.isAnnotationPresent(SoftDelete.class)) {
                    try {
                        field.setAccessible(true);
                    } catch (SecurityException e) {
                        log.warn(
                            "Cannot set accessible on field '{}' in {}. "
                                + "If using Java 17+ module system, add JVM argument: "
                                + "--add-opens java.base/java.lang.reflect=ALL-UNNAMED",
                            field.getName(), cls.getSimpleName());
                    }
                    return field.getName();
                }
            }
            return NO_FIELD_SENTINEL;
        });
        return NO_FIELD_SENTINEL.equals(result) ? null : result;
    }

    /**
     * Resolve soft delete field name via getter methods (Java 17+ compatible). Scans public methods for @SoftDelete
     * annotation on getters.
     *
     * @param entityClass the entity class to scan
     * @return the field name, or null if not found via getters
     */
    private static String resolveSoftDeleteFieldNameViaGetter(Class<?> entityClass) {
        for (Method m : entityClass.getMethods()) {
            SoftDelete ann = m.getAnnotation(SoftDelete.class);
            if (ann != null) {
                String name = m.getName();
                if (name.startsWith("get") && name.length() > 3) {
                    return Character.toLowerCase(name.charAt(3)) + name.substring(4);
                }
                if (name.startsWith("is") && name.length() > 2 && Character.isUpperCase(name.charAt(2))) {
                    return Character.toLowerCase(name.charAt(2)) + name.substring(3);
                }
            }
        }
        return null;
    }

    private static Field getField(Class<?> entityClass, String fieldName) {
        try {
            return entityClass.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            Class<?> sup = entityClass.getSuperclass();
            if (sup != null && sup != Object.class) {
                return getField(sup, fieldName);
            }
            return null;
        }
    }

    /**
     * 检查给定实体是否被标记为软删除。
     *
     * @param entityClass 实体类
     * @param entity 实体实例
     * @return 如果实体已软删除返回 {@code true}，否则返回 {@code false}
     * @throws IllegalArgumentException 如果实体类或实体实例为 {@code null}
     */
    @SuppressWarnings({"rawtypes"})
    public static <T> boolean isSoftDeleted(Class<T> entityClass, T entity) {
        if (entityClass == null) {
            throw new IllegalArgumentException("entityClass must not be null");
        }
        if (entity == null) {
            throw new IllegalArgumentException("entity must not be null");
        }
        String fieldName = findSoftDeleteField(entityClass);
        if (fieldName == null) {
            return false;
        }
        Field field = getField(entityClass, fieldName);
        if (field == null) {
            return false;
        }
        field.setAccessible(true);
        try {
            Object value = field.get(entity);
            if (value == null) {
                return false;
            }
            // Boolean type
            if (value instanceof Boolean) {
                return Boolean.TRUE.equals(value);
            }
            // Enum type
            if (value instanceof Enum enumValue && field.isAnnotationPresent(SoftDelete.class)) {
                SoftDelete annotation = field.getAnnotation(SoftDelete.class);
                if (annotation != null && !annotation.deletedValue().isEmpty()) {
                    return enumValue.name().equals(annotation.deletedValue());
                }
            }
            return false;
        } catch (ReflectiveOperationException e) {
            throw new MyJpaPlusException(String.format(
                "Failed to read soft delete field '%s' from entity %s. "
                    + "If using Java 17+ module system, add JVM argument: "
                    + "--add-opens java.base/java.lang.reflect=ALL-UNNAMED",
                fieldName, entity.getClass().getSimpleName()), e);
        }
    }

    private static List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new java.util.ArrayList<>();
        while (clazz != null && clazz != Object.class) {
            Collections.addAll(fields, clazz.getDeclaredFields());
            clazz = clazz.getSuperclass();
        }
        return fields;
    }
}
