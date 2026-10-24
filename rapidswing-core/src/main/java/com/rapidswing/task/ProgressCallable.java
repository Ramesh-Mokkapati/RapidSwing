package com.rapidswing.task;

/** Background work that reports {@link Progress}; see {@link TaskService#callWithProgress}. */
@FunctionalInterface
public interface ProgressCallable<T> {

    T call(Progress progress) throws Exception;
}
