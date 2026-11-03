# Relatório de testes pré-commit — Backend

> Bilingual note / Nota bilíngue: documento em PT-BR com resumo técnico; termos de código, comandos e nomes de classe permanecem em inglês.

## 1. Data e repositório

- **Data:** 2026-11-03
- **Repositório:** `C:\Kiro\CoC\clash-village-tracker` (backend Java 17 / Maven)
- **Branch:** `develop`
- **Escopo:** validação pré-commit da camada web REST adicionada nesta sessão (E5), sem commit/push.

## 2. Funcionalidades analisadas

A camada de domínio e o pipeline offline (`load → validate → normalize → reconcile → calculate → generate → persist`) já possuíam cobertura consolidada. A análise priorizou a **nova camada web** (`com.clashtracker.web`), que projeta o resultado do cálculo para a API REST de leitura, e a **refatoração não destrutiva** de `ReportApplication` (extração de `analyze(...)` reutilizado por `generate(...)`).

Pontos verificados:
- Projeção correta das regras de negócio nos DTOs da API (dashboard, snapshot, categorias, upgrades).
- Preservação das regras críticas documentadas (`AGENTS.md`, `docs/19_testes_e_pendencias.md`).
- Contrato HTTP dos controladores e tratamento de erro de entrada.
- Regressão: a suíte de domínio existente continua verde após a refatoração.

## 3. Arquivos de teste criados ou modificados

| Arquivo | Situação | Conteúdo |
|---|---|---|
| `src/test/java/com/clashtracker/web/service/VillageDashboardServiceTest.java` | Criado (sessão anterior) | 5 testes com o dataset histórico real. |
| `src/test/java/com/clashtracker/web/service/VillageDashboardServiceRulesTest.java` | Criado | 6 testes de regra de negócio com fixtures controladas em JSON temporário. |
| `src/test/java/com/clashtracker/web/controller/VillageControllerTest.java` | Criado | 4 testes de contrato dos controladores + mapeamento de erro 422. |

Nenhum teste existente foi removido ou enfraquecido.

## 4. Cenários cobertos

**`VillageDashboardServiceTest`** (dados reais `data/snapshot_2026-10-19.json` + `data/upgrade_steps.json`):
- Identidade histórica da vila: `#U8J89QP2`, TH18, 6 construtores, Passe 20%, data `2026-10-19`.
- Progresso geral dentro de 0–100% e FULL **não** certificado com catálogo incompleto.
- Sobrecargas expostas separadamente das melhorias permanentes.
- Categorias carregam suas entidades.
- Smasher mantém nível concluído 3 (upgrade 3→4 em andamento não avança o nível).

**`VillageDashboardServiceRulesTest`** (fixtures controladas, determinísticas):
- Passe Dourado aplicado **uma única vez** e só à etapa futura elegível (10 dias base → 8 dias; 864000 s → 691200 s).
- Timer observado **não** recebe desconto novamente (saldo de 2 dias preservado = 172800 s) e o nível concluído permanece atrás do alvo.
- Duração desconhecida é exposta como desconhecida (`durationKnown=false`, dias `null`), **nunca** zero, e bloqueia o total exato.
- 6 construtores permanentes + Goblin temporário reportados; dias-construtor (72) e limite inferior de calendário (72/6 = 12 dias) corretos.
- `count_at_max` agregado limita as melhorias permanentes pendentes (2 morteiros de 4 pendentes em 17→18).
- Sobrecargas exigem o máximo permanente elegível e permanecem tipadas como `SUPERCHARGE`.

**`VillageControllerTest`** (contrato HTTP, sem contexto Spring):
- `/api/health` reporta modo `offline-historical`.
- `/api/village/dashboard` retorna a vila histórica.
- Todos os endpoints de vila respondem para o dataset real.
- Arquivo de snapshot ausente gera `InputDataException` mapeada para HTTP 422 sem stack trace.

## 5. Comandos utilizados

```powershell
# Suíte apenas da camada web (iteração rápida)
.\mvnw.cmd -s ci-settings.xml -o -Dtest=VillageDashboardServiceRulesTest,VillageControllerTest,VillageDashboardServiceTest test

# Suíte completa + empacotamento
.\mvnw.cmd -s ci-settings.xml clean verify
```

> `ci-settings.xml` resolve dependências direto do Maven Central, contornando o espelho corporativo (`maven.senior.com.br`) indisponível neste ambiente. Não altera o `~/.m2/settings.xml` global.

## 6. Resultado por conjunto de testes

| Classe de teste | Testes | Resultado |
|---|---:|---|
| FetchPlayerProfileApplicationTest | 4 | ✅ 0 falhas |
| CalculationEngineTest | 9 | ✅ 0 falhas |
| ClashVillageTrackerTest | 6 | ✅ 0 falhas |
| ImporterTest | 7 | ✅ 0 falhas |
| JavaHttpSupercellPlayerClientTest | 8 | ✅ 0 falhas |
| SnapshotReconcilerTest | 4 | ✅ 0 falhas |
| ExcelReportWriterTest | 1 | ✅ 0 falhas |
| ValidationTest | 5 | ✅ 0 falhas |
| VillageControllerTest | 4 | ✅ 0 falhas |
| VillageDashboardServiceRulesTest | 6 | ✅ 0 falhas |
| VillageDashboardServiceTest | 5 | ✅ 0 falhas |
| OfficialProfileServiceTest | 4 | ✅ 0 falhas |
| SnapshotImportServiceTest | 1 | ✅ 0 falhas |
| **Subtotal (suíte padrão / standard suite)** | **64** | **✅ 0 falhas** |
| SnapshotRepositoryIT (integração SQL Server) | 1 | ✅ 0 falhas (executado com `CLASH_DB_PASSWORD`) |
| **Total** | **65** | **✅ 0 falhas, 0 erros, 0 ignorados** |

### Atualização 2026-11-03 — E2 (SQL Server) + Opção A

Novos testes adicionados nesta etapa:
- `OfficialProfileServiceTest` (4): leitura da coleta mais recente do `fetch-player`; ausência de coleta/metadata; escolha da coleta mais recente sem inventar arrays; resposta rejeitada mantém evidência sem perfil.
- `SnapshotImportServiceTest` (1): persistência desligada é reportada e o import falha com estado claro (não finge gravar).
- `SnapshotRepositoryIT` (1, integração): roda só quando `CLASH_DB_PASSWORD` existe; aplica Flyway, importa o snapshot histórico, prova idempotência e lê de volta no SQL Server real (`observed_date=2026-10-19`, 123 entidades). Verificado também por `sqlcmd`.

Comandos:
```powershell
.\mvnw.cmd -s ci-settings.xml -o test                                   # 64 testes
$env:CLASH_DB_PASSWORD='<senha>'; .\mvnw.cmd -s ci-settings.xml -o -Dtest=SnapshotRepositoryIT test   # +1 integração
```

> Nota: o `maven-surefire-plugin` não executa classes `*IT` na fase `test` (convenção reservada ao failsafe); por isso a integração roda por invocação explícita. O banco físico foi criado e migrado nesta máquina.

## 7. Resultado da compilação/build

`.\mvnw.cmd -s ci-settings.xml clean verify` → **BUILD SUCCESS**. Empacotamento gera `clash-village-tracker.jar` (CLI, ~22 MB, sem Spring) e `clash-village-tracker-web.jar` (~38 MB, servidor). Avisos do `maven-shade-plugin` sobre recursos sobrepostos (LICENSE/NOTICE/MANIFEST) são pré-existentes e inofensivos.

## 8. Falhas encontradas e como foram corrigidas

- **NullPointerException em 6 testes de `VillageDashboardServiceRulesTest`:** as fixtures de snapshot iniciais omitiam o array `queue`, que o `SnapshotValidator` exige (ERROR `QUEUE_MISSING`), fazendo a análise retornar `FAILED` com `calculation=null`. **Correção:** adicionado `"queue": []` ao template de snapshot do teste. Após a correção, os 6 testes passaram. Nenhuma regra de negócio ou dado real foi alterado para fazer os testes passarem.

## 9. Testes não executados e motivos

- Nenhum teste foi marcado como ignorado/pulado.
- Não foi usado `@SpringBootTest`/`MockMvc` com contexto completo para evitar dependência de `spring-boot-starter-test`, que poderia não estar em cache offline; os controladores foram testados por instanciação direta, cobrindo o contrato sem subir o contexto.
- Nenhuma chamada real à API da Supercell foi feita (respeitando a política do projeto); o cliente HTTP já é coberto por servidor local simulado.

## 10. Pendências e riscos

- Persistência PostgreSQL (E2/E3), histórico por período e comparação temporal (E4) continuam planejados e fora do escopo desta validação.
- `fullCertified` permanece `false` por catálogo `legacy-unversioned` e durações desconhecidas — comportamento correto e testado.
- O build de testes depende de conectividade ao Maven Central via `ci-settings.xml` quando o cache offline não tem o `junit-platform-launcher`; documentado no README.
