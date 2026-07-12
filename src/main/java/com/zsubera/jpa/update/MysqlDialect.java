package com.zsubera.jpa.update;

import java.util.ArrayList;
import java.util.List;

import com.zsubera.jpa.update.EntityFieldExtractor.EntityFieldValue;
import com.zsubera.jpa.exception.MyJpaPlusException;

/**
 * MySQL 方言实现。
 *
 * <p>
 * UPSERT 语法：{@code INSERT INTO t (...) VALUES (...) ON DUPLICATE KEY UPDATE col = VALUES(col)}
 *
 * <p>
 * VALUES() 函数在 MySQL 8.0.20 被标记为弃用，但当前仍可正常工作。
 * 设置系统属性 {@code myjpa-plus.mysql.use-row-alias=true} 或调用
 * {@link #setUseRowAliasSyntax(boolean)} 可启用 MySQL 8.0.19+
 * 的行别名语法 {@code AS new}。
 *
 * <p>
 * 标识符使用反引号转义：{@code `identifier`}
 */
final class MysqlDialect extends AbstractDialectStrategy {

    private static volatile boolean defaultUseRowAliasSyntax = false;

    static {
        String prop = System.getProperty("myjpa-plus.mysql.use-row-alias");
        if ("true".equalsIgnoreCase(prop)) {
            defaultUseRowAliasSyntax = true;
        }
    }

    private volatile boolean useRowAliasSyntax;

    MysqlDialect() {
        this.useRowAliasSyntax = defaultUseRowAliasSyntax;
    }

    /**
     * 设置是否使用 MySQL 8.0.19+ 的行别名语法（{@code AS new}）。
     *
     * <p><strong>线程安全说明：</strong>此字段为 volatile，读取在单次 {@link #buildUpsertSql} 调用内一致。
     * 但并发调用此 setter 与 {@code buildUpsertSql()} 可能导致不同实体使用不同语法。
     * 建议在应用启动阶段设置一次，运行时不要修改。</p>
     */
    void setUseRowAliasSyntax(boolean enabled) {
        this.useRowAliasSyntax = enabled;
    }

    boolean isUseRowAliasSyntax() {
        return useRowAliasSyntax;
    }

    @Override
    public String name() {
        return "mysql";
    }

    @Override
    protected char getQuoteChar() {
        return '`';
    }

    @Override
    protected String getEscapeSequence() {
        return "``";
    }

    @Override
    public SqlWithParams buildUpsertSql(String tableName, List<String> insertColumns,
        List<EntityFieldValue> insertFieldValues, List<String> conflictColumns, List<String> updateColumns) {
        String escapedTable = escapeIdentifier(tableName);

        if (insertColumns.isEmpty()) {
            throw new com.zsubera.jpa.exception.MyJpaPlusException("No insertable columns found for UPSERT on "
                + tableName + ". Ensure at least one non-auto-generated field has a value.");
        }

        if (updateColumns.isEmpty()) {
            org.slf4j.LoggerFactory.getLogger(MysqlDialect.class)
                .warn("No update columns specified for UPSERT on {}. MySQL ON DUPLICATE KEY UPDATE "
                    + "id = VALUES(id) is a no-op (MySQL reports 1 row affected but no data changed). "
                    + "Consider using INSERT IGNORE or specifying update columns.", tableName);
            SqlWithParams insert = buildInsertClause(escapedTable, insertColumns, insertFieldValues);
            StringBuilder sql = new StringBuilder(insert.sql());
            String idCol = escapeIdentifier(insertColumns.get(0));
            if (useRowAliasSyntax) {
                sql.append(" AS new ON DUPLICATE KEY UPDATE ").append(idCol).append(" = new.").append(idCol);
            } else {
                sql.append(" ON DUPLICATE KEY UPDATE ").append(idCol).append(" = VALUES(").append(idCol).append(")");
            }
            return new SqlWithParams(sql.toString(), insert.params());
        }

        SqlWithParams insert = buildInsertClause(escapedTable, insertColumns, insertFieldValues);
        StringBuilder sql = new StringBuilder(insert.sql());

        if (useRowAliasSyntax) {
            sql.append(" AS new ON DUPLICATE KEY UPDATE ");
        } else {
            sql.append(" ON DUPLICATE KEY UPDATE ");
        }
        List<String> setClauses = new ArrayList<>();
        for (String col : updateColumns) {
            String escaped = escapeIdentifier(col);
            if (useRowAliasSyntax) {
                setClauses.add(escaped + " = new." + escaped);
            } else {
                setClauses.add(escaped + " = VALUES(" + escaped + ")");
            }
        }
        sql.append(String.join(", ", setClauses));
        return new SqlWithParams(sql.toString(), insert.params());
    }

    private SqlWithParams buildInsertClause(String escapedTable, List<String> insertColumns,
        List<EntityFieldValue> insertFieldValues) {
        List<String> escapedCols = new ArrayList<>(insertColumns.size());
        for (String col : insertColumns) {
            escapedCols.add(escapeIdentifier(col));
        }
        StringBuilder sql = new StringBuilder("INSERT INTO ").append(escapedTable).append(" (");
        sql.append(String.join(", ", escapedCols));
        sql.append(") VALUES (");
        List<Object> params = new ArrayList<>(insertFieldValues.size());
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
                throw new MyJpaPlusException("Column count (" + insertColumns.size() + ") does not match value count ("
                    + values.size() + ") at batch row " + row);
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
        if (insertColumns.isEmpty()) {
            throw new MyJpaPlusException("Batch UPSERT requires at least one insert column. "
                + "All fields may be auto-generated with null values. "
                + "Ensure at least one non-auto-generated field is included.");
        }
        if (useRowAliasSyntax) {
            sql.append(" AS new");
        }
        if (updateColumns.isEmpty()) {
            String idCol = escapeIdentifier(insertColumns.get(0));
            if (useRowAliasSyntax) {
                sql.append(" ON DUPLICATE KEY UPDATE ").append(idCol).append(" = new.").append(idCol);
            } else {
                sql.append(" ON DUPLICATE KEY UPDATE ").append(idCol).append(" = VALUES(").append(idCol).append(")");
            }
        } else {
            sql.append(" ON DUPLICATE KEY UPDATE ");
            List<String> setClauses = new ArrayList<>();
            for (String col : updateColumns) {
                String escaped = escapeIdentifier(col);
                if (useRowAliasSyntax) {
                    setClauses.add(escaped + " = new." + escaped);
                } else {
                    setClauses.add(escaped + " = VALUES(" + escaped + ")");
                }
            }
            sql.append(String.join(", ", setClauses));
        }
        return new SqlWithParams(sql.toString(), allParams);
    }
}
