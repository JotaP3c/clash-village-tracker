package com.clashtracker.application;

import java.time.Instant;
import java.util.UUID;

/** Allowlisted metadata for one immutable API response capture. */
public record SupercellCaptureMetadata(
        String schemaVersion,
        UUID collectionId,
        String requestedPlayerTag,
        String returnedPlayerTag,
        String source,
        String origin,
        int httpStatus,
        Instant receivedAt,
        Instant persistedAt,
        String contentType,
        String charset,
        String contentEncoding,
        long bodyLengthBytes,
        String bodySha256,
        String responseFile,
        String profileFile,
        String outcome,
        String failureCode,
        String failureMessage) {
}
