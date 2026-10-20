# Clash Village Tracker — Project README

> **Bilingual documentation:** Portuguese (Brazil) first; full English version after the language separator. Both sections describe the same product, rules, and implementation goals. Do not execute their instructions twice.

## Estado executável atual / Current executable status (2026-10-20)

O projeto já possui um MVP Java 17/Maven executável. O Maven Wrapper fixa Maven 3.9.9, portanto não é necessário ter `mvn` instalado globalmente:

```powershell
.\mvnw.cmd clean verify
java -jar target\clash-village-tracker.jar report --snapshot data\snapshot_2026-10-19.json --catalog data\upgrade_steps.json --gold-pass-boost 0.20 --output output --offline
java -jar target\clash-village-tracker.jar validate-catalog --catalog data\upgrade_steps.json
```

A coleta isolada do perfil oficial também está implementada. Ela é online somente quando chamada explicitamente e exige o token no processo atual:

```powershell
$env:CLASH_API_TOKEN = "<seu-token>"
java -jar target\clash-village-tracker.jar fetch-player --tag "#U8J89QP2" --output output
Remove-Item Env:CLASH_API_TOKEN
```

`fetch-player` registra apenas os campos de topo comprovados para o recurso de jogador (`tag`, `name`, `townHallLevel`, `expLevel`, `troops`, `spells`, `heroes`, `heroEquipment`). O conteúdo dos arrays é preservado como JSON opaco. O comando **não** afirma coletar construções, Sobrecargas, filas ou cronômetros, não altera o snapshot manual e salva uma observação histórica em `output/supercell/`. Respostas 401, 403, 404, 429 e 5xx são diferenciadas; o token nunca é escrito em arquivo ou log.

# Português (Brasil) — PT-BR

## 1. Visão geral

**Clash Village Tracker** é uma aplicação Java para analisar e acompanhar o progresso de uma vila de **Clash of Clans** até o estado **FULL absoluto** definido pela versão do catálogo do jogo. O sistema importa evidências de fontes diferentes, mantém histórico, reconcilia níveis, calcula melhorias e Sobrecargas pendentes e produz uma planilha Excel que continua utilizável após a geração. A integração oficial e relatórios JasperReports serão adicionados gradualmente.

O objetivo não é automatizar ações dentro do jogo: a aplicação **não executa upgrades, ataques ou qualquer ação na conta**. Ela lê dados autorizados e cria relatórios e previsões locais.

### Referência inicial da conta

| Atributo | Referência |
|---|---|
| Tag | `#U8J89QP2` |
| Jogador | `jaoo` |
| Vila Principal | Centro de Vila 18 (TH18) |
| Snapshot inicial | `2026-10-19` (histórico; não tempo real) |
| Construtores permanentes | 6 |
| Construtor Goblin | Temporário/opcional; não é o 7º fixo |
| Bônus do Passe Dourado | 20% na fotografia de referência; editável |
| Plataforma de desenvolvimento | Windows / Kiro |
| Stack preferida | Java 17 ou 21 LTS, Maven, Jackson, Apache POI, JUnit 5 |

## 2. Problema e resultado desejado

Hoje os dados estão divididos entre prints do jogo, arquivos Markdown, JSON/CSV e uma planilha Excel. As informações ficam desatualizadas, diferentes instâncias do mesmo prédio podem ter níveis ou Sobrecargas distintos e as durações mudam com atualizações. O sistema deve produzir uma visão consistente e auditável:

- Evolução histórica desde os primeiros níveis até o máximo aplicável ao TH e à versão do catálogo.
- Níveis concluídos, próximos níveis, Sobrecargas e trabalhos ativos.
- Tempo conhecido por etapa, por entidade e por categoria, com e sem bônus de Passe.
- Gargalos e projeções de calendário respeitando filas e construtores.
- Histórico de snapshots e mudanças confirmadas entre coletas.
- Excel bem organizado, fácil de filtrar e preencher, com Dashboard e gráfico dinâmico.
- Cobertura do catálogo e alertas quando houver dados desconhecidos ou conflitantes.

## 3. Escopo funcional

O catálogo deve representar construções defensivas e auxiliares, armadilhas, muros, coletores e armazenamentos, Sobrecargas, heróis, pets, guardiões, tropas, feitiços, máquinas de cerco e atributos de defesas artesanais. O suporte ao TH18 inicial não deve impedir novas versões do jogo ou outros THs.

**FULL permanente** significa concluir níveis convencionais exigidos. **FULL de Sobrecargas** significa concluir cargas válidas e elegíveis das construções. **FULL absoluto** exige ambos e todas as demais categorias declaradas no escopo, sem lacunas impeditivas. O progresso do Dashboard será uma **métrica calculada de etapas catalogadas**, não uma porcentagem oficial da Supercell e não uma medida de horas ou recursos investidos.

## 4. Base de conhecimento e arquivos existentes

| Caminho | Propósito |
|---|---|
| `AGENTS.md` | Contrato obrigatório para os agentes de programação |
| `README.md` | Objetivo, arquitetura, operação e roteiro |
| `PROMPT_PARA_KIRO.md` | Instrução inicial executável para o Kiro |
| `.kiro/steering/clash-cv18.md` | Contexto persistente no editor |
| `.kiro/specs/clash-village-sync/` | Requisitos, design e backlog |
| `docs/00_indice.md` | Índice da documentação por categoria |
| `docs/01_regras_jogo.md` a `docs/14_construcoes_auxiliares.md` | Regras e catálogo funcional de tipos de entidades |
| `docs/15_regras_automacao.md` a `docs/19_testes_e_pendencias.md` | Modelo de dados, integração, fontes, testes e lacunas |
| `data/snapshot_2026-10-19.json` | Foto histórica inicial com procedência |
| `data/upgrade_steps.json` e `.csv` | Etapas, tempos e fontes (desconhecidos são `null`) |
| `controle_cv18_jaoo_2026-10-19.xlsx` | Planilha de referência e comparação visual |

**Nota:** o Kiro deve inspecionar o formato real dos arquivos antes de supor campos e nomes de propriedades; este documento define conceitos, não garante o esquema de cada JSON.

## 5. Modelo de domínio e regras essenciais

- `currentLevel` é o último nível **concluído**; `targetLevel` é o nível em construção/pesquisa, se houver. Uma melhoria 3→4 em andamento não significa nível atual 4.
- `observedRemainingSeconds` é um cronômetro efetivamente observado. `baseDurationSeconds` é o tempo sem bônus. `estimatedDurationSeconds` é a estimativa da próxima etapa após regras de bônus. Nunca aplique 20% novamente no cronômetro observado.
- Cada construção repetida deve ter identidade própria quando a fonte permitir. Sem IDs confiáveis, use agregação explícita; nunca fabrique identidade.
- Sobrecargas são etapas individuais após o nível permanente máximo, podem variar por categoria e ser invalidadas quando atualizações liberam novos níveis. Preserve histórico.
- Laboratório e Casa dos Pets possuem filas próprias; não divida suas durações por seis construtores. Trabalhos de construção, heróis e guardiões utilizam capacidade aplicável do jogo.
- Valores `null` ou `UNKNOWN` indicam falta de evidência, **não zero**. Nunca obtenha um total exato misturando etapas sem duração conhecida.
- O limite permanente é de seis construtores; o Goblin Builder não altera essa capacidade fixa.

**Guardiões de referência:** Logger 5 FULL, Longshot 5 FULL, Smasher com nível concluído 3 e melhoria para 4 em andamento no snapshot. Essas informações são históricas e devem ser reconciliadas em novas coletas.

## 6. Fontes de dados e estratégia de confiança

1. **API oficial da Supercell:** `https://developer.clashofclans.com/`. Consumir somente campos comprovados pelo contrato/resposta real; **não presumir** que expõe construções, Sobrecargas, filas ou cronômetros.
2. **Exportação da vila pelo jogo:** fonte potencial para construções e estados; o parser só pode ser desenvolvido depois de receber e estudar uma amostra genuína, preservando o payload original.
3. **Snapshot manual/importação Excel:** complementação de campos ausentes ou confirmação visual.
4. **Catálogo de níveis/tempos:** tabelas versionadas com fonte, data, versão e confiabilidade. Consultas auxiliares: `https://www.clash.ninja/` e referências listadas em `docs/18_fontes.md`.

Cada campo atualizado deve guardar procedência (`source`, `observedAt`, `confidence`, `reason`). Uma fonte que omite um campo não deve apagar o valor conhecido vindo de outra. Divergências produzem itens de reconciliação, não uma escolha silenciosa.

## 7. Arquitetura sugerida

Utilize **Java 17/21 + Maven**. Organização proposta, adaptável ao código existente:

```text
src/main/java/.../
  cli/                     # CLI e opções de execução
  config/                  # configuração, segredos e parâmetros
  domain/                  # entidades e regras centrais
  catalog/                 # tetos, custos, etapas e versionamento
  importer/                # snapshots JSON/CSV e entrada manual
  integration/supercell/   # cliente HTTP oficial, isolado
  integration/villageexport/ # parser do export, quando houver amostra
  reconciliation/          # merge e conflitos por campo
  calculation/             # progresso, durações e agendamento
  history/                 # snapshots imutáveis e diffs
  report/excel/            # Excel e gráficos
  report/jasper/           # opcional, em fase posterior
src/test/java/.../         # testes de domínio, importação e Excel
```

Bibliotecas recomendadas: Jackson, Java `HttpClient`, Apache POI, JUnit 5 e SLF4J. **JasperReports, Spring Boot e banco de dados não são dependências obrigatórias do MVP.** Evite classes vazias apenas para espelhar diretórios.

Pipeline lógico: `load → validate → normalize → reconcile → calculate → render → persist`. As etapas de domínio não devem depender de Apache POI nem de endpoints HTTP.

## 8. MVP e interface por linha de comando

Primeiro entregue **uma aplicação offline real** que leia os arquivos existentes, calcule etapas e produza relatório sem acessar a API. Estes comandos são **contratos planejados, não comandos existentes até serem implementados**:

```bash
java -jar clash-village-tracker.jar report \
  --snapshot data/snapshot_2026-10-19.json \
  --catalog data/upgrade_steps.json \
  --gold-pass-boost 0.20 \
  --output output/

java -jar clash-village-tracker.jar validate-catalog \
  --catalog data/upgrade_steps.json

# Futuro, somente após validar endpoint e credenciais:
java -jar clash-village-tracker.jar sync \
  --tag "#U8J89QP2" --output output/
```

Saídas desejadas: `output/report.xlsx`, `output/normalized-snapshot.json`, `output/diff.json`, `output/pending-validation.json` e logs resumidos. Salve resultados em caminhos versionados ou identificados por data; não substitua os snapshots históricos.

## 9. Regras de cálculo e previsões

Para cada elemento, identifique `currentLevel → currentLevel+1 → ... → maxLevel`; encontre a duração de **cada transição** no catálogo aplicável e some somente os valores confirmados. Para Sobrecargas, adicione etapas elegíveis por construção, respeitando pré-requisitos.

- **Sem Passe:** soma das durações-base verificadas.
- **Com Passe:** aplicar bônus configurado apenas às etapas futuras elegíveis, com arredondamento validado.
- **Upgrade ativo:** cronômetro observado permanece íntegro; futuras etapas ainda usam catálogo.
- **Duração desconhecida:** exibir subtotal conhecido + lista de etapas desconhecidas, jamais total exato ou data exata.
- **Construtores:** dias-construtor são soma de carga; dividir por seis é limite inferior ideal, não calendário final. Simulação futura deve respeitar precedências e vagas.
- **Laboratório/Pets:** filas independentes; não paralelizar artificialmente.

O gráfico pode usar `completedApplicableSteps / totalApplicableSteps` para o escopo catalogado. Reporte separadamente a cobertura dos dados e nunca declare FULL absoluto se há entidades aplicáveis desconhecidas. Compare também progresso permanente e Sobrecargas de forma independente.

## 10. Excel: experiência esperada

A planilha deve ser fácil de entender e editar, não apenas tecnicamente correta. Abas recomendadas: **Dashboard, Visão Geral, Defesas, Sobrecargas, Coletores, Armadilhas, Muros, Heróis, Pets, Guardiões, Laboratório, Fila Ativa, Etapas por Nível, Histórico, Pendências, Configurações e Fontes**. É permitido reorganizar para simplificar a navegação, sem omitir categorias.

- Dashboard com pizza `Concluído × Restante`, indicadores por categoria, progresso permanente, progresso de SC, última coleta e cobertura.
- Fórmulas e gráficos vinculados aos dados de entrada, refletindo mudanças sem reconstrução manual.
- Campos editáveis visualmente distintos das fórmulas; filtros, cabeçalhos congelados, larguras e unidades adequadas.
- Estado separado por instância e agrupamentos consolidados quando necessário.
- Informações de fonte, versão e `A VALIDAR` claramente identificadas.
- **Não** sobrescrever alterações manuais sem estratégia documentada de merge entre coletas.
- Testar abertura, fórmulas e gráfico em Excel compatível.

## 11. Plano de entrega

**Fase 1 — Offline:** build Maven, modelos, leitura JSON/CSV, normalização, cálculos e XLSX editável com dashboard; testes e CLI operante.

**Fase 2 — API:** autenticação por ambiente, consulta ao endpoint real, DTOs comprovados, tratamento de falhas/limites e testes HTTP simulados.

**Fase 3 — Exportação da vila:** receber uma amostra real, registrar formato e versão, criar parser, validar dados estruturais e reconciliar com fontes existentes.

**Fase 4 — Histórico/planejamento:** snapshots comparáveis, diferenças, estimativas por fila, simulador de construtores, relatórios Jasper opcionais.

O agente deve implementar a primeira fase antes de ampliar escopo. Uma proposta de arquitetura sem código executável não constitui conclusão do MVP.

## 12. Testes e definição de pronto

Testes mínimos: upgrade ativo não avança nível concluído; duas estruturas iguais têm SC diferentes; atualização de máximos; `null` diferente de zero; desconto não aplicado duas vezes; importação idempotente; ausência de campo não apaga informação; seis construtores; filas independentes; histórico imutável; Excel válido com fórmulas e gráfico; FULL bloqueado por cobertura insuficiente.

Uma entrega só está pronta quando **compila**, os **testes relevantes foram executados**, há uma amostra de saída verificável e as limitações estão documentadas. Reporte testes não executados como **não executados**. Guarde tokens apenas em variáveis de ambiente (`CLASH_API_TOKEN`) ou meios seguros, nunca no Git.

## 13. Próximos passos no Kiro

1. Abrir o diretório que contém este README no Kiro.
2. Ler `AGENTS.md` e `.kiro/steering/clash-cv18.md`.
3. Abrir `PROMPT_PARA_KIRO.md` e enviar seu conteúdo ao chat do agente.
4. Permitir inspeção do projeto e execução dos passos técnicos autorizados.
5. Verificar `mvn test` e o Excel gerado antes de avançar para API.

## 14. Estrutura de diretórios proposta para o MVP

A raiz do repositório deve continuar simples e permitir abertura no Kiro e IntelliJ IDEA.

```text
clash-village-tracker/
├── .kiro/
│   ├── steering/clash-cv18.md
│   └── specs/clash-village-sync/
├── docs/                      # Conhecimento de domínio e referências
├── data/                      # Snapshot inicial e catálogos
├── src/main/java/.../         # Aplicação Java
├── src/main/resources/        # Modelos de relatório e configuração pública
├── src/test/java/.../         # Testes de domínio e integração local
├── output/                    # Relatórios gerados; normalmente ignorados pelo Git
├── AGENTS.md
├── README.md
├── PROMPT_PARA_KIRO.md
├── .gitignore
└── pom.xml
```

Não substitua a pasta `docs/` pela planilha. A documentação define as regras, o catálogo organiza os valores versionados e o código implementa as operações.

## 15. Padrão de identificação de entidades

Os nomes mostrados pelo jogo podem mudar com idioma, atualização ou contexto.

| Campo | Exemplo | Função |
|---|---|---|
| `entityKey` | `air_defense` | Tipo estável |
| `instanceKey` | `air_defense:3` | Instância física, quando comprovada |
| `displayNamePtBr` | `Defesa Aérea` | Exibição em português |
| `displayNameEn` | `Air Defense` | Exibição em inglês |
| `category` | `DEFENSE` | Agrupamento |
| `sourceType` | `GAME_EXPORT` | Origem do dado |

Uma estrutura agregada não deve receber `instanceKey` inventado. Nesse caso, `count` e `distribution` representam o que a fonte realmente informou.

## 16. Exemplo conceitual de snapshot

O formato abaixo é uma **proposta do modelo interno da aplicação**, não a resposta da API oficial nem o formato exportado pelo jogo.

```json
{
  "playerTag": "#U8J89QP2",
  "townHallLevel": 18,
  "observedAt": "2026-10-19T19:00:00-03:00",
  "sourceType": "MANUAL",
  "entities": [
    {
      "entityKey": "smasher",
      "category": "GUARDIAN",
      "currentLevel": 3,
      "targetLevel": 4,
      "upgradeStatus": "IN_PROGRESS",
      "observedRemainingSeconds": 849600,
      "confidence": "CONFIRMED"
    }
  ]
}
```

A data/hora e o tempo numérico acima são ilustrativos; para dados reais, utilize os registros validados em `data/`. Não copie esse exemplo como se fosse um snapshot adicional confirmado.

## 17. Exemplo conceitual do catálogo de etapas

O catálogo armazena uma etapa por transição de nível ou de Sobrecarga.

```json
{
  "catalogVersion": "example-2026-10",
  "townHallLevel": 18,
  "steps": [
    {
      "entityKey": "example_defense",
      "upgradeType": "PERMANENT",
      "fromLevel": 12,
      "toLevel": 13,
      "baseDurationSeconds": null,
      "sourceUrl": null,
      "verificationStatus": "PENDING"
    }
  ]
}
```

`null` significa duração desconhecida. Para uma etapa real, registre referência válida, versão e data de consulta. Não calcule o tempo de uma transição inexistente ou não verificada.

## 18. Modelo de estado por instância

As construções repetidas podem estar em níveis diferentes e com SC diferentes.

Exemplo conceitual de quatro Defesas Aéreas:

| Instância | Nível normal | SC1 | SC2 | Situação |
|---|---|---|---|---|
| A | máximo | sim | não | Falta SC2 |
| B | máximo | não | não | Faltam SC1 e SC2 |
| C | máximo | sim | sim | FULL individual |
| D | máximo | ? | ? | Informação desconhecida |

Quando a origem fornecer apenas agrupamento, o modelo deverá informar quantidade por estágio em vez de identificar A, B, C e D sem evidência.

A representação individual ou agregada é parte da procedência do estado e deve aparecer de maneira compreensível na planilha.

## 19. Nível concluído versus melhoria iniciada

Uma transição precisa preservar estados diferentes:

| Campo | Exemplo | Observação |
|---|---|---|
| `currentLevel` | `3` | Último nível concluído |
| `targetLevel` | `4` | Nível em melhoria |
| `upgradeStatus` | `IN_PROGRESS` | Fila ativa |
| `observedRemainingSeconds` | valor observado | Não reaplicar Passe |
| `observedAt` | data/hora real | Indica validade do saldo |

A aplicação não deve deduzir uma conclusão confirmada apenas pela passagem do tempo. Uma reconsulta posterior pode confirmar o novo nível.

## 20. Tratamento de Sobrecargas após atualizações

O sistema precisa lidar com mudanças de catálogo entre patches.

- Uma defesa pode receber novo nível permanente após já estar com SC concluídas.
- As SC antigas podem deixar de ser aplicáveis, conforme as regras da atualização.
- O snapshot histórico deve continuar mostrando o que existia naquele período.
- O relatório atual deve refletir a versão de regras selecionada.
- O denominador do percentual de progresso poderá aumentar depois de uma atualização.
- A aplicação deverá explicar mudanças abruptas de percentual quando alterado o catálogo.
- O histórico de evolução não deve tratar automaticamente a perda de elegibilidade de SC como erro do jogador.

Não compare percentuais de versões diferentes sem indicar a diferença do catálogo utilizado.

## 21. Regras para estimativas de tempo

As durações devem ser calculadas por etapa, somando os tempos-base em segundos.

Para cada item, apresente:

1. Nível concluído e nível máximo.
2. Todas as transições pendentes.
3. Tempo-base de cada transição, quando confirmado.
4. Tempo com bônus do Passe parametrizado.
5. Tempo restante de etapa ativa quando observado.
6. Subtotal de tempos conhecidos.
7. Quantidade de etapas sem duração.
8. Total exato somente se todas as etapas necessárias forem conhecidas.

Não use uma duração média para preencher etapas faltantes sem rotular claramente como estimativa secundária.

## 22. Regras de paralelismo e filas

O tempo acumulado por etapa não deve ser confundido com o calendário.

| Fila ou recurso | Tratamento inicial |
|---|---|
| Construtores permanentes | Capacidade configurada em 6 |
| Goblin Builder | Temporário, opcional, dependente da disponibilidade |
| Laboratório | Pesquisa em fila independente |
| Casa dos Pets | Melhoria em fila independente |
| Heróis e Guardiões | Respeitar mecânica e recursos utilizados no jogo |
| Muros | Sem tempo de espera quando melhoria instantânea for aplicável |

O motor deve permitir cenários: ideal (sem períodos ociosos), disponibilidade configurada e eventual uso de itens mágicos. Somente declare uma data como exata se houver evidência suficiente.

## 23. Cálculo do Dashboard

O jogador pediu para acompanhar **toda a progressão desde os níveis iniciais**, não somente as melhorias adicionadas pelo CV18.

A primeira métrica será:

```text
progressPercent = completedApplicableSteps / totalApplicableSteps * 100
```

O cálculo deve usar o catálogo vigente e entidades do escopo definido. O mesmo indicador não representa percentual de recursos nem de tempo.

O Dashboard deve exibir, separadamente, `permanentProgress`, `superchargeProgress`, `overallCataloguedProgress`, `coverage` e `unknownItems`.

**Condição de FULL:** progresso catalogado completo **e** cobertura suficiente para certificar todas as categorias exigidas, sem pendências desconhecidas.

## 24. Dashboard e navegação de usuário

A planilha deve ser orientada à consulta rápida.

- No topo: jogador, CV, data da coleta, versão do catálogo e configuração do Passe.
- Cards: percentual acumulado, percentual restante, cobertura, etapas pendentes e data estimada quando disponível.
- Gráfico de pizza: concluído × restante, alimentado por células dinâmicas.
- Resumo por categoria: progresso, quantidade e tempo conhecido restante.
- Ações manuais: campos em cor própria, com legenda de como preencher.
- Links internos: acesso direto a Defesas, Laboratório, Heróis, Pets, Guardiões e Pendências.
- Fila Ativa: mostrar cronômetros observados junto da validade da leitura.
- Problemas de dados: lista de campos desconhecidos, conflitos ou incompatibilidades.

Evite uma aba com centenas de colunas sem navegação ou filtros.

## 25. Abas e colunas recomendadas

| Aba | Principais informações |
|---|---|
| Dashboard | progresso, SC, cobertura, gráfico e alertas |
| Visão Geral | resumo de todas as categorias |
| Defesas | tipo, quantidade, nível concluído, nível máximo, SC |
| Sobrecargas | etapa individual por construção/agrupamento |
| Recursos | coletores, armazenamentos e suas cargas |
| Armadilhas e Muros | quantidades por nível e custo |
| Heróis | nível, alvo, transições e duração |
| Pets | nível, alvo, transições e duração |
| Guardiões | Logger, Smasher, Longshot e fila ativa |
| Laboratório | tropas, feitiços e máquinas de cerco |
| Fila Ativa | cronômetro observado, origem e data |
| Etapas por Nível | todas as transições e tempos |
| Histórico | diferença entre snapshots |
| Pendências | lacunas, conflitos e validações |
| Configurações | bônus, versão, parâmetros e fontes |

Os nomes poderão evoluir, mas o conteúdo funcional não pode ser omitido.

## 26. Regras de preenchimento e atualização do Excel

A planilha deve continuar útil mesmo sem uma sincronização online.

- Permita ao jogador atualizar manualmente níveis e SC.
- Evite que a edição manual modifique fórmulas.
- Diferencie entrada manual, dado importado e dado calculado.
- Ao gerar novo relatório, preserve ou reconcilie alterações manuais de acordo com regras explícitas.
- Não use endereço fixo como `L5:L124` se novas linhas podem ser adicionadas; prefira Tabelas do Excel ou referências dinâmicas.
- Valide porcentagens e duração em formato regional compatível.
- Garanta que fórmulas e gráficos recalculam ao editar dados.
- Registre snapshot e data da versão produzida no arquivo.

A apresentação final deve ser testada em planilha real, não apenas inspecionando o XML interno.

## 27. Gestão de catálogo e atualização do jogo

Clash of Clans recebe novos níveis e alterações de tempos periodicamente.

O catálogo deve conter:

- Identificador da versão.
- Centro de Vila aplicável.
- Nome técnico de cada entidade.
- Máximo por entidade.
- Duração-base de cada etapa confirmada.
- Custos quando conhecidos.
- Elegibilidade de Sobrecargas.
- URL e data da consulta.
- Status de verificação.

O sistema deve oferecer comando para validar inconsistências, duplicidades e etapas inexistentes antes de gerar previsões.

## 28. Estratégia de fontes e precisão

A origem dos dados depende do campo.

- API oficial: consultar apenas os endpoints e propriedades disponibilizados de fato.
- Exportação da vila: obter uma amostra real antes de criar seu parser.
- Prints ou edição manual: úteis para preencher itens não cobertos por outras fontes.
- Catálogo de referência: base para máximos e durações, sujeito a atualização.
- Histórico anterior: útil para detectar progresso, mas não para confirmar estado atual sozinho.

Campos faltantes na API não devem apagar valores já conhecidos. Divergências devem aparecer em `Pendências` com as fontes envolvidas.

## 29. Integração com API oficial

A futura integração deverá ser um adaptador isolado, desacoplado do domínio.

- Documentação: `https://developer.clashofclans.com/`.
- Credenciais: variável de ambiente `CLASH_API_TOKEN` ou outro mecanismo seguro.
- Tratamento: timeouts, respostas HTTP, tentativas limitadas e logs sem segredo.
- DTOs: implementados com base no contrato real, não em payloads inventados.
- Persistência: manter a resposta ou representação normalizada com proveniência.
- Cobertura: gerar diagnóstico de quais campos o endpoint realmente forneceu.
- Testes: utilizar fixtures sintéticas e simulação HTTP local.

Esta integração não é requisito para executar o MVP offline.

## 30. Importação da exportação da vila

A exportação disponibilizada dentro do jogo será tratada como possível fonte complementar.

Antes de codificar o parser:

1. Solicitar ou receber uma amostra verdadeira exportada pelo jogador.
2. Identificar o tipo de conteúdo e sua codificação.
3. Inspecionar o schema real.
4. Mapear campos disponíveis e ausentes.
5. Propor DTOs e regras de versão.
6. Implementar um parser que preserve o payload original.
7. Criar testes de regressão com amostras autorizadas e sanitizadas.
8. Não presumir que temporizadores ou todas as SC estarão disponíveis.

Evite automações não autorizadas que manipulem o aplicativo do jogo.

## 31. Reconciliação e prioridade por campo

Uma coleta pode apresentar dados parciais ou divergentes.

A reconciliação deve avaliar:

- Campo e semântica exatos.
- Data/hora da observação.
- Versão do catálogo.
- Origem do dado.
- Confiança e disponibilidade.
- Alterações manuais posteriores.

O resultado deverá preservar informações válidas, produzir uma lista de conflitos e permitir revisão posterior. A simples ausência de um campo não representa valor nulo intencional nem exclusão do registro.

## 32. Histórico de evolução

A cada execução, a aplicação poderá registrar uma nova fotografia da vila.

- Snapshot anterior permanece imutável.
- Novo snapshot recebe data e identificador.
- Diferenças são classificadas por entidade e campo.
- Upgrades confirmados são registrados separadamente dos previstos.
- Mudanças de versão do jogo aparecem no relatório.
- Um mesmo arquivo importado duas vezes não deve duplicar etapas.
- O histórico pode alimentar futuras curvas de progresso e comparações mensais.

O usuário deseja executar a rotina manualmente; agendamento só será implementado se solicitado posteriormente.

## 33. Estrutura de arquivos de saída

Exemplo desejado após o comando `report`:

```text
output/
└── 2026-10-19_190000/
    ├── village-report.xlsx
    ├── normalized-snapshot.json
    ├── report-summary.json
    ├── pending-validation.json
    └── snapshot-diff.json
```

Os nomes são sugestões a implementar. Não considere a existência dessa árvore como fato antes de gerar os arquivos. Saídas devem registrar o cenário de cálculo e a versão do catálogo utilizada.

## 34. Logging e tratamento de erros

Cada execução deve permitir descobrir rapidamente o que aconteceu.

- Log de inicialização, versão da aplicação e arquivos de entrada.
- Quantidade de entidades lidas e processadas.
- Itens desconhecidos e conflitos encontrados.
- Tempo de processamento e caminho dos arquivos de saída.
- Exceções legíveis, com causa preservada.
- Status de execução `SUCCESS`, `PARTIAL_SUCCESS` ou `FAILED`.
- Código de saída diferente de zero para falhas relevantes.
- Ausência de token não deve impedir a execução offline.

Não escreva tokens da API ou dados confidenciais em logs.

## 35. Organização de testes

Testes unitários devem focar domínio e cálculos sem necessitar de internet.

Casos representativos:

- Bebê Dragão 9→12 gera três etapas.
- Torre do Mago 13→17 gera quatro etapas.
- Rainha 105→110 gera cinco etapas.
- Smasher em 3→4 continua nível concluído 3.
- Quatro Defesas Aéreas podem ter SC em estágios diferentes.
- Cronômetro já reduzido não recebe novo desconto.
- Duração ausente impede um total exato.
- Reimportação não duplica unidades.
- Excel contém referências e gráfico válidos.
- FULL absoluto exige cobertura completa.

Os níveis dos exemplos são cenários de teste ou instantâneos históricos, não leituras em tempo real.

## 36. Marcos de desenvolvimento e entregas

| Marco | Resultado demonstrável |
|---|---|
| M1 | Maven compilando, CLI respondendo, testes iniciais |
| M2 | Importador offline e modelo normalizado |
| M3 | Catálogo validado e motor de cálculo |
| M4 | Planilha com Dashboard e gráfico funcional |
| M5 | Snapshot/diff e pendências |
| M6 | Adaptador da API com contrato real, quando autorizado |
| M7 | Parser da exportação com amostra real |
| M8 | Simulador avançado e JasperReports opcional |

Conclua os marcos em ordem, mas evite criar infraestrutura sem uso concreto. Registre a situação real de cada marco.

## 37. Como compilar e executar no Windows

Após o código ser criado, a documentação deve fornecer comandos reais, por exemplo:

```powershell
mvn clean test
mvn package
java -jar target/clash-village-tracker.jar report --snapshot data/snapshot_2026-10-19.json --catalog data/upgrade_steps.json --gold-pass-boost 0.20 --output output/
```

Os nomes de JAR e argumentos devem ser ajustados aos que realmente forem implementados. Documente as versões de Java/Maven exigidas, a pasta de saída e como resolver erros comuns.

## 38. Padrão de commits e colaboração

Repositório sugerido: `clash-village-tracker`.

- Use commits pequenos e descritivos.
- Prefira mensagens em inglês: `feat: add snapshot importer`, `fix: preserve active upgrade level`.
- Mantenha `main` estável.
- Não versionar tokens, `.env`, arquivos temporários ou resultados não destinados a publicação.
- Arquivos `.kiro/`, `docs/`, `AGENTS.md` e exemplos seguros de dados devem ser preservados.
- Revisões de catálogo precisam citar fonte e data da mudança.

## 39. Limitações conhecidas do ponto de partida

O projeto começa com um snapshot baseado em prints e planilha de referência, não com uma API de edifícios comprovadamente completa.

- A lista real de cada instância defensiva pode estar parcialmente agregada.
- Algumas durações do catálogo estão desconhecidas.
- Defesas artesanais podem ter atributos não confirmados.
- A quantidade de SC aplicáveis depende da versão do jogo.
- O horário dos cronômetros antigos não deve ser tratado como tempo restante atual.
- A planilha inicial pode exigir revisão de fórmulas e usabilidade.

O objetivo do MVP é exibir essas limitações com clareza, não escondê-las para apresentar um relatório aparentemente final.

## 40. Definição final de sucesso

O projeto cumpriu sua primeira missão quando o usuário consegue executar localmente um comando Java que:

1. Lê dados do snapshot e do catálogo.
2. Identifica corretamente as entidades e suas melhorias.
3. Calcula progresso e durações conhecidas.
4. Preserva dados e histórico.
5. Produz uma planilha legível com gráfico atualizável.
6. Sinaliza dados que precisam ser verificados.
7. Fornece uma base sustentável para API, exportação e JasperReports posteriores.

**Integridade dos dados, transparência do cálculo e facilidade de uso valem mais que um percentual bonito sem base confiável.**

---

# English (EN) — Project documentation

## 1. Overview

**Clash Village Tracker** is a Java application for analyzing and tracking a **Clash of Clans** village toward **fully maxed status** under a chosen version of the game's catalog. It imports evidence from multiple sources, preserves history, reconciles levels, calculates outstanding upgrades and Supercharges, and produces a usable Excel workbook. Official API integration and JasperReports will be introduced incrementally.

This is **not a game automation bot**: it **does not perform upgrades, attacks, or actions on the account**. It reads authorized data and generates local analysis and reports.

### Initial account reference

| Attribute | Reference |
|---|---|
| Player tag | `#U8J89QP2` |
| In-game name | `jaoo` |
| Home Village | Town Hall 18 (TH18) |
| Initial snapshot | `2026-10-19` (historical, not live) |
| Permanent builders | 6 |
| Goblin Builder | Temporary/optional; not a permanent seventh builder |
| Gold Pass boost | 20% at the reference snapshot; configurable |
| Development platform | Windows / Kiro |
| Preferred stack | Java 17 or 21 LTS, Maven, Jackson, Apache POI, JUnit 5 |

## 2. Problem and intended result

The available information is currently distributed across game screenshots, Markdown, JSON/CSV and a spreadsheet. Information gets stale, individual building instances may have different levels or Supercharges, and durations change across game updates. The application should provide an auditable and consistent view:

- Cumulative progression from early levels to the maximum applicable to a Town Hall and catalog version.
- Completed levels, next levels, Supercharges and active upgrades.
- Trusted upgrade time per step, item and category, with and without Gold Pass.
- Bottlenecks and calendar estimates that respect queues and builder availability.
- Historical snapshots and confirmed differences between observations.
- A clear, filterable, editable Excel workbook with Dashboard and live chart.
- Catalog coverage and warnings about unknown or conflicting data.

## 3. Functional scope

The catalog must cover defensive and auxiliary buildings, traps, walls, resource collectors and storages, Supercharges, heroes, pets, guardians, troops, spells, siege machines and crafted-defense attributes. Supporting initial TH18 data should not prevent future game updates or other Town Hall levels.

**Permanent maxed status** means all conventional levels are completed. **Supercharge maxed status** means all currently valid eligible Supercharge steps are complete. **Fully maxed status** means both and every other category in the declared scope, with no blocking data gaps. Dashboard progress is a **calculated cataloged-step metric**, not an official Supercell percentage and not a measure of elapsed time or resources spent.

## 4. Knowledge base and existing files

| Path | Purpose |
|---|---|
| `AGENTS.md` | Mandatory agent-development contract |
| `README.md` | Goals, architecture, operations and roadmap |
| `PROMPT_PARA_KIRO.md` | Starting instruction to send to the Kiro agent |
| `.kiro/steering/clash-cv18.md` | Persistent editor context |
| `.kiro/specs/clash-village-sync/` | Requirements, design and task backlog |
| `docs/00_indice.md` | Category documentation index |
| `docs/01_regras_jogo.md` through `docs/14_construcoes_auxiliares.md` | Game rules and functional entity types |
| `docs/15_regras_automacao.md` through `docs/19_testes_e_pendencias.md` | Data model, integration, sources, tests and gaps |
| `data/snapshot_2026-10-19.json` | Provenance-bearing historical baseline |
| `data/upgrade_steps.json` and `.csv` | Upgrade steps, durations and sources (`null` for unknowns) |
| `controle_cv18_jaoo_2026-10-19.xlsx` | Reference workbook and visual baseline |

**Note:** Kiro must inspect the files' real structure before assuming property names or DTO fields. This README defines concepts; it does not assert a specific JSON schema.

## 5. Domain model and critical rules

- `currentLevel` means the last **completed** level; `targetLevel` is the level currently being upgraded/researched, if any. An active 3→4 upgrade does not imply current level 4.
- `observedRemainingSeconds` is a genuinely observed countdown; `baseDurationSeconds` is the original time before boosts; `estimatedDurationSeconds` is a projected future duration after applicable rules. Never apply another 20% discount to observed countdowns.
- Model repeated buildings as separate instances when the data source provides stable identity. Otherwise, explicitly represent aggregation and never invent instance identifiers.
- Supercharges are individual steps after the permanent max, vary by category and can be invalidated by game updates that add normal levels. Preserve historical observations.
- The Laboratory and Pet House have separate queues; do not divide their upgrade times by six builders. Building, hero and guardian work consumes the appropriate game capacity.
- `null` or `UNKNOWN` means missing evidence, **not zero**. Do not claim an exact total while required steps have unknown durations.
- Permanent builder capacity is six; the Goblin Builder does not permanently increase that value.

**Guardian baseline:** Logger 5 maxed, Longshot 5 maxed, and Smasher with completed level 3 and an active upgrade to 4 in the snapshot. These are historical facts that require reconciliation on later runs.

## 6. Sources and confidence strategy

1. **Official Supercell API:** `https://developer.clashofclans.com/`. Use only fields verified in real contracts/responses; **never assume** building levels, Supercharges, queues or timers are available.
2. **In-game village export:** a potential source for buildings and states; implement its parser only after inspecting a genuine export sample, retaining the raw input.
3. **Manual snapshot/Excel import:** fills missing fields or provides visual confirmation.
4. **Upgrade catalog:** versioned max-level and upgrade-time data with sources, retrieval dates and confidence. Supplementary references: `https://www.clash.ninja/` and `docs/18_fontes.md`.

Every updated field needs provenance (`source`, `observedAt`, `confidence`, `reason`). An omitted API field must not erase a known value from another source. Conflicts become reconciliation issues rather than silent choices.

## 7. Suggested architecture

Use **Java 17/21 + Maven**, adapting the following logical packages to existing implementation:

```text
src/main/java/.../
  cli/                       # entry points and command options
  config/                    # configuration and credentials
  domain/                    # entities and business rules
  catalog/                   # maxima, step durations and versions
  importer/                  # JSON/CSV and manual inputs
  integration/supercell/     # isolated official HTTP client
  integration/villageexport/ # in-game export parser after real sample
  reconciliation/            # field-level merges and conflicts
  calculation/               # progress, durations, scheduling
  history/                   # immutable snapshots and diffs
  report/excel/              # Excel workbook and charts
  report/jasper/             # optional, later
src/test/java/.../           # domain, import and workbook tests
```

Recommended dependencies: Jackson, Java `HttpClient`, Apache POI, JUnit 5, and SLF4J. **JasperReports, Spring Boot, and a database are not MVP prerequisites.** Do not create placeholder classes just to fill directories.

Logical pipeline: `load → validate → normalize → reconcile → calculate → render → persist`. Domain rules must remain independent of Apache POI and HTTP adapters.

## 8. Offline MVP and CLI contract

First deliver a **working offline application** that loads available files, calculates steps and generates a workbook without calling the API. The commands below are **proposed contracts and are not functional until implemented**:

```bash
java -jar clash-village-tracker.jar report \
  --snapshot data/snapshot_2026-10-19.json \
  --catalog data/upgrade_steps.json \
  --gold-pass-boost 0.20 \
  --output output/

java -jar clash-village-tracker.jar validate-catalog \
  --catalog data/upgrade_steps.json

# Future only, after verifying endpoints and credentials:
java -jar clash-village-tracker.jar sync \
  --tag "#U8J89QP2" --output output/
```

Desired artifacts: `output/report.xlsx`, `output/normalized-snapshot.json`, `output/diff.json`, `output/pending-validation.json` and concise logs. Date or version outputs, never overwrite historical snapshots.

## 9. Calculation rules and estimates

For each item, enumerate `currentLevel → currentLevel+1 → ... → maxLevel`. Look up **each transition's** duration in the appropriate catalog and sum only trusted values. Add eligible Supercharge steps per individual building while respecting prerequisites.

- **Without Gold Pass:** sum verified base durations.
- **With Gold Pass:** apply the configurable boost only to eligible future work, with validated rounding.
- **Active upgrade:** preserve the observed countdown; use the catalog for subsequent steps.
- **Unknown duration:** show known subtotal and unknown steps, never an exact total or finish date.
- **Builders:** builder-days describe workload; dividing by six is an ideal lower bound, not a realistic schedule by itself. Later simulations must respect dependencies and capacity.
- **Laboratory/Pets:** separate queues, never artificially parallelize.

The progress chart can use `completedApplicableSteps / totalApplicableSteps` over the cataloged scope. Report data coverage separately and never claim full completion when applicable entities remain unknown. Permanent progress and Supercharge progress must also be shown separately.

## 10. Excel user experience

The workbook should be easy to navigate and edit, not merely technically correct. Suggested sheets: **Dashboard, Overview, Defenses, Supercharges, Collectors, Traps, Walls, Heroes, Pets, Guardians, Laboratory, Active Queue, Upgrade Steps, History, Validation Issues, Settings and Sources**. Reorganize if a simpler UX is demonstrated, without dropping any category.

- Dashboard pie chart `Completed × Remaining`, category metrics, permanent progression, SC progress, last observation and coverage.
- Formulas and charts linked to source cells so user edits recalculate them.
- Editable inputs visibly distinct from computed formulas; filters, frozen headers, readable widths and units.
- Instance-level state and consolidated aggregation when needed.
- Clear source, version, and `NEEDS VERIFICATION` labels.
- **Do not** overwrite manual corrections without a documented merge strategy.
- Validate workbook opening, formulas and chart in a compatible Excel client.

## 11. Delivery roadmap

**Phase 1 — Offline:** Maven build, domain models, JSON/CSV loading, normalization, calculations, editable XLSX with Dashboard, tests and usable CLI.

**Phase 2 — Official API:** environment-based credentials, verified endpoint/fields, DTOs based on real responses, failure/rate-limit handling and mocked HTTP tests.

**Phase 3 — Village export:** obtain genuine sample, document format/version, build parser, validate structure and reconcile with other sources.

**Phase 4 — History/planning:** snapshot comparisons, diffs, queue-aware estimates, builder scheduling simulation and optional Jasper reports.

Complete Phase 1 before expanding scope. Architecture notes without runnable code are not an MVP deliverable.

## 12. Testing and definition of done

Minimum tests: active upgrade does not advance completed level; identical building types have different SC states; updated max-level catalogs; `null` is not zero; boost not applied twice; idempotent import; missing source fields do not erase known data; six builders; independent queues; immutable history; valid Excel formulas/chart; full completion blocked by unknown required data.

A deliverable is done only when it **builds**, relevant **tests have been executed**, output samples are verifiable, and limitations are documented. Label tests that were not run as **not run**. Store tokens only in environment variables (`CLASH_API_TOKEN`) or secure configuration, never in Git.

## 13. Next steps in Kiro

1. Open the repository root in Kiro.
2. Read `AGENTS.md` and `.kiro/steering/clash-cv18.md`.
3. Open `PROMPT_PARA_KIRO.md` and send its text to the agent.
4. Allow repository inspection and authorized technical operations.
5. Verify `mvn test` and the generated Excel workbook before moving to API integration.

## 14. Suggested directory structure for the MVP

Keep the repository root simple and usable from both Kiro and IntelliJ IDEA.

```text
clash-village-tracker/
├── .kiro/
│   ├── steering/clash-cv18.md
│   └── specs/clash-village-sync/
├── docs/                      # Domain knowledge and references
├── data/                      # Initial snapshot and catalogs
├── src/main/java/.../         # Java application
├── src/main/resources/        # Report templates and public configuration
├── src/test/java/.../         # Domain and local integration tests
├── output/                    # Generated reports; normally ignored by Git
├── AGENTS.md
├── README.md
├── PROMPT_PARA_KIRO.md
├── .gitignore
└── pom.xml
```

Do not replace `docs/` with the workbook. Documentation defines the rules, catalogs store versioned values, and code implements the operations.

## 15. Entity identity standards

In-game labels may change across languages, patches, and contexts.

| Field | Example | Purpose |
|---|---|---|
| `entityKey` | `air_defense` | Stable entity type |
| `instanceKey` | `air_defense:3` | Physical instance, if verifiable |
| `displayNamePtBr` | `Defesa Aérea` | Portuguese label |
| `displayNameEn` | `Air Defense` | English label |
| `category` | `DEFENSE` | Grouping |
| `sourceType` | `GAME_EXPORT` | Data provenance |

Do not fabricate an `instanceKey` for an aggregated record. Instead, represent exactly what the source provides with counts and distributions.

## 16. Conceptual snapshot example

The structure below is a **proposal for the application's internal model**, not an official API response or an in-game export format.

```json
{
  "playerTag": "#U8J89QP2",
  "townHallLevel": 18,
  "observedAt": "2026-10-19T19:00:00-03:00",
  "sourceType": "MANUAL",
  "entities": [
    {
      "entityKey": "smasher",
      "category": "GUARDIAN",
      "currentLevel": 3,
      "targetLevel": 4,
      "upgradeStatus": "IN_PROGRESS",
      "observedRemainingSeconds": 849600,
      "confidence": "CONFIRMED"
    }
  ]
}
```

The timestamp and numeric duration above are illustrative. Use verified values in `data/` for actual reports. Never import this conceptual example as an additional confirmed village snapshot.

## 17. Conceptual upgrade-step catalog

The catalog stores one step per permanent level transition or Supercharge stage.

```json
{
  "catalogVersion": "example-2026-10",
  "townHallLevel": 18,
  "steps": [
    {
      "entityKey": "example_defense",
      "upgradeType": "PERMANENT",
      "fromLevel": 12,
      "toLevel": 13,
      "baseDurationSeconds": null,
      "sourceUrl": null,
      "verificationStatus": "PENDING"
    }
  ]
}
```

`null` means the duration is unknown. Real steps require a valid reference, version, and consultation date. Never assign timing to a missing or unverified transition.

## 18. Per-instance state model

Repeated buildings can have different permanent and SC levels.

Conceptual example of four Air Defenses:

| Instance | Normal level | SC1 | SC2 | State |
|---|---|---|---|---|
| A | max | yes | no | SC2 pending |
| B | max | no | no | SC1 and SC2 pending |
| C | max | yes | yes | Individually maxed |
| D | max | ? | ? | Unknown information |

When the source provides aggregates only, record counts per stage instead of identifying A, B, C, or D without evidence.

Individual versus aggregated representation is part of the data provenance and must remain understandable in the workbook.

## 19. Completed level versus active upgrade

An upgrade transition must preserve separate states:

| Field | Example | Meaning |
|---|---|---|
| `currentLevel` | `3` | Last completed level |
| `targetLevel` | `4` | Level currently being upgraded |
| `upgradeStatus` | `IN_PROGRESS` | Active queue |
| `observedRemainingSeconds` | observed value | Never reapply Gold Pass |
| `observedAt` | actual timestamp | Establishes timer freshness |

Do not infer confirmed completion solely from elapsed time. A later observation can confirm the new level.

## 20. Handling Supercharges after updates

The system must handle catalogs changing between game patches.

- A defense may receive a new permanent level after finishing its Supercharges.
- Old SC may become inapplicable according to the update rules.
- Historical snapshots must preserve what existed at the time.
- Current reports must use the selected version of the rules.
- Progress denominators may increase after a game update.
- Explain major percentage changes caused by catalog updates.
- Do not interpret a patch-induced SC eligibility change as a player mistake.

Never compare progress percentages across catalog versions without labeling the difference.

## 21. Duration-estimation rules

Calculate each step separately and sum base durations as seconds.

For every item, show:

1. Completed and maximum levels.
2. All remaining transitions.
3. Confirmed base duration per transition.
4. Duration with the configurable Gold Pass boost.
5. Active-step remainder if observed.
6. Known-duration subtotal.
7. Number of steps with unknown durations.
8. Exact total only when all required durations are known.

Do not fill missing stages with averages unless explicitly labeled as a secondary estimate.

## 22. Concurrency and queue rules

Accumulated work duration is not calendar duration.

| Queue or resource | Initial treatment |
|---|---|
| Permanent builders | Configured capacity of 6 |
| Goblin Builder | Temporary and optional, based on availability |
| Laboratory | Independent research queue |
| Pet House | Independent upgrade queue |
| Heroes and Guardians | Follow actual resource consumption in game |
| Walls | No waiting period if instant upgrade is applicable |

Support ideal continuous-operation scenarios, configured availability, and optional future magic-item usage. Only claim exact completion dates when sufficient evidence exists.

## 23. Dashboard calculation

The user wants **cumulative progression from the earliest levels**, not only new upgrades introduced at TH18.

The first metric is:

```text
progressPercent = completedApplicableSteps / totalApplicableSteps * 100
```

The calculation must use the selected catalog version and defined entity scope. It does not represent the proportion of resources or time invested.

The Dashboard must separately show `permanentProgress`, `superchargeProgress`, `overallCataloguedProgress`, `coverage`, and `unknownItems`.

**Maxed-out condition:** complete cataloged progress **and** sufficient coverage to certify all required categories, with no unknown required fields remaining.

## 24. Dashboard and user navigation

Design the workbook for quick, practical access.

- Header: player, TH, observation date, catalog version, and Gold Pass settings.
- KPI cards: cumulative percentage, remaining percentage, coverage, outstanding stages, and estimated date when supported.
- Pie chart: completed versus remaining, driven by dynamic cells.
- Category summary: progress, counts, and known remaining work.
- Manual actions: editable fields with their own style and an explanatory legend.
- Internal links: direct access to Defenses, Laboratory, Heroes, Pets, Guardians, and Validation Issues.
- Active Queue: observed timers and freshness of observations.
- Data issues: unknown fields, conflicts, and incompatibilities.

Avoid a giant sheet with hundreds of poorly navigable columns.

## 25. Recommended sheets and columns

| Sheet | Main information |
|---|---|
| Dashboard | progress, SC, coverage, chart, alerts |
| Overview | summary of all categories |
| Defenses | type, count, completed/max levels, SC |
| Supercharges | individual step per building/aggregate |
| Resources | collectors, storage, and charges |
| Traps and Walls | level distribution and costs |
| Heroes | current level, target, transitions, duration |
| Pets | current level, target, transitions, duration |
| Guardians | Logger, Smasher, Longshot, active queue |
| Laboratory | troops, spells, siege machines |
| Active Queue | observed timers, provenance, date |
| Upgrade Steps | transitions and individual timings |
| History | snapshot differences |
| Validation Issues | gaps, conflicts, verification |
| Settings | bonuses, versions, options, sources |

Sheet names can evolve, but none of the functional scope may be dropped.

## 26. Workbook editing and refresh rules

The workbook must remain useful without any online sync.

- Allow users to manually update levels and SC.
- Prevent manual entry cells from accidentally overwriting formulas.
- Distinguish manual input, imported data, and calculated fields.
- Reconcile or preserve manual changes when a new report is generated, according to an explicit policy.
- Avoid hard-coded ranges such as `L5:L124` if new records can be added; prefer Excel Tables or dynamic references.
- Validate percentages and durations with locale-compatible number formats.
- Confirm charts and formulas recalculate after input changes.
- Display the snapshot and catalog version in the generated workbook.

Validate final presentation in an actual workbook viewer rather than inspecting only internal XML.

## 27. Catalog lifecycle and game updates

Clash of Clans periodically receives new levels and upgrade-time changes.

A catalog should include:

- Version identifier.
- Applicable Town Hall level.
- Technical entity key.
- Maximum level per entity.
- Verified base duration for each stage.
- Costs where known.
- Supercharge eligibility.
- Source URL and consultation date.
- Verification status.

Provide a catalog-validation command to detect conflicts, duplicates, and impossible transitions before producing estimates.

## 28. Sources and precision strategy

Choose data sources by individual field.

- Official API: use only actually available endpoints/properties.
- Village export: obtain a genuine sample before writing its parser.
- Screenshots/manual corrections: fill information unavailable through other sources.
- Reference catalogs: max levels and durations; subject to game updates.
- Previous history: useful for detecting progress but insufficient on its own to confirm current state.

Absent API fields must not overwrite known values. Conflicts belong in Validation Issues with both sources.

## 29. Official API integration

Future API integration should be a separate adapter, independent of the domain layer.

- Documentation: `https://developer.clashofclans.com/`.
- Credentials: `CLASH_API_TOKEN` environment variable or another secure method.
- Error handling: timeouts, HTTP responses, limited retry logic, and redacted logs.
- DTOs: based on actual API contracts, never invented payloads.
- Storage: preserve response metadata or normalized provenance.
- Coverage: report which fields the endpoint actually provided.
- Tests: synthetic fixtures and local HTTP simulations.

API integration is not required for the offline MVP.

## 30. Village export importer

Treat in-game village export as a possible complementary source.

Before implementing the parser:

1. Obtain a genuine user-exported sample.
2. Identify content type and encoding.
3. Inspect its real schema.
4. Map available and missing fields.
5. Propose DTOs and schema-version handling.
6. Implement a parser that preserves the original payload.
7. Add regression tests using authorized, sanitized samples.
8. Do not assume timers or every SC field is available.

Avoid unauthorized automation that manipulates the game application.

## 31. Field-level reconciliation

Collections may be partial or contradictory.

Reconciliation should consider:

- The exact field and its semantics.
- Observation timestamp.
- Catalog version.
- Data provenance.
- Confidence and availability.
- Subsequent manual edits.

Preserve valid information, generate explicit conflicts, and allow later review. A missing field does not imply deliberate nullification or entity removal.

## 32. Progress history

Each execution may capture a new historical snapshot.

- Older snapshots remain immutable.
- Each new snapshot has its own timestamp and identity.
- Differences are classified by entity and field.
- Confirmed upgrades are distinct from forecasts.
- Game-version changes are reported.
- Importing the same file twice does not duplicate upgrades.
- History can support future progress charts and monthly comparisons.

The user prefers manual execution; scheduling is a future optional extension only if requested.

## 33. Output-file structure

Suggested output for the `report` command:

```text
output/
└── 2026-10-19_190000/
    ├── village-report.xlsx
    ├── normalized-snapshot.json
    ├── report-summary.json
    ├── pending-validation.json
    └── snapshot-diff.json
```

These are proposed names, not claims of existing files. Every artifact should identify its scenario and catalog version.

## 34. Logging and error handling

Every execution must be auditable.

- Startup log, app version, and input paths.
- Read/processed entity counts.
- Unknown items and conflicts.
- Processing time and output artifact paths.
- Understandable exceptions with preserved root cause.
- Run status: `SUCCESS`, `PARTIAL_SUCCESS`, or `FAILED`.
- Nonzero exit code for significant failure.
- Offline runs must not require an API token.

Never log API tokens or sensitive data.

## 35. Test organization

Unit tests should focus on domain rules without requiring network access.

Representative cases:

- Baby Dragon 9→12 generates three stages.
- Wizard Tower 13→17 generates four stages.
- Archer Queen 105→110 generates five stages.
- Smasher 3→4 keeps completed level 3.
- Four Air Defenses can have different SC stages.
- Already-discounted observed timers are not discounted again.
- Missing stage duration prevents an exact total.
- Reimports do not duplicate instances.
- Workbook formulas and charts remain valid.
- Absolute maxed status requires full coverage.

Example levels refer to test scenarios or historical snapshots, never live readings.

## 36. Development milestones and deliverables

| Milestone | Demonstrable result |
|---|---|
| M1 | Maven compiles, CLI responds, initial tests |
| M2 | Offline importer and normalized domain model |
| M3 | Validated catalog and calculation engine |
| M4 | Workbook with working Dashboard and chart |
| M5 | Snapshots/diffs and validation issues |
| M6 | API adapter based on real contract, when authorized |
| M7 | Game-export parser based on real sample |
| M8 | Advanced planner and optional JasperReports |

Implement milestones in order without building unused infrastructure. Track actual completion status.

## 37. Building and running on Windows

Once code exists, document verified commands such as:

```powershell
mvn clean test
mvn package
java -jar target/clash-village-tracker.jar report --snapshot data/snapshot_2026-10-19.json --catalog data/upgrade_steps.json --gold-pass-boost 0.20 --output output/
```

Update JAR names and arguments to match real implementation. Document required Java/Maven versions, output location, and common troubleshooting steps.

## 38. Git commits and collaboration

Suggested repository name: `clash-village-tracker`.

- Use small, descriptive commits.
- Prefer English messages such as `feat: add snapshot importer` or `fix: preserve active upgrade level`.
- Keep `main` stable.
- Never commit tokens, `.env`, temporary files, or outputs that should not be public.
- Keep `.kiro/`, `docs/`, `AGENTS.md`, and safe examples under version control.
- Catalog revisions should include a source and timestamp.

## 39. Known limitations of the starting state

The project begins from screenshots and a reference workbook, not a verified complete building API.

- Individual defensive-building instances may still be aggregated.
- Several catalog durations are unknown.
- Crafted defenses may have unconfirmed attribute levels.
- Applicable SC stages depend on game version.
- Historical screenshots' timers must not be presented as current remaining times.
- The initial workbook may need formula and usability improvements.

The MVP should disclose these limitations rather than hiding them behind a falsely definitive report.

## 40. Final definition of success

The project's initial objective is achieved when the user can locally execute a Java command that:

1. Reads snapshot and catalog data.
2. Correctly identifies entities and remaining upgrades.
3. Calculates cumulative progress and known durations.
4. Preserves data and history.
5. Produces a readable workbook with an updating chart.
6. Surfaces information requiring verification.
7. Provides a maintainable foundation for the future API, village export, and JasperReports.

**Data integrity, transparent calculations, and usability matter more than an attractive but unsupported percentage.**
