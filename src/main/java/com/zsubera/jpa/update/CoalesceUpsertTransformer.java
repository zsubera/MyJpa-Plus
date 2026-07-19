package com.zsubera.jpa.update;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.insert.InsertConflictAction;
import net.sf.jsqlparser.statement.update.UpdateSet;

/**
 * 将批量 UPSERT SQL 中指定列的 SET 子句包装为 COALESCE，
 * 防止列并集填充的 NULL 覆盖已有数据。
 *
 * <p>
 * 当批量 upsert 的实体集合中某些实体的 {@code @Embedded} 字段为 null 时，
 * MergeSpec 会对缺失列填充 NULL 以保证列数一致。在 ON CONFLICT DO UPDATE SET 中，
 * 这些 NULL 会覆盖冲突行的已有值。本类通过 JSqlParser AST 后处理，
 * 将受影响列的赋值替换为 {@code COALESCE(EXCLUDED.col, col)}，
 * 使得 NULL 值被忽略、已有数据得以保留。
 *
 * <p>
 * 仅对 coalesceColumns 中列出的列生效，其余列保持原样。
 * 解析失败时安全降级，返回原始 SQL。
 */
class CoalesceUpsertTransformer {

    private static final Logger log = LoggerFactory.getLogger(CoalesceUpsertTransformer.class);

    private CoalesceUpsertTransformer() {}

    /**
     * 对 upsert SQL 应用 COALESCE 保护。
     *
     * @param sql            方言生成的原始 UPSERT SQL
     * @param coalesceColumns 需要 COALESCE 保护的列名集合（空集合则直接返回原 SQL）
     * @return 变换后的 SQL，或解析失败时的原始 SQL
     */
    static String applyCoalesce(String sql, Set<String> coalesceColumns) {
        if (coalesceColumns == null || coalesceColumns.isEmpty()) {
            return sql;
        }
        try {
            Statement stmt = CCJSqlParserUtil.parse(sql);
            if (stmt instanceof Insert insert) {
                boolean modified = false;
                // PostgreSQL: ON CONFLICT DO UPDATE SET
                InsertConflictAction conflictAction = insert.getConflictAction();
                if (conflictAction != null && conflictAction.getUpdateSets() != null) {
                    modified |= transformUpdateSets(conflictAction.getUpdateSets(), coalesceColumns);
                }
                // MySQL: ON DUPLICATE KEY UPDATE (useDuplicate 或 getDuplicateUpdateSets)
                if (insert.isUseDuplicate() && insert.getDuplicateUpdateSets() != null) {
                    modified |= transformUpdateSets(insert.getDuplicateUpdateSets(), coalesceColumns);
                }
                if (modified) {
                    return insert.toString();
                }
            }
        } catch (Exception e) {
            // ponytail: COALESCE protection is critical for data integrity — silently
            // degrading would cause NULL values from @Embedded fields to overwrite
            // existing data in conflict rows. Throw to prevent data corruption.
            throw new com.zsubera.jpa.exception.MyJpaPlusException("COALESCE transformation failed for UPSERT SQL. "
                + "This would cause NULL values to overwrite existing data on conflict. " + "SQL parse error: "
                + e.getMessage() + ". SQL (truncated): " + (sql.length() > 200 ? sql.substring(0, 200) + "..." : sql),
                e);
        }
        return sql;
    }

    @SuppressWarnings("unchecked")
    private static boolean transformUpdateSets(java.util.List<UpdateSet> updateSets, Set<String> coalesceColumns) {
        boolean modified = false;
        for (UpdateSet updateSet : updateSets) {
            int size = updateSet.getColumns().size();
            java.util.List<Expression> expressions = (java.util.List<Expression>)updateSet.getValues().getExpressions();
            for (int i = 0; i < size; i++) {
                Column col = updateSet.getColumn(i);
                // 使用 getUnquotedColumnName() 获取不含引号的列名，确保与 coalesceColumns 匹配
                String colName = col.getUnquotedColumnName();
                if (coalesceColumns.contains(colName)) {
                    Expression originalValue = expressions.get(i);
                    // 复用原始 Column 对象以保留引号元数据（如 "order" 不会被去引号）
                    Function coalesce = new Function().withName("COALESCE").withParameters(originalValue, col);
                    expressions.set(i, coalesce);
                    modified = true;
                }
            }
        }
        return modified;
    }
}
