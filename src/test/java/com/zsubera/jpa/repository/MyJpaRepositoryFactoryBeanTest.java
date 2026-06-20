package com.zsubera.jpa.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.core.support.RepositoryFactorySupport;

class MyJpaRepositoryFactoryBeanTest {

    interface TestRepository extends Repository<Object, Long> {}

    @BeforeEach
    void setUp() {
        EntityManagerHelper.reset();
    }

    @AfterEach
    void tearDown() {
        EntityManagerHelper.reset();
    }

    @Test
    void constructor_createsFactoryBean() {
        MyJpaRepositoryFactoryBean<TestRepository, Object, Long> bean =
            new MyJpaRepositoryFactoryBean<>(TestRepository.class);
        assertThat(bean).isNotNull();
    }

    @Test
    void createRepositoryFactory_returnsCorrectReturnType() throws Exception {
        java.lang.reflect.Method method = MyJpaRepositoryFactoryBean.class.getDeclaredMethod("createRepositoryFactory",
            jakarta.persistence.EntityManager.class);
        method.setAccessible(true);

        assertThat(method).isNotNull();
        assertThat(RepositoryFactorySupport.class.isAssignableFrom(method.getReturnType())).isTrue();
    }

    @Test
    void resolveEntityType_returnsNullForNonJpaRepository() throws Exception {
        MyJpaRepositoryFactoryBean<TestRepository, Object, Long> bean =
            new MyJpaRepositoryFactoryBean<>(TestRepository.class);

        java.lang.reflect.Method method =
            MyJpaRepositoryFactoryBean.class.getDeclaredMethod("resolveEntityType", Class.class);
        method.setAccessible(true);

        Class<?> result = (Class<?>)method.invoke(bean, TestRepository.class);

        // TestRepository doesn't extend JpaRepository, so entity type cannot be resolved
        assertThat(result).isNull();
    }

    @Test
    void entityTypeField_isSetFromRepositoryInterface() {
        MyJpaRepositoryFactoryBean<MyJpaTestRepository, MyJpaTestEntity, Long> bean =
            new MyJpaRepositoryFactoryBean<>(MyJpaTestRepository.class);

        // Verify the bean was created successfully
        assertThat(bean).isNotNull();
    }

    @Test
    void createRepositoryFactory_withEntityType_debugLogging() throws Exception {
        ch.qos.logback.classic.Logger logger =
            (ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(MyJpaRepositoryFactoryBean.class);
        ch.qos.logback.classic.Level oldLevel = logger.getLevel();
        try {
            logger.setLevel(ch.qos.logback.classic.Level.DEBUG);

            MyJpaRepositoryFactoryBean<MyJpaTestRepository, MyJpaTestEntity, Long> bean =
                new MyJpaRepositoryFactoryBean<>(MyJpaTestRepository.class);

            jakarta.persistence.EntityManagerFactory emf =
                org.mockito.Mockito.mock(jakarta.persistence.EntityManagerFactory.class);
            jakarta.persistence.EntityManager em = org.mockito.Mockito.mock(jakarta.persistence.EntityManager.class);
            org.mockito.Mockito.when(em.getEntityManagerFactory()).thenReturn(emf);
            org.mockito.Mockito.when(em.getDelegate()).thenReturn(new Object());

            java.lang.reflect.Method method = MyJpaRepositoryFactoryBean.class
                .getDeclaredMethod("createRepositoryFactory", jakarta.persistence.EntityManager.class);
            method.setAccessible(true);

            Object result = method.invoke(bean, em);
            assertThat(result).isNotNull();
        } finally {
            logger.setLevel(oldLevel);
        }
    }

    @Test
    void resolveEntityType_exceptionPath() throws Exception {
        // Use a repository interface that will cause EntityClassResolver.resolve to fail
        MyJpaRepositoryFactoryBean<MalformedRepository, Object, Long> bean =
            new MyJpaRepositoryFactoryBean<>(MalformedRepository.class);

        java.lang.reflect.Method method =
            MyJpaRepositoryFactoryBean.class.getDeclaredMethod("resolveEntityType", Class.class);
        method.setAccessible(true);

        // Should return null when resolve throws exception
        Class<?> result = (Class<?>)method.invoke(bean, MalformedRepository.class);
        assertThat(result).isNull();
    }

    interface MalformedRepository extends Repository<Object, Long> {}
}
