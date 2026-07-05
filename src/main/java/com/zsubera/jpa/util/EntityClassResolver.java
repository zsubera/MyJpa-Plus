package com.zsubera.jpa.util;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.persistence.Id;
import java.lang.reflect.Field;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ResolvableType;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 实体类解析工具，从 Repository 接口或实体类中解析关联信息。
 *
 * <p>
 * 提供以下能力：
 * <ul>
 * <li>从 {@code JpaRepository<T, ID>} 泛型参数解析实体类 {@code T}</li>
 * <li>解析实体类的 {@code @Id} 字段名</li>
 * <li>检测复合主键（{@code @EmbeddedId} 或 {@code @IdClass}）</li>
 * </ul>
 *
 * <p>
 * 所有解析结果均通过 {@link org.springframework.util.ConcurrentReferenceHashMap} 缓存， 使用弱引用键以支持热部署/OSGi 场景下的类加载器回收。
 *
 * @author myjpa-plus

 */
public final class EntityClassResolver {

    private static final Logger log = LoggerFactory.getLogger(EntityClassResolver.class);

    private static final Cache<Class<?>, Class<?>> CACHE =
        Caffeine.newBuilder().weakKeys().build();
    private static final Cache<Class<?>, String> ID_FIELD_CACHE =
        Caffeine.newBuilder().weakKeys().build();
    private static final Cache<Class<?>, Boolean> COMPOSITE_KEY_CACHE =
        Caffeine.newBuilder().weakKeys().build();

    private static final Class<?> UNRESOLVABLE_SENTINEL = Unresolvable.class;

    private static final class Unresolvable {}

    private EntityClassResolver() {}

    /**
     * 从 Repository 接口解析关联的实体类。
     *
     * @param repositoryClass Repository 接口类
     * @param <T> 实体类型
     * @return 解析到的实体类，如果无法解析则返回 null
     */
    @SuppressWarnings("unchecked")
    public static <T> Class<T> resolve(Class<?> repositoryClass) {
        Objects.requireNonNull(repositoryClass, "repositoryClass must not be null");
        Class<?> cached = CACHE.get(repositoryClass, EntityClassResolver::doResolve);
        return cached == UNRESOLVABLE_SENTINEL ? null : (Class<T>)cached;
    }

    private static Class<?> doResolve(Class<?> repositoryClass) {
        Class<?> result = tryDirectResolution(repositoryClass);
        if (result != null) {
            return result;
        }
        result = resolveThroughHierarchy(repositoryClass);
        return result != null ? result : UNRESOLVABLE_SENTINEL;
    }

    /**
     * 解析实体类的 {@code @Id} 字段名。
     *
     * @param entityClass 实体类
     * @return {@code @Id} 字段名
     * @throws IllegalStateException 如果未找到 {@code @Id}、{@code @EmbeddedId} 或 {@code @IdClass} 字段
     */
    public static String resolveIdFieldName(Class<?> entityClass) {
        Objects.requireNonNull(entityClass, "entityClass must not be null");
        return ID_FIELD_CACHE.get(entityClass, EntityClassResolver::doResolveIdFieldName);
    }

    private static String doResolveIdFieldName(Class<?> entityClass) {
        for (Class<?> c = entityClass; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.isAnnotationPresent(Id.class)) {
                    return f.getName();
                }
                if (f.isAnnotationPresent(jakarta.persistence.EmbeddedId.class)) {
                    return f.getName();
                }
            }
        }
        jakarta.persistence.IdClass idClassAnnotation = entityClass.getAnnotation(jakarta.persistence.IdClass.class);
        if (idClassAnnotation != null) {
            for (Class<?> c = entityClass; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (f.isAnnotationPresent(Id.class)) {
                        return f.getName();
                    }
                }
            }
        }
        throw new IllegalStateException("No @Id, @EmbeddedId, or @IdClass field found in " + entityClass.getName()
            + ". Ensure the entity has a field annotated with @jakarta.persistence.Id, @jakarta.persistence.EmbeddedId,"
            + " or @jakarta.persistence.IdClass with @Id fields.");
    }

    /**
     * 检查实体类是否使用复合主键（{@code @EmbeddedId} 或 {@code @IdClass}）。
     *
     * @param entityClass 实体类
     * @return 如果实体使用复合主键则返回 true
     */
    public static boolean hasCompositeKey(Class<?> entityClass) {
        Objects.requireNonNull(entityClass, "entityClass must not be null");
        return COMPOSITE_KEY_CACHE.get(entityClass, cls -> {
            for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (f.isAnnotationPresent(jakarta.persistence.EmbeddedId.class)) {
                        return true;
                    }
                }
            }
            return cls.getAnnotation(jakarta.persistence.IdClass.class) != null;
        });
    }

    private static Class<?> tryDirectResolution(Class<?> repositoryClass) {
        try {
            ResolvableType type = ResolvableType.forClass(repositoryClass).as(JpaRepository.class);
            if (type != ResolvableType.NONE && type.getGenerics().length > 0) {
                Class<?> resolved = type.resolveGeneric(0);
                if (resolved != null && resolved != Object.class) {
                    return resolved;
                }
            }
        } catch (IllegalArgumentException e) {
            log.debug("ResolvableType resolution failed for {}: {}", repositoryClass.getSimpleName(), e.getMessage());
        }
        return null;
    }

    private static Class<?> resolveThroughHierarchy(Class<?> repositoryClass) {
        for (Class<?> iface : getAllInterfaces(repositoryClass)) {
            if (JpaRepository.class.isAssignableFrom(iface)) {
                ResolvableType ifaceType = ResolvableType.forClass(repositoryClass).as(iface);
                if (ifaceType != ResolvableType.NONE && ifaceType.getGenerics().length > 0) {
                    Class<?> resolved = ifaceType.resolveGeneric(0);
                    if (resolved != null && resolved != Object.class) {
                        return resolved;
                    }
                }
            }
        }
        return null;
    }

    private static Class<?>[] getAllInterfaces(Class<?> clazz) {
        java.util.Set<Class<?>> seen = new java.util.LinkedHashSet<>();
        java.util.Deque<Class<?>> queue = new java.util.ArrayDeque<>();
        queue.add(clazz);
        while (!queue.isEmpty()) {
            Class<?> current = queue.poll();
            for (Class<?> iface : current.getInterfaces()) {
                if (seen.add(iface)) {
                    queue.add(iface);
                }
            }
        }
        return seen.toArray(new Class<?>[0]);
    }
}
