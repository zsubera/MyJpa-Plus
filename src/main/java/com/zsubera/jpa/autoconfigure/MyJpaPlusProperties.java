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
 *   query:
 *     max-results: 10000
 *     deep-pagination-offset-threshold: 100000
 * }</pre>
 */
@ConfigurationProperties(prefix = "myjpa-plus")
public class MyJpaPlusProperties {

  /** 软删除相关配置。 */
  private SoftDelete softDelete = new SoftDelete();

  /** 查询相关配置。 */
  private Query query = new Query();

  @SuppressFBWarnings("EI_EXPOSE_REP")
  public SoftDelete getSoftDelete() {
    return softDelete;
  }

  @SuppressFBWarnings("EI_EXPOSE_REP2")
  public void setSoftDelete(SoftDelete softDelete) {
    this.softDelete = softDelete;
  }

  @SuppressFBWarnings("EI_EXPOSE_REP")
  public Query getQuery() {
    return query;
  }

  @SuppressFBWarnings("EI_EXPOSE_REP2")
  public void setQuery(Query query) {
    this.query = query;
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

  public static class Query {
    /**
     * findAll 和 find 方法返回的默认最大行数。对于1000万用户场景，建议根据实际需求调整。 默认值：{@code 10000}
     */
    private int maxResults = 10000;

    /**
     * 深度分页的 offset 阈值，超过此值会记录警告日志。 默认值：{@code 100000}
     */
    private int deepPaginationOffsetThreshold = 100000;

    public int getMaxResults() {
      return maxResults;
    }

    public void setMaxResults(int maxResults) {
      if (maxResults <= 0) {
        throw new IllegalArgumentException("maxResults must be positive");
      }
      this.maxResults = maxResults;
    }

    public int getDeepPaginationOffsetThreshold() {
      return deepPaginationOffsetThreshold;
    }

    public void setDeepPaginationOffsetThreshold(int deepPaginationOffsetThreshold) {
      if (deepPaginationOffsetThreshold <= 0) {
        throw new IllegalArgumentException("deepPaginationOffsetThreshold must be positive");
      }
      this.deepPaginationOffsetThreshold = deepPaginationOffsetThreshold;
    }
  }
}
