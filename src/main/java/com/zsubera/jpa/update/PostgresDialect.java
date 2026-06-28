package com.zsubera.jpa.update;

import java.util.ArrayList;
import java.util.List;

import com.zsubera.jpa.update.EntityFieldExtractor.EntityFieldValue;
import com.zsubera.jpa.exception.MyJpaPlusException;

/**
 * PostgreSQL 方言实现。
 *
 * <p>
 * UPSERT 语法：{@code INSERT ... ON CONFLICT (...) DO UPDATE SET col = EXCLUDED.col}
 *
 * <p>
 * 标识符使用双引号转义：{@code "identifier"}
 */
final class PostgresDialect extends AbstractDialectStrategy {

    /**
     * {@inheritDoc}
     *
     * <p>返回 "postgresql"。
     */
    @Override
    public String name() {
        return "postgresql";
    }

    @Override
    protected char getQuoteChar() {
        return '"';
    }

    @Override
    protected String getEscapeSequence() {
        return "\"\"";
    }

    /**
     * {@inheritDoc}
     *
     * <p>生成 PostgreSQL UPSERT SQL：
     * <ul>
     * <li>有更新列时：{@code INSERT INTO t (...) VALUES (...) ON CONFLICT (...) DO UPDATE SET col = EXCLUDED.col}</li>
     * <li>无更新列时：{@code INSERT INTO t (...) VALUES (...) ON CONFLICT (...) DO NOTHING}</li>
     * </ul>
     */
    @Override
    public SqlWithParams buildUpsertSql(String tableName, List<String> insertColumns,
        List<EntityFieldValue> insertFieldValues, List<String> conflictColumns, List<String> updateColumns) {
        String escapedTable = escapeIdentifier(tableName);
        SqlWithParams insertPart =
            DialectStrategy.buildInsertPart(escapedTable, this, insertColumns, insertFieldValues);
        StringBuilder sql = new StringBuilder(insertPart.sql());
        appendConflictClause(sql, conflictColumns);
        appendUpdateOrDoNothing(sql, updateColumns);
        return new SqlWithParams(sql.toString(), insertPart.params());
    }

    @Override
    public boolean supportsBatchUpsert() {
        return true;
    }

    @Override
    public SqlWithParams buildBatchUpsertSql(String tableName, List<String> insertColumns,
        List<List<EntityFieldValue>> batchFieldValues, List<String> conflictColumns, List<String> updateColumns) {
        String escapedTable = escapeIdentifier(tableName);
        List<String> escapedCols = new ArrayList<>(insertColumns.size());
        for (String col : insertColumns) {
            escapedCols.add(escapeIdentifier(col));
        }
        StringBuilder sql = new StringBuilder("INSERT INTO ").append(escapedTable).append(" (");
        sql.append(String.join(", ", escapedCols));
        sql.append(") VALUES ");
        List<Object> allParams = new ArrayList<>();
        for (int row = 0; row < batchFieldValues.size(); row++) {
            List<EntityFieldValue> values = batchFieldValues.get(row);
            if (values.size() != insertColumns.size()) {
                throw new MyJpaPlusException("Column count (" + insertColumns.size()
                    + ") does not match value count (" + values.size() + ") at batch row " + row);
            }
            if (row > 0) {
                sql.append(", ");
            }
            sql.append("(");
            for (int i = 0; i < values.size(); i++) {
                if (i > 0) {
                    sql.append(", ");
                }
                sql.append("?");
                allParams.add(values.get(i).value());
            }
            sql.append(")");
        }
        appendConflictClause(sql, conflictColumns);
        appendUpdateOrDoNothing(sql, updateColumns);
        return new SqlWithParams(sql.toString(), allParams);
    }

    private void appendConflictClause(StringBuilder sql, List<String> conflictColumns) {
        sql.append(" ON CONFLICT (");
        List<String> escapedConflict = new ArrayList<>();
        for (String col : conflictColumns) {
            escapedConflict.add(escapeIdentifier(col));
        }
        sql.append(String.join(", ", escapedConflict));
        sql.append(") ");
    }

    private void appendUpdateOrDoNothing(StringBuilder sql, List<String> updateColumns) {
        if (updateColumns.isEmpty()) {
            sql.append("DO NOTHING");
        } else {
            sql.append("DO UPDATE SET ");
            List<String> setClauses = new ArrayList<>();
            for (String col : updateColumns) {
                String escaped = escapeIdentifier(col);
                setClauses.add(escaped + " = EXCLUDED." + escaped);
            }
            sql.append(String.join(", ", setClauses));
        }
    }
}
