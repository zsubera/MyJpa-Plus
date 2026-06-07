package com.zsubera.jpa.update;

import java.util.ArrayList;
import java.util.List;

/**
 * H2 方言实现。
 *
 * <p>
 * H2 的 UPSERT 语法：{@code MERGE INTO ... KEY(...) VALUES(...)}
 *
 * <p>
 * 当冲突键值全为 null（如自动生成 ID 的新实体）时，使用简单 INSERT 代替 MERGE INTO，因为 H2 的 MERGE INTO 不支持 null KEY 值。
 *
 * <p>
 * 标识符使用双引号转义，且转换为大写以匹配 H2 默认的大小写不敏感行为：{@code "IDENTIFIER"}
 */
class H2Dialect implements DialectStrategy {

    @Override
    public String name() {
        return "h2";
    }

    @Override
    public String escapeIdentifier(String identifier) {
        return "\"" + identifier.toUpperCase(java.util.Locale.ROOT).replace("\"", "\"\"") + "\"";
    }

    @Override
    public SqlWithParams buildInsertOnlySql(String tableName, List<String> insertColumns,
        List<MergeSpec.EntityFieldValue> insertFieldValues) {
        String escapedTable = escapeIdentifier(tableName);
        SqlWithParams insertPart = DialectStrategy.buildInsertPart(escapedTable, insertColumns, insertFieldValues);
        return new SqlWithParams(insertPart.sql(), insertPart.params());
    }

    @Override
    public SqlWithParams buildUpsertSql(String tableName, List<String> insertColumns,
        List<MergeSpec.EntityFieldValue> insertFieldValues, List<String> conflictColumns, List<String> updateColumns) {
        String escapedTable = escapeIdentifier(tableName);
        StringBuilder sql = new StringBuilder("MERGE INTO ");
        sql.append(escapedTable).append(" (");
        List<String> escapedInsertCols = new ArrayList<>();
        for (String col : insertColumns) {
            escapedInsertCols.add(escapeIdentifier(col));
        }
        sql.append(String.join(", ", escapedInsertCols));
        sql.append(") KEY (");
        List<String> escapedConflictCols = new ArrayList<>();
        for (String col : conflictColumns) {
            escapedConflictCols.add(escapeIdentifier(col));
        }
        sql.append(String.join(", ", escapedConflictCols));
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
