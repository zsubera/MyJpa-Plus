package com.zsubera.jpa.autoconfigure;

import com.zsubera.jpa.repository.SoftDeleteJpaRepository;
import com.zsubera.jpa.template.MyJpaTemplate;
import com.zsubera.jpa.util.InClauseBuilder;
import com.zsubera.jpa.util.LambdaUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.persistence.EntityManager;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;

/**
 * MyJpa-Plus 的自动配置类。
 *
 * <p>
 * 当 Spring Data JPA 和 {@link EntityManager} 在类路径上时自动激活。 通过 {@link Import} 显式注册所有 MyJpa-Plus 的 Bean，并启用
 * {@link MyJpaPlusProperties} 进行外部配置。
 *
 * <p>
 * 配置选项（前缀：{@code myjpa-plus}）：
 *
 * <ul>
 * <li>{@code myjpa-plus.soft-delete.auto-filter} — 自动应用软删除过滤器（默认：true）
 * <li>{@code myjpa-plus.query.max-results} — 查询最大返回行数（默认：10000）
 * <li>{@code myjpa-plus.query.deep-pagination-offset-threshold} — 深度分页警告阈值（默认：100000）
 * <li>{@code myjpa-plus.query.in-clause-max-size} — IN 子句最大参数数量（默认：1000）
 * <li>{@code myjpa-plus.query.in-clause-hard-limit} — IN 子句硬限制（默认：5000）
 * <li>{@code myjpa-plus.query.lambda-cache-size} — Lambda 缓存大小（默认：4096）
 * </ul>
 */
@AutoConfiguration
@ConditionalOnClass({EntityManager.class})
@EnableConfigurationProperties(MyJpaPlusProperties.class)
@Import({SoftDeleteFilterBean.class, MyJpaPlusAutoConfiguration.ModuleCompatibilityChecker.class})
@SuppressFBWarnings(value = "CT_CONSTRUCTOR_THROW",
    justification = "Constructor validates parameters before assignment")
public class MyJpaPlusAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MyJpaPlusAutoConfiguration.class);

    public MyJpaPlusAutoConfiguration(MyJpaPlusProperties properties) {
        if (properties == null) {
            throw new IllegalArgumentException("properties must not be null");
        }
        // 将 auto-filter 配置同步到 SoftDeleteJpaRepository 的静态标志，确保 Repository 层面行为一致
        SoftDeleteJpaRepository.setAutoFilterEnabled(properties.getSoftDelete().isAutoFilter());

        // 应用 IN 子句配置
        InClauseBuilder.setMaxInClauseSize(properties.getQuery().getInClauseMaxSize());
        InClauseBuilder.setHardLimit(properties.getQuery().getInClauseHardLimit());

        // 应用 Lambda 缓存配置
        LambdaUtils.setMaxCacheSize(properties.getQuery().getLambdaCacheSize());

        log.info("MyJpa-Plus AutoConfiguration initialized");
        if (log.isDebugEnabled()) {
            log.debug("  soft-delete.auto-filter = {}", properties.getSoftDelete().isAutoFilter());
            log.debug("  query.max-results = {}", properties.getQuery().getMaxResults());
            log.debug("  query.deep-pagination-offset-threshold = {}",
                properties.getQuery().getDeepPaginationOffsetThreshold());
            log.debug("  query.deep-pagination-offset-limit = {}",
                properties.getQuery().getDeepPaginationOffsetLimit());
            log.debug("  query.in-clause-max-size = {}", properties.getQuery().getInClauseMaxSize());
            log.debug("  query.in-clause-hard-limit = {}", properties.getQuery().getInClauseHardLimit());
            log.debug("  query.lambda-cache-size = {}", properties.getQuery().getLambdaCacheSize());
        }
    }

    /**
     * 初始化后检查模块兼容性。
     */
    @org.springframework.context.annotation.Lazy(false)
    @org.springframework.stereotype.Component
    static class ModuleCompatibilityChecker {

        @jakarta.annotation.PostConstruct
        public void check() {
            checkModuleCompatibility();
        }

        /**
         * 检测 Java 17+ 模块系统兼容性。
         *
         * <p>
         * LambdaUtils 通过反射调用 {@code SerializedLambda.writeReplace()} 并使用 {@code setAccessible(true)}。 在 Java 17+
         * 的强封装模块系统下，此操作可能因缺少 {@code --add-opens} 参数而失败。 此方法在启动时检测并给出明确的警告信息。
         */
        private static void checkModuleCompatibility() {
            try {
                Method writeReplace = SerializedLambda.class.getDeclaredMethod("writeReplace");
                writeReplace.setAccessible(true);
            } catch (NoSuchMethodException e) {
                // 不可能发生：writeReplace 是 SerializedLambda 的固有方法
                log.warn("Unexpected: SerializedLambda.writeReplace() not found. LambdaUtils may not work correctly.");
            } catch (SecurityException e) {
                log.warn(
                    "Java module system restriction detected. LambdaUtils uses reflection on SerializedLambda.writeReplace() "
                        + "which may fail at runtime. If you encounter InaccessibleObjectException, add this JVM argument: "
                        + "--add-opens java.base/java.lang.invoke=ALL-UNNAMED");
            }
        }
    }

    /**
     * 创建配置了自定义参数的 MyJpaTemplate Bean。
     *
     * @param properties 配置属性
     * @return MyJpaTemplate 实例
     */
    @Bean
    @ConditionalOnMissingBean(MyJpaTemplate.class)
    public MyJpaTemplate myJpaTemplate(MyJpaPlusProperties properties) {
        MyJpaTemplate template = new MyJpaTemplate(properties.getQuery().getMaxResults(),
            properties.getQuery().getDeepPaginationOffsetThreshold());
        int limit = properties.getQuery().getDeepPaginationOffsetLimit();
        if (limit > 0) {
            template.setDeepPaginationOffsetLimit(limit);
        }
        return template;
    }

    /**
     * 应用关闭时清理 LambdaUtils 后台缓存清理线程，防止在 OSGi 或热部署环境中导致类加载器泄漏。
     *
     * @param event 上下文关闭事件
     */
    @EventListener(ContextClosedEvent.class)
    public void onContextClosed(ContextClosedEvent event) {
        LambdaUtils.shutdown();
        log.info("MyJpa-Plus context closed (LambdaUtils.shutdown is no-op with LRU cache)");
    }
}
