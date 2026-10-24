package com.rapidswing.crud;

import com.rapidswing.data.BeanProperties;
import com.rapidswing.data.Page;
import com.rapidswing.data.Query;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * A thread-safe {@link QueryRepository} over a list, for prototypes, demos and tests. Items are identified by
 * a key (e.g. their id); queries read bean properties by name, or fields through your own accessor.
 * <pre>
 * InMemoryRepository&lt;Customer&gt; repository = new InMemoryRepository&lt;&gt;(Customer::getId, Customer::copy);
 * </pre>
 * Stored items are copies, so editing an item in a form does not change the stored one until it is saved.
 */
public class InMemoryRepository<T> implements QueryRepository<T> {

    private final Function<? super T, ?> key;
    private final Function<? super T, ? extends T> copy;
    private final List<T> items = new ArrayList<>();
    private BiFunction<? super T, String, ?> fieldValue = BeanProperties::get;

    public InMemoryRepository(Function<? super T, ?> key, Function<? super T, ? extends T> copy) {
        this.key = Objects.requireNonNull(key, "key");
        this.copy = Objects.requireNonNull(copy, "copy");
    }

    /** Reads fields for queries some other way than bean properties. */
    public InMemoryRepository<T> fieldValues(BiFunction<? super T, String, ?> accessor) {
        this.fieldValue = Objects.requireNonNull(accessor, "accessor");
        return this;
    }

    public synchronized InMemoryRepository<T> addAll(Collection<? extends T> initial) {
        for (T item : initial) {
            items.add(copy.apply(item));
        }
        return this;
    }

    @Override
    public synchronized Page<T> find(Query query) {
        Page<T> page = query.apply(items, fieldValue);
        List<T> copies = new ArrayList<>(page.getItems().size());
        for (T item : page.getItems()) {
            copies.add(copy.apply(item));
        }
        return new Page<>(copies, page.getOffset(), page.getLimit(), page.getTotal());
    }

    @Override
    public synchronized T save(T item) {
        T stored = copy.apply(item);
        Object id = key.apply(item);
        for (int i = 0; i < items.size(); i++) {
            if (Objects.equals(key.apply(items.get(i)), id)) {
                items.set(i, stored);
                return copy.apply(stored);
            }
        }
        items.add(stored);
        return copy.apply(stored);
    }

    @Override
    public synchronized void delete(T item) {
        Object id = key.apply(item);
        items.removeIf(existing -> Objects.equals(key.apply(existing), id));
    }

    public synchronized int size() {
        return items.size();
    }
}
