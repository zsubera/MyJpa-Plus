package com.zsubera.jpa.update;

import java.util.ArrayList;
import java.util.List;

import com.zsubera.jpa.update.EntityFieldExtractor.EntityFieldValue;

/**
 * MySQL 方言实现。
 *
 * <p>
 * UPSERT 语法：{@code INSERT INTO t (...) VALUES (...) AS new ON DUPLICATE KEY UPDATE col = new.col}
 *
 * <p>
 * 使用行别名（MySQL 8.0.19+）引用新插入值。MySQL 8.0.20+ 已弃用 {@code VALUES(col)} 语法。
 * 对于 MySQL 5.7 用户，需使用 {@link PostgresDialect} 风格的旧语法（自行扩展）。
 *
 * <p>
 * 标识符使用反引号转义：{@code `identifier`}
 */
class MysqlDialect implements DialectStrategy {

    @Override
    public String name() {
        return "mysql";
    }

    @Override
    public String escapeIdentifier(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }

    @Override
    public SqlWithParams buildUpsertSql(String tableName, List<String> insertColumns,
        List<EntityFieldValue> insertFieldValues, List<String> conflictColumns, List<String> updateColumns) {
        String escapedTable = escapeIdentifier(tableName);
        String rowAlias = "_new";

        // INSERT INTO `table` AS `_new` (`col1`, `col2`) VALUES (?, ?)
        StringBuilder sql = new StringBuilder("INSERT INTO ").append(escapedTable);
        sql.append(" AS ").append(escapeIdentifier(rowAlias)).append(" (");
        List<String> escapedInsertCols = new ArrayList<>();
        for (String col : insertColumns) {
            escapedInsertCols.add(escapeIdentifier(col));
        }
        sql.append(String.join(", ", escapedInsertCols));
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

        // ON DUPLICATE KEY UPDATE `col` = `_new`.`col`
        sql.append(" ON DUPLICATE KEY UPDATE ");
        List<String> setClauses = new ArrayList<>();
        String escapedAlias = escapeIdentifier(rowAlias);
        for (String col : updateColumns) {
            String escaped = escapeIdentifier(col);
            setClauses.add(escaped + " = " + escapedAlias + "." + escaped);
        }
        sql.append(String.join(", ", setClauses));
        return new SqlWithParams(sql.toString(), params);
    }
}
