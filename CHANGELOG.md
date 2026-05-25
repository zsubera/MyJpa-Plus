# MyJpa-Plus Changelog

## [Unreleased]

### Added
- Spring Boot 3.x / Jakarta EE 9+ support (Java 17)
- `ConditionBuilder` interface to eliminate code duplication
- Input validation on all builder methods
- Apache 2.0 license
- CI workflow with GitHub Actions
- JavaDoc for `QuerySpec` and `ConditionBuilder`

### Fixed
- `SubQuerySpec` conditions now properly AND together instead of overwriting

### Removed
- `PageDatatable` (jQuery DataTables helper — out of scope)
- `resolver` package (Spring MVC argument resolvers — out of scope)
- `lazy` package (Jackson Hibernate serializers — out of scope)
- `spring-boot-starter-web`, `jackson-databind`, `javax.servlet-api` dependencies

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
