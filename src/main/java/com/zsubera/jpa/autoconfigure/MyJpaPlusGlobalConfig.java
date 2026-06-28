package com.zsubera.jpa.autoconfigure;

import com.zsubera.jpa.template.MyJpaTemplate;

/**
 * MyJpa-Plus 全局配置中心，替代分散的静态可变状态。
 *
 * <p>
 * 所有需要跨组件共享的配置统一在此类中管理，通过 Spring Bean 注入，
 * 避免静态可变状态导致的多实例部署问题和线程安全问题。
 *
 * <p>
 * <strong>线程安全说明：</strong>所有字段均为不可变或通过 volatile 保证可见性，
 * setter 方法在 Spring 启动阶段调用，运行时为只读访问。
 *
 * @author myjpa-plus
 */
public class MyJpaPlusGlobalConfig {

    /** 软删除相关配置。 */
    private volatile boolean softDeleteAutoFilter = true;

    /** 是否阻断无条件硬删除。 */
    private volatile boolean blockUnconditionalDelete = true;

    /** 查询最大返回行数。null 表示未配置（使用 LocalFallback）。 */
    private volatile Integer maxResults;

    /** 批量操作最大影响行数。null 表示未配置。 */
    private volatile Integer maxBulkOperationRows;

    /** 深度分页警告阈值。null 表示未配置。 */
    private volatile Integer deepPaginationOffsetThreshold;

    /** 深度分页硬限制。null 表示未配置。 */
    private volatile Integer deepPaginationOffsetLimit;

    /** IN 子句最大参数数量。null 表示未配置。 */
    private volatile Integer inClauseMaxSize;

    /** IN 子句硬限制。null 表示未配置。 */
    private volatile Integer inClauseHardLimit;

    /** Lambda 属性名缓存大小。null 表示未配置。 */
    private volatile Integer lambdaCacheSize;

    /** 缓存最大条目数。null 表示未配置。 */
    private volatile Integer cacheMaxEntries;

    /** Upsert 批量独立事务最大迭代次数。null 表示未配置（默认 10000）。 */
    private volatile Integer maxUpsertBatchIterations;

    // ---- Soft Delete ----

    public boolean isSoftDeleteAutoFilter() {
        return softDeleteAutoFilter;
    }

    public void setSoftDeleteAutoFilter(boolean softDeleteAutoFilter) {
        this.softDeleteAutoFilter = softDeleteAutoFilter;
    }

    public boolean isBlockUnconditionalDelete() {
        return blockUnconditionalDelete;
    }

    public void setBlockUnconditionalDelete(boolean blockUnconditionalDelete) {
        this.blockUnconditionalDelete = blockUnconditionalDelete;
    }

    // ---- Query Limits ----

    public Integer getMaxResults() {
        return maxResults;
    }

    public void setMaxResults(int maxResults) {
        if (maxResults <= 0 && maxResults != -1) {
            throw new IllegalArgumentException("maxResults must be positive or -1 (disabled), got: " + maxResults);
        }
        this.maxResults = maxResults;
    }

    public Integer getMaxBulkOperationRows() {
        return maxBulkOperationRows;
    }

    public void setMaxBulkOperationRows(int maxBulkOperationRows) {
        if (maxBulkOperationRows < -1) {
            throw new IllegalArgumentException(
                "maxBulkOperationRows must be -1 (unlimited) or non-negative, got: " + maxBulkOperationRows);
        }
        this.maxBulkOperationRows = maxBulkOperationRows;
    }

    public Integer getDeepPaginationOffsetThreshold() {
        return deepPaginationOffsetThreshold;
    }

    public void setDeepPaginationOffsetThreshold(int deepPaginationOffsetThreshold) {
        if (deepPaginationOffsetThreshold <= 0) {
            throw new IllegalArgumentException(
                "deepPaginationOffsetThreshold must be positive, got: " + deepPaginationOffsetThreshold);
        }
        this.deepPaginationOffsetThreshold = deepPaginationOffsetThreshold;
    }

    public Integer getDeepPaginationOffsetLimit() {
        return deepPaginationOffsetLimit;
    }

    public void setDeepPaginationOffsetLimit(int deepPaginationOffsetLimit) {
        if (deepPaginationOffsetLimit <= 0 && deepPaginationOffsetLimit != -1) {
            throw new IllegalArgumentException(
                "deepPaginationOffsetLimit must be -1 (unlimited) or positive, got: " + deepPaginationOffsetLimit);
        }
        this.deepPaginationOffsetLimit = deepPaginationOffsetLimit;
    }

    // ---- IN Clause ----

    public Integer getInClauseMaxSize() {
        return inClauseMaxSize;
    }

    public void setInClauseMaxSize(int inClauseMaxSize) {
        if (inClauseMaxSize <= 0) {
            throw new IllegalArgumentException("inClauseMaxSize must be positive, got: " + inClauseMaxSize);
        }
        this.inClauseMaxSize = inClauseMaxSize;
    }

    public Integer getInClauseHardLimit() {
        return inClauseHardLimit;
    }

    public void setInClauseHardLimit(int inClauseHardLimit) {
        if (inClauseHardLimit <= 0) {
            throw new IllegalArgumentException("inClauseHardLimit must be positive, got: " + inClauseHardLimit);
        }
        this.inClauseHardLimit = inClauseHardLimit;
    }

    // ---- Lambda Cache ----

    public Integer getLambdaCacheSize() {
        return lambdaCacheSize;
    }

    public void setLambdaCacheSize(int lambdaCacheSize) {
        if (lambdaCacheSize <= 0) {
            throw new IllegalArgumentException("lambdaCacheSize must be positive, got: " + lambdaCacheSize);
        }
        this.lambdaCacheSize = lambdaCacheSize;
    }

    // ---- Cache ----

    public Integer getCacheMaxEntries() {
        return cacheMaxEntries;
    }

    public void setCacheMaxEntries(int cacheMaxEntries) {
        if (cacheMaxEntries <= 0) {
            throw new IllegalArgumentException("cacheMaxEntries must be positive, got: " + cacheMaxEntries);
        }
        this.cacheMaxEntries = cacheMaxEntries;
    }

    // ---- Upsert Batch ----

    public Integer getMaxUpsertBatchIterations() {
        return maxUpsertBatchIterations;
    }

    public void setMaxUpsertBatchIterations(int maxUpsertBatchIterations) {
        if (maxUpsertBatchIterations <= 0) {
            throw new IllegalArgumentException(
                "maxUpsertBatchIterations must be positive, got: " + maxUpsertBatchIterations);
        }
        this.maxUpsertBatchIterations = maxUpsertBatchIterations;
    }
}
