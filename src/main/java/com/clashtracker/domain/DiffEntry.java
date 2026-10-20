package com.clashtracker.domain;

public record DiffEntry(
        String entityKey,
        String field,
        Object previousValue,
        Object currentValue,
        String classification,
        String message) {
}
