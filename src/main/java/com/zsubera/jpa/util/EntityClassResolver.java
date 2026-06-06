package com.zsubera.jpa.util;

import jakarta.persistence.Id;
import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ResolvableType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.util.ConcurrentReferenceHashMap;

public final class EntityClassResolver {

    private static final Logger log = LoggerFactory.getLogger(EntityClassResolver.class);

    private static final ConcurrentMap<Class<?>, Class<?>> CACHE =
        new ConcurrentReferenceHashMap<>(16, ConcurrentReferenceHashMap.ReferenceType.WEAK);
    private static final ConcurrentMap<Class<?>, String> ID_FIELD_CACHE =
        new ConcurrentReferenceHashMap<>(16, ConcurrentReferenceHashMap.ReferenceType.WEAK);
    private static final ConcurrentMap<Class<?>, Boolean> COMPOSITE_KEY_CACHE =
        new ConcurrentReferenceHashMap<>(16, ConcurrentReferenceHashMap.ReferenceType.WEAK);

    private static final Class<?> UNRESOLVABLE_SENTINEL = Unresolvable.class;

    private static final class Unresolvable {}

    private EntityClassResolver() {}

    @SuppressWarnings("unchecked")
    public static <T> Class<T> resolve(Class<?> repositoryClass) {
        Class<?> cached = CACHE.get(repositoryClass);
        if (cached != null) {
            return cached == UNRESOLVABLE_SENTINEL ? null : (Class<T>)cached;
        }
        Class<?> result = doResolve(repositoryClass);
        CACHE.put(repositoryClass, result != null ? result : UNRESOLVABLE_SENTINEL);
        return (Class<T>)result;
    }

    private static Class<?> doResolve(Class<?> repositoryClass) {
        Class<?> result = tryDirectResolution(repositoryClass);
        if (result != null) {
            return result;
        }
        return resolveThroughHierarchy(repositoryClass);
    }

    public static String resolveIdFieldName(Class<?> entityClass) {
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

    public static boolean hasCompositeKey(Class<?> entityClass) {
        return COMPOSITE_KEY_CACHE.computeIfAbsent(entityClass, cls -> {
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
            for (Class<?> superIface : iface.getInterfaces()) {
                if (superIface == JpaRepository.class) {
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
