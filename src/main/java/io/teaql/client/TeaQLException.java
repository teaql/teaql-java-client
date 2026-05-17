package io.teaql.client;

/**
 * Unchecked exception for all TeaQL client errors.
 */
public final class TeaQLException extends RuntimeException {

    public TeaQLException(String message) {
        super(message);
    }

    public TeaQLException(String message, Throwable cause) {
        super(message, cause);
    }
}
