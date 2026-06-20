package com.zsubera.jpa.repository;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;

@DataJpaTest
@org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase(
    replace = org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = EntityManagerHelperIntegrationTest.TestConfig.class)
class EntityManagerHelperIntegrationTest {

    @SpringBootApplication
    @EntityScan(basePackageClasses = SoftDeleteRepoTestEntity.class)
    @EnableJpaRepositories(basePackageClasses = SoftDeleteRepoTestRepository.class)
    static class TestConfig {}

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @BeforeEach
    void setUp() {
        EntityManagerHelper.reset();
    }

    @AfterEach
    void tearDown() {
        EntityManagerHelper.reset();
    }

    // ---- getTransactionalEntityManager: no-arg ----

    @Test
    void getTransactionalEntityManager_noArg_withEmf() {
        EntityManagerHelper.setEntityManagerFactory(entityManagerFactory);
        var em = EntityManagerHelper.getTransactionalEntityManager();
        assertNotNull(em);
    }

    @Test
    void getTransactionalEntityManager_noArg_noEmf_throws() {
        assertThrows(IllegalStateException.class, () -> EntityManagerHelper.getTransactionalEntityManager());
    }

    // ---- getTransactionalEntityManager: entity instance ----

    @Test
    void getTransactionalEntityManager_entityInstance() {
        EntityManagerHelper.setEntityManagerFactory(entityManagerFactory);
        SoftDeleteRepoTestEntity entity = new SoftDeleteRepoTestEntity();
        var em = EntityManagerHelper.getTransactionalEntityManager(entity);
        assertNotNull(em);
    }

    @Test
    void getTransactionalEntityManager_entityInstance_noEmf_throws() {
        assertThrows(NullPointerException.class, () -> EntityManagerHelper.getTransactionalEntityManager((Object)null));
    }

    // ---- getTransactionalEntityManager: entityType ----

    @Test
    void getTransactionalEntityManager_entityType() {
        EntityManagerHelper.setEntityManagerFactory(entityManagerFactory);
        var em = EntityManagerHelper.getTransactionalEntityManager(SoftDeleteRepoTestEntity.class);
        assertNotNull(em);
    }

    @Test
    void getTransactionalEntityManager_nullEntityType() {
        EntityManagerHelper.setEntityManagerFactory(entityManagerFactory);
        var em = EntityManagerHelper.getTransactionalEntityManager((Class<?>)null);
        assertNotNull(em);
    }

    // ---- registerResolver / removeResolver: recheckPath ----

    @Test
    void removeResolver_recheckPath_withDefaultResolver() {
        EntityManagerHelper.setEntityManagerFactory(entityManagerFactory);
        EntityManagerHelper.registerResolver(String.class, type -> entityManagerFactory);
        EntityManagerHelper.removeResolver(String.class);
        // After removing, allResolversUseDefault should be restored
        var em = EntityManagerHelper.getTransactionalEntityManager();
        assertNotNull(em);
    }

    @Test
    void removeResolver_recheckPath_withNonDefaultResolver() {
        EntityManagerHelper.setEntityManagerFactory(entityManagerFactory);
        EntityManagerFactory otherEmf = org.mockito.Mockito.mock(EntityManagerFactory.class);
        EntityManagerHelper.registerResolver(String.class, type -> otherEmf);
        EntityManagerHelper.removeResolver(String.class);
    }

    @Test
    void removeResolver_recheckPath_resolverThrows() {
        EntityManagerHelper.setEntityManagerFactory(entityManagerFactory);
        EntityManagerHelper.registerResolver(String.class, type -> {
            throw new RuntimeException("fail");
        });
        EntityManagerHelper.removeResolver(String.class);
    }

    // ---- registerEntityManagerFactory ----

    @Test
    void registerEntityManagerFactory_sameAsDefault() {
        EntityManagerHelper.setEntityManagerFactory(entityManagerFactory);
        EntityManagerHelper.registerEntityManagerFactory(SoftDeleteRepoTestEntity.class, entityManagerFactory);
    }

    @Test
    void registerEntityManagerFactory_differentFromDefault() {
        EntityManagerHelper.setEntityManagerFactory(entityManagerFactory);
        EntityManagerFactory otherEmf = org.mockito.Mockito.mock(EntityManagerFactory.class);
        EntityManagerHelper.registerEntityManagerFactory(SoftDeleteRepoTestEntity.class, otherEmf);
    }

    // ---- registerEntityManagerFactoryIfAbsent ----

    @Test
    void registerEntityManagerFactoryIfAbsent_firstTime() {
        EntityManagerHelper.setEntityManagerFactory(entityManagerFactory);
        EntityManagerHelper.registerEntityManagerFactoryIfAbsent(SoftDeleteRepoTestEntity.class, entityManagerFactory);
    }

    @Test
    void registerEntityManagerFactoryIfAbsent_alreadyExists() {
        EntityManagerHelper.setEntityManagerFactory(entityManagerFactory);
        EntityManagerHelper.registerEntityManagerFactoryIfAbsent(SoftDeleteRepoTestEntity.class, entityManagerFactory);
        EntityManagerFactory otherEmf = org.mockito.Mockito.mock(EntityManagerFactory.class);
        EntityManagerHelper.registerEntityManagerFactoryIfAbsent(SoftDeleteRepoTestEntity.class, otherEmf);
        // Should not overwrite
    }

    // ---- removeResolver ----

    @Test
    void removeResolver_removesRegisteredResolver() {
        EntityManagerHelper.setEntityManagerFactory(entityManagerFactory);
        EntityManagerHelper.registerResolver(String.class, type -> entityManagerFactory);
        EntityManagerHelper.removeResolver(String.class);
    }

    // ---- reset ----

    @Test
    void reset_clearsEverything() {
        EntityManagerHelper.setEntityManagerFactory(entityManagerFactory);
        EntityManagerHelper.registerResolver(String.class, type -> entityManagerFactory);
        EntityManagerHelper.reset();
        assertThrows(IllegalStateException.class, () -> EntityManagerHelper.getTransactionalEntityManager());
    }

    // ---- registerResolver: null params ----

    @Test
    void registerResolver_nullEntityType_throws() {
        assertThrows(NullPointerException.class, () -> EntityManagerHelper.registerResolver(null, type -> null));
    }

    @Test
    void registerResolver_nullResolver_throws() {
        assertThrows(NullPointerException.class, () -> EntityManagerHelper.registerResolver(String.class, null));
    }

    @Test
    void registerEntityManagerFactory_nullEntityType_throws() {
        assertThrows(NullPointerException.class,
            () -> EntityManagerHelper.registerEntityManagerFactory(null, entityManagerFactory));
    }

    @Test
    void registerEntityManagerFactory_nullEmf_throws() {
        assertThrows(NullPointerException.class,
            () -> EntityManagerHelper.registerEntityManagerFactory(String.class, null));
    }

    @Test
    void registerEntityManagerFactoryIfAbsent_nullEntityType_throws() {
        assertThrows(NullPointerException.class,
            () -> EntityManagerHelper.registerEntityManagerFactoryIfAbsent(null, entityManagerFactory));
    }

    @Test
    void registerEntityManagerFactoryIfAbsent_nullEmf_throws() {
        assertThrows(NullPointerException.class,
            () -> EntityManagerHelper.registerEntityManagerFactoryIfAbsent(String.class, null));
    }

    @Test
    void removeResolver_nullEntityType_throws() {
        assertThrows(NullPointerException.class, () -> EntityManagerHelper.removeResolver(null));
    }
}
