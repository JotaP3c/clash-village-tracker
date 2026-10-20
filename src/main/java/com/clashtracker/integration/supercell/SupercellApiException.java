package com.clashtracker.integration.supercell;

public final class SupercellApiException extends Exception {
    private final SupercellApiFailure failure;
    private final Integer statusCode;
    private final Long retryAfterSeconds;

    public SupercellApiException(SupercellApiFailure failure, String message) {
        this(failure, message, null, null, null);
    }

    public SupercellApiException(SupercellApiFailure failure, String message, Throwable cause) {
        this(failure, message, null, null, cause);
    }

    public SupercellApiException(SupercellApiFailure failure, String message,
                                 Integer statusCode, Long retryAfterSeconds) {
        this(failure, message, statusCode, retryAfterSeconds, null);
    }

    public SupercellApiException(SupercellApiFailure failure, String message,
                                 Integer statusCode, Long retryAfterSeconds, Throwable cause) {
        super(message, cause);
        this.failure = failure;
        this.statusCode = statusCode;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public SupercellApiFailure failure() {
        return failure;
    }

    public Integer statusCode() {
        return statusCode;
    }

    public Long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
