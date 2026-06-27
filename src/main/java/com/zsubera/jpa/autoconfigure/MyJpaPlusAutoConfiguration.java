package com.zsubera.jpa.autoconfigure;

import com.zsubera.jpa.monitor.SlowQueryDataSourceProxyPostProcessor;
import com.zsubera.jpa.repository.DefaultMyJpaRepository;
import com.zsubera.jpa.repository.MyJpaRepositoryFactoryBean;
import com.zsubera.jpa.template.CacheAdapter;
import com.zsubera.jpa.template.MyJpaTemplate;
import com.zsubera.jpa.template.MyJpaTemplateOperations;
import com.zsubera.jpa.util.InClauseBuilder;
import com.zsubera.jpa.util.LambdaUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
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
import org.springframework.core.Ordered;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

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
 * <li>{@code spring.jpa.properties.jakarta.persistence.query.timeout} — 查询超时时间（毫秒），Spring Boot 标准属性
 * <li>{@code myjpa-plus.auto-repository-base-class} — 自动注册 DefaultMyJpaRepository 为仓库基类（默认：true）
 * </ul>
 */
@AutoConfiguration
@ConditionalOnClass({EntityManager.class})
@EnableConfigurationProperties(MyJpaPlusProperties.class)
@Import({SoftDeleteFilterBean.class, MyJpaPlusAutoConfiguration.ModuleCompatibilityChecker.class,
    MyJpaPlusAutoConfiguration.MyJpaPlusConfigInitializer.class, MyJpaPlusAutoConfiguration.JpaAuditingConfig.class})
@SuppressFBWarnings(value = "CT_CONSTRUCTOR_THROW",
    justification = "Constructor validates parameters before assignment")
public class MyJpaPlusAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MyJpaPlusAutoConfiguration.class);

    private final MyJpaPlusProperties properties;

    private final org.springframework.context.ApplicationContext applicationContext;

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2",
        justification = "AutoConfiguration stores Spring-managed properties bean; lifecycle managed by Spring container")
    public MyJpaPlusAutoConfiguration(MyJpaPlusProperties properties,
        @org.springframework.beans.factory.annotation.Autowired(
            required = false) org.springframework.context.ApplicationContext applicationContext) {
        if (properties == null) {
            throw new IllegalArgumentException("properties must not be null");
        }
        this.properties = properties;
        this.applicationContext = applicationContext;
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
        config.setMaxResults(properties.getQuery().getMaxResults());
        config.setMaxBulkOperationRows(properties.getQuery().getMaxBulkOperationRows());
        config.setDeepPaginationOffsetThreshold(properties.getQuery().getDeepPaginationOffsetThreshold());
        config.setDeepPaginationOffsetLimit(properties.getQuery().getDeepPaginationOffsetLimit());
        config.setInClauseMaxSize(properties.getQuery().getInClauseMaxSize());
        config.setInClauseHardLimit(properties.getQuery().getInClauseHardLimit());
        config.setLambdaCacheSize(properties.getQuery().getLambdaCacheSize());
        config.setCacheMaxEntries(properties.getCache().getMaxEntries());
        return config;
    }

    @org.springframework.context.annotation.Lazy(false)
    @org.springframework.stereotype.Component
    static class MyJpaPlusConfigInitializer {

        MyJpaPlusConfigInitializer(MyJpaPlusProperties properties,
            @org.springframework.beans.factory.annotation.Autowired(
                required = false) MyJpaPlusGlobalConfig globalConfig,
            @org.springframework.beans.factory.annotation.Autowired(
                required = false) org.springframework.context.ApplicationContext applicationContext) {
            // 使用全局配置提供者替代静态可变状态
            if (globalConfig != null) {
                DefaultMyJpaRepository.setGlobalConfigProvider(DefaultMyJpaRepository.createMutableConfigProvider(
                    globalConfig.isSoftDeleteAutoFilter(), globalConfig.isBlockUnconditionalDelete()));
                    // 通过 GlobalConfigHolder 集中管理全局配置访问
                GlobalConfigHolder.setApplicationContext(applicationContext);
                GlobalConfigHolder.setConfig(globalConfig);
            }

            // 注册 SoftDeleteHelper 事件发布回调，支持批量软删除操作后的缓存自动失效
            if (applicationContext != null) {
                com.zsubera.jpa.softdelete.SoftDeleteHelper.setEventPublisher((entityClass, affectedRows) -> {
                    applicationContext
                        .publishEvent(new com.zsubera.jpa.template.EntityModifiedEvent(entityClass, affectedRows));
                });
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

            // 应用额外函数白名单配置
            java.util.List<String> extraSafe = properties.getQuery().getExtraSafeFunctions();
            if (extraSafe != null && !extraSafe.isEmpty()) {
                com.zsubera.jpa.spec.FunctionWhitelist.addSafeFunctionNames(extraSafe);
                log.info("Added {} extra safe functions to whitelist", extraSafe.size());
            }
            java.util.List<String> extraBool = properties.getQuery().getExtraBooleanFunctions();
            if (extraBool != null && !extraBool.isEmpty()) {
                com.zsubera.jpa.spec.FunctionWhitelist.addBooleanFunctionNames(extraBool);
                log.info("Added {} extra boolean functions to whitelist", extraBool.size());
            }

            if (log.isDebugEnabled()) {
                log.debug("  soft-delete.auto-filter = {}", properties.getSoftDelete().isAutoFilter());
                log.debug("  soft-delete.block-unconditional-delete = {}",
                    properties.getSoftDelete().isBlockUnconditionalDelete());
                log.debug("  query.max-results = {}", properties.getQuery().getMaxResults());
                log.debug("  query.in-clause-max-size = {}", properties.getQuery().getInClauseMaxSize());
                log.debug("  query.in-clause-hard-limit = {}", properties.getQuery().getInClauseHardLimit());
                log.debug("  query.lambda-cache-size = {}", properties.getQuery().getLambdaCacheSize());
            }

            // 自动预热加密密钥缓存（仅在密钥已配置时）
            String encryptKey = EnvironmentHelper.getEnvOrProperty("MYJPA_ENCRYPT_KEY", "myjpa.encrypt.key");
            if (encryptKey != null && !encryptKey.isEmpty()) {
                // 启动时检查盐值配置，防止生产环境使用不安全的开发盐值
                validateEncryptionSalt();
                try {
                    com.zsubera.jpa.converter.EncryptConverter.warmUpKeyCache();
                    log.info("EncryptConverter key warmup started in background");
                } catch (Exception e) {
                    log.warn("Failed to warm up EncryptConverter key cache: {}", e.getMessage());
                }
            }
        }

        /**
         * 启动时验证加密盐值配置，防止生产环境使用不安全的开发盐值。
         *
         * <p>检查逻辑：</p>
         * <ol>
         *   <li>如果已配置盐值（环境变量或系统属性），直接通过</li>
         *   <li>如果未配置盐值，在生产环境直接拒绝启动</li>
         *   <li>如果未配置盐值，在开发/测试环境记录警告</li>
         * </ol>
         */
        private void validateEncryptionSalt() {
            String salt = System.getenv("MYJPA_ENCRYPT_SALT");
            if (salt == null || salt.isEmpty()) {
                salt = System.getProperty("myjpa.encrypt.salt");
            }
            if (salt != null && !salt.isEmpty()) {
                return; // 盐值已配置，安全
            }

            // 盐值未配置 — 检查是否为生产环境
            if (isProductionEnvironment()) {
                throw new IllegalStateException(
                    "CRITICAL: PBKDF2 salt must be configured for encryption in production. "
                        + "Set environment variable MYJPA_ENCRYPT_SALT or system property myjpa.encrypt.salt "
                        + "before starting the application.");
            }

            // 开发/测试环境 — 记录警告但不阻止启动
            log.warn("SECURITY: PBKDF2 salt not configured. "
                + "Encrypted data will use a random salt per JVM startup — data WILL NOT BE RECOVERABLE after restart. "
                + "Set environment variable MYJPA_ENCRYPT_SALT or system property myjpa.encrypt.salt for production.");
        }

        /**
         * 检查当前是否为生产环境。
         * <p>
         * 委托给 {@link EnvironmentHelper#isProductionEnvironment()}，
         * 消除与 {@link com.zsubera.jpa.converter.EncryptConverter} 之间的重复代码。
         */
        private static boolean isProductionEnvironment() {
            return EnvironmentHelper.isProductionEnvironment();
        }
    }

    /**
     * 初始化后检查模块兼容性。
     */
    @org.springframework.context.annotation.Lazy(false)
    @org.springframework.stereotype.Component
    static class ModuleCompatibilityChecker {

        private static final String ADD_OPENS_ARG = "--add-opens java.base/java.lang.invoke=ALL-UNNAMED";

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
                log.error("=".repeat(80));
                log.error("MyJpa-Plus: Java module system restriction detected!");
                log.error("=".repeat(80));
                log.error("");
                log.error("LambdaUtils uses reflection on SerializedLambda.writeReplace() to extract");
                log.error("property names from method references (e.g., User::getName). Without the");
                log.error("required JVM argument, ALL lambda-based queries will fail at runtime.");
                log.error("");
                log.error("Fix: Add this JVM argument to your application:");
                log.error("");
                log.error("  {}", ADD_OPENS_ARG);
                log.error("");
                log.error("How to apply the fix:");
                log.error("");
                log.error("  [Maven - spring-boot-maven-plugin]");
                log.error("  <plugin>");
                log.error("      <groupId>org.springframework.boot</groupId>");
                log.error("      <artifactId>spring-boot-maven-plugin</artifactId>");
                log.error("      <configuration>");
                log.error("          <jvmArguments>");
                log.error("              {}", ADD_OPENS_ARG);
                log.error("          </jvmArguments>");
                log.error("      </configuration>");
                log.error("  </plugin>");
                log.error("");
                log.error("  [Gradle]");
                log.error("  bootRun {");
                log.error("      jvmArgs '{}'", ADD_OPENS_ARG);
                log.error("  }");
                log.error("");
                log.error("  [Command Line]");
                log.error("  java {} -jar your-app.jar", ADD_OPENS_ARG);
                log.error("");
                log.error("  [Environment Variable]");
                log.error("  JAVA_TOOL_OPTIONS=\"{}\"", ADD_OPENS_ARG);
                log.error("");
                log.error("=".repeat(80));
            }
        }
    }

    /**
     * 创建默认的 {@link AuditorAware} Bean，用于 Spring Data JPA 审计功能。
     *
     * <p>
     * 如果类路径上有 Spring Security，优先从 {@code SecurityContextHolder} 获取当前用户名；
     * 否则返回 {@code "SYSTEM"}。用户可提供自定义 {@code AuditorAware<String>} Bean 覆盖。
     *
     * @return AuditorAware 实例
     */
    @Bean
    @ConditionalOnMissingBean(AuditorAware.class)
    public AuditorAware<String> auditorAware() {
        return new SecurityContextAuditorAware();
    }

    /**
     * 基于 Spring Security SecurityContextHolder 的默认 AuditorAware 实现。
     *
     * <p>
     * 使用反射访问 SecurityContextHolder，避免编译时依赖 Spring Security。
     */
    static class SecurityContextAuditorAware implements AuditorAware<String> {

        private static final org.slf4j.Logger secLog =
            org.slf4j.LoggerFactory.getLogger(SecurityContextAuditorAware.class);

        private static volatile java.lang.reflect.Method getContextMethod;
        private static volatile java.lang.reflect.Method getAuthenticationMethod;
        private static volatile java.lang.reflect.Method isAuthenticatedMethod;
        private static volatile java.lang.reflect.Method getNameMethod;
        private static volatile boolean securityChecked;

        @Override
        public java.util.Optional<String> getCurrentAuditor() {
            if (!securityChecked) {
                initSecurityReflection();
            }
            if (getContextMethod == null) {
                return java.util.Optional.of("SYSTEM");
            }
            try {
                Object context = getContextMethod.invoke(null);
                Object auth = getAuthenticationMethod != null ? getAuthenticationMethod.invoke(context) : null;
                if (auth != null && isAuthenticatedMethod != null) {
                    Boolean authenticated = (Boolean)isAuthenticatedMethod.invoke(auth);
                    if (Boolean.TRUE.equals(authenticated) && getNameMethod != null) {
                        return java.util.Optional.of((String)getNameMethod.invoke(auth));
                    }
                }
            } catch (Exception e) {
                secLog.debug("Could not get user from SecurityContext: {}", e.getMessage());
            }
            return java.util.Optional.of("SYSTEM");
        }

        private static synchronized void initSecurityReflection() {
            if (securityChecked) {
                return;
            }
            try {
                Class<?> shc = Class.forName("org.springframework.security.core.context.SecurityContextHolder");
                getContextMethod = shc.getMethod("getContext");
                Class<?> ctxClass = Class.forName("org.springframework.security.core.context.SecurityContext");
                getAuthenticationMethod = ctxClass.getMethod("getAuthentication");
                Class<?> authClass = Class.forName("org.springframework.security.core.Authentication");
                isAuthenticatedMethod = authClass.getMethod("isAuthenticated");
                getNameMethod = authClass.getMethod("getName");
                secLog.info("Spring Security detected — AuditorAware will use SecurityContext");
            } catch (Exception e) {
                secLog.debug("Spring Security not available on classpath");
            }
            securityChecked = true;
        }
    }

    /**
     * 创建配置了自定义参数的 MyJpaTemplate Bean。
     *
     * @param properties 配置属性
     * @return MyJpaTemplate 实例
     */
    @Bean
    @ConditionalOnMissingBean(MyJpaTemplateOperations.class)
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
     * 创建查询缓存管理器 Bean。
     *
     * <p>
     * 默认使用基于 ConcurrentHashMap 的本地缓存实现。用户可通过提供自定义
     * {@link com.zsubera.jpa.template.QueryCacheManager} Bean 来替换。
     *
     * @param properties 配置属性，用于读取 cache.maxEntries 配置
     * @return QueryCacheManager 实例
     */
    @Bean
    @ConditionalOnMissingBean(com.zsubera.jpa.template.QueryCacheManager.class)
    public com.zsubera.jpa.template.QueryCacheManager queryCacheManager(MyJpaPlusProperties properties) {
        int maxEntries = properties.getCache().getMaxEntries();
        return new com.zsubera.jpa.template.QueryCacheManager(maxEntries);
    }

    /**
     * 创建缓存适配器 Bean。默认使用 {@link com.zsubera.jpa.template.QueryCacheManager}。
     *
     * <p>
     * 用户可通过提供自定义 {@link com.zsubera.jpa.template.CacheAdapter} Bean 来替换为
     * Redis、Caffeine 等分布式或近端缓存实现。
     *
     * @param cacheManager 查询缓存管理器（可能为 null，如果用户提供了自定义 CacheAdapter）
     * @return CacheAdapter 实例
     */
    @Bean
    @ConditionalOnMissingBean(com.zsubera.jpa.template.CacheAdapter.class)
    public com.zsubera.jpa.template.CacheAdapter cacheAdapter(@org.springframework.beans.factory.annotation.Autowired(
        required = false) com.zsubera.jpa.template.QueryCacheManager cacheManager) {
        if (cacheManager != null) {
            return cacheManager;
        }
        return com.zsubera.jpa.template.CacheAdapter.disabled();
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
        cacheInvalidationListener(com.zsubera.jpa.template.CacheAdapter cacheAdapter) {
        log.info("CacheInvalidationListener enabled — query cache will auto-invalidate on entity modification");
        return new com.zsubera.jpa.template.CacheInvalidationListener(cacheAdapter);
    }

    /**
     * 用于在监控启用时将 DataSource 包装为慢查询代理的 BeanPostProcessor。
     *
     * <p>
     * 此处理器不依赖 Hibernate，可与任何 JPA 实现配合使用。
     * 使用 {@link com.zsubera.jpa.monitor.SlowQueryDataSourceProxy} 进行 DataSource 包装。
     */
    @Bean
    @ConditionalOnMissingBean(SlowQueryDataSourceProxyPostProcessor.class)
    @ConditionalOnProperty(prefix = "myjpa-plus.monitoring", name = "enabled", havingValue = "true")
    public SlowQueryDataSourceProxyPostProcessor slowQueryDataSourceProxyPostProcessor(MyJpaPlusProperties properties) {
        long threshold = properties.getMonitoring().getSlowQueryThresholdMs();
        log.info("SlowQueryDataSourceProxyPostProcessor enabled (threshold={} ms)", threshold);
        return new SlowQueryDataSourceProxyPostProcessor(threshold);
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
     * 应用关闭时清理所有组件资源，防止在 OSGi 或热部署环境中导致类加载器泄漏。
     *
     * <p>
     * 每个组件的清理操作独立注册，单个清理失败不影响其他组件。
     *
     * @param event 上下文关闭事件
     */
    @EventListener(ContextClosedEvent.class)
    public void onContextClosed(ContextClosedEvent event) {
        CleanupRegistry registry = new CleanupRegistry();
        registry.register("LambdaUtils", LambdaUtils::shutdown);
        registry.register("EncryptConverter cache", com.zsubera.jpa.converter.EncryptConverter::clearCaches);
        registry.register("EncryptConverter cipher", com.zsubera.jpa.converter.EncryptConverter::removeCipher);
        registry.register("SoftDeleteHelper", com.zsubera.jpa.softdelete.SoftDeleteHelper::shutdown);
        registry.register("DefaultMyJpaRepository", DefaultMyJpaRepository::clearThreadLocal);
        registry.register("SoftDeleteContext", com.zsubera.jpa.repository.SoftDeleteContext::reset);
        registry.register("EntityManagerHelper", com.zsubera.jpa.repository.EntityManagerHelper::reset);
        registry.register("SoftDeleteHelper event publisher",
            () -> com.zsubera.jpa.softdelete.SoftDeleteHelper.setEventPublisher(null));
        if (applicationContext != null) {
            registry.register("CacheAdapter", () -> {
                CacheAdapter cacheAdapter = applicationContext.getBean(CacheAdapter.class);
                cacheAdapter.close();
            });
        }
        registry.executeAll();
        log.info("MyJpa-Plus context closed, caches cleaned");
    }

    /**
     * 组件清理注册表，收集多个清理操作并逐一执行。
     * 单个清理失败不会阻止其他清理操作执行。
     */
    private static class CleanupRegistry {
        private final java.util.List<java.util.Map.Entry<String, Runnable>> actions = new java.util.ArrayList<>();

        void register(String name, Runnable action) {
            actions.add(java.util.Map.entry(name, action));
        }

        void executeAll() {
            for (var entry : actions) {
                try {
                    entry.getValue().run();
                } catch (Exception e) {
                    log.warn("{} cleanup failed", entry.getKey(), e);
                }
            }
        }
    }

    /**
     * {@link BeanDefinitionRegistryPostProcessor}，自动将所有仓库的 FactoryBean 替换为
     * {@link MyJpaRepositoryFactoryBean}。
     *
     * <p>
     * 此处理器仅在用户未手动指定 {@code repositoryFactoryBeanClass}（即 bean class 仍为默认的
     * {@code JpaRepositoryFactoryBean}）时生效，保留用户的自定义配置优先级。
     *
     * @see MyJpaRepositoryFactoryBean
     */
    static class RepositoryBaseClassPostProcessor implements BeanDefinitionRegistryPostProcessor, Ordered {

        private static final Logger postProcessorLog = LoggerFactory.getLogger(RepositoryBaseClassPostProcessor.class);

        private static final String DEFAULT_FACTORY_BEAN =
            "org.springframework.data.jpa.repository.support.JpaRepositoryFactoryBean";

        @Override
        public int getOrder() {
            return Ordered.LOWEST_PRECEDENCE;
        }

        @Override
        public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
            String factoryBeanClassName = MyJpaRepositoryFactoryBean.class.getName();
            for (String beanName : registry.getBeanDefinitionNames()) {
                var bd = registry.getBeanDefinition(beanName);
                if (!bd.getPropertyValues().contains("repositoryInterface")) {
                    continue;
                }
                // 仅当 bean class 仍为默认值（未通过 @EnableJpaRepositories 手动指定）时替换
                String currentClass = bd.getBeanClassName();
                if (DEFAULT_FACTORY_BEAN.equals(currentClass)) {
                    bd.setBeanClassName(factoryBeanClassName);
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

    /**
     * 条件性启用 JPA 审计。仅在用户未自行配置 {@code @EnableJpaAuditing} 时生效。
     * 检测方式：如果容器中已存在 auditing 相关 Bean（如 AuditingHandler），则跳过。
     */
    @org.springframework.context.annotation.Configuration
    @EnableJpaAuditing
    @ConditionalOnMissingBean(name = "auditingHandler")
    static class JpaAuditingConfig {
    }
}
