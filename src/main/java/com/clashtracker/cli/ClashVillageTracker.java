package com.clashtracker.cli;

import com.clashtracker.application.CatalogValidationResult;
import com.clashtracker.application.ExecutionStatus;
import com.clashtracker.application.FetchPlayerProfileApplication;
import com.clashtracker.application.FetchPlayerProfileResult;
import com.clashtracker.application.ReportApplication;
import com.clashtracker.application.ReportExecutionResult;
import com.clashtracker.application.ReportRequest;
import com.clashtracker.domain.ValidationIssue;
import com.clashtracker.importer.InputDataException;
import com.clashtracker.integration.supercell.JavaHttpSupercellPlayerClient;
import com.clashtracker.integration.supercell.SupercellApiException;
import com.clashtracker.util.JsonSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class ClashVillageTracker {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClashVillageTracker.class);
    private static final int EXIT_ARGUMENT_OR_INPUT = 2;
    private static final int EXIT_STRUCTURAL_VALIDATION = 3;
    private static final int EXIT_EXECUTION = 4;
    private static final int EXIT_SUPERCELL_API = 5;

    private ClashVillageTracker() {
    }

    public static void main(String[] args) {
        int exitCode = run(args, System.out, System.err);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    public static int run(String[] args, PrintStream out, PrintStream err) {
        return run(args, out, err, System.getenv());
    }

    static int run(String[] args, PrintStream out, PrintStream err, Map<String, String> environment) {
        if (args == null || args.length == 0) {
            err.println("Erro: informe um comando.");
            printHelp(err);
            return EXIT_ARGUMENT_OR_INPUT;
        }
        if (isHelp(args[0])) {
            printHelp(out);
            return 0;
        }
        try {
            ObjectMapper mapper = JsonSupport.createMapper();
            ReportApplication reportApplication = new ReportApplication(mapper);
            return switch (args[0]) {
                case "report" -> runReport(reportApplication, args, out, err);
                case "validate-catalog" -> runCatalogValidation(reportApplication, args, out, err);
                case "fetch-player" -> runFetchPlayer(mapper, args, out, environment);
                default -> {
                    err.println("Erro: comando desconhecido: " + args[0]);
                    printHelp(err);
                    yield EXIT_ARGUMENT_OR_INPUT;
                }
            };
        } catch (CliArgumentException exception) {
            err.println("Erro de argumento: " + exception.getMessage());
            printHelp(err);
            return EXIT_ARGUMENT_OR_INPUT;
        } catch (InputDataException exception) {
            err.println("Erro de arquivo/entrada: " + exception.getMessage());
            return EXIT_ARGUMENT_OR_INPUT;
        } catch (SupercellApiException exception) {
            err.printf("Falha da API Supercell [%s]: %s%n", exception.failure(), exception.getMessage());
            if (exception.statusCode() != null) {
                err.println("HTTP status: " + exception.statusCode());
            }
            if (exception.evidenceDirectory() != null) {
                err.println("Evidência da resposta: " + exception.evidenceDirectory());
            }
            if (exception.retryAfterSeconds() != null) {
                err.println("Tente novamente após aproximadamente " + exception.retryAfterSeconds() + " segundo(s).");
            }
            LOGGER.warn("Supercell API request failed: failure={}, status={}",
                    exception.failure(), exception.statusCode());
            return EXIT_SUPERCELL_API;
        } catch (IOException exception) {
            err.println("Falha ao gerar ou validar artefatos: " + exception.getMessage());
            LOGGER.error("Execution failed", exception);
            return EXIT_EXECUTION;
        } catch (RuntimeException exception) {
            err.println("Falha inesperada: " + exception.getMessage());
            LOGGER.error("Unexpected execution failure", exception);
            return EXIT_EXECUTION;
        }
    }

    private static int runReport(ReportApplication application, String[] args,
                                 PrintStream out, PrintStream err)
            throws CliArgumentException, InputDataException, IOException {
        ParsedArguments parsed = parse(args,
                Set.of("snapshot", "catalog", "gold-pass-boost", "output", "previous-snapshot"),
                Set.of("offline"));
        if (parsed.help()) {
            printReportHelp(out);
            return 0;
        }
        Path snapshot = requiredPath(parsed, "snapshot");
        Path catalog = requiredPath(parsed, "catalog");
        Path output = requiredPath(parsed, "output");
        Path previous = optionalPath(parsed, "previous-snapshot");
        BigDecimal boost = decimal(parsed.values().getOrDefault("gold-pass-boost", "0.20"),
                "gold-pass-boost");
        if (boost.compareTo(BigDecimal.ZERO) < 0 || boost.compareTo(BigDecimal.ONE) > 0) {
            throw new CliArgumentException("--gold-pass-boost deve estar entre 0 e 1");
        }

        LOGGER.info("Starting strictly offline report for snapshot {}", snapshot);
        ReportExecutionResult result = application.generate(new ReportRequest(
                snapshot, catalog, previous, boost, output, parsed.flags().contains("offline")));
        printIssues(result.issues(), out);
        out.println("STATUS: " + result.status());
        out.println(result.message());
        if (result.calculation() != null) {
            out.println("Itens normalizados: " + result.calculation().progress().categories().stream()
                    .mapToInt(category -> category.entityCount()).sum());
            out.println("Etapas de catálogo: " + result.calculation().steps().size());
            out.println("Total exato disponível: " + result.calculation().durations().exactTotalAvailable());
            out.println("FULL certificado: " + result.calculation().progress().fullCertified());
            out.println("Artefatos:");
            result.artifacts().forEach(path -> out.println("  - " + path));
        }
        return result.status() == ExecutionStatus.FAILED ? EXIT_STRUCTURAL_VALIDATION : 0;
    }

    private static int runCatalogValidation(ReportApplication application, String[] args,
                                            PrintStream out, PrintStream err)
            throws CliArgumentException, InputDataException {
        ParsedArguments parsed = parse(args, Set.of("catalog"), Set.of());
        if (parsed.help()) {
            printCatalogHelp(out);
            return 0;
        }
        Path catalogPath = requiredPath(parsed, "catalog");
        CatalogValidationResult result = application.validateCatalog(catalogPath);
        printIssues(result.issues(), out);
        out.println("STATUS: " + result.status());
        if (result.catalog() != null) {
            long known = result.catalog().steps().stream()
                    .filter(step -> step.definition().baseDurationSeconds() != null).count();
            long unknown = result.catalog().steps().size() - known;
            out.printf("Catálogo: %d linhas; %d durações conhecidas; %d desconhecidas; versão=%s%n",
                    result.catalog().steps().size(), known, unknown, result.catalog().catalogVersion());
        }
        return result.status() == ExecutionStatus.FAILED ? EXIT_STRUCTURAL_VALIDATION : 0;
    }

    private static int runFetchPlayer(ObjectMapper mapper, String[] args, PrintStream out,
                                      Map<String, String> environment)
            throws CliArgumentException, SupercellApiException, IOException {
        ParsedArguments parsed = parse(args, Set.of("tag", "output"), Set.of());
        if (parsed.help()) {
            printFetchPlayerHelp(out);
            return 0;
        }
        String playerTag = requiredText(parsed, "tag");
        Path output = requiredPath(parsed, "output");
        String token = environment.get("CLASH_API_TOKEN");
        JavaHttpSupercellPlayerClient client = JavaHttpSupercellPlayerClient.createDefault(mapper, token);
        FetchPlayerProfileApplication application = new FetchPlayerProfileApplication(
                client, mapper, Clock.systemUTC());
        FetchPlayerProfileResult result = application.fetch(playerTag, output);
        out.println("STATUS: SUCCESS");
        out.println("Perfil oficial coletado em " + result.observation().observedAt());
        out.println("Jogador: " + result.observation().player().name()
                + " (" + result.observation().player().tag() + ")");
        out.println("Perfil interpretado: " + result.artifact());
        out.println("Resposta integral: " + result.rawResponse());
        out.println("Metadados da coleta: " + result.metadata());
        out.println("SHA-256: " + result.bodySha256());
        out.println("Observação: este endpoint não fornece construções, Sobrecargas ou filas confirmadas.");
        return 0;
    }

    private static ParsedArguments parse(String[] args, Set<String> valueOptions, Set<String> flagOptions)
            throws CliArgumentException {
        Map<String, String> values = new LinkedHashMap<>();
        Set<String> flags = new java.util.LinkedHashSet<>();
        boolean help = false;
        for (int index = 1; index < args.length; index++) {
            String token = args[index];
            if (isHelp(token)) {
                help = true;
                continue;
            }
            if (!token.startsWith("--")) {
                throw new CliArgumentException("opção esperada, recebido: " + token);
            }
            String name = token.substring(2);
            if (flagOptions.contains(name)) {
                if (!flags.add(name)) {
                    throw new CliArgumentException("opção repetida: --" + name);
                }
                continue;
            }
            if (!valueOptions.contains(name)) {
                throw new CliArgumentException("opção desconhecida: --" + name);
            }
            if (index + 1 >= args.length || args[index + 1].startsWith("--")) {
                throw new CliArgumentException("valor ausente para --" + name);
            }
            if (values.putIfAbsent(name, args[++index]) != null) {
                throw new CliArgumentException("opção repetida: --" + name);
            }
        }
        return new ParsedArguments(values, flags, help);
    }

    private static Path requiredPath(ParsedArguments parsed, String name) throws CliArgumentException {
        return Path.of(requiredText(parsed, name));
    }

    private static String requiredText(ParsedArguments parsed, String name) throws CliArgumentException {
        String value = parsed.values().get(name);
        if (value == null || value.isBlank()) {
            throw new CliArgumentException("opção obrigatória ausente: --" + name);
        }
        return value;
    }

    private static Path optionalPath(ParsedArguments parsed, String name) {
        String value = parsed.values().get(name);
        return value == null ? null : Path.of(value);
    }

    private static BigDecimal decimal(String value, String name) throws CliArgumentException {
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            throw new CliArgumentException("--" + name + " exige número decimal, recebido: " + value);
        }
    }

    private static void printIssues(java.util.List<ValidationIssue> issues, PrintStream out) {
        if (issues.isEmpty()) {
            out.println("Validação: nenhuma pendência.");
            return;
        }
        long errors = issues.stream().filter(issue -> issue.severity().name().equals("ERROR")).count();
        long warnings = issues.stream().filter(issue -> issue.severity().name().equals("WARNING")).count();
        out.printf("Validação: %d issue(s), %d erro(s), %d aviso(s).%n", issues.size(), errors, warnings);
        for (ValidationIssue issue : issues) {
            out.printf("[%s] %s %s — %s%n", issue.severity(), issue.code(), issue.path(), issue.message());
        }
    }

    private static boolean isHelp(String value) {
        return "--help".equals(value) || "-h".equals(value) || "help".equals(value);
    }

    private static void printHelp(PrintStream out) {
        out.println("""
                Clash Village Tracker

                Uso:
                  java -jar target/clash-village-tracker.jar report --snapshot <arquivo.json> --catalog <arquivo.json|csv> --gold-pass-boost <0..1> --output <diretório> [--previous-snapshot <arquivo.json>] [--offline]
                  java -jar target/clash-village-tracker.jar validate-catalog --catalog <arquivo.json|csv>
                  java -jar target/clash-village-tracker.jar fetch-player --tag "#U8J89QP2" --output <diretório>

                report e validate-catalog são estritamente offline. Apenas fetch-player acessa a API oficial
                e exige CLASH_API_TOKEN no ambiente. Use '<comando> --help' para detalhes.
                """);
    }

    private static void printReportHelp(PrintStream out) {
        out.println("""
                report — gera XLSX e artefatos JSON locais
                  --snapshot <path>            snapshot snake_case obrigatório
                  --catalog <path>             catálogo JSON ou CSV obrigatório
                  --gold-pass-boost <0..1>     padrão 0.20; apenas duração-base futura
                  --output <directory>         diretório de saída obrigatório
                  --previous-snapshot <path>   opcional; gera diff.json
                  --offline                    indicador explícito; a implementação já é estritamente offline
                """);
    }

    private static void printCatalogHelp(PrintStream out) {
        out.println("validate-catalog — valida chaves, saltos, durações, quantidades e material sensível\n  --catalog <path>  catálogo JSON ou CSV obrigatório");
    }

    private static void printFetchPlayerHelp(PrintStream out) {
        out.println("""
                fetch-player — coleta somente o recurso oficial de jogador
                  --tag <#TAG>          tag bruta iniciada por #
                  --output <directory>  diretório-base das coletas locais imutáveis

                Cada coleta grava response.bin, metadata.json e, quando válido, player-profile.json
                sob supercell/collections/<identificador>. Requer CLASH_API_TOKEN.
                Não coleta construções, Sobrecargas, filas ou cronômetros.
                """);
    }

    private record ParsedArguments(Map<String, String> values, Set<String> flags, boolean help) {
    }

    private static final class CliArgumentException extends Exception {
        private CliArgumentException(String message) {
            super(message);
        }
    }
}
