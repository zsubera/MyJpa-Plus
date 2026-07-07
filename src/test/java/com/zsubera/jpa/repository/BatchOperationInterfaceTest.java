package com.zsubera.jpa.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.zsubera.jpa.spec.QuerySpec;
import com.zsubera.jpa.update.UpdateSpec;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;

/**
 * Verifies that batch operation methods are callable directly on the repository interface
 * when using {@link MyJpaRepositoryFactoryBean}.
 *
 * <p>
 * Batch operations ({@code update}, {@code delete}, {@code merge}, {@code execute}) are
 * declared as {@code default} methods on {@link MyJpaRepository}, using
 * {@link EntityManagerHelper} to obtain the transactional {@code EntityManager}.
 * This design allows direct calling without casting:
 * <pre>{@code
 * repository.update(s -> s.set(User::getStatus, "INACTIVE"));
 * }</pre>
 *
 * <p>
 * For multi-datasource scenarios, use {@code MyJpaTemplate} which accepts
 * {@code EntityManager} as a parameter.
 */
@DataJpaTest
@org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase(
    replace = org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = BatchOperationInterfaceTest.TestConfig.class)
class BatchOperationInterfaceTest {

    @SpringBootApplication
    @EntityScan(basePackageClasses = MyJpaTestEntity.class)
    @EnableJpaRepositories(basePackageClasses = MyJpaTestRepository.class,
        repositoryFactoryBeanClass = MyJpaRepositoryFactoryBean.class)
    static class TestConfig {}

    @Autowired
    private MyJpaTestRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        repository.flush();
    }

    @org.junit.jupiter.api.AfterAll
    static void cleanupAll() {
        // Reset EntityManagerHelper static state AFTER all tests in this class complete.
        // This prevents stale EMF/resolver references from leaking into the next test class
        // (DefaultMyJpaRepositoryBranchTest) which creates its own Spring context.
        EntityManagerHelper.reset();
    }

    @Test
    void factoryBeanCreatesCorrectProxyType() {
        assertThat(repository).isInstanceOf(MyJpaRepository.class);
    }

    @Test
    void defaultMethodCountNotDeletedWorks() {
        MyJpaTestEntity alice = new MyJpaTestEntity();
        alice.setName("Alice");
        alice.setDeleted(false);
        repository.save(alice);

        MyJpaTestEntity bob = new MyJpaTestEntity();
        bob.setName("Bob");
        bob.setDeleted(true);
        repository.save(bob);
        entityManager.flush();
        entityManager.clear();

        long count = repository.count();

        assertThat(count).isEqualTo(1);
    }

    @Test
    void querySpecQueryWorksThroughInterface() {
        MyJpaTestEntity alice = new MyJpaTestEntity();
        alice.setName("Alice");
        alice.setDeleted(false);
        repository.save(alice);

        MyJpaTestEntity bob = new MyJpaTestEntity();
        bob.setName("Bob");
        bob.setDeleted(true);
        repository.save(bob);
        entityManager.flush();
        entityManager.clear();

        List<MyJpaTestEntity> active =
            repository.findAll(new QuerySpec<MyJpaTestEntity>().eq(MyJpaTestEntity::getDeleted, false));

        assertThat(active).hasSize(1);
        assertThat(active.get(0).getName()).isEqualTo("Alice");
    }

    @Test
    void findAllWorksThroughInterface() {
        repository.save(new MyJpaTestEntity());
        repository.save(new MyJpaTestEntity());
        entityManager.flush();
        entityManager.clear();

        List<MyJpaTestEntity> all = repository.findAll();

        assertThat(all).hasSize(2);
    }

    @Test
    void countWorksThroughInterface() {
        repository.save(new MyJpaTestEntity());
        repository.save(new MyJpaTestEntity());
        repository.save(new MyJpaTestEntity());
        entityManager.flush();
        entityManager.clear();

        long count = repository.count();

        assertThat(count).isEqualTo(3);
    }

    @Test
    void saveAndFindByIdWorksThroughInterface() {
        MyJpaTestEntity entity = new MyJpaTestEntity();
        entity.setName("TestEntity");
        MyJpaTestEntity saved = repository.save(entity);
        entityManager.flush();
        entityManager.clear();

        var found = repository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("TestEntity");
    }

    @Test
    void directUpdateOnRepositoryInterface() {
        MyJpaTestEntity alice = new MyJpaTestEntity();
        alice.setName("Alice");
        alice.setDeleted(false);
        repository.save(alice);

        MyJpaTestEntity bob = new MyJpaTestEntity();
        bob.setName("Bob");
        bob.setDeleted(false);
        repository.save(bob);

        MyJpaTestEntity charlie = new MyJpaTestEntity();
        charlie.setName("Charlie");
        charlie.setDeleted(true);
        repository.save(charlie);
        entityManager.flush();
        entityManager.clear();

        Consumer<UpdateSpec<MyJpaTestEntity>> config =
            s -> s.set(MyJpaTestEntity::getDeleted, true).eq(MyJpaTestEntity::getDeleted, false);

        int affected = repository.update(config);

        assertThat(affected).isEqualTo(2);
    }

    @Test
    void directDeleteOnRepositoryInterface() {
        MyJpaTestEntity alice = new MyJpaTestEntity();
        alice.setName("Alice");
        alice.setDeleted(false);
        repository.save(alice);

        MyJpaTestEntity bob = new MyJpaTestEntity();
        bob.setName("Bob");
        bob.setDeleted(true);
        repository.save(bob);

        MyJpaTestEntity charlie = new MyJpaTestEntity();
        charlie.setName("Charlie");
        charlie.setDeleted(false);
        repository.save(charlie);
        entityManager.flush();
        entityManager.clear();

        int affected = repository.delete(s -> s.eq(MyJpaTestEntity::getDeleted, true));

        assertThat(affected).isEqualTo(1);
        entityManager.clear();

        List<MyJpaTestEntity> all = repository.findAll();
        assertThat(all).hasSize(2);
        assertThat(all).allMatch(e -> Boolean.FALSE.equals(e.getDeleted()));
    }

    @Test
    void directExecuteUpdateSpecOnRepositoryInterface() {
        MyJpaTestEntity alice = new MyJpaTestEntity();
        alice.setName("Alice");
        alice.setDeleted(false);
        repository.save(alice);

        MyJpaTestEntity bob = new MyJpaTestEntity();
        bob.setName("Bob");
        bob.setDeleted(false);
        repository.save(bob);
        entityManager.flush();
        entityManager.clear();

        UpdateSpec<MyJpaTestEntity> spec = new UpdateSpec<>(MyJpaTestEntity.class);
        spec.set(MyJpaTestEntity::getDeleted, true).eq(MyJpaTestEntity::getDeleted, false);

        int affected = repository.execute(spec);

        assertThat(affected).isEqualTo(2);
    }
}
