package com.zsubera.jpa.autoconfigure;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MyJpa-Plus 的配置属性。
 *
 * <p>前缀：{@code myjpa-plus}
 *
 * <p>application.yml 配置示例：
 *
 * <pre>{@code
 * myjpa-plus:
 *   soft-delete:
 *     auto-filter: true
 * }</pre>
 */
@ConfigurationProperties(prefix = "myjpa-plus")
public class MyJpaPlusProperties {

  /** 软删除相关配置。 */
  private SoftDelete softDelete = new SoftDelete();

  @SuppressFBWarnings("EI_EXPOSE_REP")
  public SoftDelete getSoftDelete() {
    return softDelete;
  }

  @SuppressFBWarnings("EI_EXPOSE_REP2")
  public void setSoftDelete(SoftDelete softDelete) {
    this.softDelete = softDelete;
  }

  public static class SoftDelete {
    /**
     * 是否自动对所有查询应用软删除过滤器。启用后，带有 {@link com.zsubera.jpa.annotation.SoftDelete @SoftDelete}
     * 字段的实体将自动过滤掉 已软删除的记录。默认值：{@code true}
     */
    private boolean autoFilter = true;

    public boolean isAutoFilter() {
      return autoFilter;
    }

    public void setAutoFilter(boolean autoFilter) {
      this.autoFilter = autoFilter;
    }
  }
}
