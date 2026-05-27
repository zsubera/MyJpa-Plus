package com.zsubera.jpa.repository;

import com.zsubera.jpa.update.SoftDeleteHelper;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.NoRepositoryBean;

/**
 * 基础仓库接口，结合了 {@link JpaRepository} 和 {@link JpaSpecificationExecutor}，
 * 使消费者只需扩展单个接口即可使用。
 *
 * <p>添加了直接接受 {@code QuerySpec} 的便捷重载方法：
 *
 * <pre>{@code
 * public interface UserRepository extends MyJpaRepository<User, Long> {
 * }
 *
 * List<User> users = repository.findAll(
 *     new QuerySpec<User>().eq(User::getStatus, "ACTIVE")
 * );
 * }</pre>
 *
 * @param <T>  实体类型
 * @param <ID> 实体ID类型
 */
@NoRepositoryBean
public interface MyJpaRepository<T, ID> extends JpaRepository<T, ID>, JpaSpecificationExecutor<T> {

  /**
   * 查找所有匹配给定 {@link Specification} 的实体。
   *
   * @param spec 查询规格说明
   * @return 匹配实体列表
   */
  List<T> findAll(Specification<T> spec);

  /**
   * 分页查找所有匹配给定 {@link Specification} 的实体。
   *
   * @param spec     查询规格说明
   * @param pageable 分页参数
   * @return 包含匹配实体的分页结果
   */
  Page<T> findAll(Specification<T> spec, Pageable pageable);

  /**
   * 排序查找所有匹配给定 {@link Specification} 的实体。
   *
   * @param spec 查询规格说明
   * @param sort 排序参数
   * @return 匹配实体列表
   */
  List<T> findAll(Specification<T> spec, Sort sort);

  /**
   * 查找单个匹配给定 {@link Specification} 的实体。
   *
   * @param spec 查询规格说明
   * @return 匹配实体的 Optional 包装
   */
  Optional<T> findOne(Specification<T> spec);

  /**
   * 统计匹配给定 {@link Specification} 的实体数量。
   *
   * @param spec 查询规格说明
   * @return 匹配实体数量
   */
  long count(Specification<T> spec);

  /**
   * 检查是否存在匹配给定 {@link Specification} 的实体。
   *
   * @param spec 查询规格说明
   * @return 如果存在匹配实体返回 true，否则返回 false
   */
  boolean exists(Specification<T> spec);

  /**
   * 查找所有匹配给定 {@link Specification} 且未被软删除的实体。
   * 如果实体具有 {@link com.zsubera.jpa.annotation.SoftDelete @SoftDelete} 注解的字段，
   * 则自动应用软删除过滤器。
   *
   * @param spec 附加过滤规格说明（可以为 null）
   * @return 匹配规格说明的未删除实体列表
   */
  default List<T> findNotDeletedAll(Specification<T> spec) {
    Specification<T> notDeleted = SoftDeleteHelper.isNotDeleted(getEntityClass());
    if (spec == null) {
      return findAll(notDeleted);
    }
    return findAll(spec.and(notDeleted));
  }

  /**
   * 查找所有未被软删除的实体，不附加额外条件。
   *
   * @return 所有未删除实体的列表
   */
  default List<T> findNotDeletedAll() {
    return findAll(SoftDeleteHelper.isNotDeleted(getEntityClass()));
  }

  /**
   * 分页查找所有匹配给定规格说明且未被软删除的实体。
   *
   * @param spec     附加过滤规格说明（可以为 null）
   * @param pageable 分页参数
   * @return 包含未删除实体的分页结果
   */
  default Page<T> findNotDeletedAll(Specification<T> spec, Pageable pageable) {
    Specification<T> notDeleted = SoftDeleteHelper.isNotDeleted(getEntityClass());
    if (spec == null) {
      return findAll(notDeleted, pageable);
    }
    return findAll(spec.and(notDeleted), pageable);
  }

  /**
   * 查找单个匹配给定规格说明且未被软删除的实体。
   *
   * @param spec 附加过滤规格说明（可以为 null）
   * @return 匹配实体的 Optional 包装
   */
  default Optional<T> findNotDeletedOne(Specification<T> spec) {
    Specification<T> notDeleted = SoftDeleteHelper.isNotDeleted(getEntityClass());
    if (spec == null) {
      return findOne(notDeleted);
    }
    return findOne(spec.and(notDeleted));
  }

  /**
   * 根据ID查找单个未被软删除的实体。
   * 使用查询级过滤器避免获取已删除的实体。
   *
   * @param id 实体ID
   * @return 匹配实体的 Optional 包装
   */
  default Optional<T> findNotDeletedById(ID id) {
    Class<T> entityClass = getEntityClass();
    String idFieldName = EntityClassResolver.resolveIdFieldName(entityClass);
    return findOne(
        SoftDeleteHelper.isNotDeleted(entityClass)
            .and((root, query, cb) -> cb.equal(root.get(idFieldName), id)));
  }

  /**
   * 统计匹配给定规格说明且未被软删除的实体数量。
   *
   * @param spec 附加过滤规格说明（可以为 null）
   * @return 未删除实体数量
   */
  default long countNotDeleted(Specification<T> spec) {
    Specification<T> notDeleted = SoftDeleteHelper.isNotDeleted(getEntityClass());
    if (spec == null) {
      return count(notDeleted);
    }
    return count(spec.and(notDeleted));
  }

  /**
   * 统计所有未被软删除的实体数量，不附加额外条件。
   *
   * @return 未删除实体总数
   */
  default long countNotDeleted() {
    return count(SoftDeleteHelper.isNotDeleted(getEntityClass()));
  }

  /**
   * 返回与此仓库关联的领域类。结果按仓库类缓存以避免重复反射。
   *
   * @return 实体类
   * @throws IllegalStateException 如果无法解析实体类
   */
  @SuppressWarnings("unchecked")
  private Class<T> getEntityClass() {
    Class<T> entityClass = EntityClassResolver.resolve(getClass());
    if (entityClass == null) {
      throw new IllegalStateException(
          "Cannot resolve entity class for repository: " + getClass().getName());
    }
    return entityClass;
  }
}
