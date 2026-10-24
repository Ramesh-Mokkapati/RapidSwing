package com.rapidswing.forms;

/**
 * A validation rule for one value. Write custom rules as lambdas:
 * <pre>
 * Validator&lt;Integer&gt; even = n -&gt; n % 2 == 0 ? null : "Must be an even number";
 * </pre>
 * By convention only {@link Validators#required()} rejects empty values: other rules accept
 * {@code null} (and empty text), so optional fields are only checked when filled in.
 */
@FunctionalInterface
public interface Validator<T> {

    /** Returns the error message to show, or {@code null} when the value is valid. */
    String validate(T value);

    /** Runs this rule, then the other one if this one passed. */
    default Validator<T> and(Validator<? super T> other) {
        return value -> {
            String error = validate(value);
            return error != null ? error : other.validate(value);
        };
    }
}
