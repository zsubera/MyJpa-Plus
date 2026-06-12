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
 * 使用 VALUES() 函数引用新插入值。此语法兼容 MySQL 5.7+ 和 8.0+。
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

        // INSERT INTO `table` (`col1`, `col2`) VALUES (?, ?)
        // ON DUPLICATE KEY UPDATE `col` = VALUES(`col`)
        StringBuilder sql = new StringBuilder("INSERT INTO ").append(escapedTable).append(" (");
        List<String> escapedInsertCols = new ArrayList<>();
        for (String col : insertColumns) {
            escapedInsertCols.add(escapeIdentifier(col));
        }
        sql.append(String.join(", ", escapedInsertCols));
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

        // ON DUPLICATE KEY UPDATE `col` = VALUES(`col`)
        if (updateColumns.isEmpty()) {
            // 无更新字段时使用 INSERT IGNORE 语义：忽略冲突，不执行任何更新
            sql = new StringBuilder("INSERT IGNORE INTO ").append(escapedTable);
            sql.append(" (");
            List<String> escapedInsertCols2 = new ArrayList<>();
            for (String col : insertColumns) {
                escapedInsertCols2.add(escapeIdentifier(col));
            }
            sql.append(String.join(", ", escapedInsertCols2));
            sql.append(") VALUES (");
            List<Object> params2 = new ArrayList<>();
            for (int i = 0; i < insertFieldValues.size(); i++) {
                if (i > 0) {
                    sql.append(", ");
                }
                sql.append("?");
                params2.add(insertFieldValues.get(i).value());
            }
            sql.append(")");
            return new SqlWithParams(sql.toString(), params2);
        }
        sql.append(" ON DUPLICATE KEY UPDATE ");
        List<String> setClauses = new ArrayList<>();
        for (String col : updateColumns) {
            String escaped = escapeIdentifier(col);
            setClauses.add(escaped + " = VALUES(" + escaped + ")");
        }
        sql.append(String.join(", ", setClauses));
        return new SqlWithParams(sql.toString(), params);
    }
}
