package com.clashtracker.integration.supercell;

import com.clashtracker.util.Hashing;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JavaHttpSupercellPlayerClient implements SupercellPlayerClient {
    public static final URI DEFAULT_BASE_URI = URI.create("https://api.clashofclans.com");
    public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    public static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(20);
    private static final Pattern CHARSET_PARAMETER = Pattern.compile(
            "(?i)(?:^|;)\\s*charset\\s*=\\s*(?:\"([^\"]+)\"|([^;\\s]+))");

    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final URI baseUri;
    private final String token;
    private final Duration requestTimeout;
    private final Clock clock;

    public JavaHttpSupercellPlayerClient(HttpClient httpClient, ObjectMapper mapper, URI baseUri,
                                         String token, Duration requestTimeout) {
        this(httpClient, mapper, baseUri, token, requestTimeout, Clock.systemUTC());
    }

    public JavaHttpSupercellPlayerClient(HttpClient httpClient, ObjectMapper mapper, URI baseUri,
                                         String token, Duration requestTimeout, Clock clock) {
        this.httpClient = java.util.Objects.requireNonNull(httpClient, "httpClient");
        this.mapper = java.util.Objects.requireNonNull(mapper, "mapper");
        this.baseUri = requireSafeBaseUri(baseUri);
        this.token = token;
        this.requestTimeout = requirePositive(requestTimeout, "requestTimeout");
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
    }

    public static JavaHttpSupercellPlayerClient createDefault(ObjectMapper mapper, String token) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(DEFAULT_CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        return new JavaHttpSupercellPlayerClient(
                client, mapper, DEFAULT_BASE_URI, token, DEFAULT_REQUEST_TIMEOUT);
    }

    @Override
    public SupercellPlayerCapture capturePlayer(String playerTag) throws SupercellApiException {
        if (token == null || token.isBlank()) {
            throw new SupercellApiException(SupercellApiFailure.CONFIGURATION,
                    "A variável de ambiente CLASH_API_TOKEN não está configurada");
        }
        String normalizedTag = normalizePlayerTag(playerTag);
        URI endpoint = playerUri(normalizedTag);
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(requestTimeout)
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();

        final HttpResponse<byte[]> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (HttpTimeoutException exception) {
            throw new SupercellApiException(SupercellApiFailure.TIMEOUT,
                    "A API da Supercell excedeu o tempo limite da requisição");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new SupercellApiException(SupercellApiFailure.INTERRUPTED,
                    "A chamada à API da Supercell foi interrompida");
        } catch (IOException exception) {
            throw new SupercellApiException(SupercellApiFailure.TRANSPORT_ERROR,
                    "Não foi possível comunicar com a API da Supercell");
        }

        byte[] body = response.body() == null ? new byte[0] : response.body();
        // Hash the untouched response bytes before charset handling or DTO mapping.
        String responseHash = Hashing.sha256(body);
        var headers = response.headers();
        String contentType = headers.firstValue("Content-Type").orElse(null);
        String contentEncoding = headers.firstValue("Content-Encoding").orElse(null);
        String charsetName = charsetName(contentType);
        java.time.Instant receivedAt = clock.instant();
        int status = response.statusCode();

        if (status != 200) {
            SupercellApiException failure = httpFailure(status, headers.firstValue("Retry-After").orElse(null));
            return capture(normalizedTag, null, endpoint, receivedAt, status, contentType,
                    charsetName, contentEncoding, body, null, failure, responseHash);
        }

        if (contentEncoding != null && !contentEncoding.isBlank()
                && !"identity".equalsIgnoreCase(contentEncoding.trim())) {
            SupercellApiException failure = invalidResponse(
                    "A resposta usa uma codificação de conteúdo não suportada para interpretação");
            return capture(normalizedTag, null, endpoint, receivedAt, status, contentType,
                    charsetName, contentEncoding, body, null, failure, responseHash);
        }

        final String decoded;
        try {
            Charset charset = Charset.forName(charsetName);
            decoded = charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(body)).toString();
        } catch (IllegalArgumentException | CharacterCodingException exception) {
            SupercellApiException failure = invalidResponse("A resposta não possui codificação de texto válida");
            return capture(normalizedTag, null, endpoint, receivedAt, status, contentType,
                    charsetName, contentEncoding, body, null, failure, responseHash);
        }

        String returnedTag = null;
        try {
            JsonNode root = mapper.readTree(decoded);
            if (root == null || !root.isObject()) {
                throw invalidResponse("A resposta de jogador não é um objeto JSON");
            }
            returnedTag = text(root, "tag", true);
            if (!sameTag(normalizedTag, returnedTag)) {
                SupercellApiException failure = new SupercellApiException(
                        SupercellApiFailure.PLAYER_TAG_MISMATCH,
                        "A API retornou uma tag diferente da solicitada", status, null);
                return capture(normalizedTag, returnedTag, endpoint, receivedAt, status, contentType,
                        charsetName, contentEncoding, body, null, failure, responseHash);
            }
            SupercellPlayerProfile profile = parsePlayer(root, returnedTag);
            return capture(normalizedTag, returnedTag, endpoint, receivedAt, status, contentType,
                    charsetName, contentEncoding, body, profile, null, responseHash);
        } catch (JsonProcessingException exception) {
            SupercellApiException failure = invalidResponse("A API retornou JSON inválido");
            return capture(normalizedTag, returnedTag, endpoint, receivedAt, status, contentType,
                    charsetName, contentEncoding, body, null, failure, responseHash);
        } catch (SupercellApiException exception) {
            return capture(normalizedTag, returnedTag, endpoint, receivedAt, status, contentType,
                    charsetName, contentEncoding, body, null, exception, responseHash);
        }
    }

    URI playerUri(String normalizedTag) {
        String encodedTag = URLEncoder.encode(normalizedTag, StandardCharsets.UTF_8)
                .replace("+", "%20");
        String root = baseUri.toString().replaceAll("/+$", "");
        return URI.create(root + "/v1/players/" + encodedTag);
    }

    private SupercellPlayerCapture capture(String requestedTag, String returnedTag, URI endpoint,
                                           java.time.Instant receivedAt, int status, String contentType,
                                           String charset, String contentEncoding, byte[] body,
                                           SupercellPlayerProfile profile, SupercellApiException failure,
                                           String expectedHash) {
        SupercellPlayerCapture capture = new SupercellPlayerCapture(requestedTag, returnedTag, endpoint,
                receivedAt, status, contentType, charset, contentEncoding, body, profile, failure);
        if (!capture.bodySha256().equals(expectedHash)) {
            throw new IllegalStateException("Response bytes changed during capture");
        }
        return capture;
    }

    private SupercellPlayerProfile parsePlayer(JsonNode root, String tag) throws SupercellApiException {
        String name = text(root, "name", true);
        Integer townHallLevel = integer(root, "townHallLevel");
        Integer expLevel = integer(root, "expLevel");
        JsonNode troops = documentedArray(root, "troops");
        JsonNode spells = documentedArray(root, "spells");
        JsonNode heroes = documentedArray(root, "heroes");
        JsonNode heroEquipment = documentedArray(root, "heroEquipment");
        return new SupercellPlayerProfile(tag, name, townHallLevel, expLevel,
                troops, spells, heroes, heroEquipment);
    }

    private static String normalizePlayerTag(String value) throws SupercellApiException {
        if (value == null || value.isBlank()) {
            throw new SupercellApiException(SupercellApiFailure.INVALID_PLAYER_TAG,
                    "A tag do jogador é obrigatória");
        }
        String normalized = value.trim();
        if (!normalized.startsWith("#") || normalized.length() == 1
                || normalized.indexOf('/') >= 0 || normalized.chars().anyMatch(Character::isWhitespace)) {
            throw new SupercellApiException(SupercellApiFailure.INVALID_PLAYER_TAG,
                    "Informe a tag no formato bruto iniciado por #, por exemplo #U8J89QP2");
        }
        return normalized;
    }

    private static boolean sameTag(String requested, String returned) {
        return returned != null && requested.replace("#", "").equalsIgnoreCase(returned.replace("#", ""));
    }

    private static String text(JsonNode root, String field, boolean required)
            throws SupercellApiException {
        JsonNode value = root.get(field);
        if (value == null || value.isNull()) {
            if (required) {
                throw invalidResponse("Campo obrigatório ausente na resposta: " + field);
            }
            return null;
        }
        if (!value.isTextual()) {
            throw invalidResponse("Campo com tipo inválido na resposta: " + field);
        }
        return value.textValue();
    }

    private static Integer integer(JsonNode root, String field) throws SupercellApiException {
        JsonNode value = root.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.canConvertToInt() || !value.isIntegralNumber()) {
            throw invalidResponse("Campo com tipo inválido na resposta: " + field);
        }
        return value.intValue();
    }

    private static JsonNode documentedArray(JsonNode root, String field)
            throws SupercellApiException {
        JsonNode value = root.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isArray()) {
            throw invalidResponse("Campo documentado como array possui tipo inválido: " + field);
        }
        return value.deepCopy();
    }

    private static Long parseRetryAfter(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static SupercellApiException httpFailure(int status, String retryAfter) {
        SupercellApiFailure failure;
        String message;
        if (status == 401) {
            failure = SupercellApiFailure.UNAUTHORIZED;
            message = "Token da API ausente, expirado ou inválido";
        } else if (status == 403) {
            failure = SupercellApiFailure.FORBIDDEN;
            message = "A API recusou o acesso; confira a chave e a lista de IPs permitidos";
        } else if (status == 404) {
            failure = SupercellApiFailure.PLAYER_NOT_FOUND;
            message = "Jogador não encontrado pela API oficial";
        } else if (status == 429) {
            failure = SupercellApiFailure.RATE_LIMITED;
            message = "Limite de chamadas da API atingido";
        } else if (status >= 500 && status <= 599) {
            failure = SupercellApiFailure.SERVER_ERROR;
            message = "A API da Supercell está temporariamente indisponível";
        } else {
            failure = SupercellApiFailure.BAD_REQUEST;
            message = "A API rejeitou a requisição";
        }
        return new SupercellApiException(failure, message, status,
                status == 429 ? parseRetryAfter(retryAfter) : null);
    }

    private static SupercellApiException invalidResponse(String message) {
        return new SupercellApiException(SupercellApiFailure.INVALID_RESPONSE, message);
    }

    private static String charsetName(String contentType) {
        if (contentType == null) {
            return StandardCharsets.UTF_8.name();
        }
        Matcher matcher = CHARSET_PARAMETER.matcher(contentType);
        if (!matcher.find()) {
            return StandardCharsets.UTF_8.name();
        }
        String value = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
        return value.trim();
    }

    private static URI requireSafeBaseUri(URI uri) {
        java.util.Objects.requireNonNull(uri, "baseUri");
        String scheme = uri.getScheme();
        if (uri.getHost() == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("baseUri must be an HTTP(S) origin/path without credentials or query");
        }
        return uri;
    }

    private static Duration requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
