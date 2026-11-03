# Design técnico — estado atual e evolução

Atualizado em 2026-10-21. PostgreSQL, API de leitura e consultas temporais descritos abaixo estão planejados. A fundação Angular será criada no repositório separado `C:\Kiro\clash-village-tracker-frontend`; consumo de dados reais continua planejado. A sequência e os aceites estão em [next-steps.md](next-steps.md).

## Estrutura existente

| Pacote | Responsabilidade atual |
|---|---|
| `cli` | `ClashVillageTracker`: comandos, argumentos, mensagens e códigos de saída. |
| `application` | Coordenação de relatório e coleta isolada de perfil. |
| `domain` | Snapshots, entidades, filas, catálogo, métricas, fontes e diagnósticos. |
| `importer`, `catalog` | Leitura de snapshot JSON e catálogo JSON/CSV. |
| `validation`, `normalization` | Validação de entradas e conversão para objetos internos. |
| `reconciliation` | Merge básico; procedência completa por campo permanece pendente. |
| `calculation` | Tempos, métricas e cargas por fila; não há simulador completo de calendário. |
| `history` | Comparação básica entre fotografias. |
| `report.excel`, `persistence` | XLSX, validação estrutural e gravação de artefatos/histórico em arquivos. |
| `integration.supercell` | Cliente HTTP, contratos parciais de resposta e falhas da API. |
| `util` | JSON, durações, hashes e arquivos temporários. |

O relatório segue `load → validate → normalize → reconcile → calculate → generate → persist`. A comparação anterior é opcional. O comando `fetch-player` é separado: consulta e salva uma observação do DTO de perfil, sem atualizar automaticamente o snapshot ou Excel.

## Comunicação com a API

- Reaproveitar `JavaHttpSupercellPlayerClient` e `FetchPlayerProfileApplication`.
- Preservar bytes do corpo recebido e calcular o hash antes da decodificação/mapeamento; guardar codificação e metadados. Distinguir corpo bruto, interpretação parcial e estado reconciliado.
- O contrato enviado confirma `troops`, `heroes`, `spells` e `heroEquipment`; confirmar a estrutura real dos itens antes de tipar seus campos internos.
- Não presumir pets/guardiões como arrays próprios, prédios, SC ou timers. `maxLevel` não substitui automaticamente o teto por CV do catálogo.
- O tipo opaco `PlayerVillage` sem endpoint/schema associado não define uma integração de vila. `verifytoken` é uma operação diferente de autenticar a consulta de perfil.
- `CLASH_API_TOKEN` entra pelo ambiente e nunca é parte do payload persistido nem de logs de cabeçalhos.

## Persistência PostgreSQL planejada

PostgreSQL local foi escolhido pelo usuário. DBeaver é o cliente de inspeção; o Java utilizará JDBC. A recomendação é Flyway com migrações SQL versionadas, revisadas e executadas explicitamente. O banco de desenvolvimento proposto é `clash_village_tracker_dev`; confirmar versão, URL, usuário e destino dedicado antes de criar o banco ou executar migrações. Se a configuração ou o acesso estiverem ausentes, não conectar a outro banco por suposição. A conexão do DBeaver não será lida automaticamente.

- Concentrar SQL/repositórios em `persistence`, com subpacote PostgreSQL quando necessário; manter contratos utilizáveis pelos casos de uso sem dependência de POI ou HTTP.
- Configuração proposta: `CLASH_DB_URL`, `CLASH_DB_USER`, `CLASH_DB_PASSWORD`, `CLASH_DB_SCHEMA`. Validar identificadores do schema e parametrizar valores SQL.
- Migrações Flyway versionadas, executadas explicitamente; fixar versões compatíveis com Java 17/Maven. Não executar DDL durante comandos `report`/`fetch-player` nem automaticamente em startup.
- Preservar execução offline por arquivos. Falta do banco deve afetar somente operações que o solicitam.
- Criar apenas tabelas utilizadas pelo incremento. Modelo lógico a detalhar em migrações:

| Conceito/tabela proposta | Papel |
|---|---|
| `ingestion_run`, `raw_payload` | Origem, jogador, resultado HTTP, captura integral, hash e identificação da coleta. |
| `village_snapshot`, `snapshot_entity`, `active_upgrade` | Fotografia consolidada, agrupamentos/instâncias comprovadas e fila. |
| `field_observation` | Valor observado, campo, fonte, confiança e precisão temporal; permite herdar um valor sem alterar sua data. |
| `catalog_version`, `upgrade_step` | Referência versionada independente das quantidades pendentes do jogador. |
| `reconciliation_issue`, `manual_override` | Conflitos com ambos os valores/fontes e correções justificadas. |
| `report_run` | Snapshots utilizados, catálogo, versão da métrica, cenário, bônus e artefatos derivados. |
| `entity_type`, `entity_observation` | Identidade técnica da entidade e valores por fotografia, preservando contagens agregadas e procedência. |

- Guardar bytes originais em arquivo imutável ou `bytea` com hash; texto integral pode ser mantido quando a codificação estiver comprovada. `jsonb` é uma representação consultável adicional, não prova dos bytes originais.
- Identidade idempotente representa uma coleta/artefato, com jogador e origem. Hash do corpo sozinho não identifica uma coleta: respostas iguais recebidas em momentos diferentes continuam observações distintas.
- Snapshot, entidades, observações e vínculos devem ser gravados em transação. Falhas deixam diagnóstico da execução, sem expor um snapshot parcialmente consolidado.
- Snapshots, payloads e versões publicadas são imutáveis; status operacional pode registrar a conclusão/falha da execução. Alteração de cenário gera novo `report_run`.
- Índices por jogador/data e chaves únicas de importação; não converter ordem de arrays em identidade.

## Contrato temporal planejado

1. **Precisão:** novas observações têm `observedAt` quando a fonte permite; recepção/importação têm instantes próprios. O legado usa `observedDate=2026-10-19`, `observationPrecision=DATE` e hora desconhecida. Não promover essa data a meia-noite ou ao instante de importação.
2. **Armazenamento:** usar instantes UTC/`timestamptz` para horários conhecidos e guardar a precisão e o fuso de interpretação separadamente. Datas sem hora permanecem datas. Apresentação padrão: `America/Sao_Paulo`.
3. **Limites por data:** comparação entre a posição conhecida até o fim de `from` e até o fim de `to`. Implementar limites de dia como início do dia seguinte exclusivo no fuso escolhido; para hoje, limitar o corte ao instante da execução. `from > to` é inválido.
4. **Seleção as-of:** escolher a observação consolidada mais recente dentro de cada corte definido acima, respeitando o limite exclusivo do dia seguinte e o limite de execução para hoje, do mesmo jogador e com escopo comparável. Nunca usar fotografia futura para preencher o passado. Permitir idade máxima configurável e seleção explícita por IDs.
5. **Datas esparsas:** exibir período solicitado e datas/IDs efetivos, precisão e defasagem. Sem baseline ou se ambos os limites selecionarem a mesma fotografia, informar histórico insuficiente/ausência de segunda observação; não afirmar nenhuma evolução na conta.
6. **Ambiguidade:** precisão de data não permite ordenar várias leituras dentro daquele dia. Se hora desconhecida ou empate de fontes alterar a seleção, sinalizar ambiguidade ou exigir escolha por IDs; importação mais recente não prova observação mais recente.
7. **Evidência:** uma mudança entre duas coletas não comprova seu instante exato. Se o baseline anteceder a data pedida, a variação pode incluir mudanças anteriores ao intervalo solicitado; mostrar essa limitação.
8. **Comparação:** nível conhecido para outro nível comparável pode confirmar variação. Desconhecido para conhecido é enriquecimento. Campo omitido ou herdado conserva fonte/data e não é nova confirmação. Regressão, origem incompatível ou reset de SC exigem conflito/reconciliação.
9. **Cobertura e agregados:** comparar também `countAtMax`, distribuições disponíveis, SC, filas, cobertura e bônus. Preservar agregados e rastrear transformação para instâncias, sem duplicar unidades.
10. **Versões:** resultados históricos usam o catálogo e a versão da métrica registrados. Mudança de denominador deve ser exibida separadamente; referência comum só em cenário explícito e compatível.
11. **Relógio:** atalhos como últimos 15 dias usam `Clock` e `ZoneId`, com `from=hoje.minusDays(15)` e `to=hoje`; não exigem coleta automática. O usuário pode gerar a coleta final separadamente.

## Cálculo e Excel

Corrigir o uso das quantidades legadas de catálogo como pendências fixas. Derivar trabalho do snapshot e transições aplicáveis versionadas; etapa ausente ou duração desconhecida impede total exato. Revalidar o resultado da reconciliação.

O objetivo da métrica é `completedApplicableSteps / totalApplicableSteps`; a implementação atual baseada em soma de níveis precisa evoluir. Cobertura permanece separada. Fórmulas Excel devem concordar com o Java inclusive para valores nulos e contagens agregadas.

O relatório temporal terá tabela de histórico filtrável por data e aba de comparação com fontes, conflitos, escopo e fotografias efetivas. No primeiro incremento temporal, o Java seleciona o período e materializa o XLSX. Alterar filtro no Excel filtra linhas já exportadas; não dispara SQL nem consulta à API. Seletores que recalculam a comparação inteira só serão anunciados após implementação e teste específicos.

Gerar em diretório identificado por execução, preservando alterações manuais; evolução para merge deve ter contrato próprio. Relatório calculado e correção manual são objetos distintos.

## CLI atual e contratos futuros

Atuais: `report --snapshot ... --catalog ... --gold-pass-boost ... --output ... [--previous-snapshot ...] [--offline]`, `validate-catalog --catalog ...`, `fetch-player --tag ... --output ...`.

Propostas a detalhar e implementar depois: migração explícita do schema, importação no PostgreSQL, listagem de snapshots e relatório por período com jogador, `from`, `to`, fuso e destino. Nenhum comando novo deve ser documentado como executável antes de existir na CLI e nos testes.

## Backend e frontend futuros

Fluxo recomendado: `Angular frontend → Java REST API (Spring Boot) → casos de uso/repositórios → PostgreSQL`. O frontend não conecta diretamente ao banco nem recebe `CLASH_API_TOKEN`/credenciais de banco. O backend reutiliza o domínio e devolve DTOs independentes do XLSX; endpoints propostos para detalhar antes de implementar: lista de snapshots por jogador/período, resumo do dashboard e evolução por entidade/categoria. A API inicial é local e somente leitura, com CORS limitado ao endereço de desenvolvimento do Angular. O frontend está no repositório Git separado `C:\Kiro\clash-village-tracker-frontend`; o contrato HTTP deve ser versionado e compatível com essa separação.

O Angular é a preferência recomendada porque o usuário já o utiliza. A fundação usa Angular 21 com Node 22.12.0, SCSS e Router. A primeira interface com dados reais terá jogador e intervalo de datas, cobertura e pendências, séries históricas e tabela filtrável; ausência de baseline e dados incompletos serão estados explícitos. A biblioteca de gráficos será escolhida ao montar essa tela, conforme compatibilidade e manutenção da versão Angular adotada. O JAR/CLI offline do backend permanece independente do frontend.

### Referências oficiais para as escolhas recomendadas

- [Angular HttpClient](https://angular.dev/guide/http) e [Angular Router](https://angular.dev/guide/routing) para chamadas HTTP e navegação da SPA.
- [Spring REST service guide](https://spring.io/guides/gs/rest-service/), que usa Java 17 ou posterior, para a API Java proposta.
- [Flyway versioned migrations](https://documentation.red-gate.com/fd/versioned-migrations-273973333.html) para histórico e checksums de migrações.

## Referências técnicas

- [pgJDBC — conexão e propriedades](https://jdbc.postgresql.org/documentation/use/).
- [PostgreSQL — JSON e diferenças de preservação em jsonb](https://www.postgresql.org/docs/current/datatype-json.html).
- [PostgreSQL — datas, instantes e fusos](https://www.postgresql.org/docs/current/datatype-datetime.html).

Referências consultadas em 2026-10-21; a versão do PostgreSQL local e compatibilidade das dependências ainda devem ser confirmadas.
