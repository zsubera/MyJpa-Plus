package com.zsubera.jpa.repository;

import jakarta.persistence.Id;
import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.core.ResolvableType;

/**
 * Resolves the entity class type parameter from a repository interface, supporting indirect
 * inheritance through intermediate interfaces.
 *
 * <p>Handles cases like: {@code interface UserRepo extends CustomBase<User, Long>} where {@code
 * CustomBase<T, ID> extends MyJpaRepository<T, ID>} by traversing the entire interface hierarchy to
 * find the actual type arguments bound to {@link MyJpaRepository}'s type parameters.
 */
final class EntityClassResolver {

  private static final ConcurrentMap<Class<?>, Class<?>> CACHE = new ConcurrentHashMap<>();
  private static final ConcurrentMap<Class<?>, String> ID_FIELD_CACHE = new ConcurrentHashMap<>();

  private EntityClassResolver() {}

  /**
   * Resolves the entity class (first type parameter {@code T}) from the given repository interface
   * class.
   *
   * <p>Supports both direct and indirect inheritance of {@link MyJpaRepository}. The result is
   * cached per repository class to avoid repeated reflection.
   *
   * @param repositoryClass the repository interface class
   * @param <T> the entity type
   * @return the entity class, or {@code null} if it cannot be resolved
   */
  @SuppressWarnings("unchecked")
  static <T> Class<T> resolve(Class<?> repositoryClass) {
    return (Class<T>)
        CACHE.computeIfAbsent(
            repositoryClass,
            clz -> {
              // 1. Try direct resolution first (works for simple cases)
              Class<?> result = tryDirectResolution(clz);
              if (result != null) {
                return result;
              }
              // 2. Fall back to full hierarchy traversal
              return resolveThroughHierarchy(clz);
            });
  }

  /**
   * Resolves the {@code @Id} field name for the given entity class. Walks the class hierarchy
   * (including superclasses) to find the field annotated with {@link Id @Id}. The result is cached
   * per entity class.
   *
   * @param entityClass the entity class
   * @return the ID field name, or {@code "id"} if no {@code @Id} is found
   */
  static String resolveIdFieldName(Class<?> entityClass) {
    return ID_FIELD_CACHE.computeIfAbsent(
        entityClass,
        cls -> {
          for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
              if (f.isAnnotationPresent(Id.class)) {
                return f.getName();
              }
            }
          }
          return "id";
        });
  }

  /**
   * Attempts to resolve using the standard ResolvableType.as() approach. This works when
   * MyJpaRepository is a direct parent or when Spring's ResolvableType can correctly traverse the
   * hierarchy.
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
    } catch (IllegalArgumentException | UnsupportedOperationException e) {
      // Fall through to hierarchy traversal
    }
    return null;
  }

  /**
   * Traverses the full interface hierarchy to find the actual type arguments bound to {@link
   * MyJpaRepository}'s type parameters.
   *
   * <p>For each interface in the hierarchy, resolves the type variable mappings until reaching
   * MyJpaRepository.
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

  /**
   * Returns all interfaces implemented by the given class, including those inherited from
   * superclasses and superinterfaces. Uses iterative BFS with deduplication to avoid recursion and
   * redundant copies.
   */
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
