package com.zsubera.jpa.autoconfigure;

import com.zsubera.jpa.converter.EncryptConverter;
import com.zsubera.jpa.monitor.SlowQueryDataSourceProxy;
import com.zsubera.jpa.monitor.SlowQueryDataSourceProxyPostProcessor;
import com.zsubera.jpa.monitor.SlowQueryListener;
import com.zsubera.jpa.repository.DefaultMyJpaRepository;
import com.zsubera.jpa.repository.EntityManagerHelper;
import com.zsubera.jpa.repository.MyJpaRepositoryFactoryBean;
import com.zsubera.jpa.repository.SoftDeleteContext;
import com.zsubera.jpa.softdelete.SoftDeleteBulkExecutor;
import com.zsubera.jpa.softdelete.SoftDeleteHelper;
import com.zsubera.jpa.spec.FunctionWhitelist;
import com.zsubera.jpa.template.*;
import com.zsubera.jpa.util.InClauseBuilder;
import com.zsubera.jpa.util.LambdaUtils;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.stereotype.Component;

import java.lang.invoke.SerializedLambda;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
    MyJpaPlusAutoConfiguration.MyJpaPlusConfigInitializer.class, MyJpaPlusAutoConfiguration.JpaAuditingConfig.class,
    MyJpaPlusAutoConfiguration.SlowQueryListenerRegistrar.class})
public class MyJpaPlusAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MyJpaPlusAutoConfiguration.class);

    /** 缓存 CacheAdapter 引用，避免 ContextClosedEvent 中 getBean() 死锁 */
    private static volatile CacheAdapter cachedCacheAdapter;

    private final MyJpaPlusProperties properties;

    private final ApplicationContext applicationContext;

    public MyJpaPlusAutoConfiguration(MyJpaPlusProperties properties, ApplicationContext applicationContext) {
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
        config.setMaxUpsertBatchIterations(properties.getQuery().getMaxUpsertBatchIterations());
        return config;
    }

    public MyJpaPlusProperties getProperties() {
        return properties;
    }

    @Lazy(false)
    @Component
    static class MyJpaPlusConfigInitializer {

        MyJpaPlusConfigInitializer(MyJpaPlusProperties properties,
            @Autowired(required = false) MyJpaPlusGlobalConfig globalConfig, ApplicationContext applicationContext) {
            // 使用全局配置提供者替代静态可变状态
            if (globalConfig != null) {
                DefaultMyJpaRepository.setGlobalConfigProvider(DefaultMyJpaRepository.createMutableConfigProvider(
                    globalConfig.isSoftDeleteAutoFilter(), globalConfig.isBlockUnconditionalDelete()));
                // 通过 GlobalConfigHolder 集中管理全局配置访问
                GlobalConfigHolder.setApplicationContext(applicationContext);
                GlobalConfigHolder.setConfig(globalConfig);
            }

            // 提前注册 ApplicationContext，使 EntityManagerHelper 可在 MyJpaRepositoryFactoryBean
            // 创建之前按需从容器解析 EMF，修复 execute() 默认方法的初始化时序问题
            if (applicationContext != null) {
                EntityManagerHelper.setApplicationContext(applicationContext);
            }

            // 注册 SoftDeleteHelper 事件发布回调，支持批量软删除操作后的缓存自动失效。
            // 使用懒解析模式：publishEvent 时才从 ApplicationContext 获取事件发布者，
            // 确保 CacheInvalidationListener 等监听器 bean 已注册。
            if (applicationContext != null) {
                SoftDeleteBulkExecutor.setEventPublisher((entityClass, affectedRows) -> {
                    try {
                        org.springframework.context.ApplicationEventPublisher publisher =
                            applicationContext.getBean(org.springframework.context.ApplicationEventPublisher.class);
                        publisher.publishEvent(new EntityModifiedEvent(entityClass, affectedRows));
                    } catch (Exception e) {
                        log.debug("Failed to publish EntityModifiedEvent: {}", e.getMessage());
                    }
                });
            }

            // 应用 IN 子句配置（验证已在 MyJpaPlusProperties.validate() 中完成）
            int inMax = properties.getQuery().getInClauseMaxSize();
            int inHard = properties.getQuery().getInClauseHardLimit();
            InClauseBuilder.setConfig(new InClauseBuilder.Config(inMax, inHard));

            // 应用 Lambda 缓存配置
            LambdaUtils.setMaxCacheSize(properties.getQuery().getLambdaCacheSize());

            // 应用 PBKDF2 迭代次数配置
            EncryptConverter.setPbkdf2Iterations(properties.getQuery().getPbkdf2Iterations());

            // 应用跳过盐值检查配置：从 Environment 读取（支持 application.yml）
            if (applicationContext != null) {
                // 注入 Spring Environment 的生产环境标志，用于检测 application.yml 中的 prod profile
                org.springframework.core.env.Environment env = applicationContext.getEnvironment();
                String[] activeProfiles = env.getActiveProfiles();
                boolean isProduction = false;
                for (String profile : activeProfiles) {
                    if (EnvironmentHelper.isProdProfile(profile)) {
                        isProduction = true;
                        break;
                    }
                }
                EncryptConverter.setSpringProductionEnvironment(isProduction);

                String skipSalt = env.getProperty("myjpa-plus.encrypt.skip-salt-check");
                if ("true".equalsIgnoreCase(skipSalt)) {
                    EncryptConverter.setSkipSaltCheck(true);
                    log.warn("SECURITY: Encryption salt check is DISABLED via "
                        + "myjpa-plus.encrypt.skip-salt-check=true. " + "This is unsafe for production environments.");
                }
            }

            // 应用额外函数白名单配置
            List<String> extraSafe = properties.getQuery().getExtraSafeFunctions();
            if (extraSafe != null && !extraSafe.isEmpty()) {
                FunctionWhitelist.addSafeFunctionNames(extraSafe);
                log.info("Added {} extra safe functions to whitelist", extraSafe.size());
            }
            List<String> extraBool = properties.getQuery().getExtraBooleanFunctions();
            if (extraBool != null && !extraBool.isEmpty()) {
                FunctionWhitelist.addBooleanFunctionNames(extraBool);
                log.info("Added {} extra boolean functions to whitelist", extraBool.size());
            }

            // 应用 Unicode 标识符验证配置
            com.zsubera.jpa.util.IdentifierValidator
                .setUnicodeIdentifiers(properties.getQuery().isUnicodeIdentifiers());

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
                    EncryptConverter.warmUpKeyCache();
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
         * 消除与 {@link EncryptConverter} 之间的重复代码。
         */
        private static boolean isProductionEnvironment() {
            return EnvironmentHelper.isProductionEnvironment();
        }
    }

    /**
     * 初始化后检查模块兼容性。
     */
    @Lazy(false)
    @Component
    static class ModuleCompatibilityChecker {

        private static final String ADD_OPENS_ARG = "--add-opens java.base/java.lang.invoke=ALL-UNNAMED";

        @PostConstruct
        public void check() {
            checkModuleCompatibility();
        }

        private static void checkModuleCompatibility() {
            try {
                Method readResolve = SerializedLambda.class.getDeclaredMethod("readResolve");
                readResolve.setAccessible(true);
            } catch (NoSuchMethodException e) {
                throw new AssertionError("SerializedLambda.readResolve() must exist in JDK", e);
            } catch (InaccessibleObjectException | SecurityException e) {
                log.error("=".repeat(80));
                log.error("MyJpa-Plus: Java module system restriction detected!");
                log.error("=".repeat(80));
                log.error("");
                log.error("LambdaUtils uses reflection on java.lang.invoke.SerializedLambda to extract");
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
                throw new IllegalStateException(
                    "MyJpa-Plus requires --add-opens java.base/java.lang.invoke=ALL-UNNAMED for lambda query support. "
                        + "All lambda-based queries (User::getName style) will fail at runtime without this JVM argument. "
                        + "See the error messages above for fix instructions.");
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

        private static final Logger SEC_LOG = LoggerFactory.getLogger(SecurityContextAuditorAware.class);

        private static volatile Method getContextMethod;
        private static volatile Method getAuthenticationMethod;
        private static volatile Method isAuthenticatedMethod;
        private static volatile Method getNameMethod;
        private static volatile Class<?> anonymousAuthenticationTokenClass;
        private static volatile boolean securityChecked;

        @Override
        public Optional<String> getCurrentAuditor() {
            if (!securityChecked) {
                initSecurityReflection();
            }
            if (getContextMethod == null) {
                return Optional.of("SYSTEM");
            }
            try {
                Object context = getContextMethod.invoke(null);
                Object auth = getAuthenticationMethod != null ? getAuthenticationMethod.invoke(context) : null;
                if (auth != null && isAuthenticatedMethod != null) {
                    Boolean authenticated = (Boolean)isAuthenticatedMethod.invoke(auth);
                    if (Boolean.TRUE.equals(authenticated) && !isAnonymousAuthentication(auth)
                        && getNameMethod != null) {
                        String principalName = (String)getNameMethod.invoke(auth);
                        if (principalName != null && !principalName.isBlank()
                            && !"anonymousUser".equalsIgnoreCase(principalName)) {
                            return Optional.of(principalName);
                        }
                    }
                }
            } catch (Exception e) {
                SEC_LOG.debug("Could not get user from SecurityContext: {}", e.getMessage());
            }
            return Optional.of("SYSTEM");
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
                try {
                    anonymousAuthenticationTokenClass =
                        Class.forName("org.springframework.security.authentication.AnonymousAuthenticationToken");
                } catch (ClassNotFoundException ignored) {
                    anonymousAuthenticationTokenClass = null;
                }
                SEC_LOG.info("Spring Security detected — AuditorAware will use SecurityContext");
            } catch (Exception e) {
                SEC_LOG.debug("Spring Security not available on classpath");
            }
            securityChecked = true;
        }

        private static boolean isAnonymousAuthentication(Object auth) {
            Class<?> anonymousType = anonymousAuthenticationTokenClass;
            return anonymousType != null && anonymousType.isInstance(auth);
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
     * {@link QueryCacheManager} Bean 来替换。
     *
     * @param properties 配置属性，用于读取 cache.maxEntries 配置
     * @return QueryCacheManager 实例
     */
    @Bean
    @ConditionalOnMissingBean(QueryCacheManager.class)
    @ConditionalOnProperty(prefix = "myjpa-plus.cache", name = "type", havingValue = "caffeine", matchIfMissing = true)
    public QueryCacheManager queryCacheManager(MyJpaPlusProperties properties) {
        int maxEntries = properties.getCache().getMaxEntries();
        return new QueryCacheManager(maxEntries);
    }

    /**
     * 创建 Redis 缓存适配器 Bean。仅在 cache.type=redis 且 Redis 在 classpath 时创建。
     *
     * @param properties 配置属性
     * @return RedisCacheAdapter 实例
     */
    /**
     * 创建缓存适配器 Bean。根据 cache.type 配置选择 Caffeine 或 Redis。
     *
     * <p>
     * - type=CAFFEINE（默认）：使用 {@link QueryCacheManager}
     * - type=REDIS：使用 {@link com.zsubera.jpa.template.RedisCacheAdapter}（需要 RedisTemplate）
     * - 用户可通过提供自定义 {@link CacheAdapter} Bean 来完全替换
     *
     * @param properties 配置属性
     * @param cacheManager 查询缓存管理器（可能为 null）
     * @param redisTemplate Redis 模板（可能为 null）
     * @return CacheAdapter 实例
     */
    @Bean
    @ConditionalOnMissingBean(CacheAdapter.class)
    public CacheAdapter cacheAdapter(MyJpaPlusProperties properties,
        @Autowired(required = false) QueryCacheManager cacheManager,
        @Autowired(required = false) org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate) {

        MyJpaPlusProperties.Cache.Type cacheType = properties.getCache().getType();

        if (cacheType == MyJpaPlusProperties.Cache.Type.REDIS) {
            if (redisTemplate == null) {
                throw new org.springframework.beans.factory.UnsatisfiedDependencyException("MyJpaPlusAutoConfiguration",
                    "cacheAdapter", "", "RedisTemplate not found. Please add spring-boot-starter-data-redis dependency "
                        + "and configure Redis connection properties (spring.data.redis.*).");
            }
            MyJpaPlusProperties.Cache.Redis redisConfig = properties.getCache().getRedis();
            CacheAdapter adapter =
                new com.zsubera.jpa.template.RedisCacheAdapter(redisTemplate, redisConfig.getKeyPrefix());
            cachedCacheAdapter = adapter;
            log.info("RedisCacheAdapter initialized — key prefix: {}", redisConfig.getKeyPrefix());
            return adapter;
        }

        // 默认使用 Caffeine
        if (cacheManager != null) {
            CacheAdapter adapter = cacheManager;
            cachedCacheAdapter = adapter;
            return adapter;
        }
        CacheAdapter adapter = CacheAdapter.disabled();
        cachedCacheAdapter = adapter;
        return adapter;
    }

    /**
     * 创建缓存失效监听器 Bean，监听实体变更事件并自动清除相关查询缓存。
     *
     * @param cacheAdapter 查询缓存管理器
     * @return CacheInvalidationListener 实例
     */
    @Bean
    @ConditionalOnMissingBean(CacheInvalidationListener.class)
    @ConditionalOnProperty(prefix = "myjpa-plus.cache", name = "auto-invalidation-enabled", havingValue = "true",
        matchIfMissing = true)
    public CacheInvalidationListener cacheInvalidationListener(CacheAdapter cacheAdapter) {
        log.info("CacheInvalidationListener enabled — query cache will auto-invalidate on entity modification");
        return new CacheInvalidationListener(cacheAdapter);
    }

    /**
     * 用于在监控启用时将 DataSource 包装为慢查询代理的 BeanPostProcessor。
     *
     * <p>
     * 此处理器不依赖 Hibernate，可与任何 JPA 实现配合使用。
     * 使用 {@link SlowQueryDataSourceProxy} 进行 DataSource 包装。
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
     * 自动将容器中所有 {@link SlowQueryListener} Bean 注册到
     * {@link SlowQueryDataSourceProxy}。
     */
    @Lazy(false)
    @Component
    static class SlowQueryListenerRegistrar {

        SlowQueryListenerRegistrar(@Autowired(required = false) ApplicationContext ctx) {
            if (ctx == null) {
                return;
            }
            try {
                Map<String, SlowQueryListener> listeners = ctx.getBeansOfType(SlowQueryListener.class);
                for (SlowQueryListener listener : listeners.values()) {
                    SlowQueryDataSourceProxy.addListener(listener);
                    log.info("Registered SlowQueryListener: {}", listener.getClass().getSimpleName());
                }
            } catch (Exception e) {
                log.debug("No SlowQueryListener beans found: {}", e.getMessage());
            }
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
        log.info(
            "MyJpa-Plus auto-configuration active: setting DefaultMyJpaRepository as default repository base class");
        return new RepositoryBaseClassPostProcessor();
    }

    /**
     * 应用关闭时清理所有组件资源，防止在 OSGi 或热部署环境中导致类加载器泄漏。
     *
     * <p>
     * 每个组件的清理操作独立注册，单个清理失败不影响其他组件。
     *
     */
    @EventListener(ContextClosedEvent.class)
    public void onContextClosed() {
        CleanupRegistry registry = new CleanupRegistry();
        registry.register("LambdaUtils", LambdaUtils::shutdown);
        registry.register("EncryptConverter cache", EncryptConverter::clearCaches);
        registry.register("EncryptConverter warmup executor", EncryptConverter::doShutdownWarmUpExecutor);
        registry.register("SoftDeleteHelper", SoftDeleteHelper::shutdown);
        registry.register("DefaultMyJpaRepository", DefaultMyJpaRepository::clearThreadLocal);
        registry.register("SoftDeleteContext", SoftDeleteContext::reset);
        registry.register("EntityManagerHelper", EntityManagerHelper::reset);
        registry.register("SoftDeleteHelper event publisher", () -> SoftDeleteBulkExecutor.setEventPublisher(null));
        registry.register("SlowQueryDataSourceProxy listeners", SlowQueryDataSourceProxy::clearListeners);
        registry.register("GlobalConfigHolder", GlobalConfigHolder::reset);
        registry.register("InClauseBuilder", InClauseBuilder::reset);
        registry.register("FunctionWhitelist", FunctionWhitelist::reset);
        // ponytail: 使用缓存的 CacheAdapter 引用，避免 ContextClosedEvent 中 getBean() 死锁。
        // Spring 的 DefaultLifecycleProcessor 在销毁 bean 时持有 singleton 锁，
        // getBean() 也需要该锁，两者同时调用会死锁。
        CacheAdapter adapterToClose = cachedCacheAdapter;
        if (adapterToClose != null) {
            registry.register("CacheAdapter", () -> adapterToClose.close());
            cachedCacheAdapter = null;
        }
        registry.executeAll();
        log.info("MyJpa-Plus context closed, caches cleaned");
    }

    /**
     * 组件清理注册表，收集多个清理操作并逐一执行。
     * 单个清理失败不会阻止其他清理操作执行。
     */
    private static class CleanupRegistry {
        private final List<Map.Entry<String, Runnable>> actions = new ArrayList<>();

        void register(String name, Runnable action) {
            actions.add(Map.entry(name, action));
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
            int count = 0;
            for (String beanName : registry.getBeanDefinitionNames()) {
                try {
                    var bd = registry.getBeanDefinition(beanName);
                    if (!bd.getPropertyValues().contains("repositoryInterface")) {
                        continue;
                    }
                    // 仅当 bean class 仍为默认值（未通过 @EnableJpaRepositories 手动指定）时替换
                    String currentClass = bd.getBeanClassName();
                    if (DEFAULT_FACTORY_BEAN.equals(currentClass)) {
                        bd.setBeanClassName(factoryBeanClassName);
                        count++;
                    }
                } catch (Exception e) {
                    // ponytail: 单个 bean 处理失败不应影响其他 bean，也不应阻断启动
                    postProcessorLog.warn("Failed to process repository bean '{}': {}", beanName, e.getMessage());
                }
            }
            postProcessorLog.info("MyJpaRepositoryFactoryBean auto-registered for {} repository beans", count);
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
    @Configuration
    @EnableJpaAuditing
    @ConditionalOnMissingBean(name = "auditingHandler")
    static class JpaAuditingConfig {}
}
