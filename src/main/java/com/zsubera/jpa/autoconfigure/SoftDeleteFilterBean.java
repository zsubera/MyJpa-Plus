package com.zsubera.jpa.autoconfigure;

import com.zsubera.jpa.annotation.SoftDelete;
import com.zsubera.jpa.update.SoftDeleteHelper;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * 自动过滤 Bean，用于实现透明的软删除过滤。
 *
 * <p>当 {@code myjpa-plus.soft-delete.auto-filter} 启用时，此 Bean 会跟踪所有带有
 * {@link SoftDelete @SoftDelete} 注解的实体类，并提供自动应用软删除过滤器的机制。
 *
 * <p>在 Repository 中的用法：
 *
 * <pre>{@code
 * // 对任意查询应用自动过滤
 * Specification<User> spec = userQuerySpec();
 * Specification<User> filtered = softDeleteFilterBean.apply(spec, User.class);
 * }</pre>
 *
 * <p>当配置中设置 {@code auto-filter: true} 时（默认值），此 Bean 会自动激活。
 */
@Component
@ConditionalOnProperty(
    prefix = "myjpa-plus.soft-delete",
    name = "auto-filter",
    havingValue = "true",
    matchIfMissing = true)
@EnableConfigurationProperties(MyJpaPlusProperties.class)
public class SoftDeleteFilterBean implements InitializingBean {

  private static final Logger log = LoggerFactory.getLogger(SoftDeleteFilterBean.class);

  private final MyJpaPlusProperties properties;

  @SuppressFBWarnings("EI_EXPOSE_REP2")
  public SoftDeleteFilterBean(MyJpaPlusProperties properties) {
    this.properties = properties;
  }

  @Override
  public void afterPropertiesSet() {
    if (log.isDebugEnabled()) {
      log.debug(
          "SoftDeleteFilterBean initialized (auto-filter={})",
          properties.getSoftDelete().isAutoFilter());
    }
  }

  /**
   * 注册实体类以进行自动过滤。委托给 {@link SoftDeleteHelper}，该类会缓存结果（包括正向和负向结果）
   * 以避免重复扫描。
   */
  public void registerEntity(Class<?> entityClass) {
    SoftDeleteHelper.findSoftDeleteField(entityClass);
    if (log.isDebugEnabled()) {
      log.debug("Registered {} for soft-delete auto-filtering", entityClass.getSimpleName());
    }
  }

  /**
   * 如果实体具有 {@link SoftDelete @SoftDelete} 注解的字段，则对给定的 Specification 应用软删除过滤器。
   *
   * @param spec 原始的 Specification
   * @param entityClass 实体类
   * @param <T> 实体类型
   * @return 应用软删除过滤器后的组合 Specification，如果实体没有软删除字段则返回原始 Specification
   */
  @SuppressWarnings("unchecked")
  public <T> Specification<T> apply(@Nullable Specification<T> spec, Class<T> entityClass) {
    if (hasSoftDeleteField(entityClass)) {
      Specification<T> notDeleted = SoftDeleteHelper.isNotDeleted(entityClass);
      return spec == null ? notDeleted : spec.and(notDeleted);
    }
    return spec;
  }

  /**
   * 检查给定的实体类是否具有 {@link SoftDelete @SoftDelete} 字段。委托给
   * {@link SoftDeleteHelper#findSoftDeleteField(Class)}，该方法内部会缓存正向和负向的结果。
   */
  public boolean hasSoftDeleteField(Class<?> entityClass) {
    return SoftDeleteHelper.findSoftDeleteField(entityClass) != null;
  }
}
