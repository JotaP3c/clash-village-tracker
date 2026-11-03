# Backlog de implementação

Atualizado em 2026-10-21. `[x]` representa implementação existente com os limites documentados; não certifica aceite integral do produto. Ordem e detalhes: [next-steps.md](next-steps.md).

## Base existente

- [x] Projeto Java 17/Maven com Wrapper, Jackson, Java HttpClient, Apache POI e JUnit.
- [x] Records/DTOs compatíveis com os arquivos legados efetivamente importados.
- [x] Carregamento, validação e normalização de snapshot JSON e catálogo JSON/CSV.
- [x] Reconciliação básica que preserva alguns valores omitidos e sinaliza regressões.
- [x] Relatório offline com 13 abas, fórmulas, tabelas e gráfico; consistência e edição completa ainda pendentes.
- [x] Coleta isolada `fetch-player`, token em ambiente, resposta integral/hash/metadados e perfil interpretado separado; testes HTTP simulados.
- [x] Separação de nível concluído, alvo e saldo observado nas melhorias ativas.
- [x] Tempos conhecidos, desconto futuro e cargas por fila; pendências ainda dependem do catálogo legado.
- [x] Diff básico e histórico por arquivos; comparação completa por período ainda não existe.

## E1 — Captura integral da API (concluída)

- [x] E1.1 Reutilizar cliente e comando existentes; preservar bytes recebidos, SHA-256 e metadados da consulta em uma pasta identificada por UUID.
- [x] E1.2 Manter perfil interpretado separado; o JSON bruto preserva campos não mapeados.
- [x] E1.3 Registrar tags solicitada/retornada, origem, instante, status e codificação; rejeitar tag incompatível sem associar perfil.
- [x] E1.4 Preservar classificação HTTP/timeout; persistir respostas HTTP/JSON/tag rejeitadas sem perfil e manter falha de transporte/configuração diferenciada.
- [x] E1.5 Testar com servidor local: tag codificada, campo não mapeado, hash/bytes exatos, JSON inválido, tag divergente, erro HTTP, token sintético ausente dos artefatos e coletas sem sobrescrita.
- [x] E1.6 Documentar execução manual via `CLASH_API_TOKEN`; chamada real permanece pendente e não é declarada como executada.

Verificação desta entrega: `.\mvnw.cmd -o verify` e regressão offline registrados em `progress.md`. Sem credencial configurada no ambiente desta sessão, não houve chamada à API real.

## E2 — PostgreSQL local

> **Atualização 2026-11-03:** o alvo de persistência passou a ser **SQL Server** (o usuário possui SQL Server 2019 Express local; não havia PostgreSQL instalado). As tarefas abaixo foram concluídas contra o SQL Server. As referências a pgJDBC/DBeaver foram substituídas por mssql-jdbc/sqlcmd.

- [x] E2.1 Detectada a instância `MSSQLSERVER` (SQL Server 2019 Express, modo MIXED) e criado o banco dedicado `clash_village_tracker_dev` sem alterar os demais bancos. Login SQL `clash_app` (db_owner só nesse banco); senha fora de arquivos, via `CLASH_DB_PASSWORD`.
- [x] E2.2 Adicionados `mssql-jdbc` (12.8.1.jre11) e Flyway (BOM Spring Boot) com `flyway-sqlserver`; migração `V1__initial_schema.sql` versionada e imutável.
- [x] E2.3 Criadas as tabelas `village_snapshot`, `village_entity_observation` e `official_api_collection` (procedência, precisão temporal, hash).
- [x] E2.4 Repositórios `SnapshotRepository`/`OfficialCollectionRepository` com `JdbcTemplate`, isolados de cálculo/CLI/Excel; ativados só quando `tracker.db.enabled=true`.
- [x] E2.5 Snapshot legado importado como observação `DATE` de 2026-10-19 (123 entidades), com instante de importação separado.
- [x] E2.6 Escrita transacional, índices por jogador/data e idempotência verificadas (reimport não duplica; `importedSnapshotId` estável).
- [x] E2.7 Teste de integração `SnapshotRepositoryIT` executado contra o SQL Server real (migração + import idempotente + leitura); habilitado só quando `CLASH_DB_PASSWORD` existe, senão é pulado. Inspeção documentada via `sqlcmd`/pgAdmin-equivalente.
- [x] E2.8 CLI offline preservada sem banco; persistência desligada por padrão; migração só quando habilitada explicitamente, sem tocar em outros bancos.

### Opção A — Perfil oficial no dashboard (2026-11-03)
- [x] Endpoint `GET /api/village/official-profile` lê a coleta mais recente de `fetch-player` (sem chamar a API nem inventar dados; registra a coleta no índice do banco quando habilitado).
- [x] Painel "Perfil oficial" no dashboard Angular, com aviso de que a API pública não expõe construções/Sobrecargas/filas/timers.

## F0 — Fundação Angular separada

- [ ] F0.1 Corrigir o remoto do repositório `clash-village-tracker-frontend` para usar SSH, criar o primeiro commit `main` e publicar o workspace.
- [x] F0.2 Gerar workspace Angular 21 com Node 22.12.0, SCSS e Router; `npm run build` aprovado em 2026-10-21.
- [x] F0.3 Criar shell e rota de dashboard com estados explícitos de API/histórico indisponíveis, sem dados fictícios de evolução.
- [x] F0.4 Preparar serviço HTTP e configuração externa da URL da API, sem token, senha ou dependência direta do PostgreSQL.

## E3 — Domínio, reconciliação, cálculo e consistência

- [ ] E3.1 Confirmar payload real de itens; implementar DTOs/mapeamento comprovados, aliases versionados e revisão de desconhecidos.
- [ ] E3.2 Completar catálogo técnico versionado, separado das quantidades e estados pendentes do jogador.
- [ ] E3.3 Recalcular pendências quando nível, `countAtMax` ou SC mudarem; não reutilizar quantidade fixa do catálogo antigo.
- [ ] E3.4 Revalidar merge e considerar jogador, data, precisão, catálogo e confiança por campo; preservar ambos os valores conflitantes.
- [ ] E3.5 Bloquear total exato quando faltar uma transição inteira; representar tempo-base ausente e saldo observado separadamente.
- [ ] E3.6 Unificar métricas Java/Excel, tratamento de nulos, cobertura e entradas efetivamente editáveis.
- [ ] E3.7 Preservar relatórios e correções manuais; definir merge antes de substituir arquivos editados.
- [ ] E3.8 Ampliar diff para `countAtMax`, SC, filas, cobertura, bônus e catálogo; não deixar diff antigo associado a nova execução.
- [ ] E3.9 Registrar cenário/catálogo/métrica de cada relatório e preservar estado histórico.

## E4 — Períodos e Excel temporal

- [ ] E4.1 Implementar seleção as-of por jogador, limites, fuso, precisão, defasagem e idade máxima configurável.
- [ ] E4.2 Tratar falta de baseline, mesma fotografia nos dois limites e ambiguidade de leituras sem hora.
- [ ] E4.3 Classificar mudança confirmada, enriquecimento, conflito e ausência de nova evidência sem inferir término por timer.
- [ ] E4.4 Expor solicitado versus efetivo, versões e diferenças agregadas sem inventar instâncias/data exata de conclusão.
- [ ] E4.5 Adicionar contratos CLI de histórico/período e DTOs de consulta independentes do Excel.
- [ ] E4.6 Gerar histórico com filtros de data e comparação materializada; documentar o alcance dos filtros no XLSX.
- [ ] E4.7 Testar 15 dias, intervalos esparsos, mudança de catálogo, precisão DATE/INSTANT e igualdade Java/Excel.

## E5 — API de leitura Java

- [ ] E5.1 Confirmar integração do Spring Boot ao Maven atual sem duplicar regras nem quebrar o JAR offline.
- [ ] E5.2 Expor endpoints somente leitura para resumo, snapshots e evolução, com filtros por jogador, data e categoria.
- [ ] E5.3 Usar DTOs de domínio independentes do Excel; validar limites de data, histórico insuficiente e cobertura parcial.
- [ ] E5.4 Manter DB e token da Supercell somente no processo servidor; configurar CORS para o frontend local e testar API com PostgreSQL de teste.

## E6 — Frontend Angular com dados reais

- [ ] E6.1 Evoluir a aplicação em `C:\Kiro\clash-village-tracker-frontend` para consumir a API E5.
- [ ] E6.2 Implementar seleção de jogador/período, resumo de cobertura/pendências e tabela filtrável por categoria/entidade.
- [ ] E6.3 Mostrar gráficos temporais somente com observações existentes, indicando datas efetivas e intervalos sem baseline.
- [ ] E6.4 Escolher biblioteca de gráficos compatível com a versão Angular adotada e testar vazio, erro HTTP, carga e filtro.
- [ ] E6.5 Não armazenar tokens ou credenciais do banco no bundle/configuração do navegador.

## Demandas complementares

- [ ] Receber amostra real de exportação nativa e implementar parser versionado; independente da captura de perfil.
- [ ] Completar regressão dos cenários de `docs/19_testes_e_pendencias.md` e de `AGENTS.md`, incluindo instâncias quando houver identidade comprovada.
- [ ] Planejamento de calendário com dependências, disponibilidade e benefícios temporários.
- [ ] Agendamento local opcional, sem ativação automática.
- [ ] JasperReports opcional após estabilizar o modelo e os relatórios.
