package com.zsubera.jpa.update;

import java.util.ArrayList;
import java.util.List;

import com.zsubera.jpa.update.EntityFieldExtractor.EntityFieldValue;

/**
 * Oracle dialect implementation.
 *
 * <p>
 * UPSERT syntax: {@code MERGE INTO t USING (SELECT ... FROM DUAL) ON (condition) WHEN MATCHED THEN UPDATE ... WHEN NOT MATCHED THEN INSERT ...}
 *
 * <p>
 * Identifiers are escaped using double quotes: {@code "identifier"} (Oracle standard SQL identifier rules).
 */
final class OracleDialect extends AbstractDialectStrategy {

    @Override
    public String name() {
        return "oracle";
    }

    @Override
    protected char getQuoteChar() {
        return '"';
    }

    @Override
    protected String getEscapeSequence() {
        return "\"\"";
    }

    @Override
    public SqlWithParams buildUpsertSql(String tableName, List<String> insertColumns,
        List<EntityFieldValue> insertFieldValues, List<String> conflictColumns, List<String> updateColumns) {
        if (conflictColumns == null || conflictColumns.isEmpty()) {
            throw new IllegalArgumentException("Oracle UPSERT requires at least one conflict column for the ON clause. "
                + "Specify conflict columns via MergeSpec.withConflictColumns().");
        }
        if (insertColumns.size() != insertFieldValues.size()) {
            throw new IllegalArgumentException("Column count (" + insertColumns.size()
                + ") does not match value count (" + insertFieldValues.size() + ")");
        }
        String escapedTable = escapeIdentifier(tableName);
        List<Object> allParams = new ArrayList<>();

        StringBuilder sql = new StringBuilder("MERGE INTO ").append(escapedTable).append(" target USING (SELECT ");

        // SELECT clause: use parameter placeholders with column aliases for DUAL
        // 包含所有 insertColumns + 不在 insertColumns 中的 conflictColumns
        List<String> escapedInsertCols = new ArrayList<>();
        List<String> selectClauses = new ArrayList<>();
        java.util.Set<String> insertColSet = new java.util.HashSet<>(insertColumns);
        for (String col : insertColumns) {
            String escaped = escapeIdentifier(col);
            escapedInsertCols.add(escaped);
            selectClauses.add("? AS " + escaped);
        }
        // 将不在 insertColumns 中的 conflictColumns 添加到源子查询（值为 NULL）
        List<String> missingConflictCols = new ArrayList<>();
        for (String col : conflictColumns) {
            if (!insertColSet.contains(col)) {
                String escaped = escapeIdentifier(col);
                missingConflictCols.add(escaped);
                selectClauses.add("NULL AS " + escaped);
            }
        }
        sql.append(String.join(", ", selectClauses));
        sql.append(" FROM DUAL) source ON (");

        // ON clause: match on conflict columns (NULL-safe)
        List<String> onConditions = new ArrayList<>();
        for (String col : conflictColumns) {
            String escaped = escapeIdentifier(col);
            onConditions.add("(target." + escaped + " = source." + escaped + " OR (target." + escaped
                + " IS NULL AND source." + escaped + " IS NULL))");
        }
        sql.append(String.join(" AND ", onConditions));
        sql.append(")");

        // WHEN MATCHED THEN UPDATE
        if (!updateColumns.isEmpty()) {
            sql.append(" WHEN MATCHED THEN UPDATE SET ");
            List<String> setClauses = new ArrayList<>();
            for (String col : updateColumns) {
                String escaped = escapeIdentifier(col);
                setClauses.add("target." + escaped + " = source." + escaped);
            }
            sql.append(String.join(", ", setClauses));
        }

        // WHEN NOT MATCHED THEN INSERT
        sql.append(" WHEN NOT MATCHED THEN INSERT (");
        sql.append(String.join(", ", escapedInsertCols));
        sql.append(") VALUES (");
        List<String> sourceRefs = new ArrayList<>();
        for (String col : insertColumns) {
            sourceRefs.add("source." + escapeIdentifier(col));
        }
        sql.append(String.join(", ", sourceRefs));
        sql.append(")");

        // Collect parameters in order: insert columns first
        for (EntityFieldValue fv : insertFieldValues) {
            allParams.add(fv.value());
        }

        return new SqlWithParams(sql.toString(), allParams);
    }
}
