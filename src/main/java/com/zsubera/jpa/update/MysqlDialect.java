package com.zsubera.jpa.update;

import java.util.ArrayList;
import java.util.List;

/**
 * MySQL 方言实现。
 *
 * <p>
 * UPSERT 语法：{@code INSERT ... ON DUPLICATE KEY UPDATE col = VALUES(col)}
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
        List<MergeSpec.EntityFieldValue> insertFieldValues, List<String> conflictColumns, List<String> updateColumns) {
        String escapedTable = escapeIdentifier(tableName);
        SqlWithParams insertPart = DialectStrategy.buildInsertPart(escapedTable, insertColumns, insertFieldValues);
        StringBuilder sql = new StringBuilder(insertPart.sql());
        sql.append(" ON DUPLICATE KEY UPDATE ");
        List<String> setClauses = new ArrayList<>();
        for (String col : updateColumns) {
            String escaped = escapeIdentifier(col);
            setClauses.add(escaped + " = VALUES(" + escaped + ")");
        }
        sql.append(String.join(", ", setClauses));
        return new SqlWithParams(sql.toString(), insertPart.params());
    }
}
