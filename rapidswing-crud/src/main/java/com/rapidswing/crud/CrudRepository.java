package com.rapidswing.crud;

import java.util.List;

/**
 * Data access for a {@link CrudScreen}: JDBC, JPA, a REST client or an in-memory list.
 * Methods are called on a background thread, never on the EDT, so they may block.
 */
public interface CrudRepository<T> {

    List<T> findAll() throws Exception;

    /**
     * Inserts a new item or updates an existing one, and returns the stored version
     * (for example with a generated id). Returning {@code null} means "use the item as passed".
     */
    T save(T item) throws Exception;

    void delete(T item) throws Exception;
}
