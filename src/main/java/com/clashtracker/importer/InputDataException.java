package com.clashtracker.importer;

public final class InputDataException extends Exception {
    public InputDataException(String message) {
        super(message);
    }

    public InputDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
