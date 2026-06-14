package com.zsubera.jpa.repository;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.core.support.RepositoryFactorySupport;

class MyJpaRepositoryFactoryBeanTest {

    interface TestRepository extends Repository<Object, Long> {}

    @Test
    void constructor_createsFactoryBean() {
        MyJpaRepositoryFactoryBean<TestRepository, Object, Long> bean =
            new MyJpaRepositoryFactoryBean<>(TestRepository.class);
        assertNotNull(bean);
    }

    @Test
    void createRepositoryFactory_returnsMyJpaRepositoryFactory() throws Exception {
        MyJpaRepositoryFactoryBean<TestRepository, Object, Long> bean =
            new MyJpaRepositoryFactoryBean<>(TestRepository.class);

        java.lang.reflect.Method method =
            MyJpaRepositoryFactoryBean.class.getDeclaredMethod("createRepositoryFactory", EntityManager.class);
        method.setAccessible(true);

        assertNotNull(method);
        assertTrue(RepositoryFactorySupport.class.isAssignableFrom(method.getReturnType()));
    }
}
