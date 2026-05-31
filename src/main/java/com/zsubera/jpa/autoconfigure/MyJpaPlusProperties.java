package com.zsubera.jpa.autoconfigure;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MyJpa-Plus 的配置属性。
 *
 * <p>
 * 前缀：{@code myjpa-plus}
 *
 * <p>
 * application.yml 配置示例：
 *
 * <pre>{@code
 * myjpa-plus:
 *   soft-delete:
 *     auto-filter: true
 *   query:
 *     max-results: 10000
 *     deep-pagination-offset-threshold: 100000
 *     deep-pagination-offset-limit: -1  # -1=禁用硬限制，>0=超过此值抛出异常
 * }</pre>
 *
 * <p>
 * 高并发场景最佳实践：
 *
 * <ul>
 * <li>避免使用 {@code findAll()} 不带 limit 查询 — 始终指定 maxResults 或使用 findAllStream()
 * <li>大数据集使用 {@code findAllStream()} 进行流式处理，避免内存溢出
 * <li>分页推荐 keyset pagination（基于上一页最后一条记录的 ID）而非 offset pagination
 * <li>批量操作使用 {@code executeBatch()} 而非 {@code execute()}，分批提交避免长事务
 * <li>对于 1000 万+ 用户系统，建议 max-results 设置为 1000-5000
 * <li>设置合理的 query-timeout 防止慢查询阻塞连接池
 * </ul>
 */
@ConfigurationProperties(prefix = "myjpa-plus")
public class MyJpaPlusProperties {

    /** 软删除相关配置。 */
    private SoftDelete softDelete = new SoftDelete();

    /** 查询相关配置。 */
    private Query query = new Query();

    /** 监控相关配置。 */
    private Monitoring monitoring = new Monitoring();

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

    @SuppressFBWarnings("EI_EXPOSE_REP")
    public Monitoring getMonitoring() {
        return monitoring;
    }

    @SuppressFBWarnings("EI_EXPOSE_REP2")
    public void setMonitoring(Monitoring monitoring) {
        this.monitoring = monitoring;
    }

    public static class SoftDelete {
        /**
         * 是否自动对所有查询应用软删除过滤器。启用后，带有 {@link com.zsubera.jpa.annotation.SoftDelete @SoftDelete} 字段的实体将自动过滤掉
         * 已软删除的记录。默认值：{@code true}
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
        /** findAll 和 find 方法返回的默认最大行数。对于1000万用户场景，建议根据实际需求调整。 默认值：{@code 10000} */
        private int maxResults = 10000;

        /** 深度分页的 offset 阈值，超过此值会记录警告日志。 默认值：{@code 100000} */
        private int deepPaginationOffsetThreshold = 100000;

        /**
         * 深度分页的硬限制。超过此 offset 值将抛出 {@link IllegalArgumentException}，阻止执行。 设置为 {@code -1} 表示禁用硬限制（仅记录警告日志）。
         * 默认值：{@code -1}（禁用）
         */
        private int deepPaginationOffsetLimit = -1;

        /**
         * IN 子句中单个批次的最大参数数量。超过此值会自动拆分为多个 OR 连接的批次。 默认值：{@code 1000}（Oracle 限制）
         */
        private int inClauseMaxSize = 1000;

        /**
         * IN 子句的硬限制。超过此限制时将抛出异常，防止数据库性能问题。 默认值：{@code 5000}
         */
        private int inClauseHardLimit = 5000;

        /**
         * Lambda 属性名缓存大小。 默认值：{@code 4096}
         */
        private int lambdaCacheSize = 4096;

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

        public int getDeepPaginationOffsetLimit() {
            return deepPaginationOffsetLimit;
        }

        public void setDeepPaginationOffsetLimit(int deepPaginationOffsetLimit) {
            if (deepPaginationOffsetLimit <= 0 && deepPaginationOffsetLimit != -1) {
                throw new IllegalArgumentException("deepPaginationOffsetLimit must be positive or -1 (disabled)");
            }
            this.deepPaginationOffsetLimit = deepPaginationOffsetLimit;
        }

        public int getInClauseMaxSize() {
            return inClauseMaxSize;
        }

        public void setInClauseMaxSize(int inClauseMaxSize) {
            if (inClauseMaxSize <= 0) {
                throw new IllegalArgumentException("inClauseMaxSize must be positive");
            }
            this.inClauseMaxSize = inClauseMaxSize;
        }

        public int getInClauseHardLimit() {
            return inClauseHardLimit;
        }

        public void setInClauseHardLimit(int inClauseHardLimit) {
            if (inClauseHardLimit <= 0) {
                throw new IllegalArgumentException("inClauseHardLimit must be positive");
            }
            this.inClauseHardLimit = inClauseHardLimit;
        }

        public int getLambdaCacheSize() {
            return lambdaCacheSize;
        }

        public void setLambdaCacheSize(int lambdaCacheSize) {
            if (lambdaCacheSize <= 0) {
                throw new IllegalArgumentException("lambdaCacheSize must be positive");
            }
            this.lambdaCacheSize = lambdaCacheSize;
        }
    }

    public static class Monitoring {
        /** 慢查询阈值（毫秒）。执行时间超过此值的 SQL 将被记录为警告。 默认值：{@code 1000} */
        private long slowQueryThresholdMs = 1000;

        /** 是否启用 SQL 慢查询监控。启用后会通过 DataSource 代理拦截 JDBC 执行并记录慢查询。 默认值：{@code false} */
        private boolean enabled = false;

        public long getSlowQueryThresholdMs() {
            return slowQueryThresholdMs;
        }

        public void setSlowQueryThresholdMs(long slowQueryThresholdMs) {
            if (slowQueryThresholdMs <= 0) {
                throw new IllegalArgumentException("slowQueryThresholdMs must be positive");
            }
            this.slowQueryThresholdMs = slowQueryThresholdMs;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
