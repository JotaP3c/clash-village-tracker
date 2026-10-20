# Especificação funcional — automação Java da vila

## RF01 — Sincronização manual e semanal futura
O usuário executará um CLI Java local, sem agendamento obrigatório inicial: `java -jar clash-tracker.jar sync --tag "#U8J89QP2" --village-export caminho.json --out ./output/`. Para execuções futuras, o mesmo comando poderá ser invocado pelo Agendador de Tarefas do Windows semanalmente. O arquivo da exportação só pode ser exigido se for necessário para prédios/filas/SC.

## RF02 — Fontes, precedência e proteção de dados
1. **Exportação da vila feita pelo jogo**: conferir estrutura real do payload e guardar fixture local; valida nível de estruturas e melhorias ativas conforme os campos que realmente existem.
2. **API oficial**: `/players/%23U8J89QP2`; aplicar dados sobre heróis, tropas e feitiços conforme os arrays efetivamente retornados. Se não vier determinado campo (prédios, pets, SC, cronômetro), **não inventar**.
3. **Overrides manuais**: permitir confirmação por tela/planilha nos casos não cobertos.
4. **Catálogo versionado**: máximos por CV, custos e tempos por target level; nunca originar tempo/custo como se fosse um campo oficial de `/players`.
Ao detectar conflito, marcar `NEEDS_REVIEW` e logar divergência.

## RF03 — Identificação, mapeamento e normalização
Mapear API e export para `VillageSnapshot` independente de idioma. Campos: `externalId`/tag, `category`, `canonicalItemId`, `localizedDisplayName`, `levelCompleted`, `maxLevel`, `quantity`, `superchargeStage`, `upgradeJob`, `observedAt`, `source`, `confidence`. Manter JSON bruto para auditoria com remoção de secrets.

## RF04 — Catálogo nível por nível
`UpgradeDefinition(itemId,targetLevel,baseDurationMinutes,cost,currency,sourceUrl,catalogVersion,effectiveDate)`. Cada etapa pendente vira `UpgradeStep`; falta de duração deve ser `null` e indicador `UNKNOWN_DURATION`, nunca zero. Validação de teto por TH e de pré-requisitos.

## RF05 — Sobrecarga
Para cada defesa no nível base máximo: conferir SC1 e SC2; para coletores, SC1/SC2/SC3. Não confundir SC2 disponível com SC1 pendente. Upgrade em andamento conta como pendente até concluído. Ao alterar o catálogo por patch do jogo, invalidar/resetar cargas conforme regra da Supercell, registrando evento.

## RF06 — Passe e tempos
Parâmetros configuráveis (`goldPassBoost=0.20`, `permanentBuilders=6`, `goblinBuilderActive=false`, `labSlots=1`, `petSlots=1`). `durationWithPass = baseDuration * (1 - boost)`. Timers ativos são saldos independentes, não reaplicar boost. Somatório de dias-construtor separado de previsão de calendário.

## RF07 — Progresso do FULL
Para cada item catalogado, calcular `etapasConcluidas = soma de níveis permanentes concluídos + SC concluídas`, com `etapasTotais = quantidade * (maxLevel + maxSC)`. Para SC agrupadas: `countSC1 + countSC2 + countSC3`. Exibir essa métrica como **"progresso por etapas"**, não como porcentagem oficial. Manter medição por **tempo/custo restante** como futura métrica alternativa. Se houver itens desconhecidos, sinalizar `COBERTURA INCOMPLETA`; "FULL confirmado" apenas com zero pendências e zero desconhecidos.

## RF08 — Excel
Gerar XLSX com: Dashboard (pizza concluído×restante), Controle Geral editável, Fila Ativa, Sobrecargas, Laboratório, Heróis, Pets, Guardiões, etapas nível a nível, Pendências e Fontes. Fórmulas devem ler a aba principal; atualizar células editáveis altera gráfico automaticamente. Destacar células editáveis e origem dos dados. Preservar observações manuais de rodadas anteriores por `canonicalItemId`.

## RF09 — Histórico e reconciliação
Gravar `snapshots/YYYY-MM-DD.json`, `reports/diff_YYYY-MM-DD.md`, `logs/sync.jsonl` e a planilha. Comparar por ID + instância quando houver; registrar subidas de nível, SC, status de melhorias, boost e alterações de teto. Não usar apenas nome traduzido para chave primária.

## RF10 — Erros e robustez
Credencial em `CLASH_API_TOKEN` (nunca hardcode). Tratar 401, 403, 404, 429, timeout, rate limit e expiração/IP de token com erro explícito. Se API indisponível, permitir modo `--offline` usando último snapshot e export manual. Validação de JSON; versionamento do payload exportado; testes unitários e de integração sem acesso de rede.

## RF11 — JasperReports (opcional)
Produzir relatório PDF opcional usando o mesmo `Snapshot`/`Projection`, sem duplicar cálculos dentro de template JRXML. Primeiro entregar Excel e modelo estável.

## RF12 — Automação incremental
MVP1: carregar JSON local + planilha atual; MVP2: API e import export; MVP3: scheduler semanal manual/Windows; MVP4: projeção de timeline com builder allocation e livros; MVP5: Jasper.

## Não escopo nesta etapa
Não criar conta na Supercell, não usar automação de cliente de jogo/scraping autenticado, não prometer extrair SC/construções da API se o payload público não os expuser. Não coletar token do usuário no chat.
