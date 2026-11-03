# API oficial, exportação da vila e integração

## API oficial
Portal: https://developer.clashofclans.com/ . Endpoint típico: `GET https://api.clashofclans.com/v1/players/%23U8J89QP2` com Bearer token. A tag `#` na URL deve ser escapada para `%23`.

Campos publicados/documentados no recurso de jogador: `tag`, `name`, `townHallLevel`, `expLevel`, `troops`, `spells`, `heroes`, `heroEquipment` (ver contrato vigente). **Não assumir** arrays de edifícios, SC e cronômetros de upgrades sem validação.

O comando local `fetch-player` captura o corpo HTTP como bytes antes de decodificar o JSON. Cada execução cria uma pasta única em `output/supercell/collections/` com `response.bin`, `metadata.json` (tag solicitada e retornada, endpoint, status, instantes, Content-Type/codificação, tamanho e SHA-256) e, somente após parse estrutural válido e tag correspondente, `player-profile.json` com o DTO interpretado. A presença de `metadata.json` sela uma coleção completa; diretórios sem ele resultam de falha de persistência e não são considerados coletas concluídas. Erros HTTP, JSON malformado e tag incompatível mantêm o corpo e o diagnóstico, sem perfil. Nenhuma coleta sobrescreve outra.

Execução manual no PowerShell (a chave fica somente no ambiente do processo):

```powershell
$env:CLASH_API_TOKEN = "<seu-token>"
java -jar target\clash-village-tracker.jar fetch-player --tag "#U8J89QP2" --output output
Remove-Item Env:CLASH_API_TOKEN
```

O perfil API não é mesclado automaticamente ao snapshot ou à planilha. Testes usam servidor HTTP local; uma chamada real só pode ser declarada após uma execução manual com o ambiente configurado.

## Exportação de vila (dado essencial para esta automação)
O jogo passou a oferecer opção de **exportar a vila para a área de transferência**; o Clash Ninja relata que essa exportação inclui níveis de construções e melhorias ativas e pode atualizar trackers. URL: https://www.clash.ninja/blog/one-tap-village-updating-is-here

### Fluxo recomendado
1. Solicitar ao usuário o export gerado por **Configurações do Clash of Clans**; não desenvolver parser às cegas.
2. Inspecionar a estrutura e preservar os bytes originais em fixture local sob `.gitignore` se houver identificadores.
3. Construir DTO Jackson que cubra `structures`, `upgradeJobs`, `quantity` etc. **somente após observar os nomes reais dos campos**; versionar `schemaVersion` e testes para diferentes versões.
4. Fundir com API oficial do player e overrides manuais. Não usar scraping ou interação automatizada no cliente do jogo.
5. Gerar Excel e diff, nunca sobrescrevendo campos manuais não representados nas fontes.

## Segurança
Token por `CLASH_API_TOKEN`, IP whitelist conforme a política atual, timeout e tratamento de 429. Proteger arquivos de exportação local; não enviar o token nem dumps privados a sites desconhecidos. Mesmo um CSV/planilha gerado localmente não deve publicar token.

## Exemplo de invocação
`java -jar target/clash-cv18-tracker.jar sync --tag "#U8J89QP2" --export ./input/village-export.json --out ./output`

## Dúvidas antes do parser
Formato real da exportação, IDs, atualização das Sobrecargas, se upgrades ativos incluem `endTime` ou duração restante, e se o perfil da API traz os pets e guardiões no momento. Registre evidência real antes de anunciar cobertura total.

## API de leitura do dashboard (E5) — PT-BR

A partir de 2026-11-03 o backend expõe uma **API REST somente leitura** (Spring Boot 3.3.5 sobre Java 17) que serve a análise offline do snapshot histórico para o frontend Angular. Essa API **não** acessa a API oficial da Supercell, não usa banco de dados e não exige token para iniciar. Ela reutiliza o caso de uso existente `ReportApplication.analyze(...)`, que executa o mesmo pipeline do relatório (carregar → validar → normalizar → reconciliar → calcular) **sem gravar o Excel nem o histórico**; nenhuma regra de negócio foi reescrita.

Classe principal: `com.clashtracker.web.WebApplication` (empacotada separadamente como `clash-village-tracker-web.jar`). A CLI e o `clash-village-tracker.jar` continuam intactos e enxutos (o fat-jar da CLI não contém o Spring).

Configuração externa (`application.properties` ou variáveis de ambiente):

- `tracker.snapshot-path` (padrão `data/snapshot_2026-10-19.json`)
- `tracker.catalog-path` (padrão `data/upgrade_steps.json`)
- `tracker.previous-snapshot-path` (opcional, habilita o diff)
- `tracker.gold-pass-boost` (padrão `0.20`)
- `server.port` (padrão `8080`)

Endpoints (todos `GET`, prefixo `/api`):

| Rota | Conteúdo |
|---|---|
| `/api/health` | Status do backend (`UP`, modo `offline-historical`). |
| `/api/village/dashboard` | Resumo: vila, progresso geral/permanente/Sobrecargas, durações, construtores, progresso por categoria e qualidade dos dados. |
| `/api/village/snapshot` | Snapshot normalizado: entidades com nível concluído, máximo, `countAtMax` e Sobrecargas por entidade. |
| `/api/village/categories` | Progresso por categoria com as entidades de cada categoria. |
| `/api/village/upgrades` | Etapas pendentes (permanentes e Sobrecargas) com contagem, fila, duração base/com Passe e saldo observado. |

CORS liberado apenas para `http://localhost:4200` e `http://127.0.0.1:4200` durante o desenvolvimento. O progresso é uma métrica de etapas catalogadas, não a porcentagem oficial da Supercell; `fullCertified` permanece `false` enquanto o catálogo for `legacy-unversioned`/incompleto.

Execução local:

```powershell
# Servidor web (porta 8080)
.\mvnw.cmd -s ci-settings.xml spring-boot:run
# ou, após empacotar:
java -jar target\clash-village-tracker-web.jar
```

> **Nota de build:** o arquivo `ci-settings.xml` resolve dependências diretamente do Maven Central. Ele existe porque o `~/.m2/settings.xml` da máquina de desenvolvimento aponta para um Artifactory corporativo que nem sempre está acessível. Use-o com `-s ci-settings.xml` quando o espelho corporativo estiver indisponível; ele não altera a configuração global.

## Read-only dashboard API (E5) — EN

Since 2026-11-03 the backend exposes a **read-only REST API** (Spring Boot 3.3.5 on Java 17) that serves the offline analysis of the historical snapshot to the Angular frontend. This API does **not** call the official Supercell API, uses no database, and needs no token to start. It reuses the existing `ReportApplication.analyze(...)` use case, which runs the same pipeline as the report (load → validate → normalize → reconcile → calculate) **without writing the Excel workbook or history**; no business rule was rewritten.

Main class: `com.clashtracker.web.WebApplication` (packaged separately as `clash-village-tracker-web.jar`). The CLI and `clash-village-tracker.jar` stay intact and lean (the CLI fat-jar does not bundle Spring).

External configuration (`application.properties` or environment variables): `tracker.snapshot-path`, `tracker.catalog-path`, `tracker.previous-snapshot-path` (optional, enables diff), `tracker.gold-pass-boost` (default `0.20`), `server.port` (default `8080`).

Endpoints (all `GET`, prefix `/api`): `/api/health`, `/api/village/dashboard`, `/api/village/snapshot`, `/api/village/categories`, `/api/village/upgrades`. CORS is restricted to the Angular dev server during development. Progress is a cataloged step-count metric, not an official Supercell percentage; `fullCertified` stays `false` while the catalog is `legacy-unversioned`/incomplete.

## Persistência SQL Server (E2) e perfil oficial no dashboard (Opção A) — PT-BR

A partir de 2026-11-03 o backend persiste o histórico em **SQL Server** (o usuário possui SQL Server 2019 Express local; não havia PostgreSQL instalado). A persistência é **opcional e desligada por padrão** (`tracker.db.enabled=false`), para a API continuar subindo e servindo o dashboard offline mesmo sem banco.

### Banco e credenciais
- Banco dedicado: `clash_village_tracker_dev` (criado sem alterar os demais bancos da instância).
- A autenticação integrada do `mssql-jdbc` exige uma DLL nativa (`mssql-jdbc_auth`) que não acompanha o driver; por isso usa-se **autenticação SQL** com um login dedicado `clash_app` (papel `db_owner` apenas no banco de desenvolvimento).
- A senha nunca fica em arquivo versionado. Ela é lida da variável de ambiente `CLASH_DB_PASSWORD`. Conexão/usuário podem ser sobrescritos por `CLASH_DB_URL` e `CLASH_DB_USER`.

### Dependências e migrações
- `spring-boot-starter-jdbc`, `com.microsoft.sqlserver:mssql-jdbc` e Flyway (`flyway-core` + `flyway-sqlserver`).
- Migração versionada `src/main/resources/db/migration/V1__initial_schema.sql` cria `village_snapshot`, `village_entity_observation` e `official_api_collection`. As migrações rodam explicitamente no startup **apenas quando a persistência está habilitada**; nunca durante `report`/`fetch-player`.

### Endpoints de persistência (ativos só com banco habilitado)

| Rota | Método | Conteúdo |
|---|---|---|
| `/api/db/status` | GET | `persistenceEnabled` e contagem de snapshots. |
| `/api/db/import-snapshot` | POST | Importa o snapshot histórico configurado (idempotente). 503 se a persistência estiver desligada. |
| `/api/db/snapshots?playerTag=#U8J89QP2` | GET | Lista os snapshots armazenados (data de observação `DATE` separada do instante de importação). |

### Perfil oficial (Opção A)

| Rota | Método | Conteúdo |
|---|---|---|
| `/api/village/official-profile` | GET | Lê a **coleta mais recente** gravada por `fetch-player` em `output/supercell/collections/` e projeta tag/name/TH/XP e as contagens de heróis/tropas/feitiços/equipamentos. Quando não há coleta, responde `available=false` com orientação. Não chama a API nem inventa dados. |

Execução local com banco:

```powershell
$env:CLASH_DB_PASSWORD = "<senha-do-login-clash_app>"
.\mvnw.cmd -s ci-settings.xml spring-boot:run -Dspring-boot.run.arguments=--tracker.db.enabled=true
# importar o snapshot histórico:  curl -X POST http://localhost:8080/api/db/import-snapshot
Remove-Item Env:CLASH_DB_PASSWORD
```

## SQL Server persistence (E2) and official profile on the dashboard (Option A) — EN

Since 2026-11-03 the backend persists history in **SQL Server** (the user has a local SQL Server 2019 Express; no PostgreSQL was installed). Persistence is **optional and disabled by default** (`tracker.db.enabled=false`) so the API still starts and serves the offline dashboard without a database.

Dedicated database `clash_village_tracker_dev`; SQL authentication via a dedicated `clash_app` login (integrated auth needs a native DLL the driver does not ship). The password comes only from `CLASH_DB_PASSWORD`, never a versioned file. Dependencies: `spring-boot-starter-jdbc`, `mssql-jdbc`, Flyway (`flyway-core` + `flyway-sqlserver`); migration `V1__initial_schema.sql` creates the snapshot/entity/collection tables and runs at startup only when persistence is enabled.

New endpoints (DB enabled): `GET /api/db/status`, `POST /api/db/import-snapshot` (idempotent), `GET /api/db/snapshots`. Option A: `GET /api/village/official-profile` reads the latest `fetch-player` collection from disk and never calls the API or fabricates data.
