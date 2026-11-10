# Checkpoint de desenvolvimento

Data: 2026-10-21 (ver também o checkpoint 2026-11-03 abaixo)

## Entrega E2 (SQL Server) + Opção A (perfil oficial) — 2026-11-03

Mudança de alvo de persistência: o usuário possui **SQL Server 2019 Express** local (instância padrão `MSSQLSERVER`, modo MIXED) e não tinha PostgreSQL. O E2 foi implementado e verificado contra SQL Server; menções a PostgreSQL/pgJDBC/DBeaver nas etapas de persistência passam a valer para SQL Server/mssql-jdbc/sqlcmd.

### Banco e segurança
- Criado o banco dedicado `clash_village_tracker_dev` (sem alterar `master`, `msdb`, `shared_planner_dev`, `VETORH_HCM` etc.).
- Autenticação integrada do mssql-jdbc exige a DLL nativa `mssql-jdbc_auth` (ausente → `UnsatisfiedLinkError`). Optou-se por **autenticação SQL** com login dedicado `clash_app` (db_owner só no banco dev). A senha nunca fica em arquivo versionado: vem de `CLASH_DB_PASSWORD`. O script de criação do login foi apagado após uso.

### Backend
- `pom.xml`: `spring-boot-starter-jdbc`, `mssql-jdbc` 12.8.1.jre11, `flyway-core` + `flyway-sqlserver` (via BOM). Flag `-parameters` no compiler (para binding de `@RequestParam`).
- Migração Flyway `V1__initial_schema.sql`: `village_snapshot`, `village_entity_observation`, `official_api_collection`.
- `PersistenceConfiguration` ativa só com `tracker.db.enabled=true` (DataSource Hikari + Flyway `migrate` + `JdbcTemplate` + repositórios). As auto-configs de DataSource/Flyway do Boot foram excluídas para a app subir sem banco por padrão.
- Repositórios `SnapshotRepository` e `OfficialCollectionRepository`; serviços `SnapshotImportService` e `OfficialProfileService` (ambos tolerantes a banco ausente via `Optional`).
- Endpoints novos: `POST /api/db/import-snapshot`, `GET /api/db/snapshots`, `GET /api/db/status` (503 claro quando persistência desligada) e `GET /api/village/official-profile` (Opção A).

### Frontend
- Modelos + método `officialProfile()` no serviço; painel "Perfil oficial" no dashboard (carregado com `catchError` → nunca quebra a página). Mostra heróis/tropas/feitiços/equipamentos e o aviso de limitação da API pública.

### Verificação (executada)
- `mvnw -s ci-settings.xml -o test`: **64 testes, 0 falhas** (suíte padrão; `*IT` não roda no surefire).
- `mvnw -s ci-settings.xml -o -Dtest=SnapshotRepositoryIT ... test` com `CLASH_DB_PASSWORD` no ambiente: **1 teste, 0 falhas** — Flyway valida a migração, importa o snapshot, prova idempotência e lê de volta no SQL Server real.
- Servidor com `--tracker.db.enabled=true`: Flyway aplicou `V1`; `POST /api/db/import-snapshot` (idempotente, `importedSnapshotId=1` nas duas chamadas), `GET /api/db/snapshots` retorna 1 snapshot/123 entidades/`observedDate=2026-10-19`; conferido por `sqlcmd` (1 snapshot, 123 entity rows). `GET /api/village/official-profile` → `available=false` com orientação (nenhuma coleta ainda).
- Frontend: `ng test` **9/9**; `ng build` de produção OK.

### Limitações / próximo passo
- Comparação por período (E4) ainda não existe: há só o snapshot de 19/10; o gráfico temporal depende de uma segunda coleta.
- A DLL de auth integrada do SQL Server não foi instalada (decisão: usar login SQL dedicado).
- Perfil oficial no dashboard aparece como "sem coleta" até você rodar `fetch-player` com o `CLASH_API_TOKEN`.

## Entrega E5 + E6 — aplicação web full-stack — 2026-11-03

Mudança de direção do produto: o foco deixou de ser o Excel e passou a ser uma aplicação web full-stack com integração real backend↔frontend. Foram implementadas, de forma pragmática e verticalmente completa, as etapas E5 (API de leitura) e E6 (Angular com dados reais), sem introduzir PostgreSQL (E2/E3/E4 de persistência permanecem planejadas e não são pré-requisito do MVP web).

### Backend (repositório `clash-village-tracker`, branch `develop`)

- Adicionada camada web Spring Boot 3.3.5 sobre o Java 17/Maven existente, **reaproveitando** `ReportApplication` e `CalculationEngine`; nenhuma regra de domínio foi reescrita nem duplicada.
- Refatoração não destrutiva: extraído `ReportApplication.analyze(ReportRequest)` que executa o pipeline (load→validate→normalize→reconcile→calculate) **sem gravar Excel/artefatos** e retorna o novo record `VillageAnalysis`. O método `generate(...)` passou a reutilizar `analyze(...)` e manteve exatamente o comportamento anterior (Excel, histórico e persistência inalterados).
- Novos pacotes: `web` (`WebApplication`), `web.config` (`TrackerProperties`, `TrackerConfiguration` com beans de `ObjectMapper`/`ReportApplication` e CORS para o Angular), `web.dto` (`DashboardResponse`, `SnapshotResponse`, `CategoryResponse`, `UpgradeResponse`), `web.service` (`VillageDashboardService`), `web.controller` (`HealthController`, `VillageController`, `ApiExceptionHandler`).
- Endpoints `GET`: `/api/health`, `/api/village/dashboard`, `/api/village/snapshot`, `/api/village/categories`, `/api/village/upgrades`. Configuração externa por `tracker.*` (snapshot, catálogo, previous, gold-pass-boost) e `server.port`.
- `pom.xml`: BOM do Spring Boot em `dependencyManagement`, `spring-boot-starter-web` (excluindo `spring-boot-starter-logging` para manter o binding slf4j-simple), `spring-boot-maven-plugin` com `classifier=web` (gera `clash-village-tracker-web.jar` separado) e `repackage`. O `maven-shade-plugin` passou a **excluir** as dependências web do fat-jar da CLI, que ficou enxuto (22 MB vs. 38 MB do jar web). `junit-jupiter.version` fixado em 5.11.0 para não ser rebaixado pelo BOM.
- `application.properties` criado em `src/main/resources`.
- Adicionado `ci-settings.xml`: espelho direto do Maven Central, para contornar o Artifactory corporativo (`maven.senior.com.br`) quando indisponível, sem alterar o `~/.m2/settings.xml` global.
- Novo teste `VillageDashboardServiceTest` (5 casos) validando identidade da vila (`#U8J89QP2`/TH18/6 construtores), limites de progresso, FULL não certificado com catálogo incompleto, separação Sobrecargas×permanentes, categorias com entidades e Smasher mantendo nível concluído 3.

### Frontend (repositório `clash-village-tracker-frontend`, branch `main`)

- Dashboard conectado à API real (antes era só fundação sem dados). Novos: `core/api/dashboard.models.ts` (tipos espelhando os DTOs), `core/api/dashboard-api.service.ts` (HTTP real via `HttpClient`), `dashboard-page.ts/.html/.scss` reescritos com signals, estados de loading/erro/retry, donut de progresso geral, barras permanente/Sobrecarga, cartões de KPI, navegação por categoria com tabela de entidades e listas de Sobrecargas e melhorias permanentes. Tema visual inspirado em Clash of Clans (badge TH, dourado, roxo para SC), responsivo.
- `api-configuration.ts`: baseUrl padrão `http://localhost:8080`, sobrescrevível por `window.__CLASH_TRACKER_API__` sem rebuild.
- `angular.json`: budget de estilo por componente elevado para acomodar o SCSS do dashboard.

### Verificação (executada)

- Backend `.\mvnw.cmd -s ci-settings.xml clean verify`: **BUILD SUCCESS**, **49 testes, 0 falhas, 0 erros** (44 existentes + 5 novos). Empacotamento gera `clash-village-tracker.jar` (CLI) e `clash-village-tracker-web.jar`.
- CLI de regressão: `validate-catalog` roda e mantém o resultado anterior (215 linhas, 10 durações desconhecidas, PARTIAL_SUCCESS). Modo offline preservado; CLI não contém Spring.
- Servidor web: Tomcat sobe na porta 8080 (`Started WebApplication in ~5s`). `GET /api/health` → `UP`; `GET /api/village/dashboard` → dados reais (progresso geral 92,79% = 8300/8945 etapas; Sobrecargas 3,7%; cobertura 91,06%; FULL não certificado). 
- Integração E2E: Angular em `:4200` recebe `Access-Control-Allow-Origin: http://localhost:4200` da API em `:8080` (preflight OPTIONS 200 + GET 200). Build de produção do frontend OK; `npm test` (vitest) **1/1 passou**.

### Limitações conhecidas / próximo passo

- Persistência PostgreSQL, histórico por período e comparação temporal (E2/E3/E4) continuam planejados e não implementados; a API serve apenas o snapshot histórico atual.
- Não houve integração com a API oficial da Supercell nesta entrega (o comando `fetch-player` segue manual e isolado).
- `fullCertified` permanece `false` (catálogo `legacy-unversioned`, 10 durações desconhecidas, 309 ocorrências de etapa sem duração).
- Nenhum commit/push foi feito (aguarda autorização explícita do usuário).
- Próximo incremento sugerido: persistência (E2) para habilitar histórico, e então expor evolução por período na API e no dashboard (E4/E6 avançado).

---

## Checkpoint anterior — 2026-10-21

## Planejamento e checkpoint solicitado — 2026-10-21

- O usuário quer histórico no PostgreSQL para consultas e gráficos por intervalo de datas. A recomendação é Angular por ser a tecnologia que já conhece; Spring Boot servirá como API Java de leitura entre a interface e o banco; Flyway cuidará das migrações versionadas.
- O usuário criou o repositório separado `clash-village-tracker-frontend`. A fundação será Angular 21 com Node 22.12.0; a camada `persistence` do backend continuará com contratos e adaptadores/repositórios Java para PostgreSQL.
- F0 (fundação Angular sem dados reais) pode iniciar em paralelo a E2. E3 domínio/reconciliação, E4 período/Excel, E5 API e E6 Angular com dados reais permanecem planejadas. Nenhum banco foi conectado/criado neste checkpoint.
- O prompt operacional atual em `NEXT_STEPS.md` executa somente E2 e exige alvo local dedicado `clash_village_tracker_dev`; se configuração/acesso não estiverem disponíveis, não conecta a outros bancos e relata essa pendência.
- O usuário solicitou commit local do estado atual na branch `develop` antes de iniciar E2 ou qualquer frontend. A entrega E1 e o checkpoint foram incluídos no commit `068b53a`; o push foi feito pelo usuário antes da fundação F0.

## F0 — fundação Angular separada — 2026-10-21

- O usuário criou `clash-village-tracker-frontend` como repositório separado. O remoto HTTPS retornava “Repository not found” sem autenticação; a URL foi alinhada à autenticação SSH já usada pelo backend: `git@github-pessoal:JotaP3c/clash-village-tracker-frontend.git`.
- Criado workspace Angular CLI 21.2.27 com SCSS, Router e Node 22.12.0. O Node 12 instalado na máquina não é compatível com essa versão e não deve ser usado para esse repositório.
- A rota `/dashboard` contém shell inicial, filtros desabilitados, cartões sem métricas e estado explícito de API/histórico indisponíveis. O serviço HTTP aceita somente uma URL pública via `window.__CLASH_TRACKER_API__`; não há token, senha, chamada HTTP nem dados simulados.
- Verificação: `npm run build` aprovado; `npm test -- --watch=false` aprovado com 1 teste. A primeira tentativa de `npm test -- --run` falhou porque Angular 21 não reconhece `--run`; foi substituída pelo argumento suportado. A instalação exigiu `npm install --legacy-peer-deps` para contornar uma falha interna do npm 10.9.0 (`edgesOut` nulo).
- `npm audit` relatou 4 vulnerabilidades altas no conjunto instalado. Nenhuma atualização automática ou com `--force` foi aplicada; avaliar dependências e correção compatível em uma etapa própria.

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

Roteiro operacional: [next-steps.md](next-steps.md). Prompt: [NEXT_STEPS.md](../../../NEXT_STEPS.md).

1. E1: preservar resposta integral e metadados da API, reaproveitando `fetch-player` (concluída em 2026-10-21; ver entrega acima).
2. E2: implementar PostgreSQL local, configuração, migrações e repositórios; DBeaver para inspeção.
3. E3: completar procedência/reconciliação e catálogo versionado; corrigir pendências calculadas e consistência Java/Excel.
4. E4: consultar histórico por período e gerar Excel com datas filtráveis e comparação rastreável.
5. E5: definir backend de leitura e frontend sobre os mesmos casos de uso (replanejado em 2026-10-21 como E5 API e E6 Angular).

Exportação nativa continua pendente de amostra real, sem bloquear a coleta de perfil.

## Recuperação após exclusão acidental — 2026-10-20

- Restaurados a partir do histórico local da IDE: `FetchPlayerProfileApplication.java`, `CatalogImporter.java` e `VillageEntity.java`.
- Inventário Maven confirmado: 64/64 fontes de produção presentes e 9/9 fontes de teste presentes.
- Build limpo recompôs `target/clash-village-tracker.jar`; 8 suítes, 38 testes, 0 falhas, 0 erros e 0 ignorados.
- IntelliJ alinhado localmente ao SDK registrado `ms-17`, language level 17 e recarga Maven automática.
- IDE configurada para JavaSE-17, importação Maven automática e Maven Wrapper.
- O log do agente registrou conflito de bundle ASM entre `redhat.java 1.58.0` e `vscode-java-test 0.47.0`; foi selecionado `vscode-java-test 0.45.0`, sem o bundle duplicado. O cache JDT/OSGi corrompido foi copiado para pastas `*.backup-*` e esvaziado. É necessário recarregar a janela do agente uma vez para iniciar o novo servidor Java.

## Planejamento atualizado — 2026-10-20

- Usuário escolheu PostgreSQL local e DBeaver; deseja comparar evolução entre datas, inclusive 15 dias, e avalia frontend futuro.
- `requirements.md`, `design.md`, `tasks.md`, `next-steps.md` e o prompt operacional passaram a distinguir base existente de novas etapas.
- A revisão anterior encontrou pendências: quantidade fixa de upgrades no catálogo, divergência Java/Excel, cópias de categoria sem vínculo, substituição de edição manual, merge sem revalidação completa e diff/histórico parciais. Nenhuma dessas correções foi implementada por esta atualização documental.
- Captura integral da API, PostgreSQL, filtro temporal e frontend ainda não implementados. O payload salvo pelo cliente atual é uma observação do DTO parcial.
- O legado continua usando `LocalDate`; a evolução deverá distinguir precisão DATE/INSTANT e separar observação de importação.
- Validação documental: `git diff --check` aprovado, nove arquivos verificados como UTF-8 válido, referências Markdown locais existentes e nenhuma ocorrência dos padrões de JWT/chave privada pesquisados. Revisão confirmou alteração somente em documentação.
- Testes Java/Maven não foram repetidos nesta atualização documental. Os 38 testes citados acima pertencem à verificação anterior; nenhuma chamada real à API, conexão PostgreSQL ou migração foi executada nesta atualização.

## 2026-11-10 — Atualização por prints / Update from screenshots

**PT-BR:** Criados `data/snapshot_2026-11-10.json` e `data/upgrade_steps_2026-11-10.json/.csv` a partir de 26 prints do jogo. O snapshot de 2026-10-19 permanece intacto. Níveis = último nível concluído; a fila (7 construtores, 2 pesquisas, 1 pet) usa saldos observados já com Passe (sem novo desconto). Itens não vistos nos prints foram marcados como carry-forward. Muros, armadilhas e Morteiros têm contagens derivadas das listas de melhorias (`confidence=inferido`). Retorno (4→6) e Reviver (3→4) têm identificação de ícone a conferir. Estágios de SC em obra (Dispersor, Torre Inferno, Torre de Super Mago, Torre da Vingança) estão como "a confirmar". Resultado: progresso catalogado 93,72% (era 92,79%); FULL não certificado (catálogo `legacy-unversioned`). API aponta para os arquivos novos; snapshot importado no SQL Server (id 2).

**EN:** Added `data/snapshot_2026-11-10.json` and `data/upgrade_steps_2026-11-10.json/.csv` from 26 game screenshots. The 2026-10-19 snapshot is untouched. Levels are last completed levels; the queue (7 builders, 2 research slots, 1 pet) uses observed balances that already include the Gold Pass (no second discount). Items not visible in the screenshots are marked as carry-forward. Wall, trap and Mortar counts are derived from upgrade lists (`confidence=inferido`). Recall (4→6) and Revive (3→4) icon identification needs review. In-progress SC stages (Spreader, Inferno Tower, Super Wizard Tower, Vengeance Tower) are flagged "to confirm". Result: cataloged progress 93.72% (was 92.79%); FULL not certified (`legacy-unversioned` catalog). The API now points to the new files; the snapshot was imported into SQL Server (id 2).
