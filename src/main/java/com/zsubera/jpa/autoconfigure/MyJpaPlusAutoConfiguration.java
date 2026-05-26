package com.zsubera.jpa.autoconfigure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

import jakarta.persistence.EntityManager;

/**
 * Auto-configuration for MyJpa-Plus.
 * <p>
 * Automatically activates when Spring Data JPA and {@link EntityManager}
 * are on the classpath. Registers all MyJpa-Plus beans via component scanning
 * and enables {@link MyJpaPlusProperties} for external configuration.
 * <p>
 * Configuration options (prefix: {@code myjpa-plus}):
 * <ul>
 *   <li>{@code myjpa-plus.soft-delete.auto-filter} — auto-apply soft-delete filters (default: true)</li>
 * </ul>
 */
@AutoConfiguration
@ConditionalOnClass({EntityManager.class})
@EnableConfigurationProperties(MyJpaPlusProperties.class)
@ComponentScan(basePackages = "com.zsubera.jpa")
public class MyJpaPlusAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MyJpaPlusAutoConfiguration.class);

    public MyJpaPlusAutoConfiguration(MyJpaPlusProperties properties) {
        if (log.isInfoEnabled()) {
            log.info("MyJpa-Plus AutoConfiguration initialized");
            log.info("  soft-delete.auto-filter = {}", properties.getSoftDelete().isAutoFilter());
        }
    }
}
