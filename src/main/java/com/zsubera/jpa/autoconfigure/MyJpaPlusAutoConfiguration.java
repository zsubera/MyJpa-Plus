package com.zsubera.jpa.autoconfigure;

import com.zsubera.jpa.template.MyJpaTemplate;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

/**
 * MyJpa-Plus 的自动配置类。
 *
 * <p>当 Spring Data JPA 和 {@link EntityManager} 在类路径上时自动激活。 通过组件扫描注册所有 MyJpa-Plus 的 Bean，并启用 {@link
 * MyJpaPlusProperties} 进行外部配置。
 *
 * <p>配置选项（前缀：{@code myjpa-plus}）：
 *
 * <ul>
 *   <li>{@code myjpa-plus.soft-delete.auto-filter} — 自动应用软删除过滤器（默认：true）
 *   <li>{@code myjpa-plus.query.max-results} — 查询最大返回行数（默认：10000）
 *   <li>{@code myjpa-plus.query.deep-pagination-offset-threshold} — 深度分页警告阈值（默认：100000）
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
            log.info(
                    "  soft-delete.auto-filter = {}", properties.getSoftDelete().isAutoFilter());
            log.info("  query.max-results = {}", properties.getQuery().getMaxResults());
            log.info(
                    "  query.deep-pagination-offset-threshold = {}",
                    properties.getQuery().getDeepPaginationOffsetThreshold());
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
        return new MyJpaTemplate(
                properties.getQuery().getMaxResults(), properties.getQuery().getDeepPaginationOffsetThreshold());
    }
}
