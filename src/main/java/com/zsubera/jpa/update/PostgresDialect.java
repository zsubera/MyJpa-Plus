package com.zsubera.jpa.update;

import java.util.ArrayList;
import java.util.List;

import com.zsubera.jpa.update.EntityFieldExtractor.EntityFieldValue;

/**
 * PostgreSQL 方言实现。
 *
 * <p>
 * UPSERT 语法：{@code INSERT ... ON CONFLICT (...) DO UPDATE SET col = EXCLUDED.col}
 *
 * <p>
 * 标识符使用双引号转义：{@code "identifier"}
 */
class PostgresDialect implements DialectStrategy {

    @Override
    public String name() {
        return "postgresql";
    }

    @Override
    public String escapeIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    @Override
    public SqlWithParams buildUpsertSql(String tableName, List<String> insertColumns,
        List<EntityFieldValue> insertFieldValues, List<String> conflictColumns, List<String> updateColumns) {
        String escapedTable = escapeIdentifier(tableName);
        SqlWithParams insertPart =
            DialectStrategy.buildInsertPart(escapedTable, this, insertColumns, insertFieldValues);
        StringBuilder sql = new StringBuilder(insertPart.sql());
        sql.append(" ON CONFLICT (");
        List<String> escapedConflict = new ArrayList<>();
        for (String col : conflictColumns) {
            escapedConflict.add(escapeIdentifier(col));
        }
        sql.append(String.join(", ", escapedConflict));
        sql.append(") DO UPDATE SET ");
        if (updateColumns.isEmpty()) {
            sql.append(escapeIdentifier(conflictColumns.get(0)) + " = " + escapeIdentifier(conflictColumns.get(0)));
        } else {
            List<String> setClauses = new ArrayList<>();
            for (String col : updateColumns) {
                String escaped = escapeIdentifier(col);
                setClauses.add(escaped + " = EXCLUDED." + escaped);
            }
            sql.append(String.join(", ", setClauses));
        }
        return new SqlWithParams(sql.toString(), insertPart.params());
    }
}
