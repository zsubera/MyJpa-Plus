package com.zsubera.jpa.autoconfigure;

import com.zsubera.jpa.annotation.AuditEntityListener;
import com.zsubera.jpa.monitor.SqlSlowQueryInterceptor;
import com.zsubera.jpa.repository.DefaultMyJpaRepository;
import com.zsubera.jpa.repository.MyJpaRepositoryFactoryBean;
import com.zsubera.jpa.template.MyJpaTemplate;
import com.zsubera.jpa.util.InClauseBuilder;
import com.zsubera.jpa.util.LambdaUtils;
import com.zsubera.jpa.util.QueryTimeoutHelper;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.persistence.EntityManager;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.lang.NonNull;
import org.springframework.core.Ordered;

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
 * <li>{@code myjpa-plus.query.default-timeout-seconds} — 查询超时时间（秒），-1 禁用（默认：30）
 * <li>{@code myjpa-plus.monitoring.enabled} — 启用 SQL 慢查询监控（默认：false）
 * <li>{@code myjpa-plus.monitoring.slow-query-threshold-ms} — 慢查询阈值，单位毫秒（默认：1000）
 * <li>{@code myjpa-plus.auto-repository-base-class} — 自动注册 DefaultMyJpaRepository 为仓库基类（默认：true）
 * </ul>
 */
@AutoConfiguration
@ConditionalOnClass({EntityManager.class})
@EnableConfigurationProperties(MyJpaPlusProperties.class)
@Import({SoftDeleteFilterBean.class, MyJpaPlusAutoConfiguration.ModuleCompatibilityChecker.class,
    MyJpaPlusAutoConfiguration.MyJpaPlusConfigInitializer.class})
@SuppressFBWarnings(value = "CT_CONSTRUCTOR_THROW",
    justification = "Constructor validates parameters before assignment")
public class MyJpaPlusAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MyJpaPlusAutoConfiguration.class);

    private final MyJpaPlusProperties properties;

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2",
        justification = "AutoConfiguration stores Spring-managed properties bean; lifecycle managed by Spring container")
    public MyJpaPlusAutoConfiguration(MyJpaPlusProperties properties) {
        if (properties == null) {
            throw new IllegalArgumentException("properties must not be null");
        }
        this.properties = properties;
        log.info("MyJpa-Plus AutoConfiguration created");
    }

    /**
     * 创建全局配置 Bean，替代分散的静态可变状态。
     *
     * @param properties 配置属性
     * @return MyJpaPlusGlobalConfig 实例
     */
    @Bean
    @ConditionalOnMissingBean(MyJpaPlusGlobalConfig.class)
    public MyJpaPlusGlobalConfig myJpaPlusGlobalConfig(MyJpaPlusProperties properties) {
        MyJpaPlusGlobalConfig config = new MyJpaPlusGlobalConfig();
        config.setSoftDeleteAutoFilter(properties.getSoftDelete().isAutoFilter());
        config.setBlockUnconditionalDelete(properties.getSoftDelete().isBlockUnconditionalDelete());
        config.setDefaultTimeoutSeconds(properties.getQuery().getDefaultTimeoutSeconds());
        config.setMaxResults(properties.getQuery().getMaxResults());
        config.setMaxBulkOperationRows(properties.getQuery().getMaxBulkOperationRows());
        config.setDeepPaginationOffsetThreshold(properties.getQuery().getDeepPaginationOffsetThreshold());
        config.setDeepPaginationOffsetLimit(properties.getQuery().getDeepPaginationOffsetLimit());
        return config;
    }

    @org.springframework.context.annotation.Lazy(false)
    @org.springframework.stereotype.Component
    static class MyJpaPlusConfigInitializer {

        MyJpaPlusConfigInitializer(MyJpaPlusProperties properties,
            @org.springframework.beans.factory.annotation.Autowired(
                required = false) MyJpaPlusGlobalConfig globalConfig) {
            // 使用全局配置提供者替代静态可变状态
            if (globalConfig != null) {
                DefaultMyJpaRepository.setGlobalConfigProvider(DefaultMyJpaRepository.createMutableConfigProvider(
                    globalConfig.isSoftDeleteAutoFilter(), globalConfig.isBlockUnconditionalDelete()));
                // 通过 GlobalConfigHolder 集中管理全局配置访问
                GlobalConfigHolder.setConfig(globalConfig);
                // 向后兼容：设置 QuerySpec 的全局配置（委托给 GlobalConfigHolder）
                com.zsubera.jpa.spec.QuerySpec.setGlobalConfig(globalConfig);
            } else {
                // 向后兼容：使用旧的静态方法
                DefaultMyJpaRepository.setAutoFilterEnabled(properties.getSoftDelete().isAutoFilter());
                DefaultMyJpaRepository
                    .setBlockUnconditionalDelete(properties.getSoftDelete().isBlockUnconditionalDelete());
            }

            // 应用 IN 子句配置
            int inMax = properties.getQuery().getInClauseMaxSize();
            int inHard = properties.getQuery().getInClauseHardLimit();
            if (inHard < inMax) {
                throw new IllegalArgumentException(
                    "inClauseHardLimit (" + inHard + ") must be >= inClauseMaxSize (" + inMax + ")");
            }
            InClauseBuilder.setConfig(new InClauseBuilder.Config(inMax, inHard));

            // 应用 Lambda 缓存配置
            LambdaUtils.setMaxCacheSize(properties.getQuery().getLambdaCacheSize());

            // 应用查询超时配置
            int timeout = properties.getQuery().getDefaultTimeoutSeconds();
            if (timeout > 0 || timeout == -1) {
                QueryTimeoutHelper.setDefaultTimeoutSeconds(timeout);
            }

            if (log.isDebugEnabled()) {
                log.debug("  soft-delete.auto-filter = {}", properties.getSoftDelete().isAutoFilter());
                log.debug("  soft-delete.block-unconditional-delete = {}",
                    properties.getSoftDelete().isBlockUnconditionalDelete());
                log.debug("  query.max-results = {}", properties.getQuery().getMaxResults());
                log.debug("  query.in-clause-max-size = {}", properties.getQuery().getInClauseMaxSize());
                log.debug("  query.in-clause-hard-limit = {}", properties.getQuery().getInClauseHardLimit());
                log.debug("  query.lambda-cache-size = {}", properties.getQuery().getLambdaCacheSize());
                log.debug("  query.default-timeout-seconds = {}", properties.getQuery().getDefaultTimeoutSeconds());
            }
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

        private static void checkModuleCompatibility() {
            try {
                java.lang.reflect.Method writeReplace =
                    java.lang.invoke.SerializedLambda.class.getDeclaredMethod("writeReplace");
                writeReplace.setAccessible(true);
            } catch (NoSuchMethodException e) {
                log.warn("Unexpected: SerializedLambda.writeReplace() not found. LambdaUtils may not work correctly.");
            } catch (java.lang.reflect.InaccessibleObjectException | SecurityException e) {
                log.warn(
                    "Java module system restriction detected. LambdaUtils uses reflection on SerializedLambda.writeReplace(). "
                        + "All lambda-based property name resolution will fail at runtime. "
                        + "Fix: add this JVM argument: --add-opens java.base/java.lang.invoke=ALL-UNNAMED");
                log.warn("Without the --add-opens argument, any code using SFunction method references "
                    + "(e.g., QuerySpec, UpdateSpec, ProjectionSpec) will throw MyJpaPlusException with the fix suggestion.");
            }
        }
    }

    /**
     * 创建 AuditEntityListener Bean。
     *
     * <p>
     * 通过自动配置注册而非 {@code @Component}，避免与 JPA {@code @EntityListeners} 机制的身份混淆。 该 Bean 实现
     * {@code ApplicationContextAware}，通过静态变量桥接 Spring 上下文与 JPA 实体监听器。
     *
     * @return AuditEntityListener 实例
     */
    @Bean
    @ConditionalOnMissingBean(AuditEntityListener.class)
    public AuditEntityListener auditEntityListener() {
        return new AuditEntityListener();
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
        int timeout = properties.getQuery().getDefaultTimeoutSeconds();
        if (timeout > 0 || timeout == -1) {
            template.setDefaultTimeoutSeconds(timeout);
        }
        return template;
    }

    /**
     * 创建查询缓存管理器 Bean。
     *
     * @return QueryCacheManager 实例
     */
    @Bean
    @ConditionalOnMissingBean(com.zsubera.jpa.template.QueryCacheManager.class)
    public com.zsubera.jpa.template.QueryCacheManager queryCacheManager() {
        return new com.zsubera.jpa.template.QueryCacheManager();
    }

    /**
     * 创建缓存失效监听器 Bean，监听实体变更事件并自动清除相关查询缓存。
     *
     * @param cacheManager 查询缓存管理器
     * @return CacheInvalidationListener 实例
     */
    @Bean
    @ConditionalOnMissingBean(com.zsubera.jpa.template.CacheInvalidationListener.class)
    @ConditionalOnProperty(prefix = "myjpa-plus.cache", name = "auto-invalidation-enabled", havingValue = "true",
        matchIfMissing = true)
    public com.zsubera.jpa.template.CacheInvalidationListener
        cacheInvalidationListener(com.zsubera.jpa.template.QueryCacheManager cacheManager) {
        log.info("CacheInvalidationListener enabled — query cache will auto-invalidate on entity modification");
        return new com.zsubera.jpa.template.CacheInvalidationListener(cacheManager);
    }

    /**
     * 创建 SQL 慢查询拦截器 Bean。
     *
     * @param properties 配置属性
     * @return SqlSlowQueryInterceptor 实例
     */
    @Bean
    @ConditionalOnMissingBean(SqlSlowQueryInterceptor.class)
    @ConditionalOnProperty(prefix = "myjpa-plus.monitoring", name = "enabled", havingValue = "true")
    public SqlSlowQueryInterceptor sqlSlowQueryInterceptor(MyJpaPlusProperties properties) {
        long threshold = properties.getMonitoring().getSlowQueryThresholdMs();
        log.info("SqlSlowQueryInterceptor enabled (threshold={} ms)", threshold);
        return new SqlSlowQueryInterceptor(threshold);
    }

    /**
     * 用于在监控启用时将 DataSource 包装为慢查询代理的 BeanPostProcessor。
     */
    @Bean
    @ConditionalOnProperty(prefix = "myjpa-plus.monitoring", name = "enabled", havingValue = "true")
    public BeanPostProcessor dataSourceSlowQueryProxyPostProcessor(SqlSlowQueryInterceptor interceptor) {
        return new DataSourceSlowQueryProxyPostProcessor(interceptor);
    }

    static class DataSourceSlowQueryProxyPostProcessor implements BeanPostProcessor {

        private final SqlSlowQueryInterceptor interceptor;

        @SuppressFBWarnings("EI_EXPOSE_REP2")
        DataSourceSlowQueryProxyPostProcessor(SqlSlowQueryInterceptor interceptor) {
            this.interceptor = interceptor;
        }

        @Override
        public Object postProcessAfterInitialization(@NonNull Object bean, @NonNull String beanName) {
            if (bean instanceof DataSource ds && !java.lang.reflect.Proxy.isProxyClass(ds.getClass())
                && !isAlreadyWrapped(ds)) {
                return interceptor.wrapDataSource(ds);
            }
            return bean;
        }

        /**
         * 检查 DataSource 是否已被 SqlSlowQueryInterceptor 包装。 通过检查 InvocationHandler 类型来避免双重包装。
         */
        private static boolean isAlreadyWrapped(DataSource ds) {
            if (java.lang.reflect.Proxy.isProxyClass(ds.getClass())) {
                java.lang.reflect.InvocationHandler handler = java.lang.reflect.Proxy.getInvocationHandler(ds);
                return handler instanceof com.zsubera.jpa.monitor.SqlSlowQueryInterceptor.DataSourceProxyHandler;
            }
            return false;
        }
    }

    /**
     * 自动注册 {@link MyJpaRepositoryFactoryBean} 作为所有仓库的默认 FactoryBean。
     *
     * <p>
     * 通过 {@link BeanDefinitionRegistryPostProcessor} 在 Spring 启动阶段自动修改仓库 Bean 定义，
     * 无需用户手动配置 {@code @EnableJpaRepositories(repositoryFactoryBeanClass = ...)}。
     *
     * @return RepositoryBaseClassPostProcessor 实例
     */
    @Bean
    @ConditionalOnMissingBean(RepositoryBaseClassPostProcessor.class)
    @ConditionalOnProperty(prefix = "myjpa-plus", name = "auto-repository-base-class", havingValue = "true",
        matchIfMissing = true)
    static RepositoryBaseClassPostProcessor repositoryBaseClassPostProcessor() {
        return new RepositoryBaseClassPostProcessor();
    }

    /**
     * 应用关闭时清理 LambdaUtils 后台缓存清理线程，防止在 OSGi 或热部署环境中导致类加载器泄漏。
     *
     * @param event 上下文关闭事件
     */
    @EventListener(ContextClosedEvent.class)
    public void onContextClosed(ContextClosedEvent event) {
        LambdaUtils.shutdown();
        com.zsubera.jpa.converter.EncryptConverter.clearCaches();
        com.zsubera.jpa.converter.EncryptConverter.removeCipher();
        com.zsubera.jpa.softdelete.SoftDeleteHelper.shutdown();
        DefaultMyJpaRepository.clearThreadLocal();
        com.zsubera.jpa.repository.SoftDeleteContext.reset();
        log.info("MyJpa-Plus context closed, caches cleaned");
    }

    /**
     * {@link BeanDefinitionRegistryPostProcessor}，自动为所有仓库 Bean 定义注入
     * {@link MyJpaRepositoryFactoryBean} 作为 {@code repositoryFactoryBeanClass}。
     *
     * <p>
     * 此处理器仅在用户未手动指定 {@code repositoryFactoryBeanClass} 时生效，
     * 保留用户的自定义配置优先级。
     *
     * @see MyJpaRepositoryFactoryBean
     */
    static class RepositoryBaseClassPostProcessor implements BeanDefinitionRegistryPostProcessor, Ordered {

        private static final Logger postProcessorLog = LoggerFactory.getLogger(RepositoryBaseClassPostProcessor.class);

        @Override
        public int getOrder() {
            return Ordered.LOWEST_PRECEDENCE;
        }

        @Override
        public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
            String factoryBeanClassName = MyJpaRepositoryFactoryBean.class.getName();
            for (String beanName : registry.getBeanDefinitionNames()) {
                var bd = registry.getBeanDefinition(beanName);
                // Repository Factory Bean 都有 "repositoryInterface" 属性
                if (bd.getPropertyValues().contains("repositoryInterface")
                    && !bd.getPropertyValues().contains("repositoryFactoryBeanClass")) {
                    bd.getPropertyValues().add("repositoryFactoryBeanClass", factoryBeanClassName);
                    if (postProcessorLog.isDebugEnabled()) {
                        postProcessorLog.debug("Auto-registered MyJpaRepositoryFactoryBean for repository: {}",
                            beanName);
                    }
                }
            }
        }

        @Override
        public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
            // No-op — 所有修改在 postProcessBeanDefinitionRegistry 中完成
        }
    }
}
