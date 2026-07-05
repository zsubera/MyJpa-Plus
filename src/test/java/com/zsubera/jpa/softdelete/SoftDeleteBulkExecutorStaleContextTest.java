package com.zsubera.jpa.softdelete;

import static org.junit.jupiter.api.Assertions.*;

import com.zsubera.jpa.spec.SoftDeleteTestEntity;
import com.zsubera.jpa.spec.SoftDeleteTestEntityRepository;
import com.zsubera.jpa.spec.TestApplication;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = TestApplication.class)
class SoftDeleteBulkExecutorStaleContextTest {

    @Autowired
    private SoftDeleteTestEntityRepository repository;

    @PersistenceContext
    private EntityManager em;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        repository.flush();
    }

    @Test
    void softDeleteAllUsingCriteriaUpdate_clearsL1Cache() {
        SoftDeleteTestEntity entity = new SoftDeleteTestEntity();
        entity.setName("test");
        repository.saveAndFlush(entity);
        Long id = entity.getId();
        em.clear();

        SoftDeleteTestEntity loaded = em.find(SoftDeleteTestEntity.class, id);
        assertNotNull(loaded);
        assertFalse(loaded.getDeleted());

        int count = SoftDeleteBulkExecutor.softDeleteAllUsingCriteriaUpdate(em, SoftDeleteTestEntity.class, true);
        assertEquals(1, count);

        SoftDeleteTestEntity found = em.find(SoftDeleteTestEntity.class, id);
        assertNotNull(found);
        assertTrue(found.getDeleted());
    }

    @Test
    void softDeleteByIdsUsingEntityManager_alsoClearsL1() {
        SoftDeleteTestEntity entity = new SoftDeleteTestEntity();
        entity.setName("test2");
        repository.saveAndFlush(entity);
        Long id = entity.getId();
        em.clear();

        SoftDeleteTestEntity loaded = em.find(SoftDeleteTestEntity.class, id);
        assertFalse(loaded.getDeleted());

        int count =
            SoftDeleteBulkExecutor.softDeleteByIdsUsingEntityManager(em, SoftDeleteTestEntity.class, List.of(id));
        assertEquals(1, count);

        SoftDeleteTestEntity found = em.find(SoftDeleteTestEntity.class, id);
        assertTrue(found.getDeleted());
    }
}
