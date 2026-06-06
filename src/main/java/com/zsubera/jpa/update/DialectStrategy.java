package com.zsubera.jpa.update;

import java.util.ArrayList;
import java.util.List;

/**
 * 数据库方言策略接口，封装不同数据库的 UPSERT SQL 语法差异。
 *
 * <p>
 * 每种数据库方言实现此接口，提供各自的标识符转义和 UPSERT SQL 构建逻辑。 新增方言时只需添加新的实现类，无需修改 {@link MergeSpec}。
 *
 * <p>
 * 当前支持的方言：
 * <ul>
 * <li>{@code postgresql} — {@code INSERT ... ON CONFLICT (...) DO UPDATE SET ...}</li>
 * <li>{@code mysql} — {@code INSERT ... ON DUPLICATE KEY UPDATE ...}</li>
 * <li>{@code h2} — {@code MERGE INTO ... KEY(...) VALUES(...)}</li>
 * </ul>
 *
 * @see MergeSpec
 */
interface DialectStrategy {

    /**
     * 返回方言标识符（如 "postgresql"、"mysql"、"h2"）。
     *
     * @return 方言名称
     */
    String name();

    /**
     * 转义 SQL 标识符（表名、列名），使用该方言的 quoting 规则。
     *
     * @param identifier 原始标识符
     * @return 转义后的标识符
     */
    String escapeIdentifier(String identifier);

    /**
     * 构建 UPSERT SQL 语句。
     *
     * @param tableName 表名
     * @param insertColumns 插入列名列表
     * @param insertFieldValues 插入字段值列表
     * @param conflictColumns 冲突列名列表
     * @param updateColumns 更新列名列表
     * @return SQL 和参数
     */
    SqlWithParams buildUpsertSql(String tableName, List<String> insertColumns,
        List<MergeSpec.EntityFieldValue> insertFieldValues, List<String> conflictColumns, List<String> updateColumns);

    /**
     * 构建简单的 INSERT SQL（不处理冲突，用于 H2 冲突键全为 null 的场景）。
     *
     * <p>
     * 默认实现抛出 UnsupportedOperationException，仅 H2 方言需要重写。
     *
     * @param tableName 表名
     * @param insertColumns 插入列名列表
     * @param insertFieldValues 插入字段值列表
     * @return SQL 和参数
     * @throws UnsupportedOperationException 如果方言不支持此操作
     */
    default SqlWithParams buildInsertOnlySql(String tableName, List<String> insertColumns,
        List<MergeSpec.EntityFieldValue> insertFieldValues) {
        throw new UnsupportedOperationException("Simple INSERT not supported for dialect: " + name());
    }

    /**
     * 构建 INSERT 部分的 SQL（所有方言共用）。
     *
     * @param escapedTable 转义后的表名
     * @param insertColumns 插入列名列表
     * @param insertFieldValues 插入字段值列表
     * @return SQL 和参数
     */
    static SqlWithParams buildInsertPart(String escapedTable, List<String> insertColumns,
        List<MergeSpec.EntityFieldValue> insertFieldValues) {
        StringBuilder sql = new StringBuilder("INSERT INTO ").append(escapedTable).append(" (");
        sql.append(String.join(", ", insertColumns.stream().map(c -> c).toList()));
        sql.append(") VALUES (");
        List<Object> params = new ArrayList<>();
        for (int i = 0; i < insertFieldValues.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append("?");
            params.add(insertFieldValues.get(i).value());
        }
        sql.append(")");
        return new SqlWithParams(sql.toString(), params);
    }
}
