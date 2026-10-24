package com.rapidswing.crud;

import com.rapidswing.data.Page;
import com.rapidswing.data.Query;

import java.util.List;

/**
 * A repository that searches, sorts and pages where the data is: for tables too large to load at once. A
 * {@link CrudScreen} with a {@link CrudScreen.Builder#search search panel} or {@link CrudScreen.Builder#pageSize
 * paging} calls {@link #find} instead of {@link #findAll}.
 * <p>
 * Translate the query's filters and sort keys into SQL, JPA criteria or REST parameters. Called on a
 * background thread.
 */
public interface QueryRepository<T> extends CrudRepository<T> {

    Page<T> find(Query query) throws Exception;

    @Override
    default List<T> findAll() throws Exception {
        return find(Query.all()).getItems();
    }
}
