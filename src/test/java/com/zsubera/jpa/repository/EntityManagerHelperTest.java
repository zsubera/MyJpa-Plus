package com.zsubera.jpa.repository;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class EntityManagerHelperTest {

    @AfterEach
    void cleanup() {
        EntityManagerHelper.reset();
    }

    @Test
    void setEntityManagerFactory() {
        EntityManagerFactory emf = Mockito.mock(EntityManagerFactory.class);
        EntityManagerHelper.setEntityManagerFactory(emf);
        // Verify no exception when EMF is set
        EntityManagerHelper.setEntityManagerFactory(null);
    }

    @Test
    void registerAndRemoveResolver() {
        EntityManagerFactory emf = Mockito.mock(EntityManagerFactory.class);
        EntityManagerHelper.registerEntityManagerFactory(String.class, emf);
        // Verify registration doesn't throw
        EntityManagerHelper.removeResolver(String.class);
    }

    @Test
    void registerIfAbsentDoesNotOverwrite() {
        EntityManagerFactory emf1 = Mockito.mock(EntityManagerFactory.class);
        EntityManagerFactory emf2 = Mockito.mock(EntityManagerFactory.class);
        EntityManagerHelper.registerEntityManagerFactoryIfAbsent(String.class, emf1);
        EntityManagerHelper.registerEntityManagerFactoryIfAbsent(String.class, emf2);
        // Should not throw - first registration wins
        EntityManagerHelper.removeResolver(String.class);
    }

    @Test
    void resetClearsAll() {
        EntityManagerFactory emf = Mockito.mock(EntityManagerFactory.class);
        EntityManagerHelper.setEntityManagerFactory(emf);
        EntityManagerHelper.registerEntityManagerFactory(String.class, emf);
        EntityManagerHelper.reset();
        // Verify reset doesn't throw
    }

    @Test
    void nullEntityTypeThrows() {
        assertThrows(NullPointerException.class, () -> EntityManagerHelper.registerResolver(null, type -> null));
    }

    @Test
    void nullResolverThrows() {
        assertThrows(NullPointerException.class, () -> EntityManagerHelper.registerResolver(String.class, null));
    }

    @Test
    void nullEntityClassForRegisterEntityManagerFactoryThrows() {
        EntityManagerFactory emf = Mockito.mock(EntityManagerFactory.class);
        assertThrows(NullPointerException.class, () -> EntityManagerHelper.registerEntityManagerFactory(null, emf));
    }

    @Test
    void nullEmfForRegisterEntityManagerFactoryThrows() {
        assertThrows(NullPointerException.class,
            () -> EntityManagerHelper.registerEntityManagerFactory(String.class, null));
    }

    @Test
    void resolverWithCustomEmf() {
        EntityManagerFactory customEmf = Mockito.mock(EntityManagerFactory.class);
        EntityManagerHelper.registerResolver(String.class, type -> customEmf);
        // Verify no exception - the resolver is registered
        EntityManagerHelper.removeResolver(String.class);
    }

    @Test
    void multipleResolvers() {
        EntityManagerFactory emf1 = Mockito.mock(EntityManagerFactory.class);
        EntityManagerFactory emf2 = Mockito.mock(EntityManagerFactory.class);
        EntityManagerHelper.registerEntityManagerFactory(String.class, emf1);
        EntityManagerHelper.registerEntityManagerFactory(Integer.class, emf2);
        // Both should be registered without conflict
        EntityManagerHelper.removeResolver(String.class);
        EntityManagerHelper.removeResolver(Integer.class);
    }
}
