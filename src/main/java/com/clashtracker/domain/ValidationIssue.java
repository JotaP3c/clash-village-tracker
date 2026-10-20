package com.clashtracker.domain;

import java.util.Objects;

public record ValidationIssue(Severity severity, String code, String path, String message) {
    public ValidationIssue {
        Objects.requireNonNull(severity, "severity");
        code = requireText(code, "code");
        path = requireText(path, "path");
        message = requireText(message, "message");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
