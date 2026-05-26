package com.zsubera.jpa.util;

import com.zsubera.jpa.spec.QuerySpec;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

/**
 * Utility class for seamless integration between {@link QuerySpec} / {@link Specification} and
 * Spring Data's {@link Pageable}.
 *
 * <p>By default, when using {@code findAll(Specification, Pageable)}, Spring Data overrides any
 * ordering set via {@link QuerySpec#orderByAsc} / {@link QuerySpec#orderByDesc} with the sort order
 * from the {@link Pageable}. This helper provides methods to resolve this conflict.
 *
 * <p>Example:
 *
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

  private PageableHelper() {}

  /**
   * Creates a {@link PageRequest} with no sort, preserving any ordering set on the {@link
   * Specification} (e.g., from {@link QuerySpec#orderByAsc}).
   *
   * @param page zero-based page index
   * @param size page size
   * @return a Pageable with no sort
   */
  public static Pageable unsorted(int page, int size) {
    return PageRequest.of(page, size, Sort.unsorted());
  }

  /**
   * Merges a {@link Pageable}'s sort with the ordering from a {@link QuerySpec}. QuerySpec ordering
   * comes first (higher priority), then the Pageable sort is appended. This allows combining
   * QuerySpec's built-in ordering with dynamic pagination sort.
   *
   * <p>If the QuerySpec has no ordering, the Pageable sort is used as-is.
   *
   * @param pageable the pageable with potential sort
   * @param querySpec the QuerySpec with potentially built-in ordering
   * @return a new Pageable that combines both orderings (QuerySpec first, Pageable second)
   */
  public static Pageable merge(Pageable pageable, QuerySpec<?> querySpec) {
    if (pageable == null) {
      return Pageable.unpaged();
    }
    Sort querySpecSort = querySpec.getSort();
    Sort pageableSort = pageable.getSort();
    Sort combined;
    if (querySpecSort.isSorted()) {
      // QuerySpec ordering takes precedence, then append Pageable sort
      if (pageableSort.isSorted()) {
        combined = querySpecSort.and(pageableSort);
      } else {
        combined = querySpecSort;
      }
    } else {
      combined = pageableSort;
    }
    return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), combined);
  }

  /**
   * Returns a {@link Pageable} with explicit sort to override any QuerySpec ordering. When used
   * with {@code findAll(spec, pageable)}, the sort from this pageable will be applied instead of
   * any ordering defined in the {@link QuerySpec}.
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
