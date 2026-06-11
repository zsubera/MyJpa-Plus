package com.zsubera.jpa.repository;

import com.zsubera.jpa.softdelete.SoftDeleteHelper;
import com.zsubera.jpa.util.EntityClassResolver;
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
 * 基础仓库接口，结合了 {@link JpaRepository} 和 {@link JpaSpecificationExecutor}， 使消费者只需扩展单个接口即可使用。
 *
 * <p>
 * 添加了直接接受 {@code QuerySpec} 的便捷重载方法：
 *
 * <pre>{@code
 * public interface UserRepository extends MyJpaRepository<User, Long> {}
 *
 * List<User> users = repository.findAll(new QuerySpec<User>().eq(User::getStatus, "ACTIVE"));
 * }</pre>
 *
 * @param <T> 实体类型
 * @param <ID> 实体ID类型
 */
@NoRepositoryBean
public interface MyJpaRepository<T, ID> extends JpaRepository<T, ID>, JpaSpecificationExecutor<T> {

    /**
     * 查找所有匹配给定 {@link Specification} 的实体。
     *
     * <p>
     * <strong>安全建议：</strong>此方法不限制返回结果数量，可能导致大数据集查询时的内存溢出（OOM）。 在生产环境中，推荐使用
     * {@link com.zsubera.jpa.template.MyJpaTemplate#findAll(Class, com.zsubera.jpa.spec.QuerySpec)}
     * 进行查询，它提供了可配置的最大行数限制（默认 10000 条）。 或使用分页查询 {@link #findAll(Specification, Pageable)} 限制结果数量。
     *
     * @param spec 查询规格说明
     * @return 匹配实体列表
     */
    @Override
    List<T> findAll(Specification<T> spec);

    /**
     * 分页查找所有匹配给定 {@link Specification} 的实体。
     *
     * @param spec 查询规格说明
     * @param pageable 分页参数
     * @return 包含匹配实体的分页结果
     */
    @Override
    Page<T> findAll(Specification<T> spec, Pageable pageable);

    /**
     * 排序查找所有匹配给定 {@link Specification} 的实体。
     *
     * @param spec 查询规格说明
     * @param sort 排序参数
     * @return 匹配实体列表
     */
    @Override
    List<T> findAll(Specification<T> spec, Sort sort);

    /**
     * 查找单个匹配给定 {@link Specification} 的实体。
     *
     * @param spec 查询规格说明
     * @return 匹配实体的 Optional 包装
     */
    @Override
    Optional<T> findOne(Specification<T> spec);

    /**
     * 统计匹配给定 {@link Specification} 的实体数量。
     *
     * @param spec 查询规格说明
     * @return 匹配实体数量
     */
    @Override
    long count(Specification<T> spec);

    /**
     * 检查是否存在匹配给定 {@link Specification} 的实体。
     *
     * @param spec 查询规格说明
     * @return 如果存在匹配实体返回 true，否则返回 false
     */
    @Override
    boolean exists(Specification<T> spec);

    /**
     * 查找所有匹配给定 {@link Specification} 且未被软删除的实体。 如果实体具有 {@link com.zsubera.jpa.annotation.SoftDelete @SoftDelete} 注解的字段，
     * 则自动应用软删除过滤器。
     *
     * @param spec 附加过滤规格说明（可以为 null）
     * @return 匹配规格说明的未删除实体列表
     */
    default List<T> findNotDeletedAll(Specification<T> spec) {
        Specification<T> notDeleted = SoftDeleteHelper.isNotDeleted(getEntityClass());
        if (notDeleted == null) {
            // 实体没有 @SoftDelete 字段，退化为普通查询
            return spec == null ? findAll() : findAll(spec);
        }
        if (spec == null) {
            return findAll(notDeleted);
        }
        return findAll(spec.and(notDeleted));
    }

    /**
     * 查找所有未被软删除的实体，不附加额外条件。
     *
     * <p>
     * <strong>安全建议：</strong>此方法不限制返回结果数量，可能导致大数据集查询时的内存溢出（OOM）。 对于大数据集，推荐使用 {@link com.zsubera.jpa.template.MyJpaTemplate#findAllStream(Class, com.zsubera.jpa.spec.QuerySpec, java.util.function.Consumer)} 进行流式查询。
     *
     * @return 所有未删除实体的列表
     */
    default List<T> findNotDeletedAll() {
        Specification<T> notDeleted = SoftDeleteHelper.isNotDeleted(getEntityClass());
        return notDeleted == null ? findAll() : findAll(notDeleted);
    }

    /**
     * 分页查找所有匹配给定规格说明且未被软删除的实体。
     *
     * @param spec 附加过滤规格说明（可以为 null）
     * @param pageable 分页参数
     * @return 包含未删除实体的分页结果
     */
    default Page<T> findNotDeletedAll(Specification<T> spec, Pageable pageable) {
        Specification<T> notDeleted = SoftDeleteHelper.isNotDeleted(getEntityClass());
        if (notDeleted == null) {
            return spec == null ? findAll(pageable) : findAll(spec, pageable);
        }
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
        // [FIX] P1-4: 使用 findSoftDeleteField 判断实体是否有 @SoftDelete 字段，
        // 而非检查 notDeleted 是否为 null（无 @SoftDelete 时 isNotDeleted 返回 conjunction 而非 null）
        boolean hasSoftDelete = SoftDeleteHelper.findSoftDeleteField(getEntityClass()) != null;
        if (!hasSoftDelete) {
            if (spec == null) {
                // [FIX] P1-4: 无 @SoftDelete 且无 spec 时，使用分页查询而非全表加载
                // 原代码 findAll().stream().findFirst() 会加载全部实体到内存
                List<T> content = findAll(org.springframework.data.domain.Pageable.ofSize(1)).getContent();
                return content.isEmpty() ? Optional.empty() : Optional.of(content.get(0));
            }
            return findOne(spec);
        }
        if (spec == null) {
            return findOne(notDeleted);
        }
        return findOne(spec.and(notDeleted));
    }

    /**
     * 根据ID查找单个未被软删除的实体。 使用查询级过滤器避免获取已删除的实体。
     *
     * @param id 实体ID
     * @return 匹配实体的 Optional 包装
     */
    default Optional<T> findNotDeletedById(ID id) {
        if (id == null) {
            return Optional.empty();
        }
        Class<T> entityClass = getEntityClass();
        Specification<T> notDeleted = SoftDeleteHelper.isNotDeleted(entityClass);
        String idFieldName = EntityClassResolver.resolveIdFieldName(entityClass);
        Specification<T> idSpec = (root, query, cb) -> cb.equal(root.get(idFieldName), id);
        if (notDeleted == null) {
            return findOne(idSpec);
        }
        return findOne(notDeleted.and(idSpec));
    }

    /**
     * 统计匹配给定规格说明且未被软删除的实体数量。
     *
     * @param spec 附加过滤规格说明（可以为 null）
     * @return 未删除实体数量
     */
    default long countNotDeleted(Specification<T> spec) {
        Specification<T> notDeleted = SoftDeleteHelper.isNotDeleted(getEntityClass());
        if (notDeleted == null) {
            return spec == null ? count() : count(spec);
        }
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
        Specification<T> notDeleted = SoftDeleteHelper.isNotDeleted(getEntityClass());
        return notDeleted == null ? count() : count(notDeleted);
    }

    /**
     * 返回与此仓库关联的领域类。结果按仓库类缓存以避免重复反射。
     *
     * @return 实体类
     * @throws IllegalStateException 如果无法解析实体类
     */
    private Class<T> getEntityClass() {
        Class<T> entityClass = EntityClassResolver.resolve(getClass());
        if (entityClass == null) {
            throw new IllegalStateException("Cannot resolve entity class for repository: " + getClass().getName());
        }
        return entityClass;
    }
}
