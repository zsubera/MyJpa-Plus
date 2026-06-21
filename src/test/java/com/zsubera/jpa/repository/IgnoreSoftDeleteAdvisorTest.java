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

/**
 * Tests for {@link IgnoreSoftDeleteAdvisor} AOP interception and {@link SoftDeleteContext} integration.
 */
@DataJpaTest
@org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase(
    replace = org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = IgnoreSoftDeleteAdvisorTest.TestConfig.class)
class IgnoreSoftDeleteAdvisorTest {

    @SpringBootApplication
    @EntityScan(basePackageClasses = SoftDeleteRepoTestEntity.class)
    @EnableJpaRepositories(basePackages = "com.zsubera.jpa.repository",
        repositoryBaseClass = DefaultMyJpaRepository.class)
    static class TestConfig {}

    @Autowired
    private SoftDeleteRepoTestRepository repository;

    @BeforeEach
    void setup() {
        com.zsubera.jpa.autoconfigure.GlobalConfigHolder.getConfig().setSoftDeleteAutoFilter(true);
        SoftDeleteContext.reset();
    }

    @AfterEach
    void cleanup() {
        SoftDeleteContext.reset();
        com.zsubera.jpa.autoconfigure.GlobalConfigHolder.getConfig().setSoftDeleteAutoFilter(true);
    }

    @Test
    void advisor_contextIsCleanByDefault() {
        assertFalse(SoftDeleteContext.isIgnoreSoftDelete());
    }

    @Test
    void advisor_pushIgnoreSetsContext() {
        SoftDeleteContext.pushIgnore();
        assertTrue(SoftDeleteContext.isIgnoreSoftDelete());
        SoftDeleteContext.popIgnore();
    }

    @Test
    void advisor_popIgnoreClearsContext() {
        SoftDeleteContext.pushIgnore();
        SoftDeleteContext.popIgnore();
        assertFalse(SoftDeleteContext.isIgnoreSoftDelete());
    }

    @Test
    void advisor_contextAffectsRepositoryBehavior() {
        saveEntity("active", false);
        saveEntity("deleted", true);

        // Without ignore - filters deleted
        List<SoftDeleteRepoTestEntity> filtered = repository.findAll();
        assertEquals(1, filtered.size());

        // With ignore - returns all
        SoftDeleteContext.pushIgnore();
        try {
            List<SoftDeleteRepoTestEntity> all = repository.findAll();
            assertEquals(2, all.size());
        } finally {
            SoftDeleteContext.popIgnore();
        }

        // After pop - filters again
        List<SoftDeleteRepoTestEntity> afterPop = repository.findAll();
        assertEquals(1, afterPop.size());
    }

    @Test
    void advisor_nestedIgnoreWorks() {
        saveEntity("active", false);
        saveEntity("deleted", true);

        // Outer ignore
        SoftDeleteContext.pushIgnore();
        assertTrue(SoftDeleteContext.isIgnoreSoftDelete());

        // Inner ignore
        SoftDeleteContext.pushIgnore();
        assertTrue(SoftDeleteContext.isIgnoreSoftDelete());

        // Pop inner
        SoftDeleteContext.popIgnore();
        // Still ignoring because of outer
        assertTrue(SoftDeleteContext.isIgnoreSoftDelete());

        // Repository should still return all
        List<SoftDeleteRepoTestEntity> result = repository.findAll();
        assertEquals(2, result.size());

        // Pop outer
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
