# Próximos passos / Next steps — Clash Village Tracker

> Atualizado em 2026-11-03. Este arquivo substitui os antigos prompts operacionais.
> O roteiro técnico detalhado e o histórico ficam em `.project/specs/clash-village-sync/`
> (`next-steps.md`, `design.md`, `requirements.md`, `tasks.md`, `progress.md`).

## Estado atual (implementado e verificado)

- **MVP offline** (CLI + Excel): `report`, `validate-catalog`, `fetch-player`.
- **API REST de leitura** (Spring Boot): `/api/health`, `/api/village/dashboard`, `/api/village/snapshot`, `/api/village/categories`, `/api/village/upgrades`, `/api/village/official-profile`.
- **Persistência SQL Server (E2)**: banco dedicado `clash_village_tracker_dev`, Flyway + mssql-jdbc, endpoints `/api/db/status`, `/api/db/import-snapshot`, `/api/db/snapshots`. Desligada por padrão (`tracker.db.enabled=false`).
- **Dashboard Angular 21** consumindo a API, com painel de perfil oficial (Opção A).

## O que vem a seguir

1. **E4 — comparação por período**: expor evolução entre datas (ex.: "últimos 15 dias") na API e um gráfico temporal no dashboard. Depende de **uma segunda coleta** além do snapshot de 2026-10-19.
2. **E3 — qualidade do catálogo**: catálogo versionado independente da vila; completar durações desconhecidas (com fontes) para destravar `fullCertified`.
3. **Integração com a API oficial no dashboard**: hoje `fetch-player` é isolado; ligar o perfil coletado às telas de forma mais rica.

## Como rodar (resumo)

```powershell
# Backend (offline, sem banco)
cd C:\Kiro\CoC\clash-village-tracker
.\mvnw.cmd -s ci-settings.xml spring-boot:run

# Backend com histórico SQL Server
$env:CLASH_DB_PASSWORD = "<senha do login clash_app>"
.\mvnw.cmd -s ci-settings.xml spring-boot:run -Dspring-boot.run.arguments=--tracker.db.enabled=true

# Frontend (Node 22)
cd C:\Kiro\CoC\clash-village-tracker-frontend
npm start   # http://localhost:4200
```

Detalhes completos de execução e testes manuais estão no `README.md` e em `docs/17_api_e_exportacao.md`.

---

# English summary

This file replaces the former operational prompts. The detailed roadmap and history live under `.project/specs/clash-village-sync/`. Implemented and verified: offline CLI/Excel MVP, read-only Spring Boot REST API (including `/api/village/official-profile`), SQL Server persistence (E2, disabled by default), and the Angular 21 dashboard with an official-profile panel. Next: period comparison (E4, needs a second collection), catalog quality (E3), and richer official-API integration. See `README.md` for run and manual-test steps.
