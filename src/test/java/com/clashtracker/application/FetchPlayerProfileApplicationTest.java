package com.clashtracker.application;

import com.clashtracker.integration.supercell.JavaHttpSupercellPlayerClient;
import com.clashtracker.integration.supercell.SupercellApiException;
import com.clashtracker.integration.supercell.SupercellApiFailure;
import com.clashtracker.util.Hashing;
import com.clashtracker.util.JsonSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FetchPlayerProfileApplicationTest {
    private static final Instant RECEIVED_AT = Instant.parse("2026-10-20T12:34:56Z");
    private static final String TEST_TOKEN = "synthetic-api-token-must-never-be-stored";
    private final ObjectMapper mapper = JsonSupport.createMapper();

    @TempDir
    Path temp;

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void persistsExactRawBodyHashMetadataAndSeparateProfileWithoutOverwritingCollections() throws Exception {
        String response = """
                {"tag":"#U8J89QP2","name":"jaoo","townHallLevel":18,"expLevel":250,
                 "troops":[],"spells":[],"heroes":[],"heroEquipment":[],
                 "unmappedApiField":{"future":true},"buildings":[{"level":1}]}
                """;
        byte[] body = response.getBytes(StandardCharsets.UTF_8);
        FetchPlayerProfileApplication application = application(200, body);

        FetchPlayerProfileResult first = application.fetch("#U8J89QP2", temp);
        byte[] firstSaved = Files.readAllBytes(first.rawResponse());
        FetchPlayerProfileResult second = application.fetch("#U8J89QP2", temp);

        assertArrayEquals(body, firstSaved);
        assertEquals(Hashing.sha256(body), first.bodySha256());
        assertEquals(first.bodySha256(), second.bodySha256());
        assertNotEquals(first.collectionDirectory(), second.collectionDirectory());
        assertArrayEquals(body, Files.readAllBytes(first.rawResponse()));
        assertArrayEquals(body, Files.readAllBytes(second.rawResponse()));
        assertEquals(RECEIVED_AT, first.observation().observedAt());
        assertTrue(Files.isRegularFile(first.metadata()));
        assertTrue(Files.isRegularFile(first.artifact()));

        var metadata = mapper.readTree(first.metadata().toFile());
        assertEquals("supercell-player-capture-v1", metadata.path("schemaVersion").asText());
        assertEquals("#U8J89QP2", metadata.path("requestedPlayerTag").asText());
        assertEquals("#U8J89QP2", metadata.path("returnedPlayerTag").asText());
        assertEquals("OFFICIAL_SUPERCELL_API", metadata.path("source").asText());
        assertTrue(metadata.path("origin").asText().startsWith("http://127.0.0.1:"));
        assertEquals(200, metadata.path("httpStatus").asInt());
        assertEquals(RECEIVED_AT.toString(), metadata.path("receivedAt").asText());
        assertEquals("application/json; charset=UTF-8", metadata.path("contentType").asText());
        assertEquals("UTF-8", metadata.path("charset").asText());
        assertEquals("identity", metadata.path("contentEncoding").asText());
        assertEquals(body.length, metadata.path("bodyLengthBytes").asInt());
        assertEquals(first.bodySha256(), metadata.path("bodySha256").asText());
        assertEquals("response.bin", metadata.path("responseFile").asText());
        assertEquals("player-profile.json", metadata.path("profileFile").asText());
        assertEquals("PROFILE_PARSED", metadata.path("outcome").asText());

        String interpretedProfile = Files.readString(first.artifact());
        assertTrue(interpretedProfile.contains("townHallLevel"));
        assertFalse(interpretedProfile.contains("unmappedApiField"));
        assertFalse(interpretedProfile.contains("buildings"));
        assertCollectionDoesNotContainSecret(first.collectionDirectory());
        assertCollectionDoesNotContainSecret(second.collectionDirectory());
    }

    @Test
    void persistsInvalidHttpResponseWithoutCreatingAnInterpretedProfile() throws Exception {
        byte[] body = "credential-independent 401 diagnostic".getBytes(StandardCharsets.UTF_8);
        FetchPlayerProfileApplication application = application(401, body);

        SupercellApiException failure = assertThrows(SupercellApiException.class,
                () -> application.fetch("#U8J89QP2", temp));

        assertEquals(SupercellApiFailure.UNAUTHORIZED, failure.failure());
        assertEquals(401, failure.statusCode());
        assertTrue(Files.isDirectory(failure.evidenceDirectory()));
        assertArrayEquals(body, Files.readAllBytes(failure.evidenceDirectory().resolve("response.bin")));
        assertFalse(Files.exists(failure.evidenceDirectory().resolve("player-profile.json")));
        var metadata = mapper.readTree(failure.evidenceDirectory().resolve("metadata.json").toFile());
        assertEquals("RESPONSE_REJECTED", metadata.path("outcome").asText());
        assertEquals("UNAUTHORIZED", metadata.path("failureCode").asText());
        assertTrue(metadata.path("profileFile").isNull());
        assertCollectionDoesNotContainSecret(failure.evidenceDirectory());
    }

    @Test
    void persistsMalformedJsonAndDoesNotPromoteItToAProfile() throws Exception {
        byte[] body = "{broken-json".getBytes(StandardCharsets.UTF_8);
        FetchPlayerProfileApplication application = application(200, body);

        SupercellApiException failure = assertThrows(SupercellApiException.class,
                () -> application.fetch("#U8J89QP2", temp));

        assertEquals(SupercellApiFailure.INVALID_RESPONSE, failure.failure());
        assertArrayEquals(body, Files.readAllBytes(failure.evidenceDirectory().resolve("response.bin")));
        assertFalse(Files.exists(failure.evidenceDirectory().resolve("player-profile.json")));
        var metadata = mapper.readTree(failure.evidenceDirectory().resolve("metadata.json").toFile());
        assertEquals("INVALID_RESPONSE", metadata.path("failureCode").asText());
        assertTrue(metadata.path("profileFile").isNull());
    }

    @Test
    void persistsTagMismatchAsRejectedEvidenceWithoutAssociatingTheProfile() throws Exception {
        byte[] body = "{\"tag\":\"#OTHER\",\"name\":\"Different player\"}"
                .getBytes(StandardCharsets.UTF_8);
        FetchPlayerProfileApplication application = application(200, body);

        SupercellApiException failure = assertThrows(SupercellApiException.class,
                () -> application.fetch("#U8J89QP2", temp));

        assertEquals(SupercellApiFailure.PLAYER_TAG_MISMATCH, failure.failure());
        assertArrayEquals(body, Files.readAllBytes(failure.evidenceDirectory().resolve("response.bin")));
        assertFalse(Files.exists(failure.evidenceDirectory().resolve("player-profile.json")));
        var metadata = mapper.readTree(failure.evidenceDirectory().resolve("metadata.json").toFile());
        assertEquals("#U8J89QP2", metadata.path("requestedPlayerTag").asText());
        assertEquals("#OTHER", metadata.path("returnedPlayerTag").asText());
        assertEquals("PLAYER_TAG_MISMATCH", metadata.path("failureCode").asText());
        assertTrue(metadata.path("profileFile").isNull());
        assertCollectionDoesNotContainSecret(failure.evidenceDirectory());
    }

    private FetchPlayerProfileApplication application(int status, byte[] body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> respond(exchange, status, body));
        server.start();
        URI baseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        JavaHttpSupercellPlayerClient client = new JavaHttpSupercellPlayerClient(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build(), mapper,
                baseUri, TEST_TOKEN, Duration.ofSeconds(2), Clock.fixed(RECEIVED_AT, ZoneOffset.UTC));
        return new FetchPlayerProfileApplication(client, mapper,
                Clock.fixed(RECEIVED_AT.plusSeconds(2), ZoneOffset.UTC));
    }

    private static void respond(HttpExchange exchange, int status, byte[] body) throws IOException {
        try {
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.getResponseHeaders().set("Content-Encoding", "identity");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
        } finally {
            exchange.close();
        }
    }

    private void assertCollectionDoesNotContainSecret(Path directory) throws IOException {
        List<Path> files = new ArrayList<>();
        try (var paths = Files.list(directory)) {
            paths.forEach(files::add);
        }
        for (Path path : files) {
            String contents = new String(Files.readAllBytes(path), StandardCharsets.ISO_8859_1);
            assertFalse(contents.contains(TEST_TOKEN), "Credential leaked into " + path.getFileName());
        }
    }
}
