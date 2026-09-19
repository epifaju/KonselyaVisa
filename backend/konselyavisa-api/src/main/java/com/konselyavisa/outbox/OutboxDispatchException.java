package com.konselyavisa.outbox;

public class OutboxDispatchException extends RuntimeException {

    private final boolean retryable;

    public OutboxDispatchException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public OutboxDispatchException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
