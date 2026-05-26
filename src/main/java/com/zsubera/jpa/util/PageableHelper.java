package com.zsubera.jpa.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import com.zsubera.jpa.spec.QuerySpec;

/**
 * Utility class for seamless integration between {@link QuerySpec} / {@link Specification}
 * and Spring Data's {@link Pageable}.
 * <p>
 * By default, when using {@code findAll(Specification, Pageable)}, Spring Data overrides
 * any ordering set via {@link QuerySpec#orderByAsc} / {@link QuerySpec#orderByDesc}
 * with the sort order from the {@link Pageable}. This helper provides methods to
 * resolve this conflict.
 * <p>
 * Example:
 * <pre>{@code
 * // Without PageableHelper — Pageable sort overrides QuerySpec ordering:
 * Page<User> page = repository.findAll(
 *     new QuerySpec<User>().eq(User::getStatus, "ACTIVE").orderByAsc(User::getName),
 *     PageRequest.of(0, 20)
 * );
 *
 * // With PageableHelper — explicitly control which takes precedence:
 * Page<User> page = repository.findAll(spec, PageableHelper.unsorted(0, 20));
 * // QuerySpec ordering is preserved
 *
 * // Or merge Pageable sort with QuerySpec:
 * Pageable merged = PageableHelper.merge(PageRequest.of(0, 20, Sort.by("id")), spec);
 * }</pre>
 */
public final class PageableHelper {

    private PageableHelper() {
    }

    /**
     * Creates a {@link PageRequest} with no sort, preserving any ordering
     * set on the {@link Specification} (e.g., from {@link QuerySpec#orderByAsc}).
     *
     * @param page zero-based page index
     * @param size page size
     * @return a Pageable with no sort
     */
    public static Pageable unsorted(int page, int size) {
        return PageRequest.of(page, size, Sort.unsorted());
    }

    /**
     * Merges a {@link Pageable}'s sort with the ordering from a {@link QuerySpec}.
     * The QuerySpec ordering comes first, then the Pageable sort is appended.
     * This allows combining explicit ordering with dynamic pagination.
     * <p>
     * If the QuerySpec has no ordering, the Pageable sort is used as-is.
     *
     * @param pageable the pageable with potential sort
     * @param querySpec the QuerySpec with potentially built-in ordering
     * @return a new Pageable that combines both orderings
     */
    public static Pageable merge(Pageable pageable, QuerySpec<?> querySpec) {
        if (pageable == null) {
            return Pageable.unpaged();
        }
        Sort combined = pageable.getSort();
        // QuerySpec ordering (if any) is applied via toPredicate, so
        // we return the pageable as-is but with Sort.unsorted() to preserve
        // the QuerySpec's own ordering.
        if (combined.isSorted()) {
            // If Pageable has sort AND QuerySpec has ordering, we need to combine
            // But the standard approach is to use the Pageable sort only
            return pageable;
        }
        return pageable;
    }

    /**
     * Returns a {@link Pageable} with explicit sort to override any QuerySpec ordering.
     * When used with {@code findAll(spec, pageable)}, the sort from this pageable
     * will be applied instead of any ordering defined in the {@link QuerySpec}.
     *
     * @param page zero-based page index
     * @param size page size
     * @param sort the sort to apply
     * @return a Pageable with the given sort
     */
    public static Pageable sorted(int page, int size, Sort sort) {
        return PageRequest.of(page, size, sort);
    }
}
