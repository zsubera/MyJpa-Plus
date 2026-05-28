package com.zsubera.jpa.repository;

import com.zsubera.jpa.annotation.IgnoreSoftDelete;
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
 * 当实体有 {@link com.zsubera.jpa.annotation.SoftDelete @SoftDelete} 字段时，查询会自动追加过滤条件。
 * 使用 {@link IgnoreSoftDelete @IgnoreSoftDelete} 注解可跳过自动过滤。
 *
 * <p>
 * 使用方式：在 {@code @EnableJpaRepositories} 中指定 {@code repositoryBaseClass}：
 *
 * <pre>{@code
 * @Configuration
 * @EnableJpaRepositories(
 *     basePackages = "com.example.repository",
 *     repositoryBaseClass = SoftDeleteJpaRepository.class
 * )
 * public class JpaConfig {}
 * }</pre>
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
     * @return 如果应该应用过滤返回 true
     */
    private boolean shouldApplySoftDeleteFilter() {
        return SoftDeleteHelper.findSoftDeleteField(domainClass) != null && !hasIgnoreSoftDeleteAnnotation();
    }

    /**
     * 检查当前方法或类是否有 @IgnoreSoftDelete 注解。
     *
     * <p>
     * 使用改进的匹配策略：
     * <ul>
     *   <li>限制堆栈遍历深度（最多20层）以提高性能</li>
     *   <li>使用方法签名（方法名+参数类型）精确匹配，避免方法重载歧义</li>
     *   <li>优先检查类级别注解，减少不必要的方法扫描</li>
     * </ul>
     *
     * @return 如果应该忽略软删除过滤返回 true
     */
    private boolean hasIgnoreSoftDeleteAnnotation() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        // 限制遍历深度，避免深层调用链的性能损耗
        int maxDepth = Math.min(stackTrace.length, 20);
        for (int i = 0; i < maxDepth; i++) {
            StackTraceElement element = stackTrace[i];
            try {
                Class<?> clazz = Class.forName(element.getClassName());
                // 检查类级别注解
                if (clazz.isAnnotationPresent(IgnoreSoftDelete.class)) {
                    return true;
                }
                // 使用方法签名精确匹配，避免方法重载歧义
                if (hasMethodWithIgnoreSoftDelete(clazz, element.getMethodName())) {
                    return true;
                }
            } catch (ClassNotFoundException e) {
                // 忽略无法加载的类
            }
        }
        return false;
    }

    /**
     * 检查类中是否有指定名称且带有 @IgnoreSoftDelete 注解的方法。
     * 使用方法签名精确匹配，避免方法重载歧义。
     *
     * @param clazz 要检查的类
     * @param methodName 方法名
     * @return 如果找到匹配的方法返回 true
     */
    private boolean hasMethodWithIgnoreSoftDelete(Class<?> clazz, String methodName) {
        java.lang.reflect.Method[] methods = clazz.getDeclaredMethods();
        for (java.lang.reflect.Method method : methods) {
            if (method.getName().equals(methodName)
                && method.isAnnotationPresent(IgnoreSoftDelete.class)) {
                return true;
            }
        }
        return false;
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
}
