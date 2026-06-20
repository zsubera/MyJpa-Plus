# AGENTS.md

## Project Overview

MyJpa-Plus is a type-safe JPA utility library built on Lambda expressions. Provides query building, batch operations, UPSERT/MERGE, CTE, projection queries, field encryption/masking, SQL slow-query monitoring.

**Tech stack**: Java 17+, Spring Boot 3.3.5, Hibernate 6.x, Spring Data JPA

## Commands

```bash
# Format check (CI step 1, must pass first)
./mvnw spotless:check

# Fix formatting
./mvnw spotless:apply

# Unit tests (exclude integration)
./mvnw test -DexcludedGroups=integration

# Full verification (CI standard)
./mvnw clean verify -Dgpg.skip=true -Ddependency-check.skip=true

# Integration tests (needs Docker)
./mvnw test -Dgroups=integration

# Single test class
./mvnw test -Dtest=QuerySpecTest -DexcludedGroups=integration

# Coverage report
./mvnw jacoco:report
```

**Windows**: PowerShell requires quoting for `-D` flags:
```powershell
.\mvnw.cmd test "-Dtest=QuerySpecTest" "-DexcludedGroups=integration"
.\mvnw.cmd clean verify "-Dgpg.skip=true" "-Ddependency-check.skip=true"
```

## CI Pipeline

`.github/workflows/ci.yml` — 3 jobs:
1. **build** (Java 17+21 matrix, MySQL 8.0 service): `spotless:check` → `clean verify`
2. **integration** (needs build): Testcontainers PostgreSQL + MySQL
3. **release-deploy**: GPG + Maven Central publish on `v*` tags

CI MySQL password is `ci_test_2024`. Local password is `1351.zhong`.

## Code Style

- **Formatter**: Spotless + Eclipse style (`eclipse-codestyle.xml`)
- Use method references (`Entity::getField`) for type safety — no hardcoded field name strings
- `ConditionNode` is sealed; all implementations must be `final`
- New condition types require 7-file sync (see checklist below)
- Public API parameters must have null validation

## Package Structure

| Package | Responsibility |
|---|---|
| `spec` | Query building: QuerySpec, ConditionBuilder, ConditionNode, NodeResolver, SubQuerySpec |
| `update` | Bulk ops: UpdateSpec, DeleteSpec, MergeSpec, AbstractBulkOperationSpec, DialectDetector |
| `repository` | Extended Repository: MyJpaRepository, DefaultMyJpaRepository, SoftDeleteContext, OptimisticLockRetryAdvisor, IgnoreSoftDeleteAdvisor |
| `projection` | Projection queries |
| `template` | Template & cache: MyJpaTemplate, BatchSaveTemplate, KeysetPaginationHelper, QueryCacheManager |
| `annotation` | @SoftDelete, @Encrypt, @Mask, @RetryOnOptimisticLock, @IgnoreSoftDelete, @CodeEnumValue |
| `autoconfigure` | Auto-configuration: GlobalConfigHolder, MyJpaPlusAutoConfiguration, SoftDeleteFilterBean, MyJpaPlusProperties |
| `converter` | CodeEnumType, EncryptConverter, MaskSerializer |
| `util` | LambdaUtils, IdentifierValidator, InClauseBuilder, EntityClassResolver |
| `monitor` | SQL monitoring: SqlSlowQueryInterceptor, QueryMetricsCollector |
| `softdelete` | SoftDeleteHelper |

## Key Architecture

- **Dual-track condition building**: Queries use `ConditionNode` tree (lazy eval), bulk ops use `BulkConditionNode` (Lambda wrapper). New conditions require both.
- **`AbstractBulkOperationSpec`** is base class for `UpdateSpec` and `DeleteSpec`, sharing `checkRowCountBeforeExecute`, `buildPredicates`.
- **`GlobalConfigHolder`** is the central config access point — all config reads go through here.
- **`IdentifierValidator`** manages SQL identifier validation and table name resolution (`resolveTableName`).
- **`MergeSpec`** generates dialect-specific SQL via `DialectStrategy` interface (PostgreSQL/MySQL/Oracle/SQLServer).
- **`DefaultMyJpaRepository`** overrides all `SimpleJpaRepository` query/delete methods to auto-inject soft-delete filters. `MyJpaRepository` adds Lambda-based query/bulk operation default methods on top.
- **`MyJpaPlusAutoConfiguration`** inner classes (`MyJpaPlusConfigInitializer`, `SecurityContextAuditUserProvider`, `DataSourceSlowQueryProxyPostProcessor`, `RepositoryBaseClassPostProcessor`) wire everything together at Spring Boot startup.

## Testing

### Prerequisites
- Local MySQL 8.0 running with `test` database created
- Config: `jdbc:mysql://localhost:3306/test` (see `src/test/resources/application.properties`)
- Credentials via env vars: `DB_USERNAME=root`, `DB_PASSWORD=1351.zhong`
- `spring-boot-starter-security` is a test dependency (for `SecurityContextAuditUserProvider` tests)

### Test Patterns

**Unit tests** use `@DataJpaTest`:
```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = TestConfig.class)
class MyTest {
    @SpringBootApplication
    @EntityScan(basePackageClasses = SoftDeleteRepoTestEntity.class)
    @EnableJpaRepositories(basePackageClasses = {SoftDeleteRepoTestRepository.class},
        repositoryBaseClass = DefaultMyJpaRepository.class)
    static class TestConfig {}
}
```

**CRITICAL: `@DataJpaTest` does NOT activate `MyJpaPlusAutoConfiguration`**. Bulk ops (`update()/delete()/merge()`) throw `UnsupportedOperationException` without it. Add `@Import(MyJpaPlusAutoConfiguration.class)` or `@ContextConfiguration(classes = TestApplication.class)`.

**CRITICAL: `@EnableJpaRepositories` must specify `repositoryBaseClass = DefaultMyJpaRepository.class`** for soft-delete to work in tests. Without it, the Spring proxy uses `SimpleJpaRepository` and soft-delete methods like `deleteByIdIfExists` won't be available.

**MySQL data isolation**: MySQL persists data between methods. Every `@DataJpaTest` MUST have:
```java
@BeforeEach
void setUp() {
    repository.deleteAll();
    repository.flush();
}
```

**Integration tests**: `@Tag("integration")` + Testcontainers (PostgreSQL, MySQL)

### Testing Gotchas

- **Reflection to bypass Spring proxies**: Repository methods on `DefaultMyJpaRepository` (like `deleteByIdIfExists`) can't be called directly on the Spring proxy — they throw `ClassCastException`. Use `org.springframework.test.util.AopTestUtils` or cast to the proxy directly if it extends the class.
- **Spring proxy wrapping**: `deleteAllById(null)` and `deleteInBatch(null)` throw `InvalidDataAccessApiUsageException` (Spring-wrapped) not `IllegalArgumentException`. Use `assertThrows(Exception.class, ...)` or catch the specific Spring exception type.
- **Mockito for transaction management**: `executeInTransaction` / `executeInManagedTransaction` paths require Mockito mocks since `@DataJpaTest` wraps in Spring transactions. Create separate non-`@DataJpaTest` classes with Mockito.
- **SubQuerySpec lazy execution**: `qs.exists()` stores the consumer — it executes during `toSpecification()`. Test exceptions with: `assertThrows(X.class, () -> parentRepository.findAll(qs.toSpecification()))` NOT wrapping `qs.exists()`.
- **Reflection `InvocationTargetException`**: When calling private methods via `Method.invoke()`, exceptions are wrapped in `InvocationTargetException`. Must unwrap via `.getCause()`.
- **MySQL self-reference in UPDATE**: MySQL forbids `UPDATE ... WHERE EXISTS(SELECT ... FROM same_table)`. Use different entity types in EXISTS subqueries.
- **`saveAllBatchedPure`** always calls `persist()` — detached/existing entities cause `EntityExistsException`. Use `saveAllBatched` for mixed lists.
- **GlobalConfigHolder.getConfig()** returns a default instance when not configured via `setConfig()`. Tests must `setConfig(null)` in `@BeforeEach`/`@AfterEach` to control behavior.
- **`condition=false` in ConditionalMethods**: When all conditions are skipped, QuerySpec has no WHERE → EXISTS subquery returns true for all rows (not 0).
- **`deleteInBatch` soft-delete path**: Uses `PersistenceUnitUtil.getIdentifier(entity)` which needs managed entities. Call `repository.save()` then `repository.flush()` before `deleteInBatch(List.of(entity))`.
- **`deleteByIdIfExists` / `deleteByIdOrThrow`**: These are on `DefaultMyJpaRepository`, not on the interface. To test through `@DataJpaTest`, use reflection: `Method m = DefaultMyJpaRepository.class.getMethod("deleteByIdIfExists", Object.class); m.invoke(proxy, id);`
- **Pre-existing test errors**: ~291 tests in `KeysetPaginationHelperTest`, `BulkOperationTemplateTest`, `QueryCacheManagerTransactionTest` etc. have pre-existing errors unrelated to changes. These are NOT caused by new code.
- **EncryptConverter test setup**: Always call `EncryptConverter.clearCacheForTesting()` in `@BeforeEach`/`@AfterEach` and reset `keyValidated` field via reflection. Set `myjpa-plus.encrypt.skip-salt-check=true` to skip PBKDF2 salt validation in tests.
- **SecurityContextAuditUserProvider**: Uses `Class.forName("org.springframework.security.core.context.SecurityContextHolder")` via reflection. Returns `"ANONYMOUS"` when Spring Security is not on classpath. With Spring Security test dependency, mock `SecurityContextHolder` to test authenticated/unauthenticated paths.

### Test Files to Know

| File | Tests | Notes |
|---|---|---|
| `QuerySpecTest` | ~100 | Core query building tests |
| `SubQuerySpecTest` | ~98 | EXISTS/subquery tests |
| `ConditionBuilderValidationTest` | ~75 | Condition validation + func() |
| `UpdateSpecTest` | ~114 | Bulk update + EXISTS/NOT EXISTS |
| `DeleteSpecTest` | ~50 | Bulk delete |
| `MergeSpecTest` | ~45 | UPSERT real DB tests |
| `MergeSpecTransactionTest` | ~13 | Mockito transaction paths |
| `DefaultMyJpaRepositoryTest` | ~41 | Soft delete repository |
| `DefaultMyJpaRepositoryIntegrationTest` | ~20 | deleteInBatch/deleteByIdIfExists via reflection |
| `MyJpaRepositoryTest` | ~30 | Lambda-based query/bulk operations |
| `SoftDeleteContextTest` | ~27 | ThreadLocal counter behavior |
| `IgnoreSoftDeleteAdvisorTest` | ~7 | AOP interception + SoftDeleteContext |
| `OptimisticLockRetryAdvisorTest` | ~8 | Retry logic + exception handling |
| `PredicateHelperTest` | ~63 | Static predicate helpers |
| `LambdaUtilsTest` | ~20 | Lambda extraction + cache |
| `EncryptConverterTest` | ~15 | Encrypt/decrypt round-trip |
| `EncryptConverterCoverageTest` | ~30 | Multi-key, salt, isProdProfile paths |
| `CodeEnumTypeTest` | ~30 | Enum type conversion |
| `CodeEnumTypeCoverageTest` | ~25 | char/long/int types, ordinal, assemble |

## Key Conventions

- JaCoCo minimum 90% LINE coverage (enforced for: spec, update, repository, projection, template, annotation, autoconfigure, codegen, converter, exception, monitor, softdelete, util)
- Current overall: ~84.9% INSTRUCTION, ~86.7% LINE
- SpotBugs Medium threshold
- PR checklist: spotless:check → verify → SpotBugs → JaCoCo → tests → CHANGELOG

## SpotBugs Suppressions

Use `@edu.umd.cs.findbugs.annotations.SuppressFBWarnings` for intentional design:
- `MS_EXPOSE_REP`: Singleton intentionally exposes config (GlobalConfigHolder)
- `RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE`: Defensive null checks for edge cases
- `EI_EXPOSE_REP` / `EI_EXPOSE_REP2`: Record components or cache intentional exposure

## 7-File Sync Checklist for New Condition Types

When adding a condition to `ConditionBuilder`:

1. `ConditionNode.java` — `Op` enum (+ sealed interface `permits`)
2. `ConditionBuilder.java` — default method
3. `ConditionalMethods.java` — abstract method declaration (if bulk ops need it)
4. `NodeResolver.java` — `if-else instanceof` branch
5. `AbstractBulkOperationSpec.java` — bulk operation impl (if needed)
6. `OrConditionBuilder.java` — bulk OR group impl (if needed)
7. `QuerySpecTest.java` — test cases
