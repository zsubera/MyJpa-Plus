package com.zsubera.jpa.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for MyJpa-Plus.
 * <p>
 * Prefix: {@code myjpa-plus}
 * <p>
 * Example application.yml:
 * <pre>{@code
 * myjpa-plus:
 *   soft-delete:
 *     auto-filter: true
 * }</pre>
 */
@ConfigurationProperties(prefix = "myjpa-plus")
public class MyJpaPlusProperties {

    /** Soft-delete related configuration. */
    private SoftDelete softDelete = new SoftDelete();

    public SoftDelete getSoftDelete() {
        return softDelete;
    }

    public void setSoftDelete(SoftDelete softDelete) {
        this.softDelete = softDelete;
    }

    public static class SoftDelete {
        /**
         * Whether to automatically apply soft-delete filters to all queries.
         * When enabled, entities with a {@link com.zsubera.jpa.annotation.SoftDelete @SoftDelete}
         * field will be automatically filtered to exclude soft-deleted records.
         * Default: {@code true}
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
