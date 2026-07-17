package com.zsubera.jpa.autoconfigure;

import jakarta.annotation.PostConstruct;
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

    /** 缓存相关配置。 */
    private Cache cache = new Cache();

    /**
     * 数据库方言。留空表示自动检测（从 JDBC 元数据推断）。
     *
     * <p>
     * 可选值：{@code mysql}、{@code postgresql}、{@code oracle}、{@code h2} 等。
     * 仅在自动检测失败或需要强制指定方言时使用。
     */
    private String dialect;

    /**
     * 启动时验证所有配置属性的合法性。
     */
    @PostConstruct
    void validate() {
        query.validate();
        monitoring.validate();
    }

    public SoftDelete getSoftDelete() {
        return softDelete;
    }

    public void setSoftDelete(SoftDelete softDelete) {
        this.softDelete = softDelete;
    }

    public Query getQuery() {
        return query;
    }

    public void setQuery(Query query) {
        this.query = query;
    }

    public Monitoring getMonitoring() {
        return monitoring;
    }

    public void setMonitoring(Monitoring monitoring) {
        this.monitoring = monitoring;
    }

    public Cache getCache() {
        return cache;
    }

    public void setCache(Cache cache) {
        this.cache = cache;
    }

    public String getDialect() {
        return dialect;
    }

    public void setDialect(String dialect) {
        this.dialect = dialect;
    }

    public static class SoftDelete {
        /**
         * 是否自动对所有查询应用软删除过滤器。启用后，带有 {@link com.zsubera.jpa.annotation.SoftDelete @SoftDelete} 字段的实体将自动过滤掉
         * 已软删除的记录。默认值：{@code true}
         */
        private boolean autoFilter = true;

        /**
         * 是否阻断对拥有 {@code @SoftDelete} 字段的实体的无条件硬删除操作。 启用后，当软删除过滤被禁用（{@code autoFilter=false} 或
         * {@code @IgnoreSoftDelete}）时， 执行 {@code deleteAll()} 等无条件硬删除将抛出 {@link IllegalStateException}。 仅对拥有
         * {@code @SoftDelete} 字段的实体生效，无该字段的实体不受影响。 默认值：{@code true}（生产环境最安全）
         */
        private boolean blockUnconditionalDelete = true;

        /**
         * 软删除忽略计数器的安全上限。防止 ThreadLocal 忽略计数器无限增长。 默认值：{@code 64}
         */
        private int maxIgnoreCount = 64;

        public boolean isAutoFilter() {
            return autoFilter;
        }

        public void setAutoFilter(boolean autoFilter) {
            this.autoFilter = autoFilter;
        }

        public boolean isBlockUnconditionalDelete() {
            return blockUnconditionalDelete;
        }

        public void setBlockUnconditionalDelete(boolean blockUnconditionalDelete) {
            this.blockUnconditionalDelete = blockUnconditionalDelete;
        }

        public int getMaxIgnoreCount() {
            return maxIgnoreCount;
        }

        public void setMaxIgnoreCount(int maxIgnoreCount) {
            if (maxIgnoreCount <= 0) {
                throw new IllegalArgumentException("maxIgnoreCount must be positive");
            }
            this.maxIgnoreCount = maxIgnoreCount;
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

        /**
         * 批量操作最大影响行数。超过此限制时将抛出异常，防止意外大规模更新/删除。 设置为 {@code 0} 表示禁用限制。 默认值：{@code 10000}
         */
        private int maxBulkOperationRows = 10000;

        /**
         * PBKDF2 密钥派生迭代次数。更高值更安全但更慢。
         * 注意：此默认值必须与 EncryptionKeyManager.PBKDF2_ITERATIONS_DEFAULT 保持一致。
         */
        private int pbkdf2Iterations = 600000;

        /**
         * 额外的安全函数名列表。添加后这些函数可在 {@code func()} 方法中使用。
         * 函数名不区分大小写，会自动转换为大写。
         *
         * <p>
         * 配置示例（application.yml）：
         * <pre>{@code
         * myjpa-plus:
         *   query:
         *     extra-safe-functions:
         *       - ARRAY_TO_STRING
         *       - REGEXP_REPLACE
         * }</pre>
         */
        private java.util.List<String> extraSafeFunctions = new java.util.ArrayList<>();

        /**
         * 额外的布尔函数名列表。添加后这些函数可在 {@code func()} 方法中作为布尔返回函数使用。
         * 函数名不区分大小写，会自动转换为大写。
         *
         * <p>
         * 配置示例（application.yml）：
         * <pre>{@code
         * myjpa-plus:
         *   query:
         *     extra-boolean-functions:
         *       - MY_CUSTOM_CHECK
         * }</pre>
         */
        private java.util.List<String> extraBooleanFunctions = new java.util.ArrayList<>();

        /**
         * Upsert 批量独立事务最大迭代次数。超过此限制时抛出异常，防止输入过大耗尽资源。 默认值：{@code 10000}
         */
        private int maxUpsertBatchIterations = 10000;

        /**
         * 是否启用 Unicode 标识符验证。启用后，SQL 中的表名和列名将进行 Unicode 合法性检查。 默认值：{@code false}
         */
        private boolean unicodeIdentifiers = false;

        /**
         * 验证所有查询配置的跨字段关系。
         *
         * <p>
         * 单字段验证由各 setter 方法内联完成，此处仅做跨字段关系校验。
         */
        void validate() {
            if (inClauseHardLimit < inClauseMaxSize) {
                throw new IllegalArgumentException("myjpa-plus.query.in-clause-hard-limit (" + inClauseHardLimit
                    + ") must be >= myjpa-plus.query.in-clause-max-size (" + inClauseMaxSize + ")");
            }
        }

        public int getMaxResults() {
            return maxResults;
        }

        public void setMaxResults(int maxResults) {
            if (maxResults <= 0 && maxResults != -1) {
                throw new IllegalArgumentException("maxResults must be positive or -1 (disabled)");
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

        public int getMaxBulkOperationRows() {
            return maxBulkOperationRows;
        }

        public void setMaxBulkOperationRows(int maxBulkOperationRows) {
            if (maxBulkOperationRows < -1) {
                throw new IllegalArgumentException("maxBulkOperationRows must be -1 (unlimited) or non-negative");
            }
            this.maxBulkOperationRows = maxBulkOperationRows;
        }

        public int getPbkdf2Iterations() {
            return pbkdf2Iterations;
        }

        public void setPbkdf2Iterations(int pbkdf2Iterations) {
            if (pbkdf2Iterations < 100_000 || pbkdf2Iterations > 10_000_000) {
                throw new IllegalArgumentException(
                    "pbkdf2Iterations must be between 100000 and 10000000, got: " + pbkdf2Iterations);
            }
            this.pbkdf2Iterations = pbkdf2Iterations;
        }

        public java.util.List<String> getExtraSafeFunctions() {
            return extraSafeFunctions;
        }

        public void setExtraSafeFunctions(java.util.List<String> extraSafeFunctions) {
            this.extraSafeFunctions = extraSafeFunctions != null ? extraSafeFunctions : new java.util.ArrayList<>();
        }

        public java.util.List<String> getExtraBooleanFunctions() {
            return extraBooleanFunctions;
        }

        public void setExtraBooleanFunctions(java.util.List<String> extraBooleanFunctions) {
            this.extraBooleanFunctions =
                extraBooleanFunctions != null ? extraBooleanFunctions : new java.util.ArrayList<>();
        }

        public int getMaxUpsertBatchIterations() {
            return maxUpsertBatchIterations;
        }

        public void setMaxUpsertBatchIterations(int maxUpsertBatchIterations) {
            if (maxUpsertBatchIterations <= 0) {
                throw new IllegalArgumentException("maxUpsertBatchIterations must be positive");
            }
            this.maxUpsertBatchIterations = maxUpsertBatchIterations;
        }

        public boolean isUnicodeIdentifiers() {
            return unicodeIdentifiers;
        }

        public void setUnicodeIdentifiers(boolean unicodeIdentifiers) {
            this.unicodeIdentifiers = unicodeIdentifiers;
        }
    }

    public static class Monitoring {
        /** 慢查询阈值（毫秒）。执行时间超过此值的 SQL 将被记录为警告。 默认值：{@code 1000} */
        private long slowQueryThresholdMs = 1000;

        /** 是否启用 SQL 慢查询监控。启用后会通过 DataSource 代理拦截 JDBC 执行并记录慢查询。 默认值：{@code false} */
        private boolean enabled = false;

        /**
         * 审计日志中保留的堆栈跟踪深度。默认值：{@code 5}
         */
        private int stackTraceDepth = 5;

        void validate() {
            if (slowQueryThresholdMs <= 0) {
                throw new IllegalArgumentException(
                    "myjpa-plus.monitoring.slow-query-threshold-ms must be positive, got: " + slowQueryThresholdMs);
            }
        }

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

        public int getStackTraceDepth() {
            return stackTraceDepth;
        }

        public void setStackTraceDepth(int stackTraceDepth) {
            if (stackTraceDepth <= 0) {
                throw new IllegalArgumentException("stackTraceDepth must be positive");
            }
            this.stackTraceDepth = stackTraceDepth;
        }
    }

    public static class Cache {
        /**
         * 缓存后端类型。
         */
        public enum Type {
            /** 本地缓存（默认） */
            CAFFEINE,
            /** Redis 分布式缓存（需要引入 spring-boot-starter-data-redis） */
            REDIS
        }

        /**
         * 是否启用缓存自动失效。启用后，当实体发生变更时（通过发布 {@link com.zsubera.jpa.template.EntityModifiedEvent}），
         * 相关查询缓存会自动在事务提交后清除。 默认值：{@code true}
         */
        private boolean autoInvalidationEnabled = true;

        /** 缓存最大条目数。默认值：{@code 10000} */
        private int maxEntries = 10000;

        /**
         * 缓存后端类型。默认值：{@code CAFFEINE}
         */
        private Type type = Type.CAFFEINE;

        /** Redis 配置（仅在 type=REDIS 时生效） */
        private Redis redis = new Redis();

        public boolean isAutoInvalidationEnabled() {
            return autoInvalidationEnabled;
        }

        public void setAutoInvalidationEnabled(boolean autoInvalidationEnabled) {
            this.autoInvalidationEnabled = autoInvalidationEnabled;
        }

        public int getMaxEntries() {
            return maxEntries;
        }

        public void setMaxEntries(int maxEntries) {
            if (maxEntries <= 0) {
                throw new IllegalArgumentException("maxEntries must be positive");
            }
            this.maxEntries = maxEntries;
        }

        public Type getType() {
            return type;
        }

        public void setType(Type type) {
            this.type = type;
        }

        /**
         * 兼容 String 类型的 setType，支持 YAML 配置。
         */
        public void setType(String type) {
            this.type = Type.valueOf(type.toUpperCase(java.util.Locale.ROOT));
        }

        public Redis getRedis() {
            return redis;
        }

        public void setRedis(Redis redis) {
            this.redis = redis;
        }

        /**
         * Redis 缓存配置。
         *
         * <p>
         * Redis 连接配置（host、port、password 等）通过 Spring Boot 标准属性
         * {@code spring.data.redis.*} 配置，此处仅定义 myjpa-plus 特有的配置项。
         */
        public static class Redis {
            /**
             * 缓存键前缀。默认值：{@code myjpa:}
             *
             * <p>
             * 所有缓存键都会加上此前缀，便于在 Redis 中区分不同应用的缓存。
             */
            private String keyPrefix = "myjpa:";

            public String getKeyPrefix() {
                return keyPrefix;
            }

            public void setKeyPrefix(String keyPrefix) {
                this.keyPrefix = keyPrefix;
            }
        }
    }
}
