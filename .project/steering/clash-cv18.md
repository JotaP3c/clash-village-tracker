---
inclusion: always
---
# Contexto persistente do projeto — Clash of Clans CV18

O projeto é um rastreador de progresso da vila do jogador `#U8J89QP2`, CV18, com 6 construtores permanentes, Passe Dourado parametrizável (snapshot 19/10/2026: 20%) e Goblin Builder **temporário**.

Antes de planejar, modificar ou implementar qualquer rotina:

1. Leia `AGENTS.md`, `README.md` e `docs/00_indice.md`.
2. Leia as regras em `docs/01_regras_jogo.md`, o contrato da automação em `docs/15_regras_automacao.md` e a estratégia de importação em `docs/17_api_e_exportacao.md`.
3. Utilize `data/snapshot_2026-10-19.json` como estado inicial e `data/upgrade_steps.json` como catálogo de *etapas pendentes*.
4. Preserve a diferença entre **nível concluído** e **nível-alvo em andamento**; apenas o término do upgrade deve incrementar o nível.
5. Preserve `null` como **duração desconhecida**. Nunca aplique 20% novamente no cronômetro restante extraído do jogo.
6. A API pública de jogador não deve ser tratada como fonte garantida para prédios, Sobrecargas e filas. Implemente importação da exportação nativa do jogo depois de receber um payload real.
7. Planeje o gerador de Excel para manter o Dashboard e o gráfico atualizáveis pelas fórmulas da aba Controle Geral.
8. Registre fonte, versão e confiança por campo; não marque a vila 100% FULL enquanto existirem lacunas.

O fluxo de implementação encontra-se em `.project/specs/clash-village-sync/`.

## Direção atual — 2026-11-03 (aplicação web full-stack)

O produto é agora uma **aplicação web full-stack**. Já existem e estão verificados: o MVP offline (CLI/Excel), `fetch-player` isolado, a **API REST de leitura Spring Boot** (`com.clashtracker.web`, endpoints `/api/health` e `/api/village/*`) e o **dashboard Angular 21** (`clash-village-tracker-frontend`) consumindo essa API com dados reais do snapshot histórico. A API reutiliza `ReportApplication.analyze(...)` e não reescreve regras; o Excel virou recurso complementar. Empacotam-se dois jars: `clash-village-tracker.jar` (CLI, sem Spring) e `clash-village-tracker-web.jar` (servidor). Use `-s ci-settings.xml` no Maven quando o espelho corporativo estiver indisponível.

Persistência **E2 implementada em SQL Server** (não PostgreSQL): o usuário tem SQL Server 2019 Express local. Banco dedicado `clash_village_tracker_dev`, login SQL `clash_app` (senha só em `CLASH_DB_PASSWORD`, nunca em arquivo), migrações Flyway (`flyway-sqlserver` + `mssql-jdbc`). A persistência é desligada por padrão (`tracker.db.enabled=false`) e a app sobe sem banco; endpoints `/api/db/*` e `/api/village/official-profile` (Opção A — lê a coleta mais recente do `fetch-player`). Autenticação integrada do driver exige DLL nativa ausente, por isso usa-se autenticação SQL.

Ainda **planejados**: E3 (reconciliação/catálogo versionado) e E4 (comparação por período e evolução temporal no dashboard — depende de uma segunda coleta). Manter o modo offline, nunca inventar snapshots para datas sem coleta e nunca declarar FULL com catálogo incompleto. Para novas sessões, seguir `.project/specs/clash-village-sync/next-steps.md` e `progress.md` (checkpoints 2026-11-03), sem recriar o que já existe.
