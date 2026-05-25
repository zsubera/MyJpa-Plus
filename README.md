# MyJpa-Plus

Type-safe JPA Criteria query builder with a lambda-based fluent API for Spring Data JPA.

## Features

- **Lambda type safety** — Use method references (`Entity::getField`) instead of hardcoded strings
- **Fluent API** — Chainable conditions with AND/OR logic
- **JOIN support** — Inner/left joins with nested conditions, sub-joins, and join cache
- **EXISTS subqueries** — Correlated subqueries with type-safe conditions
- **OR groups** — Arbitrary nesting of OR conditions within AND groups and vice versa
- **Multi-field search** — Single keyword LIKE across multiple fields
- **Collection operations** — `isEmpty` / `isNotEmpty` for `@OneToMany` / `@ManyToMany`
- **Null-safe** — Input validation on all builder methods

## Requirements

- Java 17+
- Spring Boot 3.x
- Spring Data JPA

## Installation

```xml
<dependency>
    <groupId>io.github.zsubera</groupId>
    <artifactId>myjpa-plus</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Quick Start

```java
// Simple equality
List<User> users = userRepository.findAll(
    new QuerySpec<User>()
        .eq(User::getStatus, "ACTIVE")
        .toSpecification()
);

// Multi-condition with OR
List<User> users = userRepository.findAll(
    new QuerySpec<User>()
        .like(User::getName, "%John%")
        .or()
            .eq(User::getRole, "ADMIN")
            .eq(User::getRole, "MODERATOR")
        .endOr()
        .toSpecification()
);

// JOIN with conditions
List<Order> orders = orderRepository.findAll(
    new QuerySpec<Order>()
        .join(Order::getCustomer)
            .eq(Customer::getCountry, "CN")
            .gt(Customer::getLevel, 3)
        .endJoin()
        .between(Order::getCreatedAt, startDate, endDate)
        .toSpecification()
);

// EXISTS subquery
List<Customer> customers = customerRepository.findAll(
    new QuerySpec<Customer>()
        .exists(Order.class, sub -> sub
            .eq(Order::getCustomerId, /* correlated implicitly */)
            .gt(Order::getAmount, 1000)
        )
        .toSpecification()
);

// Multi-field keyword search
List<Product> products = productRepository.findAll(
    new QuerySpec<Product>()
        .multiLike("blue", Product::getName, Product::getDescription)
        .toSpecification()
);

// Combine with external Specification
List<User> users = userRepository.findAll(
    new QuerySpec<User>()
        .eq(User::getStatus, "ACTIVE")
        .toSpecification(existingSpec)
);
```

## API Overview

| Category | Methods |
|----------|---------|
| Comparison | `eq`, `ne`, `gt`, `ge`, `lt`, `le` |
| String | `like`, `notLike` |
| Collection | `in`, `notIn`, `between`, `isEmpty`, `isNotEmpty` |
| Null | `isNull`, `isNotNull` |
| Search | `multiLike(keyword, field1, field2, ...)` |
| Join | `join`, `leftJoin` |
| Subquery | `exists`, `notExists` |
| Logic | `or()` ... `endOr()` |
| Output | `toSpecification()`, `toSpecification(external)` |

## License

Apache 2.0 — see [LICENSE](./LICENSE)
