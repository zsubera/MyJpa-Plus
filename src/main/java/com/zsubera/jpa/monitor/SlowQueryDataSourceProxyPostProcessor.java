package com.zsubera.jpa.monitor;

import java.util.Set;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.lang.NonNull;

/**
 * 用于在监控启用时将 {@link DataSource} 包装为慢查询代理的 {@link BeanPostProcessor}。
 *
 * <p>
 * 此处理器不依赖 Hibernate，可与任何 JPA 实现配合使用。使用
 * {@link SlowQueryDataSourceProxy#wrap(DataSource, long)} 进行包装，通过
 * {@link SlowQueryDataSourceProxy#isWrapped(DataSource)} 防止双重包装。
 *
 * <p>
 * 自动排除 Flyway/Liquibase 等迁移工具的 DataSource Bean，避免不必要的代理开销。
 *
 * <p>
 * <strong>自动装配：</strong>由 {@link com.zsubera.jpa.autoconfigure.MyJpaPlusAutoConfiguration}
 * 在 {@code myjpa-plus.monitoring.enabled=true} 时自动注册。
 *
 * @see SlowQueryDataSourceProxy

 */
public class SlowQueryDataSourceProxyPostProcessor implements BeanPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(SlowQueryDataSourceProxyPostProcessor.class);

    /** 自动排除的 DataSource Bean 名称前缀（迁移工具使用的 DataSource）。 */
    private static final Set<String> EXCLUDED_BEAN_NAMES =
        Set.of("flywayDataSource", "liquibaseDataSource", "migrationDataSource", "schemaInitializerDataSource");

    private final long slowQueryThresholdMs;

    /**
     * 创建 DataSource 代理后处理器。
     *
     * @param slowQueryThresholdMs 慢查询阈值（毫秒）
     */
    public SlowQueryDataSourceProxyPostProcessor(long slowQueryThresholdMs) {
        this.slowQueryThresholdMs = slowQueryThresholdMs;
    }

    @Override
    public Object postProcessAfterInitialization(@NonNull Object bean, @NonNull String beanName) throws BeansException {
        if (bean instanceof DataSource ds && !SlowQueryDataSourceProxy.isWrapped(ds)
            && !java.lang.reflect.Proxy.isProxyClass(ds.getClass()) && !EXCLUDED_BEAN_NAMES.contains(beanName)) {
            log.info("Wrapping DataSource '{}' with slow query proxy (threshold={} ms)", beanName,
                slowQueryThresholdMs);
            return SlowQueryDataSourceProxy.wrap(ds, slowQueryThresholdMs);
        }
        return bean;
    }
}
