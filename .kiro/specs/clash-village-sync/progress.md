# Checkpoint de desenvolvimento

Data: 2026-10-21

## Planejamento e checkpoint solicitado — 2026-10-21

- O usuário quer histórico no PostgreSQL para consultas e gráficos por intervalo de datas. A recomendação é Angular por ser a tecnologia que já conhece; Spring Boot servirá como API Java de leitura entre a interface e o banco; Flyway cuidará das migrações versionadas.
- Proposta para o frontend: diretório `frontend/` no mesmo repositório Git. A camada `persistence` terá contratos e adaptadores/repositórios Java para PostgreSQL. Não criar repositório Git separado antes de haver motivo concreto de implantação ou ciclo independente.
- Próxima implementação é somente E2 (PostgreSQL, migrações e persistência). E3 domínio/reconciliação, E4 período/Excel, E5 API e E6 Angular permanecem planejadas. Nenhum banco foi conectado/criado e nenhuma aplicação web foi iniciada neste checkpoint.
- O prompt operacional atual em `PROMPT_PROXIMOS_PASSOS_KIRO.md` executa somente E2 e exige alvo local dedicado `clash_village_tracker_dev`; se configuração/acesso não estiverem disponíveis, não conecta a outros bancos e relata essa pendência.
- O usuário solicitou commit local do estado atual na branch `develop` antes de iniciar E2 ou qualquer frontend. Este checkpoint documental e a entrega E1 abaixo serão incluídos nesse commit; não haverá push.

## Entrega E1 — captura integral da resposta da API

### Implementado

- `JavaHttpSupercellPlayerClient` agora recebe `byte[]` com `BodyHandlers.ofByteArray`, calcula SHA-256 antes da decodificação e mapeia o perfil a partir de uma cópia textual. `SupercellPlayerCapture` mantém o corpo imutável em memória, hash, endpoint seguro, tags, status, charset/Content-Encoding, instante e perfil ou diagnóstico.
- O caso de uso salva cada captura em `output/supercell/collections/<tag-data-uuid>/`: `response.bin` guarda os bytes exatos, `metadata.json` contém metadados allowlisted e é gravado por último, e `player-profile.json` existe apenas para HTTP 200 com JSON válido e tag correspondente. O UUID mantém coletas distintas mesmo com relógio fixo ou corpo igual.
- Erro HTTP, corpo malformado, codificação de texto inválida e tag divergente preservam corpo e metadados sem gerar perfil. Falhas antes de receber resposta (configuração, timeout/transporte) não geram coleção vazia. A CLI mantém `fetch-player`, informa o local da evidência e não imprime token nem cabeçalhos de autenticação.
- `README.md` e `docs/17_api_e_exportacao.md` documentam estrutura dos artefatos e invocação manual por `CLASH_API_TOKEN`. O snapshot histórico e a planilha existente não foram usados como destino nem alterados.

### Arquivos de implementação e testes

- Implementação: `SupercellPlayerCapture`, `SupercellPlayerClient`, `JavaHttpSupercellPlayerClient`, `SupercellApiException`, `SupercellApiFailure`, `SupercellCaptureMetadata`, `FetchPlayerProfileApplication`, `FetchPlayerProfileResult` e `ClashVillageTracker`.
- Testes: `JavaHttpSupercellPlayerClientTest` e `FetchPlayerProfileApplicationTest` cobrem bytes/hash, campo não mapeado, charset, tag codificada/divergente, HTTP 401/429, JSON malformado, campos ausentes versus arrays vazios, token sintético ausente dos artefatos e coletas não sobrescritas. `ClashVillageTrackerTest` mantém regressão do comando offline e da falta de configuração do token.

### Verificação

- `.\mvnw.cmd -o verify` — BUILD SUCCESS, Java 17; 44 testes, 0 falhas, 0 erros, 0 ignorados. Inclui regressão do relatório offline com fixtures. O empacotamento mostra avisos já emitidos pelo Maven Shade sobre recursos/classes duplicados entre dependências; não impediram o build.
- A presença de `CLASH_API_TOKEN` foi verificada como ausente sem ler ou exibir valor. Nenhuma chamada real à API foi feita. A chamada manual documentada continua pendente de configurar a variável no processo que executará o JAR.
- Não houve conexão/migração PostgreSQL, frontend, comparação temporal, commit ou push. Nenhum dado histórico ou arquivo XLSX de referência foi usado como saída.

### Próximo incremento

E2: PostgreSQL dedicado `clash_village_tracker_dev`, configuração externa, migrações Flyway e repositórios Java para captura/histórico. A comparação temporal/Excel segue em E4; a API Spring Boot e o frontend Angular seguem em E5/E6.

## Implementado

- Projeto Java 17/Maven com Wrapper 3.9.9 e JAR executável.
- Pipeline offline de snapshot e catálogo: importação JSON/CSV, validação, normalização, reconciliação, cálculo, XLSX e persistência histórica.
- Preservação de `null/UNKNOWN` para estados de Sobrecarga; chave composta por categoria e nome legado no cálculo/diff.
- Cliente `JavaHttpSupercellPlayerClient` e caso de uso `FetchPlayerProfileApplication`.
- Comando `fetch-player`, token apenas em `CLASH_API_TOKEN`, timeout, encoding da tag e classificação de 401/403/404/429/5xx.
- DTO da API restrito aos campos de topo documentados; arrays internos permanecem opacos e nenhum campo de construções/SC/filas é presumido.

## Testado

- Build limpo e testes Maven com Java 17.
- Importadores reais JSON/CSV, cálculos, reconciliação, CLI, Excel e validação.
- API contra servidor HTTP local simulado; nenhuma credencial ou chamada externa usada nos testes.

## Limitações conhecidas

- Catálogo `legacy-unversioned`, parcial e com durações ausentes; `FULL` absoluto permanece bloqueado.
- O perfil oficial não fornece neste adaptador construções, Sobrecargas, filas ou cronômetros.
- A exportação nativa ainda não possui amostra real; nenhum parser foi inventado.
- Alterações manuais em `report.xlsx` ainda não possuem política segura de merge e podem ser substituídas por nova geração no mesmo diretório.
- Entidades repetidas continuam agregadas quando a fonte não comprova identidade física.

## Roteiro anterior registrado em 2026-10-20 (histórico)

Roteiro operacional: [next-steps.md](next-steps.md). Prompt: [PROMPT_PROXIMOS_PASSOS_KIRO.md](../../../PROMPT_PROXIMOS_PASSOS_KIRO.md).

1. E1: preservar resposta integral e metadados da API, reaproveitando `fetch-player` (concluída em 2026-10-21; ver entrega acima).
2. E2: implementar PostgreSQL local, configuração, migrações e repositórios; DBeaver para inspeção.
3. E3: completar procedência/reconciliação e catálogo versionado; corrigir pendências calculadas e consistência Java/Excel.
4. E4: consultar histórico por período e gerar Excel com datas filtráveis e comparação rastreável.
5. E5: definir backend de leitura e frontend sobre os mesmos casos de uso (replanejado em 2026-10-21 como E5 API e E6 Angular).

Exportação nativa continua pendente de amostra real, sem bloquear a coleta de perfil.

## Recuperação após exclusão acidental — 2026-10-20

- Restaurados a partir do histórico local do Kiro: `FetchPlayerProfileApplication.java`, `CatalogImporter.java` e `VillageEntity.java`.
- Inventário Maven confirmado: 64/64 fontes de produção presentes e 9/9 fontes de teste presentes.
- Build limpo recompôs `target/clash-village-tracker.jar`; 8 suítes, 38 testes, 0 falhas, 0 erros e 0 ignorados.
- IntelliJ alinhado localmente ao SDK registrado `ms-17`, language level 17 e recarga Maven automática.
- Kiro configurado para JavaSE-17, importação Maven automática e Maven Wrapper.
- O log do Kiro registrou conflito de bundle ASM entre `redhat.java 1.58.0` e `vscode-java-test 0.47.0`; foi selecionado `vscode-java-test 0.45.0`, sem o bundle duplicado. O cache JDT/OSGi corrompido foi copiado para pastas `*.backup-*` e esvaziado. É necessário recarregar a janela do Kiro uma vez para iniciar o novo servidor Java.

## Planejamento atualizado — 2026-10-20

- Usuário escolheu PostgreSQL local e DBeaver; deseja comparar evolução entre datas, inclusive 15 dias, e avalia frontend futuro.
- `requirements.md`, `design.md`, `tasks.md`, `next-steps.md` e o prompt operacional passaram a distinguir base existente de novas etapas.
- A revisão anterior encontrou pendências: quantidade fixa de upgrades no catálogo, divergência Java/Excel, cópias de categoria sem vínculo, substituição de edição manual, merge sem revalidação completa e diff/histórico parciais. Nenhuma dessas correções foi implementada por esta atualização documental.
- Captura integral da API, PostgreSQL, filtro temporal e frontend ainda não implementados. O payload salvo pelo cliente atual é uma observação do DTO parcial.
- O legado continua usando `LocalDate`; a evolução deverá distinguir precisão DATE/INSTANT e separar observação de importação.
- Validação documental: `git diff --check` aprovado, nove arquivos verificados como UTF-8 válido, referências Markdown locais existentes e nenhuma ocorrência dos padrões de JWT/chave privada pesquisados. Revisão confirmou alteração somente em documentação.
- Testes Java/Maven não foram repetidos nesta atualização documental. Os 38 testes citados acima pertencem à verificação anterior; nenhuma chamada real à API, conexão PostgreSQL ou migração foi executada nesta atualização.
