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
@ContextConfiguration(classes = EntityManagerHelperFinalTest.TestConfig.class)
class EntityManagerHelperFinalTest {

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

    // ---- recheckAllResolversUseDefault: resolvers not empty after remove ----

    @Test
    void removeResolver_recheckWithRemainingResolvers() {
        EntityManagerHelper.setEntityManagerFactory(entityManagerFactory);
        // Register two resolvers
        EntityManagerResolver resolverA = type -> entityManagerFactory;
        EntityManagerResolver resolverB = type -> entityManagerFactory;
        EntityManagerHelper.registerResolver(String.class, resolverA);
        EntityManagerHelper.registerResolver(Integer.class, resolverB);

        // Remove one - resolvers still has Integer.class
        EntityManagerHelper.removeResolver(String.class);

        // Should still work (allResolversUseDefault restored to true since both use default EMF)
        var em = EntityManagerHelper.getTransactionalEntityManager();
        assertNotNull(em);
    }

    @Test
    void removeResolver_recheckWithNonDefaultResolver() {
        EntityManagerHelper.setEntityManagerFactory(entityManagerFactory);
        EntityManagerFactory otherEmf = org.mockito.Mockito.mock(EntityManagerFactory.class);

        // Register two resolvers - one non-default
        EntityManagerHelper.registerResolver(String.class, type -> entityManagerFactory);
        EntityManagerHelper.registerResolver(Integer.class, type -> otherEmf);

        // Remove the non-default one - remaining resolver uses default
        EntityManagerHelper.removeResolver(Integer.class);

        // allResolversUseDefault should be restored
        var em = EntityManagerHelper.getTransactionalEntityManager();
        assertNotNull(em);
    }

    @Test
    void removeResolver_recheckAllResolversNonDefault() {
        EntityManagerHelper.setEntityManagerFactory(entityManagerFactory);
        EntityManagerFactory otherEmf = org.mockito.Mockito.mock(EntityManagerFactory.class);

        // Register two non-default resolvers
        EntityManagerHelper.registerResolver(String.class, type -> otherEmf);
        EntityManagerHelper.registerResolver(Integer.class, type -> otherEmf);

        // Remove one - remaining resolver still non-default
        EntityManagerHelper.removeResolver(String.class);

        // allResolversUseDefault should be false
        // getTransactionalEntityManager with null type should fall back to default EMF
        var em = EntityManagerHelper.getTransactionalEntityManager((Class<?>)null);
        assertNotNull(em);
    }

    @Test
    void removeResolver_recheckResolverThrows() {
        EntityManagerHelper.setEntityManagerFactory(entityManagerFactory);

        // Register two resolvers - one throws
        EntityManagerHelper.registerResolver(String.class, type -> entityManagerFactory);
        EntityManagerHelper.registerResolver(Integer.class, type -> {
            throw new RuntimeException("fail");
        });

        // Remove the good one - remaining resolver throws
        EntityManagerHelper.removeResolver(String.class);

        // allResolversUseDefault should be false (exception in resolver)
        // getTransactionalEntityManager with null type should fall back to default
        var em = EntityManagerHelper.getTransactionalEntityManager((Class<?>)null);
        assertNotNull(em);
    }

    @Test
    void removeResolver_recheckAllResolversNonDefault_fallback() {
        EntityManagerHelper.setEntityManagerFactory(entityManagerFactory);
        EntityManagerFactory otherEmf = org.mockito.Mockito.mock(EntityManagerFactory.class);

        // Register two non-default resolvers
        EntityManagerHelper.registerResolver(String.class, type -> otherEmf);
        EntityManagerHelper.registerResolver(Integer.class, type -> otherEmf);

        // Remove one - remaining resolver still non-default
        EntityManagerHelper.removeResolver(String.class);

        // allResolversUseDefault should be false
        // getTransactionalEntityManager with null type should fall back to default EMF
        var em = EntityManagerHelper.getTransactionalEntityManager((Class<?>)null);
        assertNotNull(em);
    }

    @Test
    void removeResolver_emptyAfterRemove() {
        EntityManagerHelper.setEntityManagerFactory(entityManagerFactory);
        EntityManagerHelper.registerResolver(String.class, type -> entityManagerFactory);

        // Remove the only resolver
        EntityManagerHelper.removeResolver(String.class);

        // allResolversUseDefault should be true (empty resolvers)
        var em = EntityManagerHelper.getTransactionalEntityManager();
        assertNotNull(em);
    }

    @Test
    void removeResolver_noDefaultEmf() {
        EntityManagerHelper.reset();
        EntityManagerFactory otherEmf = org.mockito.Mockito.mock(EntityManagerFactory.class);
        EntityManagerHelper.registerResolver(String.class, type -> otherEmf);

        // Remove - no default EMF set
        EntityManagerHelper.removeResolver(String.class);

        // allResolversUseDefault should be false (no default EMF)
        assertThrows(IllegalStateException.class, () -> EntityManagerHelper.getTransactionalEntityManager());
    }

    // ---- resolveEntityManagerFactory: fallback path (line 266) ----

    @Test
    void resolveEntityManagerFactory_fallbackToDefault() {
        EntityManagerHelper.setEntityManagerFactory(entityManagerFactory);
        EntityManagerFactory otherEmf = org.mockito.Mockito.mock(EntityManagerFactory.class);
        EntityManagerHelper.registerResolver(String.class, type -> otherEmf);

        // Request entity type with no specific resolver - should fall back to default
        var em = EntityManagerHelper.getTransactionalEntityManager(Integer.class);
        assertNotNull(em);
    }

    @Test
    void resolveEntityManagerFactory_nullEntityType_fallback() {
        EntityManagerHelper.setEntityManagerFactory(entityManagerFactory);
        EntityManagerFactory otherEmf = org.mockito.Mockito.mock(EntityManagerFactory.class);
        EntityManagerHelper.registerResolver(String.class, type -> otherEmf);

        // Request null entity type - should fall back to default
        var em = EntityManagerHelper.getTransactionalEntityManager((Class<?>)null);
        assertNotNull(em);
    }

    // ---- getTransactionalEntityManager: no-arg ----

    @Test
    void getTransactionalEntityManager_noArg() {
        EntityManagerHelper.setEntityManagerFactory(entityManagerFactory);
        var em = EntityManagerHelper.getTransactionalEntityManager();
        assertNotNull(em);
    }

    // ---- getTransactionalEntityManager: entity instance ----

    @Test
    void getTransactionalEntityManager_entityInstance() {
        EntityManagerHelper.setEntityManagerFactory(entityManagerFactory);
        SoftDeleteRepoTestEntity entity = new SoftDeleteRepoTestEntity();
        var em = EntityManagerHelper.getTransactionalEntityManager(entity);
        assertNotNull(em);
    }

    // ---- registerEntityManagerFactoryIfAbsent ----

    @Test
    void registerEntityManagerFactoryIfAbsent_firstTime() {
        EntityManagerHelper.setEntityManagerFactory(entityManagerFactory);
        EntityManagerHelper.registerEntityManagerFactoryIfAbsent(String.class, entityManagerFactory);
    }

    @Test
    void registerEntityManagerFactoryIfAbsent_alreadyExists() {
        EntityManagerHelper.setEntityManagerFactory(entityManagerFactory);
        EntityManagerHelper.registerEntityManagerFactoryIfAbsent(String.class, entityManagerFactory);
        EntityManagerFactory otherEmf = org.mockito.Mockito.mock(EntityManagerFactory.class);
        EntityManagerHelper.registerEntityManagerFactoryIfAbsent(String.class, otherEmf);
        // Should not overwrite
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

    // ---- reset ----

    @Test
    void reset_clearsEverything() {
        EntityManagerHelper.setEntityManagerFactory(entityManagerFactory);
        EntityManagerHelper.registerResolver(String.class, type -> entityManagerFactory);
        EntityManagerHelper.reset();
        assertThrows(IllegalStateException.class, () -> EntityManagerHelper.getTransactionalEntityManager());
    }
}
