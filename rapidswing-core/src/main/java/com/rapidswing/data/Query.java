package com.rapidswing.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * What to load: filters (all must match), sort order and the page. Immutable;
 * the {@code with...} methods return changed copies.
 * <pre>
 * Query query = Query.all()
 *         .where(Filter.of("city", Operator.CONTAINS, "ber"))
 *         .sortBy("name", true)
 *         .page(0, 50);
 * </pre>
 * {@link #apply} evaluates a query in memory; repositories backed by a server or database translate it.
 */
public final class Query {

    /** A sort key: a field and its direction. */
    public static final class Sort {
        private final String field;
        private final boolean ascending;

        public Sort(String field, boolean ascending) {
            this.field = Objects.requireNonNull(field, "field");
            this.ascending = ascending;
        }

        public String getField() {
            return field;
        }

        public boolean isAscending() {
            return ascending;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Sort && field.equals(((Sort) other).field) && ascending == ((Sort) other).ascending;
        }

        @Override
        public int hashCode() {
            return Objects.hash(field, ascending);
        }

        @Override
        public String toString() {
            return field + (ascending ? " ASC" : " DESC");
        }
    }

    /** No limit on the number of rows. */
    public static final int UNLIMITED = -1;

    private static final Query ALL = new Query(Collections.emptyList(), Collections.emptyList(), 0, UNLIMITED);

    private final List<Filter> filters;
    private final List<Sort> sorts;
    private final int offset;
    private final int limit;

    private Query(List<Filter> filters, List<Sort> sorts, int offset, int limit) {
        this.filters = Collections.unmodifiableList(new ArrayList<>(filters));
        this.sorts = Collections.unmodifiableList(new ArrayList<>(sorts));
        this.offset = offset;
        this.limit = limit;
    }

    /** No filters, no sorting, everything. */
    public static Query all() {
        return ALL;
    }

    public Query where(Filter... more) {
        List<Filter> combined = new ArrayList<>(filters);
        Collections.addAll(combined, more);
        return new Query(combined, sorts, 0, limit);
    }

    /** The same query with other filters; starts again at the first page. */
    public Query withFilters(List<Filter> newFilters) {
        return new Query(newFilters, sorts, 0, limit);
    }

    /** Adds a sort key after the existing ones. */
    public Query sortBy(String field, boolean ascending) {
        List<Sort> combined = new ArrayList<>(sorts);
        combined.removeIf(sort -> sort.getField().equals(field));
        combined.add(new Sort(field, ascending));
        return new Query(filters, combined, offset, limit);
    }

    public Query withSorts(List<Sort> newSorts) {
        return new Query(filters, newSorts, offset, limit);
    }

    /** Page {@code index} (from 0) of {@code size} rows. */
    public Query page(int index, int size) {
        if (index < 0 || size <= 0) {
            throw new IllegalArgumentException("page " + index + " of size " + size);
        }
        return new Query(filters, sorts, index * size, size);
    }

    /** All rows, without paging. */
    public Query unpaged() {
        return new Query(filters, sorts, 0, UNLIMITED);
    }

    public List<Filter> getFilters() {
        return filters;
    }

    public List<Sort> getSorts() {
        return sorts;
    }

    public int getOffset() {
        return offset;
    }

    /** Rows per page, or {@link #UNLIMITED}. */
    public int getLimit() {
        return limit;
    }

    public boolean isPaged() {
        return limit != UNLIMITED;
    }

    /**
     * Filters, sorts and pages rows in memory. {@code fieldValue} reads a field of a row by name, e.g.
     * {@code BeanProperties::get}.
     */
    public <T> Page<T> apply(List<T> rows, BiFunction<? super T, String, ?> fieldValue) {
        List<T> matching = rows.stream()
                .filter(row -> filters.stream().allMatch(filter -> filter.matches(fieldValue.apply(row, filter.getField()))))
                .collect(Collectors.toList());
        if (!sorts.isEmpty()) {
            matching.sort((a, b) -> {
                for (Sort sort : sorts) {
                    int result = Operator.compare(fieldValue.apply(a, sort.getField()), fieldValue.apply(b, sort.getField()));
                    if (result != 0) {
                        return sort.isAscending() ? result : -result;
                    }
                }
                return 0;
            });
        }
        int total = matching.size();
        int from = Math.min(offset, total);
        int to = isPaged() ? Math.min(total, from + limit) : total;
        return new Page<>(new ArrayList<>(matching.subList(from, to)), offset, limit, total);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Query)) {
            return false;
        }
        Query that = (Query) other;
        return filters.equals(that.filters) && sorts.equals(that.sorts) && offset == that.offset && limit == that.limit;
    }

    @Override
    public int hashCode() {
        return Objects.hash(filters, sorts, offset, limit);
    }

    @Override
    public String toString() {
        return "Query" + filters + (sorts.isEmpty() ? "" : " ORDER BY " + sorts)
                + (isPaged() ? " OFFSET " + offset + " LIMIT " + limit : "");
    }
}
