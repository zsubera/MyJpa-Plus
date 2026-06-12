package com.zsubera.jpa.update;

import com.zsubera.jpa.exception.MyJpaPlusException;
import jakarta.persistence.EntityManager;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 数据库方言检测器。
 *
 * <p>
 * 从 EntityManager 自动检测数据库类型（PostgreSQL、MySQL），支持多级回退策略：
 * <ol>
 * <li>JDBC URL 属性检测</li>
 * <li>JDBC Connection.getMetaData() 检测</li>
 * <li>Hibernate Session 反射检测</li>
 * <li>手动系统属性配置</li>
 * </ol>
 *
 * <p>
 * 检测结果按 EntityManagerFactory 缓存，避免重复检测开销。
 *
 * @since 1.2.0
 */
final class DialectDetector {

    private static final Logger log = LoggerFactory.getLogger(DialectDetector.class);

    /** 方言策略实例映射，避免重复创建。 */
    static final Map<String, DialectStrategy> DIALECT_STRATEGIES =
        Map.of("postgresql", new PostgresDialect(), "mysql", new MysqlDialect());

    /** 每个 EntityManagerFactory 缓存的方言，避免重复检测。 */
    private static final ConcurrentHashMap<String, String> DIALECT_CACHE = new ConcurrentHashMap<>();

    private DialectDetector() {}

    /**
     * 检测数据库方言。
     *
     * @param em 实体管理器
     * @return 数据库方言标识（postgresql 或 mysql）
     * @throws MyJpaPlusException 如果无法检测方言且未手动配置
     */
    static String detectDialect(EntityManager em) {
        jakarta.persistence.EntityManagerFactory emf = em.getEntityManagerFactory();
        String factoryKey = resolveFactoryKey(emf);
        String cached = DIALECT_CACHE.get(factoryKey);
        if (cached != null) {
            return cached;
        }
        // 优先级 1：从 EntityManagerFactory 属性中的 JDBC URL 检测（无 Hibernate 依赖）
        try {
            Object jdbcUrl = em.getEntityManagerFactory().getProperties().get("jakarta.persistence.jdbc.url");
            if (jdbcUrl == null) {
                jdbcUrl = em.getEntityManagerFactory().getProperties().get("hibernate.connection.url");
            }
            if (jdbcUrl != null) {
                String url = jdbcUrl.toString().toLowerCase();
                if (url.contains("postgresql")) {
                    DIALECT_CACHE.putIfAbsent(factoryKey, "postgresql");
                    return "postgresql";
                }
                if (url.contains("mysql")) {
                    DIALECT_CACHE.putIfAbsent(factoryKey, "mysql");
                    return "mysql";
                }
            }
        } catch (Exception ex) {
            log.debug("Failed to detect dialect from properties: {}", ex.getMessage());
        }

        // 优先级 2：通过 EntityManager.unwrap() 的 JDBC Connection.getMetaData() 检测
        try {
            java.sql.Connection conn = em.unwrap(java.sql.Connection.class);
            if (conn != null) {
                String productName = conn.getMetaData().getDatabaseProductName().toLowerCase();
                String dialect = mapDialect(productName);
                if (DIALECT_STRATEGIES.containsKey(dialect)) {
                    DIALECT_CACHE.putIfAbsent(factoryKey, dialect);
                    return dialect;
                }
                // 未识别的方言不缓存，允许手动配置覆盖
            }
        } catch (Exception e) {
            log.debug("Failed to detect dialect via JDBC Connection.unwrap(): {}", e.getMessage());
        }

        // 优先级 3：Hibernate 回退（仅当 Hibernate 在 classpath 上时）
        try {
            Class<?> sessionClass = Class.forName("org.hibernate.Session");
            Object session = em.unwrap(sessionClass);
            Class<?> workClass = Class.forName("org.hibernate.jdbc.Work");
            String[] dialectHolder = new String[1];
            Object workProxy = java.lang.reflect.Proxy.newProxyInstance(workClass.getClassLoader(),
                new Class<?>[] {workClass}, (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return method.invoke(proxy, args);
                    }
                    if ("execute".equals(method.getName()) && args.length == 1
                        && args[0] instanceof java.sql.Connection conn) {
                        dialectHolder[0] = conn.getMetaData().getDatabaseProductName().toLowerCase();
                        return null;
                    }
                    return method.invoke(proxy, args);
                });
            java.lang.reflect.Method doWork = sessionClass.getMethod("doWork", workClass);
            doWork.invoke(session, workProxy);
            String dialect = mapDialect(dialectHolder[0]);
            if (DIALECT_STRATEGIES.containsKey(dialect)) {
                DIALECT_CACHE.putIfAbsent(factoryKey, dialect);
                return dialect;
            }
            // 未识别的方言不缓存，允许手动配置覆盖
        } catch (ClassNotFoundException e) {
            log.debug("Hibernate not available on classpath");
        } catch (Exception e) {
            log.debug("Hibernate dialect detection failed: {}", e.getMessage());
        }

        // 优先级 4：手动配置
        log.warn("Failed to detect database dialect automatically. "
            + "Set system property 'myjpa-plus.dialect' to 'postgresql' or 'mysql' to specify manually.");
        String manualDialect = System.getProperty("myjpa-plus.dialect");
        if (manualDialect != null && !manualDialect.isEmpty()) {
            String mapped = mapDialect(manualDialect.toLowerCase());
            log.info("Using manually configured dialect: {}", mapped);
            DIALECT_CACHE.putIfAbsent(factoryKey, mapped);
            return mapped;
        }
        throw new MyJpaPlusException("Failed to detect database dialect and no manual dialect configured. "
            + "Set system property 'myjpa-plus.dialect' to 'postgresql' or 'mysql'.");
    }

    /**
     * 将数据库产品名称映射为方言标识。
     *
     * @param productName 数据库产品名称（小写）
     * @return 方言标识
     */
    static String mapDialect(String productName) {
        if (productName == null) {
            return "unknown";
        }
        if (productName.contains("postgresql")) {
            return "postgresql";
        }
        if (productName.contains("mysql")) {
            return "mysql";
        }
        log.warn("Unknown database product '{}'. "
            + "Set system property 'myjpa-plus.dialect' to 'postgresql' or 'mysql' manually.", productName);
        return productName;
    }

    /**
     * 为 EntityManagerFactory 生成稳定的缓存键。
     *
     * <p>
     * 优先使用 JDBC URL 作为缓存键（跨 JVM 重启稳定），回退到基于 identityHashCode 的键。
     *
     * @param emf EntityManagerFactory 实例
     * @return 稳定的缓存键字符串
     */
    private static String resolveFactoryKey(jakarta.persistence.EntityManagerFactory emf) {
        try {
            Object jdbcUrl = emf.getProperties().get("jakarta.persistence.jdbc.url");
            if (jdbcUrl == null) {
                jdbcUrl = emf.getProperties().get("hibernate.connection.url");
            }
            if (jdbcUrl != null) {
                return jdbcUrl.toString();
            }
        } catch (Exception ignored) {
            // 回退到基于 identity 的键
        }
        return emf.getClass().getName() + "@" + System.identityHashCode(emf);
    }
}
