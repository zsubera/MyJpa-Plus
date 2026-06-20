# 贡献指南

## 开发环境搭建

- JDK 17+
- Maven 3.8+

```bash
git clone https://github.com/zsubera/myjpa-plus
cd myjpa-plus
./mvnw compile
```

## 构建与验证

```bash
# 格式检查（CI 第一步）
./mvnw spotless:check

# 格式化代码
./mvnw spotless:apply

# 单元测试（排除集成测试）
./mvnw test -DexcludedGroups=integration

# 完整验证（CI 使用）
./mvnw clean verify -Dgpg.skip=true -Ddependency-check.skip=true

# 集成测试（需要 Docker）
./mvnw test -Dgroups=integration

# 安装到本地仓库
./mvnw install -DskipTests -Ddependency-check.skip=true -Dgpg.skip=true
```

## 包结构

| 包 | 职责 |
|---|---|
| `com.zsubera.jpa.spec` | 核心查询构建：QuerySpec、ConditionBuilder（含子接口）、ConditionNode、NodeResolver、CteSpec、QueryAggregates |
| `com.zsubera.jpa.update` | 批量操作：UpdateSpec、DeleteSpec、MergeSpec、DialectDetector、DialectStrategy |
| `com.zsubera.jpa.repository` | 扩展 Repository：MyJpaRepository、DefaultMyJpaRepository、SoftDeleteContext、EntityManagerHelper、EntityManagerResolver、OptimisticLockRetryAdvisor、IgnoreSoftDeleteAdvisor |
| `com.zsubera.jpa.projection` | 投影查询：ProjectionSpec |
| `com.zsubera.jpa.template` | 模板与缓存：MyJpaTemplate、BulkOperationTemplate、BatchSaveTemplate、KeysetPaginationHelper、QueryCacheManager |
| `com.zsubera.jpa.converter` | 枚举转换与序列化：@CodeEnum、@CodeEnumValue、CodeEnumType、EncryptConverter、MaskSerializer |
| `com.zsubera.jpa.annotation` | 注解：@SoftDelete、@IgnoreSoftDelete、@Encrypt、@Mask、@RetryOnOptimisticLock、@CodeEnumValue |
| `com.zsubera.jpa.autoconfigure` | 自动配置：GlobalConfigHolder、MyJpaPlusAutoConfiguration、SoftDeleteFilterBean、MyJpaPlusProperties |
| `com.zsubera.jpa.monitor` | SQL 监控：SqlSlowQueryInterceptor、QueryMetricsCollector |
| `com.zsubera.jpa.codegen` | 代码生成：EntityCodeGenerator |
| `com.zsubera.jpa.exception` | 异常类：MyJpaPlusException、QueryBuildException、BulkOperationException、DataAccessException、SecurityViolationException、TimeoutException |
| `com.zsubera.jpa.softdelete` | 软删除：SoftDeleteHelper |
| `com.zsubera.jpa.util` | 工具类：LambdaUtils、IdentifierValidator、InClauseBuilder、EntityClassResolver、EntityGraphHelper |

## 代码风格

- 使用方法引用（`Entity::getField`）确保类型安全 — 切勿硬编码字段名字符串
- 为公开 API 参数添加 null 校验
- 遵循现有包结构
- 所有条件方法应作为默认方法归属于 `ConditionBuilder` 接口或其子接口（`EqualityConditions`、`StringConditions`、`ComparisonConditions` 等）
- `ConditionNode` 是 sealed 接口，所有实现类必须为 `final`
- 新增条件类型需同步更新：ConditionBuilder（含子接口）、ConditionNode.Op、NodeResolver、SubQuerySpec、AbstractBulkOperationSpec、ProjectionSpec.ProjectionJoinGroup

## 添加新运算符

1. 在 `ConditionNode.Op` 中添加枚举值
2. 在 `ConditionBuilder<E, SELF>` 中添加默认方法
3. 在 `ConditionalMethods.java` 中添加抽象方法声明（若批量操作也需要）
4. 在 `NodeResolver` 中添加对应的 `if-else instanceof` 分支（Java 17，不用 switch 模式匹配）
5. 在 `SubQuerySpec` 中添加对应方法
6. 在 `AbstractBulkOperationSpec` 中添加对应方法（若需要）
7. 在 `OrConditionBuilder` 中添加实现（若批量 OR 组也需要）
8. 在 `QuerySpecTest` 中添加测试

### 新增条件类型同步检查清单

新增条件类型时，请确保同步更新以下 **7 个位置**：

| 序号 | 文件 | 位置 | 说明 |
|---|---|---|---|
| 1 | `ConditionNode.java` | `Op` 枚举 | 添加新的枚举值（sealed 接口，新增节点类型需加 `permits`） |
| 2 | `ConditionBuilder.java` | `default` 方法 | 添加类型安全的条件方法 |
| 3 | `ConditionalMethods.java` | 抽象方法声明 | 添加抽象方法（若批量操作也需要） |
| 4 | `NodeResolver.java` | `resolveNode()` | 添加对应的 `if-else instanceof` 分支（深度限制 50 层） |
| 5 | `AbstractBulkOperationSpec.java` | 实现 | 添加批量操作实现（若需要） |
| 6 | `OrConditionBuilder.java` | 实现 | 添加批量 OR 组实现（若需要） |
| 7 | `QuerySpecTest.java` | 测试用例 | 添加对应的测试 |

> **注意：**`AbstractBulkOperationSpec` 是 `UpdateSpec` 和 `DeleteSpec` 的基类，条件方法定义在此基类中。
> `SubQuerySpec` 和 `ProjectionSpec.ProjectionJoinGroup` 也需同步更新（如果新条件适用于子查询或投影 JOIN 场景）。

## 枚举转换开发

新增枚举支持时，需确保：

1. `CodeEnumType` 能正确解析 `@CodeEnumValue` 字段
2. 支持 `int`、`long`、`String` 类型的 code
3. 无 `@CodeEnumValue` 时自动使用 ordinal

## 测试约定

- **单元测试需要本地 MySQL 实例**（不再使用 H2）
- 连接信息：`src/test/resources/application.properties`（URL: `jdbc:mysql://localhost:3306/test`）
- 测试前确保 MySQL 运行且 `test` 数据库存在：`CREATE DATABASE IF NOT EXISTS test`
- `ddl-auto=create`，Hibernate 自动建表（测试间数据通过 `@BeforeEach deleteAll` 清理）
- 单元测试使用 `@DataJpaTest` + `@AutoConfigureTestDatabase(replace = NONE)` + 本地 MySQL
- 集成测试使用 `@Tag("integration")` + Testcontainers（PostgreSQL、MySQL）
- 核心测试类：`QuerySpecTest`、`ConditionBuilderValidationTest`

## Pull Request 检查清单

- [ ] 格式检查通过：`./mvnw spotless:check`
- [ ] 构建通过：`./mvnw clean verify -Dgpg.skip=true -Ddependency-check.skip=true`
- [ ] SpotBugs 检查通过（Medium 阈值）
- [ ] JaCoCo 覆盖率 >= 90% LINE（spec, update, repository, projection, template, annotation, autoconfigure, codegen, converter, exception, monitor, softdelete, util）
- [ ] 为新增功能添加了测试
- [ ] 已更新 CHANGELOG.md
- [ ] 已更新 README.md（如有 API 变更）
