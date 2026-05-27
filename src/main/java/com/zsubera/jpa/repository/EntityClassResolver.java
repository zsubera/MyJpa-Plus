package com.zsubera.jpa.repository;

import jakarta.persistence.Id;
import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.core.ResolvableType;

/**
 * 从仓库接口解析实体类类型参数，支持通过中间接口的间接继承。
 *
 * <p>处理类似以下情况：{@code interface UserRepo extends CustomBase<User, Long>}，其中{@code
 * CustomBase<T, ID> extends MyJpaRepository<T, ID>}，通过遍历整个接口层次结构来找到绑定到
 * {@link MyJpaRepository}类型参数的实际类型参数。
 */
final class EntityClassResolver {

  private static final ConcurrentMap<Class<?>, Class<?>> CACHE = new ConcurrentHashMap<>();
  private static final ConcurrentMap<Class<?>, String> ID_FIELD_CACHE = new ConcurrentHashMap<>();

  private EntityClassResolver() {}

  /**
   * 从给定的仓库接口类解析实体类（第一个类型参数{@code T}）。
   *
   * <p>支持{@link MyJpaRepository}的直接和间接继承。结果按仓库类缓存以避免重复反射。
   *
   * @param repositoryClass 仓库接口类
   * @param <T> 实体类型
   * @return 实体类，如果无法解析则返回{@code null}
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
   * 为给定实体类解析{@code @Id}字段名。遍历类层次结构（包括超类）以找到使用{@link Id @Id}注解的字段。
   * 结果按实体类缓存。
   *
   * @param entityClass 实体类
   * @return ID字段名，如果未找到{@code @Id}则返回{@code "id"}
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
   * 尝试使用标准的ResolvableType.as()方法进行解析。当MyJpaRepository是直接父接口或
   * Spring的ResolvableType能正确遍历层次结构时有效。
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
   * 遍历整个接口层次结构以找到绑定到{@link MyJpaRepository}类型参数的实际类型参数。
   *
   * <p>对于层次结构中的每个接口，解析类型变量映射直到到达MyJpaRepository。
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
   * 返回给定类实现的所有接口，包括从超类和超级接口继承的接口。使用带去重的迭代BFS以避免递归和冗余复制。
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
