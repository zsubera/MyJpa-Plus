# MyJpa-Plus Changelog

## [Unreleased]

### Added
- `eqIgnoreCase` / `likeIgnoreCase` — case-insensitive string conditions (UPPER-based)
- `groupBy(SFunction...)` — GROUP BY clause support
- `having(BiFunction)` — HAVING clause support for aggregated queries
- `where(BiFunction)` — raw Predicate injection as escape hatch
- `not(Consumer)` — negative condition group
- `startsWith` / `endsWith` / `contains` — convenience LIKE methods
- `in(Collection)` / `notIn(Collection)` overloads
- Consumer-mode `or(Consumer)` / `join(field, Consumer)` / `leftJoin(field, Consumer)`
- Spring Boot Auto-Configuration
- Subquery correlation support via `correlate(root)`
- `LambdaUtils` property name caching (by implClass + methodName)
- Empty values validation on `in` / `notIn`
- `eq(field, null)` auto-converts to `IS NULL`
- `endOr()` throws `IllegalStateException` when mismatched

### Fixed
- `SubQuerySpec` conditions no longer overwrite each other
- `select()` no longer silently overridden by `resolveExists`
- `resolveSimple` handles Collection values in IN/NOT_IN

### Changed
- `ConditionNode` → sealed interface; all implementations are `final`
- SpotBugs threshold set to Medium
- JaCoCo coverage minimum 60% (autoconfigure excluded)
- doclint enabled (`reference,html`)

### Infrastructure
- GitHub Actions CI (JDK 17/21 matrix + release deployment on v* tags)
- Dependabot for automated dependency updates
- CODE_OF_CONDUCT, ISSUE_TEMPLATE, PR_TEMPLATE, .editorconfig

## [1.0.0] - 2022-04-01 (original jpa-extensions fork)

### Initial Release
- Type-safe JPA `Specification` builder with lambda-based API
- `QuerySpec<T>`: eq, ne, gt, ge, lt, le, like, notLike, in, notIn, between, isNull, isNotNull
- JOIN support: `join()`, `leftJoin()` with `JoinGroup`
- OR groups: `or()` with `OrGroup`, nested within joins with `OrJoinGroup`
- EXISTS subqueries with `SubQuerySpec`
- Multi-field LIKE search via `multiLike`
- Spring MVC argument resolvers: `@SearchParam`, `@ListParam`
- Jackson serializers for Hibernate lazy proxies
