package com.zsubera.jpa.repository;

import com.zsubera.jpa.update.SoftDeleteHelper;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.lang.Nullable;

/**
 * 支持软删除自动过滤的 {@link SimpleJpaRepository} 实现。
 *
 * <p>
 * 当实体有 {@link com.zsubera.jpa.annotation.SoftDelete @SoftDelete} 字段时，查询会自动追加过滤条件。 使用
 * {@link IgnoreSoftDelete @IgnoreSoftDelete} 注解可跳过自动过滤。
 *
 * <p>
 * 使用方式：在 {@code @EnableJpaRepositories} 中指定 {@code repositoryBaseClass}：
 *
 * <pre>
 * {
 *     &#64;code
 *     &#64;Configuration
 *     @EnableJpaRepositories(basePackages = "com.example.repository",
 *         repositoryBaseClass = SoftDeleteJpaRepository.class)
 *     public class JpaConfig {}
 * }
 * </pre>
 *
 * @param <T> 实体类型
 * @param <ID> ID 类型
 * @see com.zsubera.jpa.annotation.SoftDelete
 * @see IgnoreSoftDelete
 */
@NoRepositoryBean
public class SoftDeleteJpaRepository<T, ID> extends SimpleJpaRepository<T, ID> {

    private final Class<T> domainClass;
    private final EntityManager entityManager;

    @SuppressFBWarnings("EI_EXPOSE_REP2")
    public SoftDeleteJpaRepository(JpaEntityInformation<T, ?> entityInformation, EntityManager entityManager) {
        super(entityInformation, entityManager);
        this.domainClass = entityInformation.getJavaType();
        this.entityManager = entityManager;
    }

    /**
     * 检查当前调用是否应该应用软删除过滤。
     *
     * <p>
     * 使用 {@link SoftDeleteContext} 的 ThreadLocal 标志判断， 由 {@link IgnoreSoftDeleteAdvisor} 在方法调用前自动设置。替代了原先基于栈遍历的检测方案。
     *
     * @return 如果应该应用过滤返回 true
     */
    private boolean shouldApplySoftDeleteFilter() {
        return SoftDeleteHelper.findSoftDeleteField(domainClass) != null && !SoftDeleteContext.isIgnoreSoftDelete();
    }

    private Specification<T> mergeSoftDeleteFilter(@Nullable Specification<T> spec) {
        if (!shouldApplySoftDeleteFilter()) {
            return spec == null ? (root, query, cb) -> cb.conjunction() : spec;
        }
        Specification<T> softDeleteSpec = SoftDeleteHelper.isNotDeleted(domainClass);
        return spec == null ? softDeleteSpec : spec.and(softDeleteSpec);
    }

    // ---- 覆盖查询方法，自动注入软删除条件 ----

    @Override
    public List<T> findAll() {
        return super.findAll(mergeSoftDeleteFilter(null));
    }

    @Override
    public List<T> findAll(Sort sort) {
        return super.findAll(mergeSoftDeleteFilter(null), sort);
    }

    @Override
    public Page<T> findAll(Pageable pageable) {
        return super.findAll(mergeSoftDeleteFilter(null), pageable);
    }

    @Override
    public List<T> findAll(Specification<T> spec) {
        return super.findAll(mergeSoftDeleteFilter(spec));
    }

    @Override
    public Page<T> findAll(Specification<T> spec, Pageable pageable) {
        return super.findAll(mergeSoftDeleteFilter(spec), pageable);
    }

    @Override
    public List<T> findAll(Specification<T> spec, Sort sort) {
        return super.findAll(mergeSoftDeleteFilter(spec), sort);
    }

    @Override
    public Optional<T> findOne(Specification<T> spec) {
        return super.findOne(mergeSoftDeleteFilter(spec));
    }

    @Override
    public long count() {
        return super.count(mergeSoftDeleteFilter(null));
    }

    @Override
    public long count(Specification<T> spec) {
        return super.count(mergeSoftDeleteFilter(spec));
    }

    @Override
    public boolean exists(Specification<T> spec) {
        return super.exists(mergeSoftDeleteFilter(spec));
    }

    /**
     * 覆盖 findById() 以支持软删除过滤。
     *
     * <p>
     * 默认的 {@link SimpleJpaRepository#findById(Object)} 不会过滤软删除记录， 导致用户可能通过 {@code repository.findById(id)} 获取已删除的实体。
     * 此实现会自动追加软删除过滤条件，确保已删除的实体返回 {@link Optional#empty()}。
     *
     * @param id 实体 ID，不能为 {@code null}
     * @return 包含实体的 {@link Optional}，如果实体不存在或已软删除则返回 {@link Optional#empty()}
     */
    @Override
    public Optional<T> findById(ID id) {
        if (id == null) {
            return Optional.empty();
        }
        // 构建只包含 ID 条件的 Specification，软删除过滤由 findOne 自动处理
        Specification<T> spec = (root, query, cb) -> {
            String idFieldName = EntityClassResolver.resolveIdFieldName(domainClass);
            return cb.equal(root.get(idFieldName), id);
        };
        return findOne(spec);
    }
}
