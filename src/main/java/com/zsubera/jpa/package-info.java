/**
 * MyJpa-Plus: Type-safe JPA extension library for Spring Data JPA.
 *
 * <p>
 * This library provides Lambda-based type-safe query building, batch operations,
 * UPSERT/MERGE, CTE, projection queries, field encryption/masking, SQL monitoring,
 * optimistic lock retry, aggregate queries, query caching, and code generation.
 *
 * <h2>Core Modules</h2>
 * <ul>
 *   <li>{@code spec} - Type-safe query specification builder using Lambda method references</li>
 *   <li>{@code update} - Batch UPDATE/DELETE operations with safety guards</li>
 *   <li>{@code template} - MyJpaTemplate for convenient query execution and caching</li>
 *   <li>{@code projection} - DTO projection and aggregate queries</li>
 *   <li>{@code repository} - Extended JPA repository base class with soft-delete support</li>
 *   <li>{@code softdelete} - Soft-delete filtering and context management</li>
 *   <li>{@code converter} - Type converters for CodeEnum, field encryption, and data masking</li>
 *   <li>{@code monitor} - SQL slow query monitoring and metrics collection</li>
 *   <li>{@code autoconfigure} - Spring Boot auto-configuration</li>
 * </ul>
 *
 * <h2>Quick Start</h2>
 * <pre>{@code
 * // Query with Lambda method references
 * List<User> users = repository.findAll(s -> s.eq(User::getStatus, "ACTIVE"));
 *
 * // Batch update
 * repository.update(s -> s.set(User::getStatus, "INACTIVE").eq(User::getStatus, "ACTIVE"));
 *
 * // UPSERT
 * repository.merge(s -> s.withEntity(user).onConflict(User::getEmail).updateOnConflict(User::getName));
 * }</pre>
 *
 * <h2>Design Notes</h2>
 * <p>
 * <b>Raw type casts ({@code @SuppressWarnings("unchecked", "rawtypes")})</b>: The JPA Criteria API
 * uses generic type parameters that are erased at runtime (e.g., {@code Root<T>}, {@code Path<R>},
 * {@code CriteriaBuilder}). When building dynamic queries where the entity type and field types are
 * known only at runtime, raw types are unavoidable. This is not a type-safety issue — all casts are
 * validated at runtime by the JPA provider and by this library's identifier validation. Examples:
 * {@code ConditionBuilder}, {@code SubQuerySpec}, {@code NodeResolver}, {@code PredicateHelper}.
 *
 * @see com.zsubera.jpa.spec.QuerySpec
 * @see com.zsubera.jpa.template.MyJpaTemplate
 * @see com.zsubera.jpa.repository.MyJpaRepository
 */
@org.springframework.lang.NonNullApi package com.zsubera.jpa;
