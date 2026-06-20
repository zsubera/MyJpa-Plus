package com.zsubera.jpa.repository;

import static org.junit.jupiter.api.Assertions.*;

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
@ContextConfiguration(classes = IgnoreSoftDeleteAdvisorExtendedTest.TestConfig.class)
class IgnoreSoftDeleteAdvisorExtendedTest {

    @SpringBootApplication
    @EntityScan(basePackageClasses = SoftDeleteRepoTestEntity.class)
    @EnableJpaRepositories(basePackages = "com.zsubera.jpa.repository",
        repositoryBaseClass = DefaultMyJpaRepository.class)
    static class TestConfig {}

    @Autowired
    private SoftDeleteRepoTestRepository repository;

    @BeforeEach
    void setup() {
        DefaultMyJpaRepository.setAutoFilterEnabled(true);
        SoftDeleteContext.reset();
    }

    @AfterEach
    void cleanup() {
        SoftDeleteContext.reset();
        DefaultMyJpaRepository.setAutoFilterEnabled(true);
    }

    @Test
    void advisor_annotationCacheWorks() {
        saveEntity("active", false);
        saveEntity("deleted", true);

        List<SoftDeleteRepoTestEntity> filtered = repository.findAll();
        assertEquals(1, filtered.size());

        List<SoftDeleteRepoTestEntity> filtered2 = repository.findAll();
        assertEquals(1, filtered2.size());
    }

    @Test
    void advisor_ignoresSoftDeleteForAnnotatedMethod() {
        saveEntity("active", false);
        saveEntity("deleted", true);

        SoftDeleteContext.pushIgnore();
        try {
            List<SoftDeleteRepoTestEntity> all = repository.findAll();
            assertEquals(2, all.size());
        } finally {
            SoftDeleteContext.popIgnore();
        }

        List<SoftDeleteRepoTestEntity> filtered = repository.findAll();
        assertEquals(1, filtered.size());
    }

    @Test
    void advisor_popMultipleTimes() {
        SoftDeleteContext.pushIgnore();
        SoftDeleteContext.pushIgnore();
        SoftDeleteContext.popIgnore();
        SoftDeleteContext.popIgnore();
        assertFalse(SoftDeleteContext.isIgnoreSoftDelete());
    }

    @Test
    void advisor_popBelowZero_doesNotCrash() {
        SoftDeleteContext.popIgnore();
        assertFalse(SoftDeleteContext.isIgnoreSoftDelete());
    }

    private SoftDeleteRepoTestEntity saveEntity(String name, boolean deleted) {
        SoftDeleteRepoTestEntity entity = new SoftDeleteRepoTestEntity();
        entity.setName(name);
        entity.setDeleted(deleted);
        return repository.save(entity);
    }
}
