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

O fluxo de implementação encontra-se em `.kiro/specs/clash-village-sync/`.

## Direção atual — 2026-10-20

O MVP offline e `fetch-player` já existem. Para novas sessões, seguir `.kiro/specs/clash-village-sync/next-steps.md` e `PROMPT_PROXIMOS_PASSOS_KIRO.md`, sem recriar o projeto inicial.

O usuário escolheu PostgreSQL local, com DBeaver para inspeção, histórico comparável por período e frontend futuro. Próximo incremento: E1, captura integral da API. Banco, consultas temporais e interface são etapas posteriores documentadas, ainda não implementadas. Manter o modo offline e nunca inventar snapshots para datas sem coleta.
