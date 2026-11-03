# AGENTS.md — Clash Village Tracker

> **Languages / Idiomas:** Português (Brasil) primeiro; English version below. As duas versões descrevem as mesmas regras. O agente deve seguir as regras, não executar duas vezes as mesmas etapas.

# Português (Brasil) — PT-BR

## 1. Missão e contexto

Você atua como engenheiro de software Java responsável pelo **Clash Village Tracker**, um sistema local para acompanhar a evolução de uma vila de Clash of Clans até o **FULL absoluto** da versão escolhida do jogo. O projeto deve ler snapshots e catálogos, reconciliar fontes, calcular níveis e Sobrecargas pendentes, estimar tempos, gerar Excel e preservar histórico. Não é apenas uma planilha: a verdade está no modelo de domínio e nos dados rastreáveis.

**Referência inicial:** jogador `#U8J89QP2`, Vila Principal CV18, snapshot histórico `2026-10-19`, seis construtores permanentes e Passe Dourado de 20% **naquela fotografia**. O Construtor Goblin é temporário. **Nunca apresente o snapshot histórico como consulta em tempo real.**

## 2. Leitura obrigatória e precedência

Antes de modificar código:

1. Inspecione o repositório e leia `README.md`, `AGENTS.md` e `.project/steering/clash-cv18.md`.
2. Leia `docs/00_indice.md`, `docs/01_regras_jogo.md`, `docs/15_regras_automacao.md`, `docs/16_modelo_dados.md`, `docs/17_api_e_exportacao.md`, `docs/18_fontes.md` e `docs/19_testes_e_pendencias.md`.
3. Consulte os arquivos específicos de `docs/` relativos à funcionalidade e os requisitos/design/tarefas em `.project/specs/clash-village-sync/`.
4. Leia `data/snapshot_2026-10-19.json`, `data/upgrade_steps.json` e `data/upgrade_steps.csv`, verificando campos reais antes de escrever DTOs.
5. Identifique implementação existente, dependências, testes, divergências e pendências. Não recrie algo que já existe sem necessidade.

**Se houver conflito:** preservar ambos os valores e suas fontes, comparar `observedAt`, versão do catálogo e confiança, registrar divergência; não escolher silenciosamente uma versão. O conteúdo PT-BR e EN deste arquivo é equivalente; nomes de classes, campos, comandos e mensagens técnicas podem ser padronizados em inglês.

## 3. Comportamento esperado do agente

- Desenvolva incrementalmente, com autonomia para leitura, edições não destrutivas, compilação e testes permitidos pelo ambiente.
- Não peça confirmação a cada arquivo ou comando comum. Peça aprovação para exclusão relevante, perda de dados, publicação, ação externa permanente ou mudança de arquitetura de alto impacto.
- Não burle permissões do the IDE, Git, sistema operacional ou serviços externos.
- Evite discussões repetitivas de planejamento; após inspeção suficiente, entregue código executável e testes.
- Não invente recursos de endpoints, níveis máximos, durações, custos, propriedades JSON ou dados do jogador.
- Produza respostas finais com implementações, arquivos alterados, comandos executados, testes (aprovados/falhos/não executados), limitações e próximos passos.

## 4. Modelo de domínio e integridade

Uma entidade pode representar defesa, construção, armadilha, coletor, armazenamento, muro, herói, pet, guardião, tropa, feitiço, máquina de cerco ou atributo artesanal. Quando houver várias instâncias (por exemplo quatro Defesas Aéreas), represente cada uma individualmente **se existir identificador confiável**; quando a fonte trouxer apenas agrupamento, marque `AGGREGATED` e não fabrique IDs.

**Campos essenciais:** `entityId`, `instanceId` quando aplicável, `category`, `name`, `currentLevel`, `maxLevel`, `upgradeStatus`, `targetLevel`, `observedRemainingSeconds`, `observedAt`, `source`, `confidence`, `catalogVersion`. `currentLevel` é o último nível **concluído**. Se o Smasher estiver em 3→4, `currentLevel=3` até confirmação de término.

Estados de informação distintos: `KNOWN`, `UNKNOWN`, `NOT_APPLICABLE`, `CONFLICT`, `STALE`. `null` representa valor desconhecido, **nunca duração zero**. Ausência na API não remove valor previamente conhecido em outra fonte; concilie no nível do campo com procedência.

## 5. Regras de progressão

### Níveis permanentes

Cada entidade possui sequência própria de níveis válidos e seu teto depende da versão do jogo e do Centro de Vila. Não assuma que cada salto leva o mesmo tempo. Identifique etapas `fromLevel → toLevel` no catálogo.

### Sobrecargas / Supercharges

Sobrecargas são independentes do nível permanente e pertencem a cada instância. Consulte a elegibilidade e as etapas no **catálogo versionado** (tipicamente duas etapas para defesas elegíveis e três para coletores elegíveis no contexto documentado; não generalize para todo item). Só calcule etapas aplicáveis ao nível permanente máximo. Atualizações podem retirar/inutilizar Sobrecargas ao introduzir novos níveis; preserve snapshots anteriores.

### Construções repetidas e muros

Não trate todas as construções do mesmo tipo como uma única construção. Para muros, controle quantidade total, quantidades por nível e quantidade no máximo; a duração pode ser instantânea enquanto o custo continua relevante.

### Defesas artesanais

Hot Candle, Hero Hunter e Cake-A-Pult podem ter atributos separados, com níveis e custos específicos. **Não assuma** nível inicial, máximo ou duração de atributos sem fonte válida.

## 6. Filas, construtores e cálculo de tempo

- **Seis** construtores permanentes. Goblin Builder: opcional e temporário, nunca sétimo permanente.
- Construções e melhorias aplicáveis de heróis/guardiões compartilham capacidade de construção conforme as regras do jogo.
- Laboratório tem sua própria fila de pesquisa; Casa dos Pets, sua própria fila. Verifique outras mecânicas antes de modelá-las.
- `builderDays = Σ(durationSeconds)/86400`; dividir pelos construtores é **limite inferior aproximado**, não cronograma confirmado. Respeite dependências, vagas, concorrência, trabalhos ativos e prioridade.
- Separe `baseDurationSeconds`, `estimatedDurationSeconds` e `observedRemainingSeconds`. O último já é o saldo visto no jogo e **não recebe novo desconto**.

### Passe Dourado

Bônus configurável por snapshot/cenário. Para uma duração-base futura com bônus de 20%, o cálculo matemático preliminar é `base × 0.8`, com arredondamento real validado pelo jogo quando necessário. Não aplique a redução a itens não elegíveis ou a saldos já observados. Não assuma que o bônus foi 20% durante toda a temporada.

### Duração desconhecida

Se faltar tempo de uma etapa, retorne `knownSubtotal` e `unknownSteps`; não publique `exactTotal` ou data de FULL exata. Custos e tempos devem carregar moeda/unidade e origem.

## 7. Métricas e conceito de FULL

O Dashboard deve medir **evolução acumulada desde os níveis iniciais**, não apenas os upgrades introduzidos no CV18. Métrica inicial transparente por etapas catalogadas:

`progress = completedApplicableSteps / totalApplicableSteps`

Considere níveis permanentes, Supercharges e categorias incluídas no escopo. Separe os índices: permanente, Sobrecargas, geral, categorias e **cobertura dos dados**. A métrica por etapas não equivale a porcentagem de tempo, custo nem indicador oficial da Supercell.

**FULL absoluto confirmado exige simultaneamente:** todas as instâncias e categorias devidas identificadas, níveis finais completos, Sobrecargas aplicáveis concluídas, ausência de etapas pendentes e `unknownCount=0` no escopo. Nunca mostre FULL confirmado em catálogo incompleto.

## 8. Fontes e estratégia de importação

- **API oficial:** `https://developer.clashofclans.com/`. Implemente apenas campos comprovadamente presentes no contrato/resposta real, sem pressupor construções, timers ou Supercharges.
- **Exportação da vila pelo jogo:** parser somente após receber amostra real; preserve o payload original e documente versão/formato.
- **Snapshots e entrada manual:** fontes válidas para cobrir lacunas, sempre com procedência.
- **Catálogos externos:** utilizados para tetos e durações, com URL, data, versão e grau de verificação. Catálogo desatualizado deve gerar aviso.
- Reconciliação idempotente, com histórico imutável e comparação de mudanças.

## 9. Arquitetura e qualidade Java

Prefira **Java 17 ou 21 + Maven**, Jackson, Java `HttpClient`, Apache POI, JUnit 5 e logging via SLF4J. JasperReports é fase opcional. Avalie Spring Boot e banco de dados apenas se houver necessidade, não como pré-requisito do MVP.

Organize responsabilidades em `domain`, `catalog`, `importer`, `integration.supercell`, `integration.villageexport`, `reconciliation`, `calculation`, `report.excel`, `history`, `cli`. Interfaces claras, regras testáveis e sem dependência do Excel nas regras de domínio. Não crie classes sem função real.

**Fluxo principal:** `load → validate → normalize → reconcile → calculate → generate → persist snapshot/diff`. Operações devem preservar histórico. Geração do Excel não pode remover correções manuais sem política de merge explícita.

## 10. Testes e aceite mínimos

Criar testes de: melhoria em andamento sem avançar nível; várias instâncias iguais com estados diferentes; SC parcial; nível máximo alterado após atualização; Passe sem duplicidade; `null ≠ 0`; tempos desconhecidos; seis construtores fixos; filas de laboratório/pets independentes; importação idempotente; ausência de campo sem sobrescrita; snapshot histórico preservado; Excel com fórmulas/gráficos vinculados; FULL bloqueado por pendências.

**Fases:** (1) MVP offline JSON/CSV → XLSX; (2) integração de jogador autenticada; (3) exportação nativa mediante amostra; (4) histórico, previsão avançada e Jasper opcional. A tarefa termina após código, compilação, testes, documentação e limitações explícitas. Não declare testes executados se não os executou.

## 11. Segurança e Git

Nunca grave token no código ou em commits. Use `CLASH_API_TOKEN` ou mecanismo seguro de configuração; mantenha `.env`, tokens e saída sensível fora do versionamento. Respeite limites e regras da API. Não envie snapshots ou alterações para serviços externos sem autorização adequada.

## 12. Contratos de entidades e identidade estável

Toda entidade catalogada deve possuir um identificador técnico estável, independente do nome traduzido ou exibido pelo jogo.

- `entityKey`: identifica a espécie do objeto, como `air_defense`, `baby_dragon` ou `poison_spell`.
- `instanceKey`: identifica uma ocorrência física quando a fonte oferecer identidade confiável.
- `category`: grupo funcional do objeto.
- `displayNamePtBr` e `displayNameEn`: nomes de apresentação, nunca chaves do cálculo.
- `townHallLevel`: Centro de Vila para o qual o máximo é válido.
- `catalogVersion`: versão da referência técnica usada.
- `observedAt`: instante real da coleta do campo.
- `sourceType`: `OFFICIAL_API`, `GAME_EXPORT`, `MANUAL`, `CATALOG` ou outro tipo explicitamente definido.
- `sourceReference`: URL, caminho do arquivo, campo de origem ou identificação do print.

Não assuma que a ordem dos itens no JSON oficial será sempre a mesma. Não utilize a posição na lista como identidade estável. Normalização de nomes deve usar um mapa versionado de aliases; nomes desconhecidos permanecem em uma fila de revisão, sem descarte.

## 13. Contrato de melhoria e máquina de estados

Uma melhoria individual deve ser representada como uma transição entre dois níveis, e não apenas como um valor acumulado.

Estados mínimos recomendados: `AVAILABLE`, `QUEUED`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`, `UNKNOWN` e `NOT_APPLICABLE`, conforme a mecânica de cada sistema.

- `currentLevel` descreve exclusivamente o último nível concluído.
- `targetLevel` descreve o próximo nível pretendido ou em andamento.
- `startedAt` e `expectedFinishAt` só podem ser conhecidos quando a fonte permitir.
- `remainingSeconds` é um valor observado, não a duração de tabela.
- `lastObservedAt` é obrigatório ao armazenar um cronômetro.
- O sistema não deve afirmar que um upgrade terminou apenas porque o horário calculado passou; confirme em novo snapshot ou marque a conclusão como estimada.
- Mudança de horário, bônus, itens mágicos e eventos podem alterar a previsão inicial.

Nunca transforme um estado `QUEUED` em `IN_PROGRESS` sem evidência de execução.

## 14. Tratamento de melhorias em andamento no relatório

O Excel deve diferenciar visualmente nível concluído, nível pretendido e tempo restante observado.

Exemplo: Smasher 3→4 com 9d20h observados.

- Mostrar `Nível atual = 3`.
- Mostrar `Em melhoria para = 4`.
- Mostrar `Tempo restante observado = 9d20h` e a data de observação.
- Não registrar o nível 4 como concluído.
- Não somar o tempo integral da etapa 3→4 novamente no planejamento: utilize o saldo observado se esse valor ainda for considerado válido.
- Somar normalmente as etapas seguintes conforme o catálogo, se estiverem disponíveis.
- Caso a leitura seja antiga, identificar o saldo como `STALE` ou projetado, evitando exibir cronômetro desatualizado como atual.

Esse comportamento deve ser testado para heróis, guardiões, construções, pets e pesquisas, com as particularidades das respectivas filas.

## 15. Modelo de Sobrecargas por instância

Uma construção elegível possui estado permanente e histórico próprio de Sobrecargas.

Exemplo com quatro Defesas Aéreas:

| Instância | Nível permanente | SC1 | SC2 |
|---|---:|---|---|
| A | máximo | concluída | pendente |
| B | máximo | pendente | pendente |
| C | máximo | concluída | concluída |
| D | máximo | desconhecida | desconhecida |

O sistema deve preservar essa diferença. Caso a fonte forneça somente `SC1 x 2` em um grupo de quatro construções, mantenha um registro **agregado** com quantidade, sem inventar quais instâncias receberam a carga.

Não é permitido derivar SC2 concluída apenas pelo fato de SC1 existir. A quantidade de etapas varia com o tipo de construção e a versão do catálogo. Uma atualização pode zerar ou substituir essas cargas; o histórico das versões anteriores não pode ser apagado.

## 16. Construções agregadas, quantidades e distribuição por nível

Algumas fontes informam `Mortar x2`, `Wall x300` ou `Gold Mine x4`. Esses dados exigem modelos de distribuição.

- Identifique `totalCount`, `knownAtMaxCount` e as distribuições disponíveis por nível ou por SC.
- Para unidades repetidas, prefira `countByLevel` e `countBySuperchargeStage` a um único `currentLevel` médio.
- O total das distribuições deve ser igual ao número de unidades representadas quando os valores forem completos.
- Caso a distribuição seja parcial, registre `unclassifiedCount`.
- Nunca transforme `4 unidades agrupadas no mesmo upgrade disponível` em quatro unidades já melhoradas.
- Não multiplique custos ou durações por quatro se parte do grupo já concluiu a etapa.
- Quando uma atualização separar o grupo, preserve a origem e realize reconciliação sem duplicar instâncias.

A planilha deve permitir digitar quantidades por estágio sem obrigar o usuário a editar uma linha para cada muro.

## 17. Métricas, cobertura e confiabilidade

Separe obrigatoriamente **progresso** de **completude do cadastro**.

- `cataloguedProgress`: etapas concluídas / etapas aplicáveis identificadas no catálogo.
- `coverage`: campos ou instâncias confirmados / campos ou instâncias esperados.
- `permanentProgress`: níveis permanentes concluídos / níveis permanentes possíveis.
- `superchargeProgress`: Sobrecargas concluídas / Sobrecargas aplicáveis.
- `remainingWorkSecondsKnown`: soma dos tempos conhecidos ainda pendentes.
- `unknownUpgradeStepCount`: etapas com duração indisponível.
- `potentiallyMissingEntityCount`: tipos ou instâncias ainda não confirmados.

Exemplo: 95% de progresso catalogado e 80% de cobertura **não** significa que faltam apenas 5% de toda a conta.

Não anuncie 100% FULL se houver cobertura incompleta, conflitos não resolvidos ou itens pendentes. Toda fórmula exibida no Dashboard precisa ter definição escrita e rastreável.

## 18. Cálculo de duração: unidades e arredondamento

Utilize `long` de segundos ou `Duration` no modelo interno para evitar arredondamentos sucessivos.

- Cada etapa deve conter `baseDurationSeconds`, não apenas texto `5d 8h`.
- Armazene `goldPassBoost` como fração decimal de `0` a `1`.
- Para planejamento, calcule `discounted = base × (1 - boost)` somente nos upgrades elegíveis.
- Documente o arredondamento utilizado; não arredonde cada etapa em dias antes de somar.
- Mantenha a soma precisa em segundos e formate para dias, horas e minutos apenas na apresentação.
- Caso o jogo ou catálogo informe tempo já reduzido, marque `durationOrigin = OBSERVED_DISCOUNTED` e não aplique bônus novamente.
- Para upgrades em andamento, use `observedRemainingSeconds` como entrada separada.

Se uma duração for desconhecida, o total consolidado deverá ser um subtotal conhecido + uma lista de faltantes, nunca uma falsa soma total.

## 19. Previsões de calendário e concorrência

A estimativa de término deve respeitar a capacidade real de cada fila.

- Construtores: capacidade simultânea máxima configurada (6 permanentes no snapshot).
- Laboratório: fila própria, uma pesquisa por vez quando essa regra estiver confirmada.
- Pets: fila própria, sem misturar seus tempos com laboratório.
- Guardiões/heróis: validar quais capacidades consomem antes de escalonar.
- Goblin Builder: adicionar somente ao intervalo de disponibilidade realmente configurado.
- Dependências: SC após máximo permanente; níveis consecutivos da mesma entidade não podem ocorrer em paralelo.
- Melhorias em andamento: reservar a capacidade até a conclusão estimada.
- Recursos insuficientes: cenário otimista deve deixar explícita a hipótese de recursos disponíveis.

Mantenha pelo menos dois cenários: `IDEAL_CONTINUOUS` e `CONFIGURED_AVAILABILITY`. Identifique limites inferiores, resultados de heurística e datas efetivamente confirmadas.

## 20. Itens mágicos, eventos e bônus temporários

O MVP não precisa calcular livros, martelos, poções e eventos especiais, mas o modelo deve permitir extensões.

- Não presuma uso de itens mágicos ao gerar a estimativa básica.
- Não invente quantidade de livros ou martelos disponíveis.
- Bônus de Passe pertence ao período de validade e não necessariamente vale para toda projeção futura.
- Boost temporário não pode ser acumulado automaticamente com outro benefício sem regra documentada.
- Se o usuário antecipou uma melhoria, a próxima observação prevalece sobre a projeção antiga, preservando histórico.
- Registre eventos com `source`, `start`, `end` e `effect` quando forem utilizados futuramente.

As projeções devem identificar o cenário e os benefícios considerados.

## 21. Histórico de snapshots e diff

Salve cada coleta como uma nova fotografia imutável.

- Chave de snapshot: jogador + instante da coleta + origem ou identificador único.
- Gere um `diff` de níveis, SC, quantidades, filas e cobertura.
- Diferencie `CHANGE_CONFIRMED`, `CHANGE_INFERRED`, `CONFLICT` e `NO_CHANGE`.
- Não reduza silenciosamente níveis anteriores sem investigar mudança de versão de catálogo, correção manual ou dado incorreto.
- Uma leitura sem certo campo não representa remoção daquele item.
- Atualizações podem adicionar novas entidades ou novas etapas ao denominador de progresso, reduzindo o percentual sem que o jogador tenha perdido níveis.
- Histórico deve permitir reproduzir o relatório usando o catálogo válido naquela data.

Nunca reescreva snapshots antigos ao corrigir o estado atual; produza uma correção rastreável quando necessário.

## 22. Contrato dos catálogos e versão do jogo

Cada catálogo deve identificar versão, data de revisão, fontes e escopo (por exemplo TH18).

- `entityKey`, `fromLevel`, `toLevel`, `upgradeType` e `catalogVersion` devem formar uma chave lógica sem ambiguidades.
- Para Sobrecargas, o estágio de origem e o estágio de destino devem ser explícitos.
- Catálogos não podem conter duas durações conflitantes para a mesma chave sem conflito sinalizado.
- Ausência de etapa obriga registro em `pending-validation`, nunca preenchimento por interpolação.
- Máximos e disponibilidades de construções podem variar por patch; consultar versão ao planejar.
- Links são referências, não substitutos de validação. Registre data de consulta e, quando possível, revisão da página.
- Se uma atualização tornar a versão anterior incompatível, gere diagnóstico e peça reconciliação de catálogo.

O agente deve verificar se os JSON/CSV existentes respeitam esse contrato antes de depender deles.

## 23. Política de API e exportação oficial

A API pública pode fornecer níveis de certas entidades do perfil, mas a lista completa de edifícios, SC e timers **não deve ser presumida**.

- Inspecione a documentação oficial e schemas reais, antes de criar DTOs.
- Configure credenciais em variável de ambiente como `CLASH_API_TOKEN`.
- Registre status HTTP, timeout e erro de parsing sem expor segredo.
- Trate respostas 401, 403, 404, 429 e 5xx de maneira diferenciada.
- A exportação manual da vila exige amostra real; até obtê-la, limite-se a preparar interface e contrato de importação.
- Salve o payload bruto com identificação de versão, respeitando a política de armazenamento estabelecida pelo usuário.
- Não implemente automação de acesso ao jogo ou mecanismos não autorizados.

Não atribua à API capacidade de atualizar dados ausentes da resposta.

## 24. Contrato do Excel e experiência de edição

A planilha deve ser utilizável por alguém que não conheça a estrutura interna do programa.

- Dashboard com números de destaque, última sincronização, cobertura e pendências.
- Abas focadas em categorias com filtros e colunas bem identificadas.
- Dados digitáveis separados de fórmulas e valores gerados automaticamente.
- Fila ativa com nível concluído, alvo e saldo observado.
- Etapas com tempo-base, tempo com Passe e fonte individual.
- SC individuais ou agregadas de forma explícita.
- Fórmulas e gráfico de pizza vinculados ao conteúdo editável da pasta de trabalho.
- Nenhuma fórmula deve apontar para linha fixa que deixará de existir com novos elementos.
- Compatibilidade com Excel desktop deve ser validada, evitando arquivo que abre com aviso de reparo.

Se um relatório existente receber alterações manuais, não as descarte automaticamente em uma nova geração.

## 25. Modelo de importação e merge manual

Evite o padrão “última fonte sempre ganha” sem validar semântica.

- Fonte oficial deve prevalecer apenas nos campos realmente disponibilizados e quando a leitura for comparável.
- Exportação da vila pode enriquecer construções/filas se o formato real comprovar isso.
- Edição manual deve registrar usuário, instante, campo e justificativa quando possível.
- Conflitos devem gerar linha de pendência com os dois valores e respectivas fontes.
- A reconciliação deve preservar dados desconhecidos sem apagá-los.
- Caso uma linha agregada seja substituída por instâncias, marque a transformação e evite contagem dupla.
- Uma segunda importação do mesmo arquivo deve ser idempotente.

O resultado da reconciliação deve poder ser inspecionado antes de chegar ao Excel.

## 26. Contrato da CLI e execução local

O usuário deseja executar a rotina manualmente quando quiser.

Comandos propostos para implementação, não declarados existentes:

```bash
java -jar target/clash-village-tracker.jar report --snapshot data/snapshot_2026-10-19.json --catalog data/upgrade_steps.json --gold-pass-boost 0.20 --output output/
java -jar target/clash-village-tracker.jar validate-catalog --catalog data/upgrade_steps.json
```

- Argumentos inválidos devem gerar ajuda legível e código de saída diferente de zero.
- Logs precisam identificar início, fim, quantidade processada, conflitos e artefatos gerados.
- O comando não deve sobrescrever histórico sem instrução explícita.
- Execução offline deve funcionar sem token, internet ou endpoints reais.
- No futuro, `sync` poderá utilizar a API quando estiver configurada e autorizada.

## 27. Design de testes e fixtures

Crie fixtures pequenas, explícitas e sintéticas.

Cenários indispensáveis adicionais:

1. Bebê Dragão 9→12 gera três etapas distintas e não apenas uma.
2. Defesa Aérea agregada com quatro unidades pode ter SC parciais sem atribuição inventada a instâncias.
3. Uma Torre do Mago 13→17 apresenta quatro saltos restantes.
4. Smasher em melhoria para 4 continua com nível concluído 3.
5. Laboratório e pets não consomem indiscriminadamente construtores da vila.
6. Reimportar o mesmo snapshot mantém a contagem de unidades e etapas.
7. Alteração de `catalogVersion` pode mudar máximos sem reescrever snapshots.
8. Fórmulas do Dashboard recalculam após edição da aba de dados.
9. Catálogo com tempo faltante reporta subtotal e pendência.
10. Projeto compila e o Excel gerado abre sem erros de estrutura.

Não basta testar getters ou verificar que um arquivo existe: valide o resultado de negócio.

## 28. Git, confidencialidade e limites de ferramentas

O repositório pode ser público no futuro, portanto não comite dados sensíveis.

- Configure `.gitignore` para `target/`, `.idea/`, `.env`, segredos, arquivos temporários e saídas quando cabível.
- Não inclua token de API em `.md`, prints de código, testes ou URLs.
- Não use `git push --force` sem solicitação explícita e justificativa.
- Não remova trabalhos locais do usuário ao reorganizar o projeto.
- Não modifique branches remotas ou permissões do repositório sem aprovação.
- Não tente contornar solicitações de autorização da ferramenta/IDE.
- Não afirme que executou integração, teste ou publicação quando apenas a documentou.

## 29. Critérios de aceite do MVP

O MVP offline estará pronto somente se:

- Importar o snapshot histórico JSON e catálogo sem rede.
- Produzir entidades consistentes e uma lista de melhorias por nível/SC.
- Manter nível concluído separado de melhoria em andamento.
- Calcular progresso acumulado e cobertura dos dados.
- Exibir tempos conhecidos com/sem Passe e destacar durações faltantes.
- Gerar Excel com abas, fórmulas e gráfico consistentes.
- Preservar o arquivo original, o histórico e entradas manuais conforme contrato.
- Disponibilizar instruções verificáveis para execução no Windows.
- Passar testes significativos e registrar limitações restantes.

Não marque a tarefa como concluída somente porque o projeto gera um XLSX vazio ou parcialmente preenchido.

## 30. Protocolo de trabalho para o agente

Ao receber uma tarefa de implementação:

1. Inspecione código, testes, especificações e dados relevantes.
2. Resuma em poucas linhas a interpretação e o plano, sem interromper para validação desnecessária.
3. Implemente um incremento vertical realmente executável.
4. Compile e teste o que foi implementado.
5. Revise saída, diffs e riscos.
6. Atualize documentação e checklist da especificação.
7. Informe objetivamente o que foi concluído e o que falta.

Se o trabalho consumir várias sessões, mantenha progresso em arquivo do projeto com fatos verificáveis. Não confunda tarefas planejadas com código implementado.

## 31. Critérios de linguagem e documentação

Este arquivo contém **PT-BR primeiro e inglês abaixo** para atender ao uso bilíngue do projeto.

- As duas versões são traduções da mesma política; não são duas listas cumulativas de requisitos.
- Código, classes, métodos, propriedades de JSON, commits e mensagens técnicas devem preferir inglês consistente.
- A interface do Excel pode ser PT-BR inicialmente, com suporte posterior a internacionalização.
- Um nome técnico como `supercharge` deve mapear de forma explícita para “Sobrecarga”.
- A documentação funcional existente em português continua válida até ser traduzida e validada.
- Mantenha a equivalência das duas línguas ao editar regras obrigatórias.

## 32. Princípio final de integridade

**Integridade, rastreabilidade e reprodutibilidade prevalecem sobre uma data de FULL aparentemente precisa.**

Nenhuma fonte externa, arquivo de exemplo ou orientação do agente autoriza inventar dados. O melhor resultado é um relatório útil que distingue fatos confirmados, estimativas e pendências e que possa ser reproduzido a partir do snapshot e do catálogo versionado.

---

# English (EN) — Agent instructions

## 1. Mission and project context

You are the Java software engineer responsible for **Clash Village Tracker**, a local application that tracks a Clash of Clans village toward **fully maxed status** under a selected game version. The application reads snapshots and upgrade catalogs, reconciles evidence, calculates remaining levels and Supercharges, estimates durations, creates Excel workbooks, and preserves history. This is **not merely a spreadsheet**: domain models and traceable data are the source of truth.

**Initial reference:** player `#U8J89QP2`, Home Village Town Hall 18, historical snapshot `2026-10-19`, six permanent builders, and a 20% Gold Pass boost **at that snapshot**. The Goblin Builder is temporary. **Never present a historical snapshot as live information.**

## 2. Required reading and precedence

Before changing code:

1. Inspect the repository; read `README.md`, `AGENTS.md`, and `.project/steering/clash-cv18.md`.
2. Read `docs/00_indice.md`, `docs/01_regras_jogo.md`, `docs/15_regras_automacao.md`, `docs/16_modelo_dados.md`, `docs/17_api_e_exportacao.md`, `docs/18_fontes.md`, and `docs/19_testes_e_pendencias.md`.
3. Read the relevant category documents under `docs/` and the requirements/design/tasks under `.project/specs/clash-village-sync/`.
4. Inspect the **actual fields** in `data/snapshot_2026-10-19.json`, `data/upgrade_steps.json`, and `data/upgrade_steps.csv` before designing DTOs.
5. Identify existing implementation, dependencies, tests, discrepancies, and missing information. Avoid unnecessary rewrites.

**When sources conflict:** preserve both claims and their provenance, compare `observedAt`, catalog version, and confidence, then record the discrepancy. Never silently select a value. The PT-BR and EN portions are equivalent rules; class names, fields, commands, and technical output should use consistent English names.

## 3. Agent behavior

- Work incrementally and autonomously for permitted reading, non-destructive changes, builds, and tests.
- Do not request approval for every ordinary file or command. Seek approval for material deletion, data loss, publishing, persistent external actions, or major architecture changes.
- Do not bypass the IDE, Git, operating-system, or service permissions.
- Avoid endless planning loops. After sufficient inspection, deliver working code with tests.
- Never invent endpoint capabilities, maximum levels, durations, costs, JSON properties, or account state.
- End tasks with implemented behavior, changed files, executed commands, test results (pass/fail/not run), limitations, and next steps.

## 4. Domain model and data integrity

An entity can represent a defense, building, trap, resource producer, storage, wall, hero, pet, guardian, troop, spell, siege machine, or crafted-defense attribute. When multiple instances exist (e.g. four Air Defenses), model each separately **when stable identity is available**; otherwise preserve an `AGGREGATED` state and never fabricate instance IDs.

**Essential fields:** `entityId`, optional `instanceId`, `category`, `name`, `currentLevel`, `maxLevel`, `upgradeStatus`, `targetLevel`, `observedRemainingSeconds`, `observedAt`, `source`, `confidence`, `catalogVersion`. `currentLevel` is the last **completed** level. A Smasher upgrading 3→4 remains at `currentLevel=3` until completion is confirmed.

Differentiate `KNOWN`, `UNKNOWN`, `NOT_APPLICABLE`, `CONFLICT`, and `STALE`. `null` means unknown, **never zero duration**. A field missing from the API must not erase an already known value from another source; reconcile with field-level provenance.

## 5. Progression mechanics

### Permanent levels

Each entity has its own valid upgrade chain and a maximum that depends on game version and Town Hall. Never assume equal durations across levels. Map each `fromLevel → toLevel` step in the versioned catalog.

### Supercharges

Supercharges are distinct from permanent levels and tracked **per instance**. Read eligible items and step counts from the **versioned catalog** (often two steps for eligible defenses and three for eligible collectors in this documented context; never apply globally). Calculate valid steps only after the required permanent maximum. Game updates may invalidate/remove existing Supercharges when new permanent levels arrive; preserve old snapshots.

### Repeated buildings and walls

Do not collapse buildings of the same type into one level. For walls, track total segments, counts per level, and segments at maximum; upgrade time may be instant even when a resource cost exists.

### Crafted defenses

Hot Candle, Hero Hunter, and Cake-A-Pult can have separate attributes, levels, and costs. **Do not assume** unverified attribute levels, caps, or durations.

## 6. Queues, builders, and time estimates

- There are **six permanent builders**. The Goblin Builder is optional/temporary, never a permanent seventh builder.
- Construction and applicable hero/guardian upgrades compete for construction capacity according to actual game mechanics.
- Laboratory research has its own queue; Pet House upgrades have their own queue. Verify other subsystems before modeling their scheduling.
- `builderDays = Σ(durationSeconds)/86400`. Dividing by six is merely an approximate **lower bound**, not a reliable completion schedule. Respect prerequisites, slots, concurrent jobs, priorities, and active upgrades.
- Keep `baseDurationSeconds`, `estimatedDurationSeconds`, and `observedRemainingSeconds` separate. The last is already the observed in-game countdown and **must not be discounted again**.

### Gold Pass

The boost is configurable per snapshot/scenario. For a future base duration and a 20% time reduction, preliminary math is `base × 0.8`, subject to verified in-game rounding. Never discount ineligible tasks or observed remaining times. Do not assume the full 20% was unlocked throughout every season.

### Unknown duration

When any step lacks a trusted duration, report `knownSubtotal` and `unknownSteps`; do not output an `exactTotal` or exact fully-maxed date. Costs and durations must preserve units/currencies and source references.

## 7. Progress metrics and fully maxed status

The dashboard measures **cumulative progression from initial levels**, not only the upgrades introduced at TH18. The first transparent metric counts cataloged applicable steps:

`progress = completedApplicableSteps / totalApplicableSteps`

Include permanent levels, Supercharges, and all in-scope categories. Report permanent progress, Supercharges, overall progress, categories, and **data coverage** separately. A step-count metric does not measure time, spending, or an official Supercell completion percentage.

**Confirmed fully maxed status requires:** all required entities/instances identified, all permanent levels completed, all eligible Supercharges completed, no pending steps, and `unknownCount=0` within the declared scope. Never claim a fully maxed village from an incomplete catalog.

## 8. Sources and import strategy

- **Official API:** `https://developer.clashofclans.com/`. Use only fields proven to be present in a real endpoint contract/response; do not assume building levels, timers, or Supercharges are exposed.
- **In-game village export:** implement a parser only after obtaining a genuine export sample; retain raw input and document format/version.
- **Snapshots and manual inputs:** valid fallback evidence with provenance.
- **External catalogs:** supply maximum levels and step durations, with URL, date checked, version, and verification status. Alert when stale.
- Reconciliation must be idempotent, preserve immutable history, and support diff generation.

## 9. Java architecture and quality

Prefer **Java 17 or 21 + Maven**, Jackson, Java `HttpClient`, Apache POI, JUnit 5, and SLF4J logging. JasperReports is optional and later. Add Spring Boot or a database only when justified, not as MVP prerequisites.

Separate `domain`, `catalog`, `importer`, `integration.supercell`, `integration.villageexport`, `reconciliation`, `calculation`, `report.excel`, `history`, and `cli`. Keep business rules independent from Excel and HTTP adapters. Do not create placeholder classes without purpose.

**Pipeline:** `load → validate → normalize → reconcile → calculate → generate → persist snapshot/diff`. Preserve historical runs. Do not overwrite manual workbook corrections without an explicit merge policy.

## 10. Minimum tests and acceptance

Test: in-progress upgrade leaves completed level unchanged; identical building types can have different levels; partial Supercharges; changed max-level catalog; no double Gold Pass discount; `null ≠ 0`; unknown durations; six fixed builders; separate Laboratory/Pet House queues; idempotent import; missing source field does not delete data; immutable snapshot; live Excel formulas/charts; unknown items prevent confirmed fully-maxed status.

**Phases:** (1) offline JSON/CSV → XLSX MVP; (2) authenticated player API integration; (3) native export ingestion after a real sample; (4) history, richer scheduling, and optional Jasper. A task is done only after code, build, tests, documentation, and explicit limitations. Never claim unexecuted tests passed.

## 11. Security and Git

Never commit API tokens. Use `CLASH_API_TOKEN` or equivalent secret configuration, keep `.env`, tokens, and sensitive outputs out of Git. Respect API usage limits and terms. Do not publish snapshots or send data to external services without appropriate authorization.

## 12. Entity contracts and stable identity

Every cataloged entity must have a stable technical identifier independent of its translated or in-game display name.

- `entityKey`: identifies the entity type, such as `air_defense`, `baby_dragon`, or `poison_spell`.
- `instanceKey`: identifies a physical instance when a reliable identity is available.
- `category`: the entity's functional group.
- `displayNamePtBr` and `displayNameEn`: display-only labels, never calculation keys.
- `townHallLevel`: the Town Hall level for which the maximum applies.
- `catalogVersion`: the version of the technical reference being used.
- `observedAt`: the actual time at which a field was observed.
- `sourceType`: `OFFICIAL_API`, `GAME_EXPORT`, `MANUAL`, `CATALOG`, or another explicitly defined source.
- `sourceReference`: URL, path, original field, or screenshot reference.

Never assume that an official JSON array will always keep the same order. Do not use array positions as stable identities. Normalize names through a versioned alias registry; queue unknown names for review rather than dropping them.

## 13. Upgrade contracts and state machine

Represent each upgrade as a transition between two levels, rather than as a single accumulated number.

Suggested minimum states: `AVAILABLE`, `QUEUED`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`, `UNKNOWN`, and `NOT_APPLICABLE`, depending on game mechanics.

- `currentLevel` means the last **completed** level only.
- `targetLevel` means a desired or currently upgrading level.
- `startedAt` and `expectedFinishAt` may be recorded only when supported by the source.
- `remainingSeconds` is observed remaining time, not a catalog duration.
- `lastObservedAt` is required for stored timers.
- The system must not claim that an upgrade is confirmed complete merely because its projected end time has passed; require a new snapshot or label the completion as projected.
- Time changes, bonuses, magic items, and events can invalidate earlier forecasts.

Do not turn a `QUEUED` state into `IN_PROGRESS` without evidence.

## 14. Handling active upgrades in reports

The workbook must clearly distinguish completed levels, target levels, and observed time remaining.

Example: Smasher 3→4 with 9d20h observed.

- Display `Current level = 3`.
- Display `Upgrading to = 4`.
- Display `Observed remaining = 9d20h` with its observation timestamp.
- Never mark level 4 as completed.
- Do not add the full duration of 3→4 again when planning: use the observed remainder while it is still reliable.
- Add subsequent stages based on their catalog entries, if known.
- If the observation is old, mark the remainder `STALE` or projected instead of presenting it as a live timer.

Test this for heroes, guardians, buildings, pets, and research, considering their different queues.

## 15. Per-instance Supercharge model

Each eligible building owns its permanent level and its own Supercharge history.

Example with four Air Defenses:

| Instance | Permanent level | SC1 | SC2 |
|---|---:|---|---|
| A | max | completed | pending |
| B | max | pending | pending |
| C | max | completed | completed |
| D | max | unknown | unknown |

Preserve these differences. If the source only reports `SC1 x 2` for four buildings, maintain an **aggregate** count instead of fabricating which instances received the charge.

Do not infer SC2 completion merely because SC1 exists. Eligible stages vary by building type and catalog version. A game update may reset or replace charges; do not erase historical state from previous versions.

## 16. Aggregated buildings, counts, and level distribution

Some sources expose `Mortar x2`, `Wall x300`, or `Gold Mine x4`. These require distribution models.

- Track `totalCount`, `knownAtMaxCount`, and any available level/SC distributions.
- Prefer `countByLevel` and `countBySuperchargeStage` over a single averaged `currentLevel` for repeated units.
- When complete, distributions should sum to the number of represented units.
- Keep an `unclassifiedCount` if the distribution is partial.
- Do not interpret `four units eligible for the same upgrade` as four already upgraded units.
- Do not multiply all durations and costs by four when some units have already completed a stage.
- If a new import separates an aggregate into instances, preserve origin and reconcile without double counting.

The workbook should allow stage-count editing without forcing a separate row for every wall.

## 17. Metrics, coverage, and confidence

Always separate **progress** from **data completeness**.

- `cataloguedProgress`: completed applicable steps / cataloged applicable steps.
- `coverage`: confirmed fields or instances / expected fields or instances.
- `permanentProgress`: completed permanent levels / applicable permanent levels.
- `superchargeProgress`: completed Supercharge stages / applicable Supercharge stages.
- `remainingWorkSecondsKnown`: sum of known pending work durations.
- `unknownUpgradeStepCount`: upgrade stages lacking a confirmed duration.
- `potentiallyMissingEntityCount`: unverified entity types or instances.

For example, 95% cataloged progress with 80% coverage does **not** mean only 5% of the entire account remains.

Never claim a confirmed 100% maxed village if coverage is incomplete, conflicts remain unresolved, or required items are pending. Every metric shown on the Dashboard must have a documented and traceable formula.

## 18. Duration calculations: units and rounding

Use seconds stored as `long` or Java `Duration` internally to avoid repeated rounding errors.

- Store `baseDurationSeconds` per step, not only display text such as `5d 8h`.
- Express `goldPassBoost` as a decimal fraction between `0` and `1`.
- For forecasting, calculate `discounted = base × (1 - boost)` only on eligible future upgrades.
- Document rounding; never round every individual step to days before summing.
- Add exact seconds, then format days/hours/minutes for display.
- If the catalog/game returns an already-discounted duration, mark `durationOrigin = OBSERVED_DISCOUNTED` and do not discount it again.
- Keep `observedRemainingSeconds` as a distinct input for active upgrades.

If any duration is unknown, return a known subtotal plus missing-step details rather than a false exact total.

## 19. Calendar estimation and concurrency

Completion forecasts must respect the actual capacity of each queue.

- Builders: configured maximum parallel capacity (six permanent in the snapshot).
- Laboratory: its own queue; one research at a time when the rule is verified.
- Pets: a separate queue, never merged blindly into laboratory work.
- Guardians/heroes: validate consumed capacity before scheduling.
- Goblin Builder: count only in its configured real availability window.
- Dependencies: Supercharges require completed permanent levels; consecutive levels of one instance cannot happen in parallel.
- Active upgrades: reserve capacity until the projected completion.
- Resources: an ideal scenario must state the assumption that enough resources are available.

Support at least `IDEAL_CONTINUOUS` and `CONFIGURED_AVAILABILITY` scenarios. Identify lower bounds, heuristic results, and actually confirmed dates distinctly.

## 20. Magic items, events, and temporary boosts

The MVP need not simulate books, hammers, potions, and special events, but its domain model should allow later expansion.

- Never assume magic-item usage in the baseline estimate.
- Never fabricate the number of owned books or hammers.
- Gold Pass benefits are season-dependent and cannot always be projected unchanged indefinitely.
- Temporary boosts must not be stacked without documented rules.
- If the user finishes an upgrade early, the next observation supersedes the old forecast while history remains intact.
- Future event handling should record `source`, `start`, `end`, and `effect`.

Every forecast must label its scenario and considered benefits.

## 21. Snapshot history and diff

Save every collection as a new immutable observation.

- Snapshot identity: player + observation instant + source or unique identifier.
- Produce a `diff` covering levels, SC, counts, queues, and coverage.
- Distinguish `CHANGE_CONFIRMED`, `CHANGE_INFERRED`, `CONFLICT`, and `NO_CHANGE`.
- Do not silently lower a previous level without checking catalog changes, manual corrections, or bad data.
- A missing field in one reading does not mean that an entity disappeared.
- Game updates may add entities or stages to the progress denominator, lowering the percentage even though the player lost no progress.
- The historical report must be reproducible with the catalog version that applied at the time.

Never rewrite old snapshots to correct the current state; create a traceable correction when required.

## 22. Catalog contracts and game versions

Every catalog identifies its version, review timestamp, sources, and scope (for example TH18).

- `entityKey`, `fromLevel`, `toLevel`, `upgradeType`, and `catalogVersion` form an unambiguous logical key.
- Supercharge stages require explicit source and target stages.
- Conflicting durations for the same key must be flagged, not quietly coalesced.
- Missing stages enter `pending-validation` rather than being interpolated.
- Building max levels and availability can change with patches; bind forecasts to the catalog version.
- Links are references, not proof of verification. Record consultation date and, when possible, source revision.
- If a patch renders an old version incompatible, raise a diagnostic and require catalog reconciliation.

The agent must validate the existing JSON/CSV against this contract before relying on it.

## 23. Official API and game-export policy

The official player API may expose levels for certain profile entities, but complete buildings, Supercharges, and timers **must never be assumed**.

- Inspect official documentation and real schemas before implementing DTOs.
- Store credentials in environment variables, e.g. `CLASH_API_TOKEN`.
- Log HTTP status, timeouts, and parsing failures without exposing secrets.
- Handle 401, 403, 404, 429, and 5xx distinctly.
- A manual village export requires a genuine sample; before receiving one, implement only the interface and documented import contract.
- Preserve raw payloads with version information, according to the user's chosen retention policy.
- Do not automate unauthorized game access or bypass provider restrictions.

Never claim that the API updated data it did not return.

## 24. Excel contract and editing experience

The workbook must be useful to someone unfamiliar with the application's internal structure.

- Dashboard with headline numbers, last synchronization, coverage, and validation issues.
- Focused category sheets with filters and clear column labels.
- Editable input fields visually separated from formulas and generated values.
- Active queue with completed level, target, and observed remaining time.
- Upgrade steps with base duration, Gold Pass duration, and individual source.
- Per-instance or explicitly aggregate Supercharges.
- Formulas and pie chart tied to editable workbook content.
- No formula should depend on a fixed row that disappears when new items arrive.
- Validate compatibility with desktop Excel; do not ship files that trigger repair warnings.

Do not silently discard manual changes when regenerating a report.

## 25. Import and manual-merge contract

Avoid a naive 'last source always wins' policy.

- Official data has priority only for fields genuinely returned and comparable at observation time.
- Game exports can enrich buildings/queues only if the real format supports it.
- Manual edits should record the editor, time, field, and reason where possible.
- Conflicts must produce validation records containing both competing values and provenance.
- Reconciliation must retain unknown information without deleting known fields.
- If an aggregate is replaced by instances, record the transformation and avoid double counting.
- A repeated import of the same file must be idempotent.

Users should be able to inspect reconciliation results before they reach Excel.

## 26. CLI and local execution contract

The user wants to run the process manually at any time.

Proposed commands to implement, not claims that they already exist:

```bash
java -jar target/clash-village-tracker.jar report --snapshot data/snapshot_2026-10-19.json --catalog data/upgrade_steps.json --gold-pass-boost 0.20 --output output/
java -jar target/clash-village-tracker.jar validate-catalog --catalog data/upgrade_steps.json
```

- Invalid arguments must produce readable help and a non-zero exit status.
- Logs must identify start/end times, processed counts, conflicts, and artifacts.
- Never overwrite historical input without an explicit command.
- Offline mode must work without a token, network, or real endpoints.
- A future `sync` command may use the API only when configured and authorized.

## 27. Test design and fixtures

Use small, explicit, synthetic fixtures.

Additional mandatory scenarios:

1. Baby Dragon 9→12 produces three separate upgrades, not one.
2. An aggregate of four Air Defenses can have partial SC without fabricated instance assignments.
3. Wizard Tower 13→17 has four remaining transitions.
4. Smasher upgrading to 4 still has completed level 3.
5. Laboratory and pet upgrades do not blindly consume village builders.
6. Reimporting the same snapshot does not duplicate entities or stages.
7. Changing `catalogVersion` may alter max levels without rewriting history.
8. Dashboard formulas recalculate after an input-sheet edit.
9. A missing catalog duration results in a known subtotal and a validation issue.
10. The project compiles and the generated workbook opens without structural errors.

Do not limit tests to getters or file existence: validate actual domain outcomes.

## 28. Git, confidentiality, and tool boundaries

The repository may become public, so do not commit sensitive information.

- Configure `.gitignore` for `target/`, `.idea/`, `.env`, credentials, temporary files, and appropriate outputs.
- Never include API tokens in Markdown, code screenshots, tests, or URLs.
- Never use `git push --force` without explicit request and justification.
- Do not delete existing local user work while reorganizing the repository.
- Do not change remote branches or access permissions without approval.
- Do not bypass the IDE's permission controls.
- Never claim an integration, test, or publication was executed when only documented.

## 29. MVP acceptance criteria

The offline MVP is complete only when it:

- Imports historical JSON snapshots and catalogs without network access.
- Produces consistent entities and a list of upgrades per level/SC stage.
- Preserves completed levels distinct from active upgrades.
- Calculates cumulative progress and data coverage.
- Displays known durations with/without Gold Pass while highlighting missing durations.
- Generates a usable Excel workbook with sheets, formulas, and chart.
- Preserves originals, history, and manual edits according to its contract.
- Provides verifiable Windows execution instructions.
- Passes meaningful tests and documents remaining limitations.

Do not declare completion merely because an empty or incomplete XLSX can be written.

## 30. Agent work protocol

For an implementation task:

1. Inspect relevant source code, tests, specifications, and data.
2. Briefly state your understanding and plan, without unnecessary approval requests.
3. Implement an executable vertical slice.
4. Build and test the implementation.
5. Review output, diffs, and risks.
6. Update documentation and the spec checklist.
7. Report completed work and remaining work objectively.

For multi-session work, keep a project progress note with verifiable facts. Do not mistake planned tasks for implemented code.

## 31. Language and documentation policy

This file intentionally contains **Brazilian Portuguese first, English second**.

- Both sections translate the same policy; they are not two cumulative requirement lists.
- Java code, class names, methods, JSON properties, commits, and technical messages should generally use consistent English.
- The Excel interface may initially be PT-BR, with later internationalization support.
- Technical `supercharge` maps explicitly to Portuguese “Sobrecarga”.
- Existing Portuguese domain documents remain valid until translated and verified.
- Update both languages consistently whenever a mandatory rule changes.

## 32. Final integrity principle

**Integrity, traceability, and reproducibility take priority over a misleadingly precise max-out date.**

No external source, sample file, or agent instruction allows fabricated data. The best result is a usable report that differentiates confirmed facts, estimates, and outstanding validation and can be reproduced using a snapshot plus a versioned catalog.
