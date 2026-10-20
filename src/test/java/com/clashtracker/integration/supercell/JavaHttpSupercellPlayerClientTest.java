package com.clashtracker.integration.supercell;

import com.clashtracker.util.JsonSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaHttpSupercellPlayerClientTest {
    private final ObjectMapper mapper = JsonSupport.createMapper();
    private HttpServer server;
    private final AtomicReference<String> requestedPath = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void encodesRawTagSendsBearerAndParsesOnlyDocumentedTopLevelFields() throws Exception {
        JavaHttpSupercellPlayerClient client = client(200, """
                {
                  "tag":"#U8J89QP2","name":"jaoo","townHallLevel":18,"expLevel":250,
                  "troops":[{"unverifiedNestedField":1}],"spells":[],"heroes":[],"heroEquipment":[],
                  "buildings":[{"mustNotBecomePartOfTheTypedContract":true}]
                }
                """, Map.of(), Duration.ofSeconds(2), 0);

        SupercellPlayerProfile profile = client.fetchPlayer("#U8J89QP2");

        assertEquals("/v1/players/%23U8J89QP2", requestedPath.get());
        assertEquals("Bearer test-token", authorization.get());
        assertEquals("#U8J89QP2", profile.tag());
        assertEquals("jaoo", profile.name());
        assertEquals(18, profile.townHallLevel());
        assertEquals(250, profile.expLevel());
        assertTrue(profile.troops().isArray());
        assertTrue(profile.spells().isEmpty());
        assertFalse(mapper.writeValueAsString(profile).contains("buildings"));
    }

    @Test
    void preservesDifferenceBetweenMissingAndEmptyDocumentedArrays() throws Exception {
        JavaHttpSupercellPlayerClient client = client(200,
                "{\"tag\":\"#TEST\",\"name\":\"Fixture\",\"spells\":[]}",
                Map.of(), Duration.ofSeconds(2), 0);

        SupercellPlayerProfile profile = client.fetchPlayer("#TEST");

        assertNull(profile.troops());
        assertNotNull(profile.spells());
        assertTrue(profile.spells().isEmpty());
        assertNull(profile.heroes());
        assertNull(profile.heroEquipment());
    }

    @Test
    void classifiesAuthenticationNotFoundRateLimitAndServerFailures() throws Exception {
        Map<Integer, SupercellApiFailure> expected = new LinkedHashMap<>();
        expected.put(401, SupercellApiFailure.UNAUTHORIZED);
        expected.put(403, SupercellApiFailure.FORBIDDEN);
        expected.put(404, SupercellApiFailure.PLAYER_NOT_FOUND);
        expected.put(429, SupercellApiFailure.RATE_LIMITED);
        expected.put(500, SupercellApiFailure.SERVER_ERROR);
        expected.put(503, SupercellApiFailure.SERVER_ERROR);

        for (Map.Entry<Integer, SupercellApiFailure> entry : expected.entrySet()) {
            JavaHttpSupercellPlayerClient client = client(entry.getKey(), "{}",
                    entry.getKey() == 429 ? Map.of("Retry-After", "17") : Map.of(),
                    Duration.ofSeconds(2), 0);
            SupercellApiException exception = assertThrows(SupercellApiException.class,
                    () -> client.fetchPlayer("#TEST"));
            assertEquals(entry.getValue(), exception.failure());
            assertEquals(entry.getKey(), exception.statusCode());
            if (entry.getKey() == 429) {
                assertEquals(17L, exception.retryAfterSeconds());
            }
            stopServer();
            server = null;
        }
    }

    @Test
    void rejectsMalformedOrStructurallyInvalidResponses() throws Exception {
        JavaHttpSupercellPlayerClient malformed = client(200, "{bad", Map.of(),
                Duration.ofSeconds(2), 0);
        assertEquals(SupercellApiFailure.INVALID_RESPONSE,
                assertThrows(SupercellApiException.class,
                        () -> malformed.fetchPlayer("#TEST")).failure());

        stopServer();
        server = null;
        JavaHttpSupercellPlayerClient wrongArrayType = client(200,
                "{\"tag\":\"#TEST\",\"name\":\"Fixture\",\"troops\":{}}",
                Map.of(), Duration.ofSeconds(2), 0);
        assertEquals(SupercellApiFailure.INVALID_RESPONSE,
                assertThrows(SupercellApiException.class,
                        () -> wrongArrayType.fetchPlayer("#TEST")).failure());
    }

    @Test
    void enforcesConfigurationTagValidationAndRequestTimeoutWithoutExternalNetwork() throws Exception {
        JavaHttpSupercellPlayerClient noToken = client(200,
                "{\"tag\":\"#TEST\",\"name\":\"Fixture\"}", Map.of(),
                Duration.ofSeconds(2), 0, " ");
        assertEquals(SupercellApiFailure.CONFIGURATION,
                assertThrows(SupercellApiException.class,
                        () -> noToken.fetchPlayer("#TEST")).failure());

        JavaHttpSupercellPlayerClient validToken = new JavaHttpSupercellPlayerClient(
                HttpClient.newHttpClient(), mapper, URI.create("http://127.0.0.1:1"),
                "test-token", Duration.ofSeconds(1));
        assertEquals(SupercellApiFailure.INVALID_PLAYER_TAG,
                assertThrows(SupercellApiException.class,
                        () -> validToken.fetchPlayer("U8J89QP2")).failure());

        stopServer();
        server = null;
        JavaHttpSupercellPlayerClient slow = client(200,
                "{\"tag\":\"#TEST\",\"name\":\"Fixture\"}", Map.of(),
                Duration.ofMillis(40), 250);
        assertEquals(SupercellApiFailure.TIMEOUT,
                assertThrows(SupercellApiException.class,
                        () -> slow.fetchPlayer("#TEST")).failure());
    }

    private JavaHttpSupercellPlayerClient client(int status, String body, Map<String, String> headers,
                                                 Duration requestTimeout, long delayMillis)
            throws IOException {
        return client(status, body, headers, requestTimeout, delayMillis, "test-token");
    }

    private JavaHttpSupercellPlayerClient client(int status, String body, Map<String, String> headers,
                                                 Duration requestTimeout, long delayMillis, String token)
            throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> respond(exchange, status, body, headers, delayMillis));
        server.start();
        URI baseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
        return new JavaHttpSupercellPlayerClient(httpClient, mapper, baseUri, token, requestTimeout);
    }

    private void respond(HttpExchange exchange, int status, String body,
                         Map<String, String> headers, long delayMillis) throws IOException {
        requestedPath.set(exchange.getRequestURI().getRawPath());
        authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
        headers.forEach((name, value) -> exchange.getResponseHeaders().set(name, value));
        try {
            if (delayMillis > 0) {
                Thread.sleep(delayMillis);
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } finally {
            exchange.close();
        }
    }
}
