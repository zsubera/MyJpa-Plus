package com.zsubera.jpa.update;

import com.zsubera.jpa.annotation.SoftDelete;
import com.zsubera.jpa.spec.QuerySpec;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.springframework.data.jpa.domain.Specification;

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

    // Sentinel value for entities without a @SoftDelete field (avoids null in
    // cache)
    private static final String NO_FIELD_SENTINEL = "\0";

    // Cache: entityClass -> field name (or sentinel for "no field")
    // Using ConcurrentHashMap for thread-safe access; entity class count is limited
    // in practice
    private static final ConcurrentMap<Class<?>, String> FIELD_CACHE = new ConcurrentHashMap<>();

    // Cache: entityClass -> Specification for isNotDeleted
    private static final ConcurrentMap<Class<?>, Specification<?>> NOT_DELETED_SPEC_CACHE = new ConcurrentHashMap<>();

    // Cache: entityClass -> Specification for isDeleted
    private static final ConcurrentMap<Class<?>, Specification<?>> DELETED_SPEC_CACHE = new ConcurrentHashMap<>();

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
    @SuppressWarnings("unchecked")
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
        if (Enum.class.isAssignableFrom(field.getType()) && annotation != null
            && !annotation.deletedValue().isEmpty()) {
            Object deletedEnumValue = getEnumConstant(field.getType(), annotation.deletedValue());
            if (deletedEnumValue != null) {
                return cb.or(cb.isNull(path.get(fieldName)), cb.notEqual(path.get(fieldName), deletedEnumValue));
            }
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
        if (Enum.class.isAssignableFrom(field.getType()) && annotation != null
            && !annotation.deletedValue().isEmpty()) {
            Object deletedEnumValue = getEnumConstant(field.getType(), annotation.deletedValue());
            if (deletedEnumValue != null) {
                return cb.equal(path.get(fieldName), deletedEnumValue);
            }
        }
        // 默认：按 Boolean true 处理
        return cb.equal(path.get(fieldName), true);
    }

    /**
     * 获取枚举类型的指定常量。
     *
     * @param enumType 枚举类型
     * @param constantName 枚举常量名称
     * @return 枚举常量，如果未找到则返回 null
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object getEnumConstant(Class<?> enumType, String constantName) {
        try {
            return Enum.valueOf((Class<Enum>)enumType, constantName);
        } catch (IllegalArgumentException e) {
            log.warn("Enum constant '{}' not found in {}", constantName, enumType.getName());
            return null;
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
        }
        String result = FIELD_CACHE.computeIfAbsent(entityClass, cls -> {
            for (Field field : getAllFields(cls)) {
                if (field.isAnnotationPresent(SoftDelete.class)) {
                    field.setAccessible(true);
                    return field.getName();
                }
            }
            return NO_FIELD_SENTINEL;
        });
        return NO_FIELD_SENTINEL.equals(result) ? null : result;
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
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T> boolean isSoftDeleted(Class<T> entityClass, T entity) {
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
            // Boolean 类型
            if (value instanceof Boolean) {
                return Boolean.TRUE.equals(value);
            }
            // 枚举类型
            if (value instanceof Enum && field.isAnnotationPresent(SoftDelete.class)) {
                SoftDelete annotation = field.getAnnotation(SoftDelete.class);
                if (annotation != null && !annotation.deletedValue().isEmpty()) {
                    Enum enumValue = (Enum)value;
                    return enumValue.name().equals(annotation.deletedValue());
                }
            }
            return false;
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    private static List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new java.util.ArrayList<>();
        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                fields.add(field);
            }
            clazz = clazz.getSuperclass();
        }
        return fields;
    }
}
