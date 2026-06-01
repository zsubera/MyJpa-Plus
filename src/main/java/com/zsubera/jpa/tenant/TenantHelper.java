package com.zsubera.jpa.tenant;

import com.zsubera.jpa.annotation.TenantId;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.ConcurrentReferenceHashMap;

/**
 * 租户过滤辅助工具类。
 *
 * <p>
 * 用于处理带有 {@link TenantId @TenantId} 注解的实体，提供自动过滤非当前租户记录的 {@link Specification} 实例。
 *
 * <p>
 * 缓存策略：使用 {@link ConcurrentReferenceHashMap}（弱引用键）缓存字段名扫描结果，允许类加载器在 OSGi/热重载场景中被 GC 回收。
 *
 * @see TenantId
 * @see TenantProvider
 * @see Specification
 */
public final class TenantHelper {

    private static final int MAX_CACHE_SIZE = 1024;
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(TenantHelper.class);

    private static final String NO_FIELD_SENTINEL = "\0";

    private static final ConcurrentMap<Class<?>, String> FIELD_CACHE =
        new ConcurrentReferenceHashMap<>(16, ConcurrentReferenceHashMap.ReferenceType.WEAK);

    /** P2-26: Flag to track if cache size warning has been logged. */
    private static final java.util.concurrent.atomic.AtomicBoolean CACHE_WARNING_LOGGED =
        new java.util.concurrent.atomic.AtomicBoolean(false);

    private TenantHelper() {}

    /**
     * 返回仅匹配当前租户记录的 {@link Specification}。
     *
     * @param entityClass 实体类
     * @param tenantId 当前租户 ID
     * @param <T> 实体类型
     * @return 匹配当前租户记录的 Specification，如果实体没有 {@code @TenantId} 字段则返回始终为 true 的 Specification
     */
    public static <T> Specification<T> belongsToTenant(Class<T> entityClass, Object tenantId) {
        String fieldName = findTenantIdField(entityClass);
        if (fieldName == null) {
            return (root, query, cb) -> cb.conjunction();
        }
        return (root, query, cb) -> cb.equal(root.get(fieldName), tenantId);
    }

    /**
     * 查找实体类上带有 {@link TenantId @TenantId} 注解的字段名称。
     *
     * <p>
     * 会遍历类层次结构（包括父类）。结果按实体类缓存。
     *
     * @param entityClass 要扫描的实体类
     * @return 字段名称，如果未找到 {@code @TenantId} 字段则返回 {@code null}
     */
    public static String findTenantIdField(Class<?> entityClass) {
        int currentSize = FIELD_CACHE.size();
        if (currentSize > MAX_CACHE_SIZE && CACHE_WARNING_LOGGED.compareAndSet(false, true)) {
            log.warn("TenantHelper field cache size ({}) exceeds limit ({}). "
                + "This may indicate a class loader leak or excessive entity classes. "
                + "Weak reference entries will be cleaned by GC automatically.", currentSize, MAX_CACHE_SIZE);
        }
        String result = FIELD_CACHE.computeIfAbsent(entityClass, cls -> {
            String viaGetter = resolveTenantIdFieldNameViaGetter(cls);
            if (viaGetter != null) {
                return viaGetter;
            }
            for (Field field : getAllFields(cls)) {
                if (field.isAnnotationPresent(TenantId.class)) {
                    try {
                        field.setAccessible(true);
                    } catch (SecurityException e) {
                        log.error(
                            "Cannot set accessible on tenant field '{}' in {}. "
                                + "Multi-tenant filtering will NOT work correctly. "
                                + "If using Java 17+ module system, add JVM argument: "
                                + "--add-opens java.base/java.lang.reflect=ALL-UNNAMED",
                            field.getName(), cls.getSimpleName());
                        throw new IllegalStateException("Cannot access @TenantId field '" + field.getName() + "' in "
                            + cls.getSimpleName() + ". Multi-tenant filtering requires field access. "
                            + "Add JVM argument: --add-opens java.base/java.lang.reflect=ALL-UNNAMED", e);
                    }
                    return field.getName();
                }
            }
            return NO_FIELD_SENTINEL;
        });
        return NO_FIELD_SENTINEL.equals(result) ? null : result;
    }

    private static String resolveTenantIdFieldNameViaGetter(Class<?> entityClass) {
        for (Method m : entityClass.getMethods()) {
            TenantId ann = m.getAnnotation(TenantId.class);
            if (ann != null && m.getParameterCount() == 0) {
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

    private static List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new java.util.ArrayList<>();
        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()) {
                    fields.add(field);
                }
            }
            clazz = clazz.getSuperclass();
        }
        return fields;
    }
}
