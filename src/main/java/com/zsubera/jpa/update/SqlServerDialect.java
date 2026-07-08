package com.zsubera.jpa.update;

import java.util.ArrayList;
import java.util.List;

import com.zsubera.jpa.update.EntityFieldExtractor.EntityFieldValue;

/**
 * SQL Server dialect implementation.
 *
 * <p>
 * UPSERT syntax: {@code MERGE INTO t USING (SELECT ...) ON (condition) WHEN MATCHED THEN UPDATE ... WHEN NOT MATCHED THEN INSERT ...}
 *
 * <p>
 * SQL Server uses square brackets to escape identifiers: {@code [identifier]}.
 */
final class SqlServerDialect extends AbstractDialectStrategy {

    @Override
    public String name() {
        return "sqlserver";
    }

    @Override
    protected char getQuoteChar() {
        return '[';
    }

    @Override
    protected char getClosingQuoteChar() {
        return ']';
    }

    @Override
    protected String getEscapeSequence() {
        return "]]";
    }

    @Override
    public SqlWithParams buildUpsertSql(String tableName, List<String> insertColumns,
        List<EntityFieldValue> insertFieldValues, List<String> conflictColumns, List<String> updateColumns) {
        if (conflictColumns == null || conflictColumns.isEmpty()) {
            throw new IllegalArgumentException(
                "SQL Server UPSERT requires at least one conflict column for the ON clause. "
                    + "Specify conflict columns via MergeSpec.withConflictColumns().");
        }
        if (insertColumns.size() != insertFieldValues.size()) {
            throw new IllegalArgumentException("Column count (" + insertColumns.size()
                + ") does not match value count (" + insertFieldValues.size() + ")");
        }
        String escapedTable = escapeIdentifier(tableName);
        List<Object> allParams = new ArrayList<>();

        StringBuilder sql = new StringBuilder("MERGE INTO ").append(escapedTable).append(" AS target USING (SELECT ");

        // SELECT clause for source values
        List<String> escapedInsertCols = new ArrayList<>();
        for (String col : insertColumns) {
            escapedInsertCols.add(escapeIdentifier(col));
        }
        sql.append(String.join(", ", escapedInsertCols));
        sql.append(" FROM (VALUES (");

        // VALUES clause with parameters
        for (int i = 0; i < insertFieldValues.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append("?");
            allParams.add(insertFieldValues.get(i).value());
        }
        sql.append(")) AS vals (");
        sql.append(String.join(", ", escapedInsertCols));
        sql.append(")) AS source ON (");

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
        } else {
            org.slf4j.LoggerFactory.getLogger(SqlServerDialect.class)
                .warn(
                    "No update columns specified for UPSERT on {}. SQL Server MERGE without "
                        + "WHEN MATCHED silently ignores conflicting rows. Consider specifying update columns.",
                    tableName);
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
        sql.append(");");

        return new SqlWithParams(sql.toString(), allParams);
    }
}
