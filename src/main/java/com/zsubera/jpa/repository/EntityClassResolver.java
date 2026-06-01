package com.zsubera.jpa.repository;

import jakarta.persistence.Id;
import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ResolvableType;
import org.springframework.util.ConcurrentReferenceHashMap;

/**
 * 从仓库接口解析实体类类型参数，支持通过中间接口的间接继承。
 *
 * <p>
 * 处理类似以下情况：{@code interface UserRepo extends CustomBase<User, Long>}，其中{@code CustomBase<T, ID>
 * extends MyJpaRepository<T, ID>}，通过遍历整个接口层次结构来找到绑定到 {@link MyJpaRepository}类型参数的实际类型参数。
 *
 * <p>
 * <strong>P2-8 改进：</strong>优先使用 Spring 的 {@link ResolvableType} API 进行类型解析， 仅在必要时使用反射。{@code resolveIdFieldName()}
 * 方法通过遍历类层次结构查找 {@code @Id} 注解字段， 这是 JPA 标准做法，在 Java 17+ 模块系统下通常不需要 {@code --add-opens} 参数。
 */
public final class EntityClassResolver {

    private static final Logger log = LoggerFactory.getLogger(EntityClassResolver.class);

    /**
     * 缓存使用弱引用键，允许 GC 回收旧类加载器，防止热部署场景下的类加载器泄漏。 与 SoftDeleteHelper 的缓存策略保持一致。
     */
    private static final ConcurrentMap<Class<?>, Class<?>> CACHE =
        new ConcurrentReferenceHashMap<>(16, ConcurrentReferenceHashMap.ReferenceType.WEAK);
    private static final ConcurrentMap<Class<?>, String> ID_FIELD_CACHE =
        new ConcurrentReferenceHashMap<>(16, ConcurrentReferenceHashMap.ReferenceType.WEAK);

    /** P1: Cache for composite key check results to avoid repeated reflection. */
    private static final ConcurrentMap<Class<?>, Boolean> COMPOSITE_KEY_CACHE =
        new ConcurrentReferenceHashMap<>(16, ConcurrentReferenceHashMap.ReferenceType.WEAK);

    /** 哨兵值，表示缓存了 null 结果（无法解析实体类）。 */
    private static final Class<?> UNRESOLVABLE_SENTINEL = Unresolvable.class;

    /** 标记类，用作缓存中的 null 替代。 */
    private static final class Unresolvable {}

    private EntityClassResolver() {}

    /**
     * 从给定的仓库接口类解析实体类（第一个类型参数{@code T}）。
     *
     * <p>
     * 支持{@link MyJpaRepository}的直接和间接继承。结果按仓库类缓存以避免重复反射。
     *
     * @param repositoryClass 仓库接口类
     * @param <T> 实体类型
     * @return 实体类，如果无法解析则返回{@code null}
     */
    @SuppressWarnings("unchecked")
    static <T> Class<T> resolve(Class<?> repositoryClass) {
        Class<?> cached = CACHE.get(repositoryClass);
        if (cached != null) {
            return cached == UNRESOLVABLE_SENTINEL ? null : (Class<T>)cached;
        }
        Class<?> result = doResolve(repositoryClass);
        CACHE.put(repositoryClass, result != null ? result : UNRESOLVABLE_SENTINEL);
        return (Class<T>)result;
    }

    private static Class<?> doResolve(Class<?> repositoryClass) {
        // 1. Try direct resolution first (works for simple cases)
        Class<?> result = tryDirectResolution(repositoryClass);
        if (result != null) {
            return result;
        }
        // 2. Fall back to full hierarchy traversal
        return resolveThroughHierarchy(repositoryClass);
    }

    /**
     * 为给定实体类解析{@code @Id}字段名。遍历类层次结构（包括超类）以找到使用{@link Id @Id}注解的字段。 支持 {@code @EmbeddedId} 复合主键。结果按实体类缓存。
     *
     * @param entityClass 实体类
     * @return ID字段名
     * @throws IllegalStateException 如果实体类没有{@code @Id}或{@code @EmbeddedId}注解的字段
     */
    public static String resolveIdFieldName(Class<?> entityClass) {
        // B-14: Use try-catch around computeIfAbsent to prevent cache corruption
        // when the mapping function throws an exception
        String cached = ID_FIELD_CACHE.get(entityClass);
        if (cached != null) {
            return cached;
        }
        String result = doResolveIdFieldName(entityClass);
        ID_FIELD_CACHE.put(entityClass, result);
        return result;
    }

    private static String doResolveIdFieldName(Class<?> entityClass) {
        for (Class<?> c = entityClass; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.isAnnotationPresent(Id.class)) {
                    return f.getName();
                }
                // Support @EmbeddedId composite keys
                if (f.isAnnotationPresent(jakarta.persistence.EmbeddedId.class)) {
                    return f.getName();
                }
            }
        }
        // Support @IdClass composite keys - find the first @Id field
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
     * @return 如果使用复合主键返回 true
     */
    public static boolean hasCompositeKey(Class<?> entityClass) {
        // P1: Use cache to avoid repeated reflection
        return COMPOSITE_KEY_CACHE.computeIfAbsent(entityClass, cls -> {
            // Check for @EmbeddedId
            for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (f.isAnnotationPresent(jakarta.persistence.EmbeddedId.class)) {
                        return true;
                    }
                }
            }
            // Check for @IdClass
            return cls.getAnnotation(jakarta.persistence.IdClass.class) != null;
        });
    }

    /**
     * 尝试使用标准的ResolvableType.as()方法进行解析。当MyJpaRepository是直接父接口或 Spring的ResolvableType能正确遍历层次结构时有效。
     */
    private static Class<?> tryDirectResolution(Class<?> repositoryClass) {
        try {
            ResolvableType type = ResolvableType.forClass(repositoryClass).as(MyJpaRepository.class);
            if (type != ResolvableType.NONE && type.getGenerics().length > 0) {
                Class<?> resolved = type.resolveGeneric(0);
                if (resolved != null && resolved != Object.class) {
                    return resolved;
                }
            }
        } catch (IllegalArgumentException e) {
            // 仅捕获 IllegalArgumentException，这是 ResolvableType 在类型无法解析时的预期异常
            // 不捕获 UnsupportedOperationException，因为它可能表示编程错误
            log.debug("ResolvableType resolution failed for {}: {}", repositoryClass.getSimpleName(), e.getMessage());
        }
        return null;
    }

    /**
     * 遍历整个接口层次结构以找到绑定到{@link MyJpaRepository}类型参数的实际类型参数。
     *
     * <p>
     * 对于层次结构中的每个接口，解析类型变量映射直到到达MyJpaRepository。
     */
    private static Class<?> resolveThroughHierarchy(Class<?> repositoryClass) {
        // Find the interface that directly extends MyJpaRepository
        // and trace back the type variable bindings
        for (Class<?> iface : getAllInterfaces(repositoryClass)) {
            for (Class<?> superIface : iface.getInterfaces()) {
                if (superIface == MyJpaRepository.class) {
                    // Found: iface directly extends MyJpaRepository<T, ID>
                    // Now resolve the type arguments of iface as seen from repositoryClass
                    ResolvableType ifaceType = ResolvableType.forClass(repositoryClass).as(iface);
                    if (ifaceType != ResolvableType.NONE && ifaceType.getGenerics().length > 0) {
                        Class<?> resolved = ifaceType.resolveGeneric(0);
                        if (resolved != null && resolved != Object.class) {
                            return resolved;
                        }
                    }
                }
            }
        }
        return null;
    }

    /** 返回给定类实现的所有接口，包括从超类和超级接口继承的接口。使用带去重的迭代BFS以避免递归和冗余复制。 */
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
