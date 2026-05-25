# MyJpa-Plus

基于 Lambda 表达式的类型安全 JPA 动态查询构建器，专为 Spring Data JPA 设计。

*A type-safe JPA Criteria query builder with a lambda-based fluent API for Spring Data JPA.*

## 特性 / Features

- **Lambda 类型安全** — 使用方法引用（`Entity::getField`）替代硬编码字段名字符串
- **流式 API** — 可链式调用的 AND / OR 条件组合
- **JOIN 支持** — 内连接 / 左连接，支持嵌套条件、子连接和连接缓存
- **EXISTS 子查询** — 带类型安全条件的关联子查询
- **OR 分组** — 可在 AND 分组内任意嵌套 OR 分组，反之亦然
- **多字段搜索** — 单个关键词跨多个字段 LIKE 搜索
- **集合操作** — `isEmpty` / `isNotEmpty`，适用于 `@OneToMany` 和 `@ManyToMany`
- **空值校验** — 所有构建器方法均对 Lambda 参数进行 null 检查

## 环境要求 / Requirements

- Java 17+
- Spring Boot 3.x
- Spring Data JPA

## 安装 / Installation

```xml
<dependency>
    <groupId>io.github.zsubera</groupId>
    <artifactId>myjpa-plus</artifactId>
    <version>0.0.1</version>
</dependency>
```

## 快速开始 / Quick Start

```java
// 简单等值查询
List<User> users = userRepository.findAll(
    new QuerySpec<User>()
        .eq(User::getStatus, "ACTIVE")
        .toSpecification()
);

// OR 多条件组合
List<User> users = userRepository.findAll(
    new QuerySpec<User>()
        .like(User::getName, "%John%")
        .or()
            .eq(User::getRole, "ADMIN")
            .eq(User::getRole, "MODERATOR")
        .endOr()
        .toSpecification()
);

// JOIN 关联查询
List<Order> orders = orderRepository.findAll(
    new QuerySpec<Order>()
        .join(Order::getCustomer)
            .eq(Customer::getCountry, "CN")
            .gt(Customer::getLevel, 3)
        .endJoin()
        .between(Order::getCreatedAt, startDate, endDate)
        .toSpecification()
);

// EXISTS 子查询
List<Customer> customers = customerRepository.findAll(
    new QuerySpec<Customer>()
        .exists(Order.class, sub -> sub
            .gt(Order::getAmount, 1000)
        )
        .toSpecification()
);

// 多字段模糊搜索
List<Product> products = productRepository.findAll(
    new QuerySpec<Product>()
        .multiLike("蓝色", Product::getName, Product::getDescription)
        .toSpecification()
);

// 与外部 Specification 组合
List<User> users = userRepository.findAll(
    new QuerySpec<User>()
        .eq(User::getStatus, "ACTIVE")
        .toSpecification(existingSpec)
);
```

## API 一览 / API Overview

| 分类 | 方法 |
|------|------|
| 比较 / Comparison | `eq`, `ne`, `gt`, `ge`, `lt`, `le` |
| 字符串 / String | `like`, `notLike` |
| 集合 / Collection | `in`, `notIn`, `between`, `isEmpty`, `isNotEmpty` |
| 空值 / Null | `isNull`, `isNotNull` |
| 搜索 / Search | `multiLike(keyword, field1, field2, ...)` |
| 连接 / Join | `join`, `leftJoin` |
| 子查询 / Subquery | `exists`, `notExists` |
| 逻辑 / Logic | `or()` ... `endOr()` |
| 输出 / Output | `toSpecification()`, `toSpecification(external)` |

## 协议 / License

Apache 2.0 — 详见 [LICENSE](./LICENSE)
