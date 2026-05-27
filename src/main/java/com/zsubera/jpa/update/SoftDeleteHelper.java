package com.zsubera.jpa.update;

import com.zsubera.jpa.annotation.SoftDelete;
import com.zsubera.jpa.spec.QuerySpec;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.data.jpa.domain.Specification;

/**
 * 软删除实体辅助工具类。
 *
 * <p>用于处理带有 {@link SoftDelete @SoftDelete} 注解的实体，提供自动过滤软删除记录的
 * {@link Specification} 实例。
 *
 * <p>所有缓存均使用 {@link ConcurrentHashMap} 实现线程安全访问。由于实体类数量在实际应用中
 * 有限，无需 LRU 淘汰策略。
 *
 * <p>使用示例：
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

  private static final int MAX_FIELD_CACHE_SIZE = 1024;

  // Sentinel value for entities without a @SoftDelete field (avoids null in cache)
  private static final String NO_FIELD_SENTINEL = "\0";

  // Cache: entityClass -> field name (or sentinel for "no field")
  // Using ConcurrentHashMap for thread-safe access; entity class count is limited in practice
  private static final ConcurrentMap<Class<?>, String> FIELD_CACHE = new ConcurrentHashMap<>();

  // Cache: entityClass -> Specification for isNotDeleted
  private static final ConcurrentMap<Class<?>, Specification<?>> NOT_DELETED_SPEC_CACHE =
      new ConcurrentHashMap<>();

  // Cache: entityClass -> Specification for isDeleted
  private static final ConcurrentMap<Class<?>, Specification<?>> DELETED_SPEC_CACHE =
      new ConcurrentHashMap<>();

  private SoftDeleteHelper() {}

  /**
   * 返回排除软删除记录的 {@link Specification}。
   *
   * <p>对于 {@code Boolean} 类型字段，生成条件为 {@code field = false}；对于引用类型
   * {@code Boolean} 字段（使用 null 表示"未删除"），生成条件为 {@code field IS NULL}。
   *
   * <p>结果按实体类缓存，避免每次调用创建新的 lambda 实例。
   *
   * @param entityClass 实体类
   * @param <T> 实体类型
   * @return 排除软删除记录的 Specification
   */
  @SuppressWarnings("unchecked")
  public static <T> Specification<T> isNotDeleted(Class<T> entityClass) {
    return (Specification<T>)
        NOT_DELETED_SPEC_CACHE.computeIfAbsent(
            entityClass,
            cls -> {
              String fieldName = findSoftDeleteField(entityClass);
              if (fieldName == null) {
                return (Specification<T>) (root, query, cb) -> cb.conjunction();
              }
              return (Specification<T>)
                  (root, query, cb) -> buildNotDeleted(cb, root, fieldName, entityClass);
            });
  }

  /**
   * 返回仅匹配软删除记录的 {@link Specification}。
   *
   * <p>结果按实体类缓存，避免每次调用创建新的 lambda 实例。
   *
   * @param entityClass 实体类
   * @param <T> 实体类型
   * @return 匹配软删除记录的 Specification
   */
  @SuppressWarnings("unchecked")
  public static <T> Specification<T> isDeleted(Class<T> entityClass) {
    return (Specification<T>)
        DELETED_SPEC_CACHE.computeIfAbsent(
            entityClass,
            cls -> {
              String fieldName = findSoftDeleteField(entityClass);
              if (fieldName == null) {
                return (Specification<T>) (root, query, cb) -> cb.disjunction();
              }
              return (Specification<T>) (root, query, cb) -> buildDeleted(cb, root, fieldName);
            });
  }

  /**
   * 构建预应用软删除条件的新 {@link QuerySpec}。
   *
   * <p>注意：与缓存结果的 {@link #isNotDeleted(Class)} 不同，此方法每次调用都会创建新的
   * {@code QuerySpec} 实例，因为 {@code QuerySpec} 是可变的，不适合共享。
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

  private static Predicate buildNotDeleted(
      CriteriaBuilder cb, Path<?> path, String fieldName, Class<?> entityClass) {
    Field field = getField(entityClass, fieldName);
    if (field != null && field.getType() == Boolean.class) {
      return cb.or(cb.isNull(path.get(fieldName)), cb.equal(path.get(fieldName), false));
    }
    return cb.equal(path.get(fieldName), false);
  }

  private static Predicate buildDeleted(CriteriaBuilder cb, Path<?> path, String fieldName) {
    return cb.equal(path.get(fieldName), true);
  }

  /**
   * 查找实体类上带有 {@link SoftDelete @SoftDelete} 注解的字段名称。
   *
   * <p>会遍历类层次结构（包括父类）。结果按实体类缓存。
   *
   * @param entityClass 要扫描的实体类
   * @return 字段名称，如果未找到 {@code @SoftDelete} 字段则返回 {@code null}
   */
  public static String findSoftDeleteField(Class<?> entityClass) {
    String result =
        FIELD_CACHE.computeIfAbsent(
            entityClass,
            cls -> {
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
      return Boolean.TRUE.equals(field.get(entity));
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
