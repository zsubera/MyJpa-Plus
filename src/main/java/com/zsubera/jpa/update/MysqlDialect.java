package com.zsubera.jpa.update;

import java.util.ArrayList;
import java.util.List;

import com.zsubera.jpa.update.EntityFieldExtractor.EntityFieldValue;

/**
 * MySQL 方言实现。
 *
 * <p>
 * UPSERT 语法：{@code INSERT ... ON DUPLICATE KEY UPDATE col = col}
 *
 * <p>
 * 注意：MySQL 8.0.20+ 已弃用 {@code VALUES(col)} 语法，推荐直接引用列名。
 * 此实现使用列名引用方式，兼容 MySQL 5.7+ 和 8.0+。
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
        SqlWithParams insertPart = DialectStrategy.buildInsertPart(escapedTable, insertColumns, insertFieldValues);
        StringBuilder sql = new StringBuilder(insertPart.sql());
        sql.append(" ON DUPLICATE KEY UPDATE ");
        List<String> setClauses = new ArrayList<>();
        for (String col : updateColumns) {
            String escaped = escapeIdentifier(col);
            // MySQL 8.0.20+ 推荐直接引用列名，而非使用 VALUES(col)
            // 列名在 ON DUPLICATE KEY UPDATE 上下文中会引用新插入的值
            setClauses.add(escaped + " = " + escaped);
        }
        sql.append(String.join(", ", setClauses));
        return new SqlWithParams(sql.toString(), insertPart.params());
    }
}
