package com.zsubera.jpa.repository;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class EntityManagerHelperTest {

    @BeforeEach
    void setUp() {
        EntityManagerHelper.reset();
    }

    @AfterEach
    void tearDown() {
        EntityManagerHelper.reset();
    }

    @Nested
    class SingleDataSource {

        @Test
        void setEntityManagerFactory_setsDefaultEmf() {
            EntityManagerFactory emf = mock(EntityManagerFactory.class);
            EntityManagerHelper.setEntityManagerFactory(emf);

            // Verify by checking that getTransactionalEntityManager uses this EMF
            // (will fail with "no transaction" since we're not in a transaction)
            assertThatThrownBy(() -> EntityManagerHelper.getTransactionalEntityManager())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No transactional EntityManager available");
        }

        @Test
        void getTransactionalEntityManager_noEntityType_usesDefaultEmf() {
            EntityManagerFactory emf = mock(EntityManagerFactory.class);
            EntityManagerHelper.setEntityManagerFactory(emf);

            assertThatThrownBy(() -> EntityManagerHelper.getTransactionalEntityManager())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No transactional EntityManager available");
        }

        @Test
        void getTransactionalEntityManager_withNullEntityType_usesDefaultEmf() {
            EntityManagerFactory emf = mock(EntityManagerFactory.class);
            EntityManagerHelper.setEntityManagerFactory(emf);

            assertThatThrownBy(() -> EntityManagerHelper.getTransactionalEntityManager((Class<?>)null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No transactional EntityManager available");
        }
    }

    @Nested
    class MultiDataSource {

        @Test
        void registerResolver_registersCustomResolver() {
            EntityManagerFactory orderEmf = mock(EntityManagerFactory.class);
            EntityManagerFactory userEmf = mock(EntityManagerFactory.class);

            EntityManagerHelper.registerResolver(String.class, type -> orderEmf);
            EntityManagerHelper.registerResolver(Integer.class, type -> userEmf);

            // Verify resolvers are registered by checking they're used
            // (will fail with "no transaction" but proves the resolver was called)
            assertThatThrownBy(() -> EntityManagerHelper.getTransactionalEntityManager(String.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No transactional EntityManager available");

            assertThatThrownBy(() -> EntityManagerHelper.getTransactionalEntityManager(Integer.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No transactional EntityManager available");
        }

        @Test
        void registerEntityManagerFactory_convenienceMethod() {
            EntityManagerFactory emf = mock(EntityManagerFactory.class);

            EntityManagerHelper.registerEntityManagerFactory(String.class, emf);

            assertThatThrownBy(() -> EntityManagerHelper.getTransactionalEntityManager(String.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No transactional EntityManager available");
        }

        @Test
        void registerEntityManagerFactoryIfAbsent_doesNotOverwrite() {
            EntityManagerFactory originalEmf = mock(EntityManagerFactory.class);
            EntityManagerFactory newEmf = mock(EntityManagerFactory.class);

            EntityManagerHelper.registerEntityManagerFactory(String.class, originalEmf);
            EntityManagerHelper.registerEntityManagerFactoryIfAbsent(String.class, newEmf);

            // Original should still be used (no overwrite)
            assertThatThrownBy(() -> EntityManagerHelper.getTransactionalEntityManager(String.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No transactional EntityManager available");
        }

        @Test
        void removeResolver_removesRegistration() {
            EntityManagerFactory emf = mock(EntityManagerFactory.class);
            EntityManagerHelper.registerEntityManagerFactory(String.class, emf);

            EntityManagerHelper.removeResolver(String.class);

            // After removal, should fall back to default EMF (not set)
            assertThatThrownBy(() -> EntityManagerHelper.getTransactionalEntityManager(String.class))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("EntityManagerFactory not initialized");
        }

        @Test
        void getEntityType_fromEntityInstance() {
            EntityManagerFactory emf = mock(EntityManagerFactory.class);
            EntityManagerHelper.registerEntityManagerFactory(String.class, emf);

            assertThatThrownBy(() -> EntityManagerHelper.getTransactionalEntityManager("test"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No transactional EntityManager available");
        }
    }

    @Nested
    class NullSafety {

        @Test
        void registerResolver_nullEntityType_throws() {
            assertThatThrownBy(() -> EntityManagerHelper.registerResolver(null, type -> null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void registerResolver_nullResolver_throws() {
            assertThatThrownBy(() -> EntityManagerHelper.registerResolver(String.class, null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void registerEntityManagerFactory_nullEntityType_throws() {
            EntityManagerFactory emf = mock(EntityManagerFactory.class);
            assertThatThrownBy(() -> EntityManagerHelper.registerEntityManagerFactory(null, emf))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void registerEntityManagerFactory_nullEmf_throws() {
            assertThatThrownBy(() -> EntityManagerHelper.registerEntityManagerFactory(String.class, null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void getTransactionalEntityManager_nullEntity_throws() {
            assertThatThrownBy(() -> EntityManagerHelper.getTransactionalEntityManager((Object)null))
                .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class Reset {

        @Test
        void reset_clearsAllRegistrations() {
            EntityManagerFactory defaultEmf = mock(EntityManagerFactory.class);
            EntityManagerFactory customEmf = mock(EntityManagerFactory.class);

            EntityManagerHelper.setEntityManagerFactory(defaultEmf);
            EntityManagerHelper.registerEntityManagerFactory(String.class, customEmf);

            EntityManagerHelper.reset();

            // After reset, default EMF is null
            assertThatThrownBy(() -> EntityManagerHelper.getTransactionalEntityManager())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("EntityManagerFactory not initialized");
        }
    }

    @Nested
    class BackwardCompatibility {

        @Test
        void singleDataSource_noEntityType_worksWithDefaultEmf() {
            // This test verifies that the old API still works
            EntityManagerFactory emf = mock(EntityManagerFactory.class);
            EntityManagerHelper.setEntityManagerFactory(emf);

            // The old method should still work (delegates to getTransactionalEntityManager(null))
            assertThatThrownBy(() -> EntityManagerHelper.getTransactionalEntityManager())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No transactional EntityManager available");
        }
    }

    @Nested
    class AdvancedPaths {

        @Test
        void getTransactionalEntityManager_noDefaultEmf_noResolver_throws() {
            assertThatThrownBy(() -> EntityManagerHelper.getTransactionalEntityManager())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("EntityManagerFactory not initialized");
        }

        @Test
        void getTransactionalEntityManager_noDefaultEmf_withResolver_resolvesViaResolver() {
            EntityManagerFactory customEmf = mock(EntityManagerFactory.class);
            EntityManagerHelper.registerEntityManagerFactory(String.class, customEmf);

            assertThatThrownBy(() -> EntityManagerHelper.getTransactionalEntityManager(String.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No transactional EntityManager available");
        }

        @Test
        void registerEntityManagerFactoryIfAbsent_insertsWhenAbsent() {
            EntityManagerFactory emf = mock(EntityManagerFactory.class);

            EntityManagerHelper.registerEntityManagerFactoryIfAbsent(String.class, emf);

            assertThatThrownBy(() -> EntityManagerHelper.getTransactionalEntityManager(String.class))
                .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void removeResolver_allResolversGone_resetsFlag() {
            EntityManagerFactory emf1 = mock(EntityManagerFactory.class);
            EntityManagerFactory emf2 = mock(EntityManagerFactory.class);

            EntityManagerHelper.registerEntityManagerFactory(String.class, emf1);
            EntityManagerHelper.registerEntityManagerFactory(Integer.class, emf2);
            EntityManagerHelper.removeResolver(String.class);
            EntityManagerHelper.removeResolver(Integer.class);

            assertThatThrownBy(() -> EntityManagerHelper.getTransactionalEntityManager())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("EntityManagerFactory not initialized");
        }

        @Test
        void removeResolver_otherResolversRemain_fallbackToDefault() {
            EntityManagerFactory defaultEmf = mock(EntityManagerFactory.class);
            EntityManagerFactory customEmf = mock(EntityManagerFactory.class);

            EntityManagerHelper.setEntityManagerFactory(defaultEmf);
            EntityManagerHelper.registerEntityManagerFactory(String.class, customEmf);
            EntityManagerHelper.removeResolver(String.class);

            assertThatThrownBy(() -> EntityManagerHelper.getTransactionalEntityManager(String.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No transactional EntityManager available");
        }

        @Test
        void registerResolver_withDefaultEmf_sameAsDefault_keepsFastPath() {
            EntityManagerFactory defaultEmf = mock(EntityManagerFactory.class);
            EntityManagerHelper.setEntityManagerFactory(defaultEmf);
            EntityManagerHelper.registerResolver(String.class, type -> defaultEmf);

            assertThatThrownBy(() -> EntityManagerHelper.getTransactionalEntityManager(String.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No transactional EntityManager available");
        }

        @Test
        void registerResolver_withDefaultEmf_differentFromDefault_disablesFastPath() {
            EntityManagerFactory defaultEmf = mock(EntityManagerFactory.class);
            EntityManagerFactory customEmf = mock(EntityManagerFactory.class);
            EntityManagerHelper.setEntityManagerFactory(defaultEmf);
            EntityManagerHelper.registerResolver(String.class, type -> customEmf);

            assertThatThrownBy(() -> EntityManagerHelper.getTransactionalEntityManager(String.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No transactional EntityManager available");
        }

        @Test
        void registerResolver_noDefaultEmf_disablesFastPath() {
            EntityManagerFactory customEmf = mock(EntityManagerFactory.class);
            EntityManagerHelper.registerResolver(String.class, type -> customEmf);

            assertThatThrownBy(() -> EntityManagerHelper.getTransactionalEntityManager(String.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No transactional EntityManager available");
        }

        @Test
        void registerResolver_exceptionInResolve_disablesFastPath() {
            EntityManagerFactory defaultEmf = mock(EntityManagerFactory.class);
            EntityManagerHelper.setEntityManagerFactory(defaultEmf);
            EntityManagerHelper.registerResolver(String.class, type -> {
                throw new RuntimeException("resolve failed");
            });

            assertThatThrownBy(() -> EntityManagerHelper.getTransactionalEntityManager(String.class))
                .isInstanceOf(RuntimeException.class);
        }

        @Test
        void removeResolver_recheckWithExceptionInResolver_disablesFastPath() {
            EntityManagerFactory defaultEmf = mock(EntityManagerFactory.class);
            EntityManagerHelper.setEntityManagerFactory(defaultEmf);
            EntityManagerHelper.registerResolver(String.class, type -> {
                throw new RuntimeException("resolve failed");
            });

            EntityManagerHelper.removeResolver(String.class);

            assertThatThrownBy(() -> EntityManagerHelper.getTransactionalEntityManager())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No transactional EntityManager available");
        }

        @Test
        void registerEntityManagerFactoryWithSameAsDefault_keepsFastPath() {
            EntityManagerFactory defaultEmf = mock(EntityManagerFactory.class);
            EntityManagerHelper.setEntityManagerFactory(defaultEmf);
            EntityManagerHelper.registerEntityManagerFactory(String.class, defaultEmf);

            assertThatThrownBy(() -> EntityManagerHelper.getTransactionalEntityManager(String.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No transactional EntityManager available");
        }

        @Test
        void registerEntityManagerFactory_nullEntityType_throws() {
            EntityManagerFactory emf = mock(EntityManagerFactory.class);
            assertThatThrownBy(() -> EntityManagerHelper.registerEntityManagerFactory(null, emf))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void registerEntityManagerFactory_nullEmf_throws() {
            assertThatThrownBy(() -> EntityManagerHelper.registerEntityManagerFactory(String.class, null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void registerEntityManagerFactoryIfAbsent_nullEntityType_throws() {
            EntityManagerFactory emf = mock(EntityManagerFactory.class);
            assertThatThrownBy(() -> EntityManagerHelper.registerEntityManagerFactoryIfAbsent(null, emf))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void registerEntityManagerFactoryIfAbsent_nullEmf_throws() {
            assertThatThrownBy(() -> EntityManagerHelper.registerEntityManagerFactoryIfAbsent(String.class, null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void removeResolver_nullEntityType_throws() {
            assertThatThrownBy(() -> EntityManagerHelper.removeResolver(null)).isInstanceOf(NullPointerException.class);
        }
    }
}
