package com.clashtracker.integration.supercell;

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
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public final class JavaHttpSupercellPlayerClient implements SupercellPlayerClient {
    public static final URI DEFAULT_BASE_URI = URI.create("https://api.clashofclans.com");
    public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    public static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(20);

    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final URI baseUri;
    private final String token;
    private final Duration requestTimeout;

    public JavaHttpSupercellPlayerClient(HttpClient httpClient, ObjectMapper mapper, URI baseUri,
                                         String token, Duration requestTimeout) {
        this.httpClient = java.util.Objects.requireNonNull(httpClient, "httpClient");
        this.mapper = java.util.Objects.requireNonNull(mapper, "mapper");
        this.baseUri = java.util.Objects.requireNonNull(baseUri, "baseUri");
        this.token = token;
        this.requestTimeout = requirePositive(requestTimeout, "requestTimeout");
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
    public SupercellPlayerProfile fetchPlayer(String playerTag) throws SupercellApiException {
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

        final HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpTimeoutException exception) {
            throw new SupercellApiException(SupercellApiFailure.TIMEOUT,
                    "A API da Supercell excedeu o tempo limite da requisição", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new SupercellApiException(SupercellApiFailure.INTERRUPTED,
                    "A chamada à API da Supercell foi interrompida", exception);
        } catch (IOException exception) {
            throw new SupercellApiException(SupercellApiFailure.TRANSPORT_ERROR,
                    "Não foi possível comunicar com a API da Supercell", exception);
        }

        return handleResponse(response);
    }

    URI playerUri(String normalizedTag) {
        String encodedTag = URLEncoder.encode(normalizedTag, StandardCharsets.UTF_8)
                .replace("+", "%20");
        String root = baseUri.toString().replaceAll("/+$", "");
        return URI.create(root + "/v1/players/" + encodedTag);
    }

    private SupercellPlayerProfile handleResponse(HttpResponse<String> response)
            throws SupercellApiException {
        int status = response.statusCode();
        if (status == 200) {
            return parsePlayer(response.body());
        }
        if (status == 401) {
            throw httpFailure(SupercellApiFailure.UNAUTHORIZED,
                    "Token da API ausente, expirado ou inválido", status, null);
        }
        if (status == 403) {
            throw httpFailure(SupercellApiFailure.FORBIDDEN,
                    "A API recusou o acesso; confira a chave e a lista de IPs permitidos", status, null);
        }
        if (status == 404) {
            throw httpFailure(SupercellApiFailure.PLAYER_NOT_FOUND,
                    "Jogador não encontrado pela API oficial", status, null);
        }
        if (status == 429) {
            Long retryAfter = parseRetryAfter(response.headers().firstValue("Retry-After").orElse(null));
            throw httpFailure(SupercellApiFailure.RATE_LIMITED,
                    "Limite de chamadas da API atingido", status, retryAfter);
        }
        if (status >= 500 && status <= 599) {
            throw httpFailure(SupercellApiFailure.SERVER_ERROR,
                    "A API da Supercell está temporariamente indisponível", status, null);
        }
        throw httpFailure(SupercellApiFailure.BAD_REQUEST,
                "A API rejeitou a requisição", status, null);
    }

    private SupercellPlayerProfile parsePlayer(String body) throws SupercellApiException {
        try {
            JsonNode root = mapper.readTree(body);
            if (root == null || !root.isObject()) {
                throw invalidResponse("A resposta de jogador não é um objeto JSON", null);
            }
            String tag = text(root, "tag", true);
            String name = text(root, "name", true);
            Integer townHallLevel = integer(root, "townHallLevel");
            Integer expLevel = integer(root, "expLevel");
            JsonNode troops = documentedArray(root, "troops");
            JsonNode spells = documentedArray(root, "spells");
            JsonNode heroes = documentedArray(root, "heroes");
            JsonNode heroEquipment = documentedArray(root, "heroEquipment");
            return new SupercellPlayerProfile(tag, name, townHallLevel, expLevel,
                    troops, spells, heroes, heroEquipment);
        } catch (JsonProcessingException exception) {
            throw invalidResponse("A API retornou JSON inválido", exception);
        }
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

    private static String text(JsonNode root, String field, boolean required)
            throws SupercellApiException {
        JsonNode value = root.get(field);
        if (value == null || value.isNull()) {
            if (required) {
                throw invalidResponse("Campo obrigatório ausente na resposta: " + field, null);
            }
            return null;
        }
        if (!value.isTextual()) {
            throw invalidResponse("Campo com tipo inválido na resposta: " + field, null);
        }
        return value.textValue();
    }

    private static Integer integer(JsonNode root, String field) throws SupercellApiException {
        JsonNode value = root.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.canConvertToInt() || !value.isIntegralNumber()) {
            throw invalidResponse("Campo com tipo inválido na resposta: " + field, null);
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
            throw invalidResponse("Campo documentado como array possui tipo inválido: " + field, null);
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

    private static SupercellApiException httpFailure(SupercellApiFailure failure, String message,
                                                     int status, Long retryAfter) {
        return new SupercellApiException(failure, message, status, retryAfter);
    }

    private static SupercellApiException invalidResponse(String message, Throwable cause) {
        return new SupercellApiException(SupercellApiFailure.INVALID_RESPONSE,
                message, null, null, cause);
    }

    private static Duration requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
