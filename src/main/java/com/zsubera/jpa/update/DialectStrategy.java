package com.zsubera.jpa.update;

import java.util.ArrayList;
import java.util.List;

import com.zsubera.jpa.update.EntityFieldExtractor.EntityFieldValue;

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
 * </ul>
 *
 * @see MergeSpec
 * @see EntityFieldExtractor
 */
public interface DialectStrategy {

    /**
     * 返回方言标识符（如 "postgresql"、"mysql"）。
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
    SqlWithParams buildUpsertSql(String tableName, List<String> insertColumns, List<EntityFieldValue> insertFieldValues,
        List<String> conflictColumns, List<String> updateColumns);

    /**
     * 构建 INSERT 部分的 SQL（PostgresDialect 共用，MysqlDialect 自行实现）。
     *
     * @param escapedTable 转义后的表名
     * @param dialect 方言实例，用于转义列名
     * @param insertColumns 插入列名列表（原始名称）
     * @param insertFieldValues 插入字段值列表
     * @return SQL 和参数
     */
    static SqlWithParams buildInsertPart(String escapedTable, DialectStrategy dialect, List<String> insertColumns,
        List<EntityFieldValue> insertFieldValues) {
        if (insertColumns.size() != insertFieldValues.size()) {
            throw new IllegalArgumentException("Column count (" + insertColumns.size()
                + ") does not match value count (" + insertFieldValues.size() + ")");
        }
        StringBuilder sql = new StringBuilder("INSERT INTO ").append(escapedTable).append(" (");
        List<String> escapedCols = new ArrayList<>();
        for (String col : insertColumns) {
            escapedCols.add(dialect.escapeIdentifier(col));
        }
        sql.append(String.join(", ", escapedCols));
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
