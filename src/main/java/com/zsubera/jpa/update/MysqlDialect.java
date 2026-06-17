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
 * MySQL 尚未提供完全替代 VALUES() 的语法（行别名语法 {@code INSERT ... AS new_row ON DUPLICATE KEY UPDATE col = new_row.col}
 * 在某些 MySQL 版本中不被支持），因此当前使用 VALUES() 以确保兼容性。
 *
 * <p>
 * 标识符使用反引号转义：{@code `identifier`}
 */
final class MysqlDialect implements DialectStrategy {

    /**
     * {@inheritDoc}
     *
     * <p>返回 "mysql"。
     */
    @Override
    public String name() {
        return "mysql";
    }

    /**
     * {@inheritDoc}
     *
     * <p>使用反引号转义：{@code `identifier`}。
     */
    @Override
    public String escapeIdentifier(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }

    /**
     * {@inheritDoc}
     *
     * <p>生成 MySQL UPSERT SQL：
     * <ul>
     * <li>有更新列时：{@code INSERT INTO t (...) VALUES (...) ON DUPLICATE KEY UPDATE col = VALUES(col)}</li>
     * <li>无更新列时：{@code INSERT IGNORE INTO t (...) VALUES (...)}</li>
     * </ul>
     */
    @Override
    public SqlWithParams buildUpsertSql(String tableName, List<String> insertColumns,
        List<EntityFieldValue> insertFieldValues, List<String> conflictColumns, List<String> updateColumns) {
        String escapedTable = escapeIdentifier(tableName);

        if (updateColumns.isEmpty()) {
            // 无更新字段时使用 INSERT IGNORE 语义：忽略冲突，不执行任何更新
            SqlWithParams insert = buildInsertClause(escapedTable, insertColumns, insertFieldValues);
            return new SqlWithParams("INSERT IGNORE INTO " + insert.sql(), insert.params());
        }

        // INSERT INTO `table` (`col1`, `col2`) VALUES (?, ?)
        SqlWithParams insert = buildInsertClause(escapedTable, insertColumns, insertFieldValues);
        StringBuilder sql = new StringBuilder(insert.sql());

        // ON DUPLICATE KEY UPDATE `col` = VALUES(`col`)
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
}
