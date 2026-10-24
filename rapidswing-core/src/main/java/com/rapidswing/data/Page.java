package com.rapidswing.data;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** One page of query results, with the total number of matching rows. */
public final class Page<T> {

    private final List<T> items;
    private final int offset;
    private final int limit;
    private final long total;

    public Page(List<T> items, int offset, int limit, long total) {
        this.items = Collections.unmodifiableList(Objects.requireNonNull(items, "items"));
        this.offset = offset;
        this.limit = limit;
        this.total = total;
    }

    /** Everything on one page. */
    public static <T> Page<T> of(List<T> items) {
        return new Page<>(items, 0, Query.UNLIMITED, items.size());
    }

    public List<T> getItems() {
        return items;
    }

    public int getOffset() {
        return offset;
    }

    /** Rows per page, or {@link Query#UNLIMITED}. */
    public int getLimit() {
        return limit;
    }

    /** All matching rows, on all pages. */
    public long getTotal() {
        return total;
    }

    /** The page number, from 0. */
    public int getIndex() {
        return limit > 0 ? offset / limit : 0;
    }

    public int getPageCount() {
        return limit > 0 ? (int) Math.max(1, (total + limit - 1) / limit) : 1;
    }
}
