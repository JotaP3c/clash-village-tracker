package com.clashtracker.integration.supercell;

import java.net.URI;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

import com.clashtracker.util.Hashing;

/** Complete response evidence captured before any decoding or profile mapping. */
public final class SupercellPlayerCapture {
    private final String requestedPlayerTag;
    private final String returnedPlayerTag;
    private final URI endpoint;
    private final Instant receivedAt;
    private final int httpStatus;
    private final String contentType;
    private final String charset;
    private final String contentEncoding;
    private final byte[] body;
    private final String bodySha256;
    private final SupercellPlayerProfile profile;
    private final SupercellApiException failure;

    public SupercellPlayerCapture(String requestedPlayerTag, String returnedPlayerTag, URI endpoint,
                                  Instant receivedAt, int httpStatus, String contentType,
                                  String charset, String contentEncoding, byte[] body,
                                  SupercellPlayerProfile profile, SupercellApiException failure) {
        this.requestedPlayerTag = Objects.requireNonNull(requestedPlayerTag, "requestedPlayerTag");
        this.returnedPlayerTag = returnedPlayerTag;
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
        this.receivedAt = Objects.requireNonNull(receivedAt, "receivedAt");
        this.httpStatus = httpStatus;
        this.contentType = contentType;
        this.charset = charset;
        this.contentEncoding = contentEncoding;
        this.body = Arrays.copyOf(Objects.requireNonNull(body, "body"), body.length);
        this.bodySha256 = Hashing.sha256(this.body);
        this.profile = profile;
        this.failure = failure;
        if ((profile == null) == (failure == null)) {
            throw new IllegalArgumentException("Capture must contain either a profile or a failure");
        }
    }

    public String requestedPlayerTag() { return requestedPlayerTag; }
    public String returnedPlayerTag() { return returnedPlayerTag; }
    public URI endpoint() { return endpoint; }
    public Instant receivedAt() { return receivedAt; }
    public int httpStatus() { return httpStatus; }
    public String contentType() { return contentType; }
    public String charset() { return charset; }
    public String contentEncoding() { return contentEncoding; }
    public byte[] body() { return Arrays.copyOf(body, body.length); }
    public String bodySha256() { return bodySha256; }
    public SupercellPlayerProfile profile() { return profile; }
    public SupercellApiException failure() { return failure; }

    public SupercellPlayerProfile requireProfile() throws SupercellApiException {
        if (failure != null) {
            throw failure;
        }
        return profile;
    }
}
