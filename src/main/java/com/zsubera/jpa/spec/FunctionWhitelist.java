package com.zsubera.jpa.spec;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 用户扩展的数据库函数白名单管理工具类。
 *
 * <p>
 * 管理运行时可扩展的安全函数名和布尔函数名集合。启动期间通过 {@link #addSafeFunctionNames(Collection)} 和
 * {@link #addBooleanFunctionNames(Collection)} 添加条目，启动完成后通过 {@link #freezeExtraFunctionNames()} 冻结为不可变快照。
 *
 * <p>
 * 冻结后的 {@code contains()} 调用基于 {@link AtomicReference} 实现无锁读取，线程安全且无竞态条件。
 *
 * <p>
 * 此类从 {@link ConditionBuilder} 接口中提取，因为这些可变字段违反了接口常量的不可变语义。
 */
public final class FunctionWhitelist {

    private FunctionWhitelist() {}

    /**
     * 用户扩展的安全函数名集合。通过 {@link #addSafeFunctionNames(Collection)} 在启动时添加。
     * 与 {@link ConditionBuilder#SAFE_FUNCTION_NAMES} 合并检查。
     *
     * <p>
     * <strong>线程安全说明：</strong>启动期间通过 {@code addSafeFunctionNames()} 添加条目，
     * 启动完成后通过 {@link #freezeExtraFunctionNames()} 冻结为不可变快照。
     * 冻结后的 {@code contains()} 调用无锁且线程安全。
     */
    static final ConcurrentHashMap.KeySetView<String, Boolean> EXTRA_SAFE_FUNCTION_NAMES =
        new ConcurrentHashMap<String, Boolean>().keySet(Boolean.TRUE);

    /**
     * 用户扩展的布尔函数名集合。通过 {@link #addBooleanFunctionNames(Collection)} 在启动时添加。
     * 与 {@link ConditionBuilder#BOOLEAN_FUNCTION_NAMES} 合并检查。
     */
    static final ConcurrentHashMap.KeySetView<String, Boolean> EXTRA_BOOLEAN_FUNCTION_NAMES =
        new ConcurrentHashMap<String, Boolean>().keySet(Boolean.TRUE);

    /**
     * 冻结后的不可变安全函数名快照，用于运行时 contains() 检查。
     * 启动完成后由 {@link #freezeExtraFunctionNames()} 设置。
     */
    static final AtomicReference<Set<String>> FROZEN_EXTRA_SAFE_FUNCTION_NAMES = new AtomicReference<>(Set.of());

    /**
     * 冻结后的不可变布尔函数名快照，用于运行时 contains() 检查。
     * 启动完成后由 {@link #freezeExtraFunctionNames()} 设置。
     */
    static final AtomicReference<Set<String>> FROZEN_EXTRA_BOOLEAN_FUNCTION_NAMES = new AtomicReference<>(Set.of());

    /**
     * 向安全函数白名单中添加额外的函数名。在应用启动时调用，运行期间不应修改。
     *
     * <p>
     * 函数名会被自动转换为大写。添加后，这些函数可在 {@code func()} 方法中使用。
     * 添加后会自动更新冻结快照，确保运行时检查使用最新数据。
     *
     * @param functionNames 要添加的函数名集合
     * @throws IllegalArgumentException 如果 functionNames 为 null
     */
    public static void addSafeFunctionNames(Collection<String> functionNames) {
        if (functionNames == null) {
            throw new IllegalArgumentException("functionNames must not be null");
        }
        for (String name : functionNames) {
            if (name != null && !name.isEmpty()) {
                EXTRA_SAFE_FUNCTION_NAMES.add(name.toUpperCase(java.util.Locale.ROOT));
            }
        }
        synchronized (FunctionWhitelist.class) {
            freezeExtraFunctionNames();
        }
    }

    /**
     * 向布尔函数白名单中添加额外的函数名。在应用启动时调用，运行期间不应修改。
     *
     * <p>
     * 函数名会被自动转换为大写。添加后，这些函数可在 {@code func()} 方法中作为布尔函数使用。
     * 添加后会自动更新冻结快照，确保运行时检查使用最新数据。
     *
     * @param functionNames 要添加的函数名集合
     * @throws IllegalArgumentException 如果 functionNames 为 null
     */
    public static void addBooleanFunctionNames(Collection<String> functionNames) {
        if (functionNames == null) {
            throw new IllegalArgumentException("functionNames must not be null");
        }
        for (String name : functionNames) {
            if (name != null && !name.isEmpty()) {
                EXTRA_BOOLEAN_FUNCTION_NAMES.add(name.toUpperCase(java.util.Locale.ROOT));
            }
        }
        synchronized (FunctionWhitelist.class) {
            freezeExtraFunctionNames();
        }
    }

    /**
     * 将运行时扩展的函数名集合冻结为不可变快照，供 {@link ConditionNode.FuncNode} 运行时检查使用。
     *
     * <p>
     * 此方法在 {@link #addSafeFunctionNames(Collection)} 和 {@link #addBooleanFunctionNames(Collection)} 末尾自动调用。
     * 也可在所有扩展函数名添加完成后手动调用以确保快照是最新的。
     *
     * <p>
     * 冻结后的 {@code contains()} 调用基于 {@link AtomicReference} 实现无锁读取，
     * 线程安全且无竞态条件。
     */
    public static synchronized void freezeExtraFunctionNames() {
        Set<String> safeSnapshot = Set.copyOf(EXTRA_SAFE_FUNCTION_NAMES);
        Set<String> boolSnapshot = Set.copyOf(EXTRA_BOOLEAN_FUNCTION_NAMES);
        FROZEN_EXTRA_SAFE_FUNCTION_NAMES.set(safeSnapshot);
        FROZEN_EXTRA_BOOLEAN_FUNCTION_NAMES.set(boolSnapshot);
    }

    /**
     * 检查函数名是否在扩展安全函数白名单中。先查冻结快照（无锁），
     * 未命中时回退到实时集合，解决启动期间冻结快照未更新导致的误判。
     *
     * @param upperName 大写函数名
     * @return 如果在扩展白名单中返回 true
     */
    public static boolean containsSafeFunction(String upperName) {
        return FROZEN_EXTRA_SAFE_FUNCTION_NAMES.get().contains(upperName)
            || EXTRA_SAFE_FUNCTION_NAMES.contains(upperName);
    }

    /**
     * 检查函数名是否在扩展布尔函数白名单中。先查冻结快照（无锁），
     * 未命中时回退到实时集合。
     *
     * @param upperName 大写函数名
     * @return 如果在扩展布尔白名单中返回 true
     */
    public static boolean containsBooleanFunction(String upperName) {
        return FROZEN_EXTRA_BOOLEAN_FUNCTION_NAMES.get().contains(upperName)
            || EXTRA_BOOLEAN_FUNCTION_NAMES.contains(upperName);
    }

    /**
     * 重置所有状态为初始值。用于测试隔离，防止跨测试的状态污染。
     *
     * <p>
     * 调用此方法后，所有扩展函数名和冻结快照都被清除。
     */
    public static void reset() {
        EXTRA_SAFE_FUNCTION_NAMES.clear();
        EXTRA_BOOLEAN_FUNCTION_NAMES.clear();
        FROZEN_EXTRA_SAFE_FUNCTION_NAMES.set(Set.of());
        FROZEN_EXTRA_BOOLEAN_FUNCTION_NAMES.set(Set.of());
    }
}
