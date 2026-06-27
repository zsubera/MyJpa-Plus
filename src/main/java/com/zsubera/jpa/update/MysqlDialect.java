package com.zsubera.jpa.update;

import java.util.ArrayList;
import java.util.List;

import com.zsubera.jpa.update.EntityFieldExtractor.EntityFieldValue;

/**
 * MySQL 方言实现。
 *
 * <p>
 * UPSERT 语法：{@code INSERT INTO t (...) VALUES (...) ON DUPLICATE KEY UPDATE col = VALUES(col)}
 *
 * <p>
 * 使用 VALUES() 函数引用新插入值。VALUES() 在 MySQL 8.0.20 中被标记为弃用但仍可用。
 * MySQL 推荐使用行别名语法 {@code INSERT ... AS new_row ON DUPLICATE KEY UPDATE col = new_row.col}，
 * 但该语法仅在 MySQL 8.0.19+ 可用，且部分中间件/代理可能不兼容。因此当前使用 VALUES() 以确保广泛兼容性。
 *
 * <p>
 * ponytail: 行别名语法需要版本检测能力（通过 JDBC Connection.getMetaData().getDatabaseMajorVersion()），
 * 但当前 DialectStrategy 接口未传递 EntityManager/Connection，且 MysqlDialect 是单例无法存储 per-connection 状态。
 * 若 MySQL 未来版本移除 VALUES() 支持，需重构 DialectStrategy 接口增加版本参数，或使用 ThreadLocal 传递版本信息。
 *
 * <p>
 * 标识符使用反引号转义：{@code `identifier`}
 */
final class MysqlDialect extends AbstractDialectStrategy {

    /**
     * {@inheritDoc}
     *
     * <p>返回 "mysql"。
     */
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

    /**
     * {@inheritDoc}
     *
     * <p>生成 MySQL UPSERT SQL：
     * <ul>
     * <li>有更新列时：{@code INSERT INTO t (...) VALUES (...) ON DUPLICATE KEY UPDATE col = VALUES(col)}</li>
     * <li>无更新列时：{@code INSERT INTO t (...) VALUES (...) ON DUPLICATE KEY UPDATE id = VALUES(id)}</li>
     * </ul>
     */
    @Override
    public SqlWithParams buildUpsertSql(String tableName, List<String> insertColumns,
        List<EntityFieldValue> insertFieldValues, List<String> conflictColumns, List<String> updateColumns) {
        String escapedTable = escapeIdentifier(tableName);

        if (insertColumns.isEmpty()) {
            throw new com.zsubera.jpa.exception.MyJpaPlusException("No insertable columns found for UPSERT on "
                + tableName + ". Ensure at least one non-auto-generated field has a value.");
        }

        if (updateColumns.isEmpty()) {
            SqlWithParams insert = buildInsertClause(escapedTable, insertColumns, insertFieldValues);
            StringBuilder sql = new StringBuilder(insert.sql());
            sql.append(" ON DUPLICATE KEY UPDATE ");
            String idCol = escapeIdentifier(insertColumns.get(0));
            sql.append(idCol).append(" = VALUES(").append(idCol).append(")");
            return new SqlWithParams(sql.toString(), insert.params());
        }

        SqlWithParams insert = buildInsertClause(escapedTable, insertColumns, insertFieldValues);
        StringBuilder sql = new StringBuilder(insert.sql());

        // ON DUPLICATE KEY UPDATE `col` = VALUES(`col`)
        // ponytail: VALUES() deprecated in MySQL 8.0.20+ but still functional. See class Javadoc for upgrade path.
        sql.append(" ON DUPLICATE KEY UPDATE ");
        List<String> setClauses = new ArrayList<>();
        for (String col : updateColumns) {
            String escaped = escapeIdentifier(col);
            setClauses.add(escaped + " = VALUES(" + escaped + ")");
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
            if (row > 0) {
                sql.append(", ");
            }
            sql.append("(");
            List<EntityFieldValue> values = batchFieldValues.get(row);
            for (int i = 0; i < values.size(); i++) {
                if (i > 0) {
                    sql.append(", ");
                }
                sql.append("?");
                allParams.add(values.get(i).value());
            }
            sql.append(")");
        }
        if (updateColumns.isEmpty()) {
            String idCol = escapeIdentifier(insertColumns.get(0));
            sql.append(" ON DUPLICATE KEY UPDATE ").append(idCol).append(" = VALUES(").append(idCol).append(")");
        } else {
            sql.append(" ON DUPLICATE KEY UPDATE ");
            List<String> setClauses = new ArrayList<>();
            for (String col : updateColumns) {
                String escaped = escapeIdentifier(col);
                setClauses.add(escaped + " = VALUES(" + escaped + ")");
            }
            sql.append(String.join(", ", setClauses));
        }
        return new SqlWithParams(sql.toString(), allParams);
    }
}
