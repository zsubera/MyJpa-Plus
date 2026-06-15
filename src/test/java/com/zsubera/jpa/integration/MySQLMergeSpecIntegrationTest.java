package com.zsubera.jpa.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.zsubera.jpa.update.MergeSpec;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

@Tag("integration")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:mysql://localhost:3306/test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
    "spring.datasource.username=root", "spring.datasource.password=1351.zhong",
    "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
    "spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect", "spring.jpa.hibernate.ddl-auto=create"})
@Transactional
class MySQLMergeSpecIntegrationTest {

    @Autowired
    private MySQLTestEntityRepository repository;

    @PersistenceContext
    private EntityManager em;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        repository.flush();
    }

    @Test
    void mergeInsertNew() {
        MySQLTestEntity entity = new MySQLTestEntity();
        entity.setName("new_entity");
        entity.setStatus(1);

        int count = new MergeSpec<>(MySQLTestEntity.class).withEntity(entity).execute(em);
        em.flush();
        em.clear();

        assertEquals(1, count);
        List<MySQLTestEntity> all = repository.findAll();
        assertEquals(1, all.size());
        assertEquals("new_entity", all.get(0).getName());
        assertEquals(Integer.valueOf(1), all.get(0).getStatus());
    }

    @Test
    void mergeUpdateExistingByDefaultId() {
        MySQLTestEntity saved = new MySQLTestEntity();
        saved.setName("original");
        saved.setStatus(1);
        repository.save(saved);
        em.flush();
        em.clear();

        MySQLTestEntity entity = new MySQLTestEntity();
        entity.setId(saved.getId());
        entity.setName("original");
        entity.setStatus(99);

        int count = new MergeSpec<>(MySQLTestEntity.class).withEntity(entity).execute(em);
        em.flush();
        em.clear();

        assertEquals(2, count, "MySQL UPSERT returns 2 for UPDATE");
        MySQLTestEntity found = repository.findById(saved.getId()).orElseThrow();
        assertEquals("original", found.getName());
        assertEquals(Integer.valueOf(99), found.getStatus());
    }

    @Test
    void mergeWithExplicitConflictColumns() {
        MySQLTestEntity existing = new MySQLTestEntity();
        existing.setName("unique_name");
        existing.setStatus(1);
        repository.save(existing);
        em.flush();
        em.clear();

        MySQLTestEntity entity = new MySQLTestEntity();
        entity.setName("unique_name");
        entity.setStatus(99);

        int count =
            new MergeSpec<>(MySQLTestEntity.class).withEntity(entity).onConflict(MySQLTestEntity::getName).execute(em);
        em.flush();
        em.clear();

        assertEquals(2, count, "MySQL UPSERT returns 2 for UPDATE");
        List<MySQLTestEntity> all = repository.findAll();
        assertEquals(1, all.size());
        assertEquals(Integer.valueOf(99), all.get(0).getStatus());
    }

    @Test
    void mergeWithUpdateOnConflictSpecificColumns() {
        MySQLTestEntity existing = new MySQLTestEntity();
        existing.setName("conflict");
        existing.setStatus(1);
        repository.save(existing);
        em.flush();
        em.clear();

        MySQLTestEntity entity = new MySQLTestEntity();
        entity.setName("conflict");
        entity.setStatus(2);

        int count = new MergeSpec<>(MySQLTestEntity.class).withEntity(entity).onConflict(MySQLTestEntity::getName)
            .updateOnConflict(MySQLTestEntity::getStatus).execute(em);
        em.flush();
        em.clear();

        assertEquals(2, count, "MySQL UPSERT returns 2 for UPDATE");
        MySQLTestEntity found = repository.findById(existing.getId()).orElseThrow();
        assertEquals("conflict", found.getName());
        assertEquals(Integer.valueOf(2), found.getStatus());
    }

    @Test
    void mergeBatchInsert() {
        List<MySQLTestEntity> entities = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            MySQLTestEntity e = new MySQLTestEntity();
            e.setName("batch_" + i);
            e.setStatus(i);
            entities.add(e);
        }

        int count = new MergeSpec<>(MySQLTestEntity.class).executeBatch(entities, em, 100);
        em.flush();
        em.clear();

        assertEquals(5, count);
        assertEquals(5, repository.count());
    }

    @Test
    void mergeBatchUpsertMixed() {
        MySQLTestEntity existing = new MySQLTestEntity();
        existing.setName("existing");
        existing.setStatus(1);
        repository.save(existing);
        em.flush();
        em.clear();

        List<MySQLTestEntity> entities = new ArrayList<>();

        MySQLTestEntity update = new MySQLTestEntity();
        update.setName("existing");
        update.setStatus(99);
        entities.add(update);

        MySQLTestEntity insert = new MySQLTestEntity();
        insert.setName("new_entry");
        insert.setStatus(2);
        entities.add(insert);

        int count = new MergeSpec<>(MySQLTestEntity.class).executeBatch(entities, em, 100);
        em.flush();
        em.clear();

        assertEquals(3, count, "MySQL UPSERT: 1 update returns 2 + 1 insert returns 1");
        assertEquals(2, repository.count());

        MySQLTestEntity updated =
            repository.findAll().stream().filter(e -> "existing".equals(e.getName())).findFirst().orElseThrow();
        assertEquals(Integer.valueOf(99), updated.getStatus());
    }

    @Test
    void mergeBatchWithLargeDataset() {
        List<MySQLTestEntity> entities = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            MySQLTestEntity e = new MySQLTestEntity();
            e.setName("large_" + i);
            e.setStatus(i);
            entities.add(e);
        }

        int count = new MergeSpec<>(MySQLTestEntity.class).executeBatch(entities, em, 20);
        em.flush();
        em.clear();

        assertEquals(50, count);
        assertEquals(50, repository.count());
    }

    @Test
    void mergeWithoutOnConflictUsesId() {
        MySQLTestEntity saved = new MySQLTestEntity();
        saved.setName("by_id");
        saved.setStatus(1);
        repository.save(saved);
        em.flush();
        em.clear();

        MySQLTestEntity entity = new MySQLTestEntity();
        entity.setId(saved.getId());
        entity.setName("by_id_updated");
        entity.setStatus(100);

        int count = new MergeSpec<>(MySQLTestEntity.class).withEntity(entity).execute(em);
        em.flush();
        em.clear();

        assertEquals(2, count, "MySQL UPSERT returns 2 for UPDATE");
        MySQLTestEntity found = repository.findById(saved.getId()).orElseThrow();
        assertEquals("by_id_updated", found.getName());
        assertEquals(Integer.valueOf(100), found.getStatus());
    }

    @Test
    void mergeInsertReturnsOneForMySQL() {
        MySQLTestEntity entity = new MySQLTestEntity();
        entity.setName("insert_check");
        entity.setStatus(5);

        int count = new MergeSpec<>(MySQLTestEntity.class).withEntity(entity).execute(em);
        em.flush();
        em.clear();

        assertEquals(1, count, "MySQL UPSERT should return 1 for INSERT");
    }

    @Test
    void mergeUpdateReturnsTwoForMySQL() {
        MySQLTestEntity existing = new MySQLTestEntity();
        existing.setName("update_check");
        existing.setStatus(1);
        repository.save(existing);
        em.flush();
        em.clear();

        MySQLTestEntity entity = new MySQLTestEntity();
        entity.setName("update_check");
        entity.setStatus(2);

        int count =
            new MergeSpec<>(MySQLTestEntity.class).withEntity(entity).onConflict(MySQLTestEntity::getName).execute(em);
        em.flush();
        em.clear();

        assertEquals(2, count, "MySQL UPSERT should return 2 for UPDATE");
    }

    @Test
    void mergeInTransaction() {
        MySQLTestEntity entity = new MySQLTestEntity();
        entity.setName("tx_entity");
        entity.setStatus(1);

        int count = new MergeSpec<>(MySQLTestEntity.class).withEntity(entity).executeInTransaction(em);
        em.flush();
        em.clear();

        assertEquals(1, count);
        assertEquals(1, repository.count());
    }
}
