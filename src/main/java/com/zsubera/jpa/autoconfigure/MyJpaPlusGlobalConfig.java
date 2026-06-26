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

    /** 查询最大返回行数。 */
    private volatile int maxResults = MyJpaTemplate.DEFAULT_MAX_RESULTS;

    /** 批量操作最大影响行数。 */
    private volatile int maxBulkOperationRows = MyJpaTemplate.DEFAULT_MAX_BULK_OPERATION_ROWS;

    /** 深度分页警告阈值。 */
    private volatile int deepPaginationOffsetThreshold = MyJpaTemplate.DEFAULT_DEEP_PAGINATION_OFFSET_THRESHOLD;

    /** 深度分页硬限制。 */
    private volatile int deepPaginationOffsetLimit = MyJpaTemplate.DEFAULT_DEEP_PAGINATION_OFFSET_LIMIT;

    /** IN 子句最大参数数量。 */
    private volatile int inClauseMaxSize = 1000;

    /** IN 子句硬限制。 */
    private volatile int inClauseHardLimit = 5000;

    /** Lambda 属性名缓存大小。 */
    private volatile int lambdaCacheSize = 4096;

    /** 缓存最大条目数。 */
    private volatile int cacheMaxEntries = 10000;

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

    public int getMaxResults() {
        return maxResults;
    }

    public void setMaxResults(int maxResults) {
        if (maxResults <= 0 && maxResults != -1) {
            throw new IllegalArgumentException("maxResults must be positive or -1 (disabled), got: " + maxResults);
        }
        this.maxResults = maxResults;
    }

    public int getMaxBulkOperationRows() {
        return maxBulkOperationRows;
    }

    public void setMaxBulkOperationRows(int maxBulkOperationRows) {
        if (maxBulkOperationRows < -1) {
            throw new IllegalArgumentException(
                "maxBulkOperationRows must be -1 (unlimited) or non-negative, got: " + maxBulkOperationRows);
        }
        this.maxBulkOperationRows = maxBulkOperationRows;
    }

    public int getDeepPaginationOffsetThreshold() {
        return deepPaginationOffsetThreshold;
    }

    public void setDeepPaginationOffsetThreshold(int deepPaginationOffsetThreshold) {
        if (deepPaginationOffsetThreshold <= 0) {
            throw new IllegalArgumentException(
                "deepPaginationOffsetThreshold must be positive, got: " + deepPaginationOffsetThreshold);
        }
        this.deepPaginationOffsetThreshold = deepPaginationOffsetThreshold;
    }

    public int getDeepPaginationOffsetLimit() {
        return deepPaginationOffsetLimit;
    }

    public void setDeepPaginationOffsetLimit(int deepPaginationOffsetLimit) {
        if (deepPaginationOffsetLimit < -1) {
            throw new IllegalArgumentException(
                "deepPaginationOffsetLimit must be -1 (unlimited) or non-negative, got: " + deepPaginationOffsetLimit);
        }
        this.deepPaginationOffsetLimit = deepPaginationOffsetLimit;
    }

    // ---- IN Clause ----

    public int getInClauseMaxSize() {
        return inClauseMaxSize;
    }

    public void setInClauseMaxSize(int inClauseMaxSize) {
        if (inClauseMaxSize <= 0) {
            throw new IllegalArgumentException("inClauseMaxSize must be positive, got: " + inClauseMaxSize);
        }
        this.inClauseMaxSize = inClauseMaxSize;
    }

    public int getInClauseHardLimit() {
        return inClauseHardLimit;
    }

    public void setInClauseHardLimit(int inClauseHardLimit) {
        if (inClauseHardLimit <= 0) {
            throw new IllegalArgumentException("inClauseHardLimit must be positive, got: " + inClauseHardLimit);
        }
        this.inClauseHardLimit = inClauseHardLimit;
    }

    // ---- Lambda Cache ----

    public int getLambdaCacheSize() {
        return lambdaCacheSize;
    }

    public void setLambdaCacheSize(int lambdaCacheSize) {
        if (lambdaCacheSize <= 0) {
            throw new IllegalArgumentException("lambdaCacheSize must be positive, got: " + lambdaCacheSize);
        }
        this.lambdaCacheSize = lambdaCacheSize;
    }

    // ---- Cache ----

    public int getCacheMaxEntries() {
        return cacheMaxEntries;
    }

    public void setCacheMaxEntries(int cacheMaxEntries) {
        if (cacheMaxEntries <= 0) {
            throw new IllegalArgumentException("cacheMaxEntries must be positive, got: " + cacheMaxEntries);
        }
        this.cacheMaxEntries = cacheMaxEntries;
    }
}
