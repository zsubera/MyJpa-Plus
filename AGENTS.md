# AGENTS.md

## Project Overview

MyJpa-Plus is a type-safe JPA tool library using Lambda expressions for query construction. Provides query building, batch operations, UPSERT/MERGE, CTE, projection queries, field encryption/masking, SQL slow query monitoring.

**Stack**: Java 17+, Spring Boot 3.3.5, Hibernate 6.x, Spring Data JPA

## Quick Commands

```bash
# Format check (CI gate — must pass first)
./mvnw spotless:check

# Fix formatting
./mvnw spotless:apply

# Unit tests (exclude integration tests)
./mvnw test -DexcludedGroups=integration

# Full verification (CI standard)
./mvnw clean verify -Dgpg.skip=true -Ddependency-check.skip=true

# Integration tests (requires Docker)
./mvnw test -Dgroups=integration

# Single test class
./mvnw test -Dtest=QuerySpecTest -DexcludedGroups=integration

# Coverage report
./mvnw jacoco:report
```

**Windows PowerShell** — quote `-D` args:
```powershell
.\mvnw.cmd test "-Dtest=QuerySpecTest" "-DexcludedGroups=integration"
.\mvnw.cmd clean verify "-Dgpg.skip=true" "-Ddependency-check.skip=true"
```

## CI Pipeline

`.github/workflows/ci.yml` — 3 jobs:
1. **build** (Java 17+21 matrix, MySQL 8.0 service): `spotless:check` → `clean verify`
2. **integration** (depends on build): Testcontainers PostgreSQL + MySQL
3. **release-deploy**: GPG + Maven Central publish (triggered by `v*` tags)

CI MySQL password: `ci_test_2024`. Local: `1351.zhong`.

## Code Style

- **Formatter**: Spotless + Eclipse style (`eclipse-codestyle.xml`)
- Use method references (`Entity::getField`) for type safety — never hardcode field name strings
- `ConditionNode` is sealed; all implementations must be `final`
- Public API parameters must have null checks
- No comments unless asked; avoid over-engineering

## Package Structure

| Package | Key Classes |
|---|---|
| `spec` | QuerySpec, ConditionBuilder, ConditionNode, NodeResolver, SubQuerySpec, CacheKeyBuilder, PredicateHelper, AggregateHelper |
| `update` | UpdateSpec, DeleteSpec, MergeSpec, AbstractBulkOperationSpec, DialectDetector |
| `repository` | MyJpaRepository, DefaultMyJpaRepository, SoftDeleteContext, OptimisticLockRetryAdvisor, IgnoreSoftDeleteAdvisor |
| `projection` | ProjectionSpec |
| `template` | MyJpaTemplate, BulkOperationTemplate, BatchSaveTemplate, KeysetPaginationHelper, QueryCacheManager |
| `annotation` | @SoftDelete, @Encrypt, @Mask, @RetryOnOptimisticLock, @IgnoreSoftDelete, @CodeEnumValue |
| `autoconfigure` | GlobalConfigHolder, MyJpaPlusAutoConfiguration, SoftDeleteFilterBean, MyJpaPlusProperties |
| `converter` | CodeEnumType, EncryptConverter, MaskSerializer |
| `util` | LambdaUtils, IdentifierValidator, InClauseBuilder, EntityClassResolver |
| `monitor` | SqlSlowQueryInterceptor, SlowQueryDataSourceProxy, QueryMetricsCollector |
| `softdelete` | SoftDeleteHelper |

## Architecture

- **Dual-track condition building**: Queries use `ConditionNode` tree (deferred evaluation), batch ops use `BulkConditionNode` (Lambda wrapper). New condition types need both tracks updated.
- **`Op.resolve()` strategy pattern**: The `Op` enum is the single source of truth for predicate building. Both query resolution (`NodeResolver` → `PredicateHelper` → `Op.resolve()`) and batch operations (`BulkConditionSupport` → `Op.resolve()`) delegate to the same `Op` implementation.
- **`AbstractBulkOperationSpec`** is base class for `UpdateSpec`/`DeleteSpec`, sharing `checkRowCountBeforeExecute`/`buildPredicates`.
- **`GlobalConfigHolder`** is the central config access point.
- **`MergeSpec`** uses `DialectStrategy` for dialect-specific SQL (PostgreSQL/MySQL/Oracle/SQLServer).
- **`DefaultMyJpaRepository`** overrides all `SimpleJpaRepository` query/delete methods with auto soft-delete filtering.
- **Extracted helpers** (internal, package-private): `CacheKeyBuilder` (cache key generation from QuerySpec), `PredicateHelper` (shared predicate construction), `AggregateHelper` (HAVING validation/comparison).

## Testing

### Prerequisites
- Local MySQL 8.0 running with `test` database created
- Config: `src/test/resources/application.properties` (`jdbc:mysql://localhost:3306/test`)
- Credentials via env vars: `DB_USERNAME=root`, `DB_PASSWORD=1351.zhong`
- `spring-boot-starter-security` is a test dependency (for `SecurityContextAuditUserProvider` tests)

### Test Patterns

Unit tests use `@DataJpaTest`:
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

**Key: `@DataJpaTest` does NOT activate `MyJpaPlusAutoConfiguration`**. Batch ops (`update()/delete()/merge()`) throw `UnsupportedOperationException` without it. Add `@Import(MyJpaPlusAutoConfiguration.class)` or `@ContextConfiguration(classes = TestApplication.class)`.

**Key: `@EnableJpaRepositories` must specify `repositoryBaseClass = DefaultMyJpaRepository.class`**, otherwise soft delete won't work in tests. Without it, Spring uses `SimpleJpaRepository` and `deleteByIdIfExists` etc. are unavailable.

**MySQL data isolation**: MySQL preserves data between methods. Every `@DataJpaTest` needs:
```java
@BeforeEach
void setUp() {
    repository.deleteAll();
    repository.flush();
}
```

**Connection pool**: `application.properties` configures HikariCP `maximum-pool-size=5`. Surefire uses `parallel=none` + `forkCount=1` to prevent pool exhaustion.

**Integration tests**: `@Tag("integration")` + Testcontainers (PostgreSQL, MySQL)

### Test Pitfalls

- **Spring proxy wrapping**: `deleteAllById(null)` and `deleteInBatch(null)` throw `InvalidDataAccessApiUsageException` (Spring-wrapped) not `IllegalArgumentException`. Use `assertThrows(Exception.class, ...)`.
- **SubQuerySpec deferred execution**: `qs.exists()` stores consumer — it executes at `toSpecification()` time. Test exceptions: `assertThrows(X.class, () -> parentRepository.findAll(qs.toSpecification()))`.
- **Reflection `InvocationTargetException`**: Via `Method.invoke()`, exceptions are wrapped. Must `.getCause()` to unwrap.
- **MySQL self-reference UPDATE**: MySQL blocks `UPDATE ... WHERE EXISTS(SELECT ... FROM same_table)`. Use a different entity type in EXISTS subqueries.
- **`saveAllBatchedPure`** always calls `persist()` — detached/existing entities throw `EntityExistsException`. Use `saveAllBatched` for mixed lists.
- **GlobalConfigHolder.getConfig()** returns default instance if not configured. Tests must `setConfig(null)` in `@BeforeEach`/`@AfterEach`.
- **EncryptConverter test setup**: Call `EncryptConverter.clearCacheForTesting()` and reset `keyValidated` via reflection in `@BeforeEach`/`@AfterEach`. Set `myjpa-plus.encrypt.skip-salt-check=true` to skip PBKDF2 salt check.
- **`deleteByIdIfExists` / `deleteByIdOrThrow`**: These are on `DefaultMyJpaRepository`, not the interface. Test via reflection: `Method m = DefaultMyJpaRepository.class.getMethod("deleteByIdIfExists", Object.class); m.invoke(proxy, id);`

### Current Test Count: ~3032 unit tests

## Key Conventions

- JaCoCo minimum 90% line coverage (enforced on: spec, update, repository, projection, template, annotation, autoconfigure, codegen, converter, exception, monitor, softdelete, util)
- SpotBugs Medium threshold
- PR checklist: spotless:check → verify → SpotBugs → JaCoCo → tests → CHANGELOG

## SpotBugs Suppressions

Use `@edu.umd.cs.findbugs.annotations.SuppressFBWarnings` for intentional design:
- `MS_EXPOSE_REP`: Singleton intentionally exposes config (GlobalConfigHolder)
- `RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE`: Defensive null checks for edge cases
- `EI_EXPOSE_REP` / `EI_EXPOSE_REP2`: Record components or cache intentional exposure

## Adding a New Operator (4-file sync checklist)

1. `ConditionNode.java` — `Op` enum (+ `resolve()` case if simple Op, or sealed interface `permits` if new node type)
2. `ConditionBuilder.java` — default method
3. `ConditionalMethods.java` — abstract method declaration (if batch ops need it)
4. `QuerySpecTest.java` — test cases

**No changes needed in**: `PredicateHelper`, `NodeResolver`, `BulkConditionSupport`, `OrConditionBuilder` — they all delegate to `Op.resolve()` automatically.

## Extracted Utility Classes

Internal helpers extracted during refactoring (package-private, not public API):

| Class | Package | Purpose |
|---|---|---|
| `CacheKeyBuilder` | `spec` | Cache key generation from condition trees (extracted from QuerySpec) |
| `PredicateHelper` | `spec` | Shared predicate construction for queries and batch ops |
| `AggregateHelper` | `spec` | HAVING operator validation and expression comparison |

## Helper Methods in Key Classes

**QuerySpec** (1067 lines):
- `addHavingCondition()` — unified validation for havingSum/Avg/Max/Min
- `addSubQueryNode()` — unified validation for exists/notExists
- `addInSubQueryNode()` — unified validation for inSubQuery/notInSubQuery

**DefaultMyJpaRepository** (548 lines):
- `executeDeleteOrBlock()` — unified 3-way delete logic (soft delete → hard delete block → hard delete fallback)
- `withIdAndSoftDelete()` — ID-based spec with soft delete filter merging

**MyJpaTemplate** (1359 lines):
- `validateQueryParams()` — null check for entityClass + spec
- `validateBatchParams()` — null check for param + positive batchSize
- `requireInitialized()` — unified getter null guard with error message
