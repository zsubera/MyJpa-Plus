package com.zsubera.jpa.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.zsubera.jpa.monitor.SlowQueryDataSourceProxy;
import com.zsubera.jpa.template.MyJpaTemplate;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.lang.reflect.Proxy;
import javax.sql.DataSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

@Tag("integration")
@SpringBootTest
@Transactional
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:mysql://localhost:3306/test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
    "spring.datasource.username=root", "spring.datasource.password=1351.zhong",
    "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
    "spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect", "spring.jpa.hibernate.ddl-auto=create",
    "myjpa-plus.monitoring.enabled=true", "myjpa-plus.monitoring.slow-query-threshold-ms=500"})
class MySQLMonitoringIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private MyJpaTemplate jpaTemplate;

    @PersistenceContext
    private EntityManager em;

    @Test
    void dataSourceIsWrappedBySlowQueryProxy() {
        assertTrue(Proxy.isProxyClass(dataSource.getClass()),
            "DataSource should be a JDK proxy when monitoring is enabled");
        assertTrue(SlowQueryDataSourceProxy.isWrapped(dataSource),
            "DataSource should be wrapped by SlowQueryDataSourceProxy");
    }

    @Test
    void queryExecutionWorksWithMonitoring() {
        MySQLTestEntity entity = new MySQLTestEntity();
        entity.setName("monitor-test");
        entity.setStatus(1);
        em.persist(entity);
        em.flush();

        MySQLTestEntity found = em.find(MySQLTestEntity.class, entity.getId());
        assertNotNull(found);
        assertEquals("monitor-test", found.getName());
    }

    @Test
    void jpaTemplateQueryWorksWithMonitoring() {
        MySQLTestEntity entity = new MySQLTestEntity();
        entity.setName("template-test");
        entity.setStatus(1);
        em.persist(entity);
        em.flush();

        var qs = new com.zsubera.jpa.spec.QuerySpec<MySQLTestEntity>();
        qs.eq(MySQLTestEntity::getName, "template-test");
        java.util.Optional<MySQLTestEntity> found = jpaTemplate.findOne(MySQLTestEntity.class, qs);
        assertTrue(found.isPresent());
        assertEquals("template-test", found.get().getName());
    }
}
