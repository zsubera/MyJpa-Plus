package com.zsubera.jpa.util;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CacheEvictionHelperTest {

    @Mock
    private EntityManager em;

    @Test
    void evictEntityCache_nullEntityClass_callsEmClear() {
        CacheEvictionHelper.evictEntityCache(em, null);
        verify(em).clear();
    }

    @Test
    void evictEntityCache_nonNullEntityClass_callsEmClear() {
        CacheEvictionHelper.evictEntityCache(em, Object.class);
        verify(em).clear();
    }

    @Test
    void evictEntityCache_hibernatePath_evictsL2AndL1() throws Exception {
        Session mockSession = mock(Session.class, RETURNS_DEEP_STUBS);
        when(em.getDelegate()).thenReturn(mockSession);
        when(em.unwrap(Session.class)).thenReturn(mockSession);

        CacheEvictionHelper.evictEntityCache(em, String.class);

        verify(mockSession.getSessionFactory().getCache()).evictEntityData(String.class);
        verify(em).clear();
    }

    @Test
    void evictEntityCache_nonHibernateDelegate_skipsL2() {
        when(em.getDelegate()).thenReturn("not-a-session");

        CacheEvictionHelper.evictEntityCache(em, Integer.class);

        verify(em).clear();
    }
}
