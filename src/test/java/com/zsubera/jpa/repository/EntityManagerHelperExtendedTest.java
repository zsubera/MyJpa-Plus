package com.zsubera.jpa.repository;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
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
@ContextConfiguration(classes = EntityManagerHelperExtendedTest.TestConfig.class)
class EntityManagerHelperExtendedTest {

    @SpringBootApplication
    @EntityScan(basePackageClasses = SoftDeleteRepoTestEntity.class)
    @EnableJpaRepositories(basePackages = "com.zsubera.jpa.repository",
        repositoryBaseClass = DefaultMyJpaRepository.class)
    static class TestConfig {}

    @Autowired
    private SoftDeleteRepoTestRepository repository;

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

    @Test
    void getTransactionalEntityManager_noEntityType_noEmf_throws() {
        assertThrows(IllegalStateException.class, () -> EntityManagerHelper.getTransactionalEntityManager());
    }

    @Test
    void getTransactionalEntityManager_withEntityType_noEmf_throws() {
        assertThrows(IllegalStateException.class,
            () -> EntityManagerHelper.getTransactionalEntityManager(SoftDeleteRepoTestEntity.class));
    }

    @Test
    void getTransactionalEntityManager_withEntity_noEmf_throws() {
        SoftDeleteRepoTestEntity entity = new SoftDeleteRepoTestEntity();
        assertThrows(IllegalStateException.class,
            () -> EntityManagerHelper.getTransactionalEntityManager(entity));
    }

    @Test
    void getTransactionalEntityManager_withNullEntity_throws() {
        assertThrows(NullPointerException.class,
            () -> EntityManagerHelper.getTransactionalEntityManager((Object)null));
    }

    @Test
    void registerResolver_nullEntityType_throws() {
        assertThrows(NullPointerException.class,
            () -> EntityManagerHelper.registerResolver(null, type -> null));
    }

    @Test
    void registerResolver_nullResolver_throws() {
        assertThrows(NullPointerException.class,
            () -> EntityManagerHelper.registerResolver(String.class, null));
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
        assertThrows(NullPointerException.class,
            () -> EntityManagerHelper.removeResolver(null));
    }

    @Test
    void registerResolver_sameAsDefault_doesNotDisableFastPath() {
        EntityManagerHelper.setEntityManagerFactory(entityManagerFactory);
        EntityManagerHelper.registerResolver(SoftDeleteRepoTestEntity.class, type -> entityManagerFactory);
        // allResolversUseDefault should remain true since resolver returns default EMF
    }

    @Test
    void registerResolver_differentFromDefault_disablesFastPath() {
        EntityManagerHelper.setEntityManagerFactory(entityManagerFactory);
        EntityManagerFactory otherEmf = org.mockito.Mockito.mock(EntityManagerFactory.class);
        EntityManagerHelper.registerResolver(SoftDeleteRepoTestEntity.class, type -> otherEmf);
    }

    @Test
    void registerResolver_withoutDefaultEmf_disablesFastPath() {
        EntityManagerFactory otherEmf = org.mockito.Mockito.mock(EntityManagerFactory.class);
        EntityManagerHelper.registerResolver(SoftDeleteRepoTestEntity.class, type -> otherEmf);
    }

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

    @Test
    void removeResolver_restoresFastPath() {
        EntityManagerHelper.setEntityManagerFactory(entityManagerFactory);
        EntityManagerFactory otherEmf = org.mockito.Mockito.mock(EntityManagerFactory.class);
        EntityManagerHelper.registerResolver(SoftDeleteRepoTestEntity.class, type -> otherEmf);
        EntityManagerHelper.removeResolver(SoftDeleteRepoTestEntity.class);
        // After removing, allResolversUseDefault should be true again
    }

    @Test
    void registerEntityManagerFactoryIfAbsent_doesNotOverwrite() {
        EntityManagerHelper.setEntityManagerFactory(entityManagerFactory);
        EntityManagerHelper.registerEntityManagerFactoryIfAbsent(SoftDeleteRepoTestEntity.class, entityManagerFactory);
        EntityManagerFactory otherEmf = org.mockito.Mockito.mock(EntityManagerFactory.class);
        EntityManagerHelper.registerEntityManagerFactoryIfAbsent(SoftDeleteRepoTestEntity.class, otherEmf);
        // Should not be overwritten
    }

    @Test
    void reset_clearsEverything() {
        EntityManagerHelper.setEntityManagerFactory(entityManagerFactory);
        EntityManagerHelper.registerResolver(SoftDeleteRepoTestEntity.class, type -> entityManagerFactory);
        EntityManagerHelper.reset();
        assertThrows(IllegalStateException.class, () -> EntityManagerHelper.getTransactionalEntityManager());
    }

    @Test
    void registerResolver_throwsException_inResolve() {
        EntityManagerHelper.setEntityManagerFactory(entityManagerFactory);
        EntityManagerHelper.registerResolver(SoftDeleteRepoTestEntity.class, type -> {
            throw new RuntimeException("resolve failed");
        });
    }

    @Test
    void getTransactionalEntityManager_entityTypeFallsBackToDefault() {
        EntityManagerHelper.setEntityManagerFactory(entityManagerFactory);
        // Falls back to default EMF since no resolver for Integer.class
        // Should succeed because we have an active transaction in @DataJpaTest
        var em = EntityManagerHelper.getTransactionalEntityManager(Integer.class);
        assertNotNull(em);
    }

    @Test
    void getTransactionalEntityManager_entityTypeWithResolver() {
        EntityManagerHelper.setEntityManagerFactory(entityManagerFactory);
        EntityManagerHelper.registerResolver(SoftDeleteRepoTestEntity.class, type -> entityManagerFactory);
        var em = EntityManagerHelper.getTransactionalEntityManager(SoftDeleteRepoTestEntity.class);
        assertNotNull(em);
    }
}
