package com.zsubera.jpa.update;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * SQL 构建结果，包含 SQL 语句和有序的参数值。
 *
 * @param sql SQL 语句（使用 ? 占位符）
 * @param params 参数值列表（按 ? 出现顺序排列，允许 null 元素表示 UPSERT 中的 NULL 值）
 */
record SqlWithParams(String sql, List<Object> params) {
    SqlWithParams {
        params = Collections.unmodifiableList(new ArrayList<>(params));
    }
}
