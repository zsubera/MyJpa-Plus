package com.zsubera.jpa.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import jakarta.persistence.EntityManagerFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration test verifying multi-datasource support in {@link EntityManagerHelper}.
 */
class EntityManagerHelperMultiDsTest {

    @BeforeEach
    void setUp() {
        EntityManagerHelper.reset();
    }

    @AfterEach
    void tearDown() {
        EntityManagerHelper.reset();
    }

    @Test
    void differentEntityTypes_resolveToDifferentEmfs() {
        List<Class<?>> resolvedTypes = new ArrayList<>();

        EntityManagerFactory orderEmf = mock(EntityManagerFactory.class);
        EntityManagerFactory userEmf = mock(EntityManagerFactory.class);

        EntityManagerHelper.registerResolver(String.class, type -> {
            resolvedTypes.add(type);
            return orderEmf;
        });
        EntityManagerHelper.registerResolver(Integer.class, type -> {
            resolvedTypes.add(type);
            return userEmf;
        });

        EntityManagerFactory resolvedForString = resolveEmf(String.class);
        EntityManagerFactory resolvedForInteger = resolveEmf(Integer.class);

        assertThat(resolvedForString).isSameAs(orderEmf);
        assertThat(resolvedForInteger).isSameAs(userEmf);
        assertThat(resolvedTypes).containsExactly(String.class, Integer.class);
    }

    @Test
    void unregisteredEntityType_fallsBackToDefaultEmf() {
        EntityManagerFactory defaultEmf = mock(EntityManagerFactory.class);
        EntityManagerFactory orderEmf = mock(EntityManagerFactory.class);

        EntityManagerHelper.setEntityManagerFactory(defaultEmf);
        EntityManagerHelper.registerEntityManagerFactory(String.class, orderEmf);

        EntityManagerFactory resolvedForString = resolveEmf(String.class);
        assertThat(resolvedForString).isSameAs(orderEmf);

        EntityManagerFactory resolvedForInteger = resolveEmf(Integer.class);
        assertThat(resolvedForInteger).isSameAs(defaultEmf);
    }

    @Test
    void resolverCanUseDynamicLogic() {
        AtomicReference<Boolean> useTenant1 = new AtomicReference<>(true);
        EntityManagerFactory tenant1Emf = mock(EntityManagerFactory.class);
        EntityManagerFactory tenant2Emf = mock(EntityManagerFactory.class);

        EntityManagerHelper.registerResolver(String.class, type -> {
            return useTenant1.get() ? tenant1Emf : tenant2Emf;
        });

        assertThat(resolveEmf(String.class)).isSameAs(tenant1Emf);

        useTenant1.set(false);
        assertThat(resolveEmf(String.class)).isSameAs(tenant2Emf);
    }

    @Test
    void customResolver_overridesDirectRegistration() {
        EntityManagerFactory orderEmf = mock(EntityManagerFactory.class);
        EntityManagerFactory customEmf = mock(EntityManagerFactory.class);

        EntityManagerHelper.registerEntityManagerFactory(String.class, orderEmf);
        EntityManagerHelper.registerResolver(String.class, type -> customEmf);

        assertThat(resolveEmf(String.class)).isSameAs(customEmf);
    }

    @Test
    void removeResolver_fallsBackToDefault() {
        EntityManagerFactory defaultEmf = mock(EntityManagerFactory.class);
        EntityManagerFactory orderEmf = mock(EntityManagerFactory.class);

        EntityManagerHelper.setEntityManagerFactory(defaultEmf);
        EntityManagerHelper.registerEntityManagerFactory(String.class, orderEmf);
        EntityManagerHelper.removeResolver(String.class);

        assertThat(resolveEmf(String.class)).isSameAs(defaultEmf);
    }

    @Test
    void registerEntityManagerFactoryIfAbsent_doesNotOverwrite() {
        EntityManagerFactory originalEmf = mock(EntityManagerFactory.class);
        EntityManagerFactory newEmf = mock(EntityManagerFactory.class);

        EntityManagerHelper.registerEntityManagerFactory(String.class, originalEmf);
        EntityManagerHelper.registerEntityManagerFactoryIfAbsent(String.class, newEmf);

        assertThat(resolveEmf(String.class)).isSameAs(originalEmf);
    }

    @Test
    void reset_clearsAllRegistrations() {
        EntityManagerFactory defaultEmf = mock(EntityManagerFactory.class);
        EntityManagerFactory orderEmf = mock(EntityManagerFactory.class);

        EntityManagerHelper.setEntityManagerFactory(defaultEmf);
        EntityManagerHelper.registerEntityManagerFactory(String.class, orderEmf);
        EntityManagerHelper.reset();

        assertThatThrownBy(() -> resolveEmf(String.class)).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("EntityManagerFactory not initialized");
    }

    @Test
    void noDefaultEmf_noResolver_throwsException() {
        assertThatThrownBy(() -> resolveEmf(String.class)).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("EntityManagerFactory not initialized");
    }

    private EntityManagerFactory resolveEmf(Class<?> entityType) {
        try {
            java.lang.reflect.Method method =
                EntityManagerHelper.class.getDeclaredMethod("resolveEntityManagerFactory", Class.class);
            method.setAccessible(true);
            return (EntityManagerFactory)method.invoke(null, entityType);
        } catch (java.lang.reflect.InvocationTargetException e) {
            // Unwrap the actual exception thrown by the method
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException("Failed to invoke resolveEntityManagerFactory", cause);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke resolveEntityManagerFactory", e);
        }
    }
}
