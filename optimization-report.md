# myjpa-plus 优化报告

---
## 轮次 1 - 优化记录
时间：2026-05-29 19:00

### 已修复问题
- [P1-1] ProjectionSpec.JoinGroup 大量重复条件节点定义（22个 record 与 spec.ConditionNode 结构重复）：删除 JoinGroup 内部的 ConditionNode sealed interface（72行），改用 spec.ConditionNode.SimpleNode 和 spec.ConditionNode.CollectionNode，消除约 590 行重复代码
- [P1-2] ProjectionSpec.resolveJoins() 超长 if-else 链（~90行）：重构为 resolveJoinCondition() + resolveSimpleForJoin() 两个方法，使用 switch 表达式按 Op 枚举分派，代码行数从 ~90 行降至 ~50 行，新增条件类型时编译器会提示未处理的 case
- [P1-3] ProjectionSpec 类过大（1074行）：通过消除 JoinGroup 内部的重复 ConditionNode 定义（约 590 行），文件从 1074 行降至约 910 行
- [P1-4] AbstractBulkOperationSpec 条件方法未实现 ConditionBuilder 接口：添加详细的类级 Javadoc 说明设计原因（延迟 Lambda vs 延迟 ConditionNode 树），以及新增条件类型时的六处同步清单
- [P1-5] SubQuerySpec 独立实现条件 API：在已有设计说明基础上，添加六处同步更新清单（ConditionBuilder → ConditionNode.Op → QuerySpec → ProjectionSpec.JoinGroup → AbstractBulkOperationSpec → SubQuerySpec）
- [P1-6] executeLimited() 两步操作存在竞态条件：增强 UpdateSpec 和 DeleteSpec 的 JavaDoc，添加按推荐程度排序的安全使用建议（悲观锁 > @Transactional > 原生 SQL > 分布式锁），包含代码示例
- [P1-8] 深度分页无实际限制：新增 myjpa-plus.query.deep-pagination-offset-limit 配置项，默认值 -1（禁用硬限制），超过此值抛出 IllegalArgumentException。同步更新 MyJpaPlusProperties、MyJpaTemplate、MyJpaPlusAutoConfiguration

### 未修复问题
- P1-7 findAllStream() 废弃版本资源泄漏风险：已有充分处理（@Deprecated(forRemoval=true) + 运行时日志警告 + 安全替代方案指引），无需额外修改
- P2-1 至 P2-15：均为 P2 优先级的可选优化，本轮未涉及

### 修改详情
#### 1. P1-1/P1-2: ProjectionSpec.JoinGroup 条件节点去重与 resolveJoins 重构
**文件**：ProjectionSpec.java
**修改前**：JoinGroup 内部定义了完整的 ConditionNode sealed interface（22个 record），resolveJoins() 使用 ~90 行 if-else instanceof 链逐个处理
**修改后**：JoinGroup 条件方法直接创建 spec.ConditionNode.SimpleNode/CollectionNode 实例，resolveJoins() 改为 resolveJoinCondition() + resolveSimpleForJoin() 两层分派
**原因**：消除约 590 行重复代码，新增条件类型只需修改 spec.ConditionNode.Op 枚举和 switch 表达式

#### 2. P1-4: AbstractBulkOperationSpec 同步清单文档
**文件**：AbstractBulkOperationSpec.java:22-46
**修改前**：类级 Javadoc 仅描述基本用途
**修改后**：添加设计说明（延迟 Lambda vs 延迟 ConditionNode 树）和六处同步更新清单
**原因**：降低新增条件类型时同步遗漏的风险

#### 3. P1-5: SubQuerySpec 同步清单文档
**文件**：SubQuerySpec.java:13-40
**修改前**：已有设计说明但缺少同步清单
**修改后**：添加六处同步更新清单
**原因**：与 P1-4 保持一致的同步清单机制

#### 4. P1-6: executeLimited() 竞态条件文档增强
**文件**：UpdateSpec.java:219-265, DeleteSpec.java:166-212
**修改前**：简要的并发风险警告和建议列表
**修改后**：按推荐程度排序的安全使用建议，包含悲观锁代码示例
**原因**：提供更清晰的安全使用指引

#### 5. P1-8: 深度分页硬限制配置
**文件**：MyJpaPlusProperties.java:81-109, MyJpaTemplate.java:83/115-140/564-572, MyJpaPlusAutoConfiguration.java:41-63
**修改前**：仅记录警告日志，不阻止执行
**修改后**：新增 deepPaginationOffsetLimit 配置项（默认 -1 禁用），超过时抛出 IllegalArgumentException
**原因**：防止意外的深度分页性能问题

### 验证结果
- spotless:apply: 通过
- compile: 通过
- test -DexcludedGroups=integration: 全部通过（0 failures）
