# 数据库兼容性矩阵

## 支持的数据库

| 数据库 | 最低版本 | JDBC 驱动 | 测试状态 | UPSERT | 多行批量 UPSERT | 软删除 | 加密 | 慢查询监控 |
|--------|---------|-----------|---------|--------|----------------|--------|------|-----------|
| PostgreSQL | 12+ | `org.postgresql:postgresql` | ✅ Testcontainers | ✅ `ON CONFLICT DO UPDATE` | ✅ | ✅ | ✅ | ✅ |
| MySQL | 8.0+ | `com.mysql:mysql-connector-j` | ✅ Testcontainers | ✅ `ON DUPLICATE KEY UPDATE` | ✅ | ✅ | ✅ | ✅ |
| Oracle | 12c+ | `com.oracle.database.jdbc` | ⚠️ 社区验证 | ✅ `MERGE INTO` | ❌ | ✅ | ✅ | ✅ |
| SQL Server | 2016+ | `com.microsoft.sqlserver:mssql-jdbc` | ⚠️ 社区验证 | ✅ `MERGE INTO` | ❌ | ✅ | ✅ | ✅ |

**图例：**
- ✅ 已测试/已支持
- ⚠️ 社区验证（非 CI 自动测试）
- ❌ 不支持

## 功能兼容性详解

### UPSERT/MERGE 方言

| 数据库 | SQL 方言 | 冲突检测方式 | 批量模式 |
|--------|---------|-------------|---------|
| PostgreSQL | `INSERT ... ON CONFLICT (...) DO UPDATE SET ...` | 唯一约束/主键 | 多行 VALUES |
| MySQL | `INSERT ... ON DUPLICATE KEY UPDATE col = VALUES(col)` | 唯一键/主键 | 多行 VALUES |
| Oracle | `MERGE INTO t USING (SELECT ... FROM DUAL) ON (...) WHEN NOT MATCHED ...` | 唯一约束 | 逐行 |
| SQL Server | `MERGE INTO t USING (VALUES (...)) AS source(...) ON (...) WHEN NOT MATCHED ...` | 唯一约束 | 逐行 |

### IN 子句参数限制

| 数据库 | 每批最大参数 | 自动分批 | 硬限制 |
|--------|------------|---------|--------|
| PostgreSQL | 1,000 | ✅ | 5,000 |
| MySQL | 1,000 | ✅ | 5,000 |
| Oracle | 1,000 | ✅ | 5,000 |
| SQL Server | 2,100 | ✅ | 5,000 |

可通过配置调整：
```yaml
myjpa-plus:
  query:
    in-clause-max-size: 1000
    in-clause-hard-limit: 5000
```

### 查询超时支持

| 数据库 | 超时机制 | 精度 |
|--------|---------|------|
| PostgreSQL | `jakarta.persistence.query.timeout` hint | 语句级 |
| MySQL | `jakarta.persistence.query.timeout` hint | 连接级 |
| Oracle | `jakarta.persistence.query.timeout` hint | 语句级 |
| SQL Server | `jakarta.persistence.query.timeout` hint | 语句级 |

### 流式查询（fetchSize）

| 数据库 | fetchSize 支持 | 游标模式 |
|--------|---------------|---------|
| PostgreSQL | ✅ 需设置 `fetchSize` | 只向前游标 |
| MySQL | ✅ 需设置 `fetchSize` + 连接参数 | 只向前游标 |
| Oracle | ⚠️ 需额外配置 | REF CURSOR |
| SQL Server | ✅ 需设置 `fetchSize` | 只向前游标 |

## 测试配置

### Testcontainers 集成测试

项目使用 Testcontainers 自动化集成测试：

```java
@Testcontainers
class PostgreSQLIntegrationTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
        .withDatabaseName("test");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
}
```

### 运行集成测试

```bash
# 运行所有集成测试（需要 Docker）
mvn verify -Pintegration-tests

# 仅运行 PostgreSQL 测试
mvn verify -Pintegration-tests -Dtest=PostgreSQL*

# 仅运行 MySQL 测试
mvn verify -Pintegration-tests -Dtest=MySQL*
```

## 已知限制

### Oracle
1. **不支持多行批量 UPSERT** — `MERGE INTO` 语法不支持多行 VALUES 子句，自动回退到逐行执行
2. **IN 子句参数限制** — Oracle 限制为 1,000 个绑定参数，`InClauseBuilder` 自动分批
3. **IDENTITY 列不支持 UPSERT** — Oracle 使用 SEQUENCE 而非 IDENTITY，需显式指定冲突列

### SQL Server
1. **不支持多行批量 UPSERT** — `MERGE INTO` 语法不支持多行 VALUES 子句
2. **IN 子句参数限制** — SQL Server 限制为 2,100 个绑定参数
3. **`MERGE` 语句限制** — 同一目标表不能在单个 `MERGE` 语句中被多次引用

### MySQL
1. **`ON DUPLICATE KEY UPDATE` 限制** — 仅在唯一键冲突时触发，不支持部分列更新的条件判断
2. **流式查询** — 需在 JDBC URL 中添加 `useCursorFetch=true` 和 `defaultFetchSize=100`

### PostgreSQL
1. **`ON CONFLICT DO UPDATE` 限制** — 冲突列必须有唯一约束或主键
2. **流式查询** — 需设置 `fetchSize` 为非零值以启用游标模式

## 升级路径

### 从 1.2.x 升级到 1.3.0

参见 [MIGRATION.md](../MIGRATION.md) 获取详细迁移指南。

主要变更：
- `SoftDeleteHelper` 的批量删除方法迁移到 `SoftDeleteBulkExecutor`
- `QueryCacheManager` 新增 `CacheAdapter` SPI 支持
- 新增 Keyset 分页支持
- 新增 `@IgnoreSoftDelete` 注解的 ThreadLocal 管理
