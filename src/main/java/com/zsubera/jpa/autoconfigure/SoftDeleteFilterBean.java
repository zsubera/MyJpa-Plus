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
 * Auto-filter bean that enables transparent soft-delete filtering.
 *
 * <p>When {@code myjpa-plus.soft-delete.auto-filter} is enabled, this bean tracks all entity
 * classes with {@link SoftDelete @SoftDelete} annotations and provides a mechanism to automatically
 * apply soft-delete filters.
 *
 * <p>Usage in repositories:
 *
 * <pre>{@code
 * // Apply auto-filter to any query
 * Specification<User> spec = userQuerySpec();
 * Specification<User> filtered = softDeleteFilterBean.apply(spec, User.class);
 * }</pre>
 *
 * <p>The bean is automatically activated when {@code auto-filter: true} is set in configuration
 * (default).
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
   * Registers an entity class for auto-filtering. Delegates to {@link SoftDeleteHelper} which
   * caches the result (both positive and negative) to avoid repeated scanning.
   */
  public void registerEntity(Class<?> entityClass) {
    SoftDeleteHelper.findSoftDeleteField(entityClass);
    if (log.isDebugEnabled()) {
      log.debug("Registered {} for soft-delete auto-filtering", entityClass.getSimpleName());
    }
  }

  /**
   * Applies the soft-delete filter to the given specification if the entity has a {@link
   * SoftDelete @SoftDelete} annotated field.
   *
   * @param spec the original specification
   * @param entityClass the entity class
   * @param <T> the entity type
   * @return the combined specification with soft-delete filter applied, or the original
   *     specification if the entity has no soft-delete field
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
   * Checks whether the given entity class has a {@link SoftDelete @SoftDelete} field. Delegates to
   * {@link SoftDeleteHelper#findSoftDeleteField(Class)} which caches positive and negative results
   * internally.
   */
  public boolean hasSoftDeleteField(Class<?> entityClass) {
    return SoftDeleteHelper.findSoftDeleteField(entityClass) != null;
  }
}
