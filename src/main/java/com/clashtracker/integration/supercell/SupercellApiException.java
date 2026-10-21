package com.clashtracker.integration.supercell;

import java.nio.file.Path;

public final class SupercellApiException extends Exception {
    private final SupercellApiFailure failure;
    private final Integer statusCode;
    private final Long retryAfterSeconds;
    private final Path evidenceDirectory;

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
        this(failure, message, statusCode, retryAfterSeconds, cause, null);
    }

    private SupercellApiException(SupercellApiFailure failure, String message,
                                  Integer statusCode, Long retryAfterSeconds, Throwable cause,
                                  Path evidenceDirectory) {
        super(message, cause);
        this.failure = failure;
        this.statusCode = statusCode;
        this.retryAfterSeconds = retryAfterSeconds;
        this.evidenceDirectory = evidenceDirectory;
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

    public Path evidenceDirectory() {
        return evidenceDirectory;
    }

    public SupercellApiException withEvidenceDirectory(Path directory) {
        return new SupercellApiException(failure, getMessage(), statusCode, retryAfterSeconds,
                getCause(), directory);
    }
}
